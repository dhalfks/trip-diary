package com.tripdiary.image;

import java.util.UUID;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/days/{dayId}/entries/{entryId}/images")
@SecurityRequirement(name = "bearerAuth")
public class ImageUploadController {
    private final ImageUploadService service;
    public ImageUploadController(ImageUploadService service) { this.service = service; }

    @PostMapping("/upload-url")
    public ResponseEntity<ImageUploadResponse> initiate(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId, @PathVariable UUID dayId, @PathVariable UUID entryId,
            @Valid @RequestBody ImageUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.initiate(UUID.fromString(jwt.getSubject()), tripId, dayId, entryId,
                        new ImageUploadService.ImageUploadCommand(request.originalFileName(), request.contentType(), request.fileSize())));
    }

    @PostMapping("/{imageId}/complete")
    public ImageUploadService.ImageUploadCompletionResponse complete(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId, @PathVariable UUID dayId, @PathVariable UUID entryId, @PathVariable UUID imageId) {
        return service.complete(UUID.fromString(jwt.getSubject()), tripId, dayId, entryId, imageId);
    }

    public record ImageUploadRequest(@NotBlank @Size(max = 255) String originalFileName,
                                     @NotBlank @Size(max = 255) String contentType,
                                     @NotNull @Positive @Max(ImageUploadService.MAX_FILE_SIZE) Long fileSize) {}
}
