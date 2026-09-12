package com.tripdiary.image;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/days/{dayId}/entries/{entryId}/images")
@SecurityRequirement(name = "bearerAuth")
public class ImageController {
    private final ImageService service;
    public ImageController(ImageService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<ImageViewResponse>> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId,
            @PathVariable UUID dayId, @PathVariable UUID entryId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(UUID.fromString(jwt.getSubject()), tripId, dayId, entryId));
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID dayId,
            @PathVariable UUID entryId, @PathVariable UUID imageId) {
        service.delete(UUID.fromString(jwt.getSubject()), tripId, dayId, entryId, imageId);
    }
}
