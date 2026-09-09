package com.tripdiary.trip;

import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/trips")
@SecurityRequirement(name = "bearerAuth")
public class TripController {
    private final TripService trips;

    public TripController(TripService trips) {
        this.trips = trips;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TripRequest request) {
        return trips.create(userId(jwt), request.toCommand());
    }

    @GetMapping
    public PageResponse<TripResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return trips.list(userId(jwt), page, size);
    }

    @GetMapping("/{tripId}")
    public TripResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId) {
        return trips.get(userId(jwt), tripId);
    }

    @PutMapping("/{tripId}")
    public TripResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tripId,
            @Valid @RequestBody TripRequest request) {
        return trips.update(userId(jwt), tripId, request.toCommand());
    }

    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId) {
        trips.delete(userId(jwt), tripId);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record TripRequest(
            @NotBlank @Size(max = 100) String title,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotBlank @Size(max = 50) String timezone) {
        TripService.TripCommand toCommand() {
            return new TripService.TripCommand(title, startDate, endDate, timezone);
        }
    }
}
