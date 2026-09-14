package com.tripdiary.book;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/diaries")
@SecurityRequirement(name = "bearerAuth")
public class TravelDiaryController {
    private final TravelDiaryService service;
    public TravelDiaryController(TravelDiaryService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TravelDiaryResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @Valid @RequestBody GenerateRequest request) {
        return service.create(UUID.fromString(jwt.getSubject()), tripId,
                new TravelDiaryService.GenerateCommand(request.title(), request.templateType(), request.coverImageId()));
    }

    @GetMapping
    public List<TravelDiaryResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId) {
        return service.list(UUID.fromString(jwt.getSubject()), tripId);
    }

    @GetMapping("/{diaryId}")
    public ResponseEntity<TravelDiaryResponse.Detail> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID diaryId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(UUID.fromString(jwt.getSubject()), tripId, diaryId));
    }

    @DeleteMapping("/{diaryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID diaryId) {
        service.delete(UUID.fromString(jwt.getSubject()), tripId, diaryId);
    }

    public record GenerateRequest(@Size(max = 120) String title, @NotNull TemplateType templateType, UUID coverImageId) {}
}
