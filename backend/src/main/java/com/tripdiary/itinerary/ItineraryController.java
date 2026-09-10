package com.tripdiary.itinerary;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/trips/{tripId}/days/{dayId}/itineraries") @SecurityRequirement(name = "bearerAuth")
public class ItineraryController {
    private final ItineraryService service;
    public ItineraryController(ItineraryService service) { this.service = service; }
    @GetMapping public List<ItineraryResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID dayId) { return service.list(userId(jwt), tripId, dayId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ItineraryResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID dayId, @Valid @RequestBody ItineraryRequest request) { return service.create(userId(jwt), tripId, dayId, request.command()); }
    @PutMapping("/{itemId}")
    public ItineraryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID dayId, @PathVariable UUID itemId, @Valid @RequestBody ItineraryRequest request) { return service.update(userId(jwt), tripId, dayId, itemId, request.command()); }
    @DeleteMapping("/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID dayId, @PathVariable UUID itemId) { service.delete(userId(jwt), tripId, dayId, itemId); }
    @PutMapping("/order")
    public List<ItineraryResponse> reorder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID dayId, @Valid @RequestBody OrderRequest request) { return service.reorder(userId(jwt), tripId, dayId, request.itineraryIds()); }
    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    public record ItineraryRequest(@NotBlank @Size(max = 120) String title, @Size(max = 1000) String notes, LocalTime startTime, LocalTime endTime, UUID placeId) {
        ItineraryService.ItineraryCommand command() { return new ItineraryService.ItineraryCommand(title, notes, startTime, endTime, placeId); }
    }
    public record OrderRequest(@NotNull @Size(max = 200) List<@NotNull UUID> itineraryIds) {}
}
