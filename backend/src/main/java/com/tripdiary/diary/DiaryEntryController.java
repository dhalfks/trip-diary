package com.tripdiary.diary;

import java.util.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/days/{dayId}/entries")
@SecurityRequirement(name = "bearerAuth")
public class DiaryEntryController {
    private final DiaryEntryService service;
    public DiaryEntryController(DiaryEntryService service) { this.service = service; }

    @GetMapping
    public List<DiaryEntryResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID dayId) {
        return service.list(userId(jwt), tripId, dayId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiaryEntryResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID dayId, @Valid @RequestBody DiaryEntryRequest request) {
        return service.create(userId(jwt), tripId, dayId, request.command());
    }

    @PutMapping("/{entryId}")
    public DiaryEntryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID dayId, @PathVariable UUID entryId, @Valid @RequestBody DiaryEntryRequest request) {
        return service.update(userId(jwt), tripId, dayId, entryId, request.command());
    }

    @DeleteMapping("/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID dayId, @PathVariable UUID entryId) {
        service.delete(userId(jwt), tripId, dayId, entryId);
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    public record DiaryEntryRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 20000) String content,
            UUID itineraryId,
            UUID placeId) {
        DiaryEntryService.DiaryEntryCommand command() { return new DiaryEntryService.DiaryEntryCommand(title, content, itineraryId, placeId); }
    }
}
