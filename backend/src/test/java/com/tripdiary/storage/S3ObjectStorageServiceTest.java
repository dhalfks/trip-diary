package com.tripdiary.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ObjectStorageServiceTest {
    S3Client client;
    S3Presigner presigner;
    S3ObjectStorageService storage;

    @BeforeEach
    void setUp() {
        client = mock(S3Client.class);
        // Signing is local: fabricated test credentials, no AWS calls or real environment credentials.
        presigner = S3Presigner.builder().region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access-key", "test-secret-key"))).build();
        storage = new S3ObjectStorageService(client, presigner, new S3Properties("ap-northeast-2", "private-test-bucket", Duration.ofMinutes(5)));
    }

    @AfterEach
    void close() { presigner.close(); }

    @Test
    void signsShortLivedCreateOnlyPutWithTypeAndLengthBoundToSignature() throws IOException {
        Instant before = Instant.now();
        var upload = storage.createUploadUrl("images/random-key", "image/jpeg", 1234, Duration.ofMinutes(5));
        assertThat(upload.url().getScheme()).isEqualTo("https");
        assertThat(upload.url().getHost()).isEqualTo("private-test-bucket.s3.ap-northeast-2.amazonaws.com");
        assertThat(upload.url().getPath()).isEqualTo("/images/random-key");
        String query = URLDecoder.decode(upload.url().getRawQuery(), StandardCharsets.UTF_8);
        assertThat(query).contains("X-Amz-Expires=300", "X-Amz-Signature=", "content-length", "content-type", "if-none-match");
        assertThat(upload.headers()).containsEntry("content-type", List.of("image/jpeg"))
                .containsEntry("content-length", List.of("1234")).containsEntry("if-none-match", List.of("*"));
        assertThat(upload.headers()).doesNotContainKeys("host", "x-amz-acl");
        assertThat(upload.expiresAt()).isBetween(before.plusSeconds(299), Instant.now().plusSeconds(301));
        verifyNoInteractions(client);
    }

    @Test
    void downloadUrlIsSignedAndValidityIsBounded() throws IOException {
        assertThat(storage.createDownloadUrl("images/key", Duration.ofMinutes(5)).getQuery()).contains("X-Amz-Signature=", "X-Amz-Expires=300");
        for (Duration duration : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMinutes(16))) {
            assertThatThrownBy(() -> storage.createUploadUrl("key", "image/jpeg", 1, duration)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.createDownloadUrl("key", duration)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new S3Properties("ap-northeast-2", "bucket", duration)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(client);
    }

    @Test
    void headReturnsMetadataAndOnly404IsMissing() throws IOException {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(1234L).contentType("image/jpeg").build())
                .thenThrow(S3Exception.builder().statusCode(404).build())
                .thenThrow(S3Exception.builder().statusCode(403).build())
                .thenThrow(SdkClientException.create("network error"));
        assertThat(storage.head("key")).contains(new PresignedObjectStorageService.ObjectMetadata(1234, "image/jpeg"));
        assertThat(storage.head("key")).isEmpty();
        assertThatThrownBy(() -> storage.head("key")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> storage.head("key")).isInstanceOf(IOException.class);
        ArgumentCaptor<HeadObjectRequest> request = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(client, times(4)).headObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("private-test-bucket");
        assertThat(request.getValue().key()).isEqualTo("key");
    }

    @Test
    void implementsExistingUploadAndDeleteWithoutPublicAcl() throws IOException {
        storage.upload("key", new ByteArrayInputStream(new byte[]{1}), "image/jpeg", 1);
        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getValue().acl()).isNull();
        assertThat(put.getValue().ifNoneMatch()).isEqualTo("*");
        assertThat(put.getValue().contentLength()).isEqualTo(1);
        storage.delete("key");
        verify(client).deleteObject(DeleteObjectRequest.builder().bucket("private-test-bucket").key("key").build());
        assertThat(storage.storageType()).isEqualTo(StorageType.S3);
    }

    @Test
    void missingObjectDeletionIsSuccessfulButMissingBucketIsNot() throws IOException {
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build())
                .thenThrow(NoSuchKeyException.builder().statusCode(404).build())
                .thenThrow(S3Exception.builder().statusCode(404).awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchKey").build()).build())
                .thenThrow(S3Exception.builder().statusCode(404).awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchBucket").build()).build());
        storage.delete("missing");
        storage.delete("missing");
        storage.delete("missing");
        assertThatThrownBy(() -> storage.delete("missing")).isInstanceOf(IOException.class);
    }

    @Test
    void deleteAccessAndNetworkFailuresAreReportedWithoutSdkDetails() {
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).build())
                .thenThrow(SdkClientException.create("request details"));
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> storage.delete("key")).isInstanceOf(IOException.class)
                    .hasMessage("Object storage is unavailable").hasNoCause();
        }
    }

    @Test
    void downloadResponseDisablesPrivateImageCaching() throws IOException {
        String query = URLDecoder.decode(storage.createDownloadUrl("key", Duration.ofMinutes(5)).getRawQuery(), StandardCharsets.UTF_8);
        assertThat(query).contains("response-cache-control=private, no-store", "X-Amz-Signature=");
        verifyNoInteractions(client);
    }

    @Test
    void missingBucketAndCredentialsFailWithoutLeakingSdkDetails() {
        var unconfigured = new S3ObjectStorageService(client, presigner, new S3Properties("ap-northeast-2", "", Duration.ofMinutes(5)));
        assertThatThrownBy(() -> unconfigured.createUploadUrl("key", "image/jpeg", 1, Duration.ofMinutes(5)))
                .isInstanceOf(IOException.class).hasMessage("Object storage is unavailable");
        try (S3Presigner failing = S3Presigner.builder().region(Region.AP_NORTHEAST_2)
                .credentialsProvider(() -> { throw SdkClientException.create("credential detail"); }).build()) {
            var unavailable = new S3ObjectStorageService(client, failing, new S3Properties("ap-northeast-2", "bucket", Duration.ofMinutes(5)));
            assertThatThrownBy(() -> unavailable.createUploadUrl("key", "image/jpeg", 1, Duration.ofMinutes(5)))
                    .isInstanceOf(IOException.class).hasMessage("Object storage is unavailable").hasNoCause();
        }
        verifyNoInteractions(client);
    }
}
