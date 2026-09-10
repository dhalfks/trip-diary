package com.tripdiary.itinerary;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/trips/{tripId}/places") @SecurityRequirement(name = "bearerAuth")
public class PlaceController {
    private final PlaceService service; private final PlaceSearchService searchService;
    public PlaceController(PlaceService service, PlaceSearchService searchService) { this.service = service; this.searchService = searchService; }
    @GetMapping public List<PlaceResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId) { return service.list(userId(jwt), tripId); }
    @GetMapping("/search")
    public List<PlaceSearchResult> search(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId,
            @RequestParam @NotBlank @Size(min = 2, max = 100) String query) {
        return searchService.search(userId(jwt), tripId, query);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public PlaceResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @Valid @RequestBody PlaceRequest request) { return service.create(userId(jwt), tripId, request.command()); }
    @PutMapping("/{placeId}")
    public PlaceResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID placeId, @Valid @RequestBody PlaceRequest request) { return service.update(userId(jwt), tripId, placeId, request.command()); }
    @DeleteMapping("/{placeId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId, @PathVariable UUID placeId) { service.delete(userId(jwt), tripId, placeId); }
    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    public record PlaceRequest(@NotBlank @Size(max = 120) String name, @Size(max = 300) String address,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude) {
        PlaceService.PlaceCommand command() { return new PlaceService.PlaceCommand(name, address, latitude, longitude); }
    }
}
