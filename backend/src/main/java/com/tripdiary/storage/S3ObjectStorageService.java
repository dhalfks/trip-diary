package com.tripdiary.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

@Service
public class S3ObjectStorageService implements PresignedObjectStorageService {
    private final S3Client client;
    private final S3Presigner presigner;
    private final S3Properties properties;

    public S3ObjectStorageService(S3Client client, S3Presigner presigner, S3Properties properties) {
        this.client = client; this.presigner = presigner; this.properties = properties;
    }

    @Override
    public StorageType storageType() { return StorageType.S3; }

    @Override
    public void upload(String key, InputStream content, String contentType, long fileSize) throws IOException {
        requireBucket();
        try {
            client.putObject(putRequest(key, contentType, fileSize), RequestBody.fromInputStream(content, fileSize));
        } catch (SdkException exception) { throw unavailable(); }
    }

    @Override
    public void delete(String key) throws IOException {
        requireBucket();
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        } catch (NoSuchKeyException exception) {
            // A confirmed absent object is already deleted. Do not hide missing buckets or access failures.
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404 || exception.awsErrorDetails() == null
                    || !"NoSuchKey".equals(exception.awsErrorDetails().errorCode())) throw unavailable();
        } catch (SdkException exception) { throw unavailable(); }
    }

    @Override
    public URI createDownloadUrl(String key, Duration validity) throws IOException {
        requireBucket();
        S3Properties.requireShortValidity(validity);
        try {
            return URI.create(presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(validity)
                    .getObjectRequest(GetObjectRequest.builder().bucket(properties.bucket()).key(key)
                            .responseCacheControl("private, no-store").build())
                    .build()).url().toExternalForm());
        } catch (SdkException exception) { throw unavailable(); }
    }

    @Override
    public PresignedUpload createUploadUrl(String key, String contentType, long fileSize, Duration validity) throws IOException {
        requireBucket();
        S3Properties.requireShortValidity(validity);
        try {
            PresignedPutObjectRequest signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(validity).putObjectRequest(putRequest(key, contentType, fileSize)).build());
            Map<String, List<String>> headers = new LinkedHashMap<>();
            signed.signedHeaders().forEach((name, values) -> {
                // HTTP clients supply Host from the URL; callers must preserve all other signed headers.
                if (!name.equalsIgnoreCase("host")) headers.put(name, List.copyOf(values));
            });
            return new PresignedUpload(URI.create(signed.url().toExternalForm()), Map.copyOf(headers), signed.expiration());
        } catch (SdkException exception) { throw unavailable(); }
    }

    @Override
    public Optional<ObjectMetadata> head(String key) throws IOException {
        requireBucket();
        try {
            HeadObjectResponse object = client.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(key).build());
            return Optional.of(new ObjectMetadata(object.contentLength(), object.contentType()));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) return Optional.empty();
            throw unavailable();
        } catch (SdkException exception) { throw unavailable(); }
    }

    private PutObjectRequest putRequest(String key, String contentType, long fileSize) {
        return PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                .contentType(contentType).contentLength(fileSize).ifNoneMatch("*").build();
    }

    private void requireBucket() throws IOException {
        if (properties.bucket() == null || properties.bucket().isBlank()) throw unavailable();
    }

    // Do not propagate SDK messages that can contain signed requests or credential details.
    private IOException unavailable() { return new IOException("Object storage is unavailable"); }
}
