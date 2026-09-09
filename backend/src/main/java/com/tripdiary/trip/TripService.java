package com.tripdiary.trip;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TripService {
    static final int MAX_TRIP_DAYS = 366;

    private final TripRepository trips;
    private final UserRepository users;

    public TripService(TripRepository trips, UserRepository users) {
        this.trips = trips;
        this.users = users;
    }

    @Transactional
    public TripResponse create(UUID userId, TripCommand command) {
        ValidatedTrip value = validate(command);
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Trip trip = trips.saveAndFlush(new Trip(user, value.title(), value.startDate(), value.endDate(), value.timezone()));
        return TripResponse.from(trip);
    }

    public PageResponse<TripResponse> list(UUID userId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(trips.findAllByUserId(userId, pageable).map(TripResponse::from));
    }

    public TripResponse get(UUID userId, UUID tripId) {
        return TripResponse.from(findOwned(userId, tripId));
    }

    @Transactional
    public TripResponse update(UUID userId, UUID tripId, TripCommand command) {
        ValidatedTrip value = validate(command);
        Trip trip = findOwned(userId, tripId);
        trip.clearDays();
        trips.flush();
        trip.changeDetails(value.title(), value.startDate(), value.endDate(), value.timezone());
        trip.buildDays();
        trips.flush();
        return TripResponse.from(trip);
    }

    @Transactional
    public void delete(UUID userId, UUID tripId) {
        trips.delete(findOwned(userId, tripId));
    }

    private Trip findOwned(UUID userId, UUID tripId) {
        return trips.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
    }

    private ValidatedTrip validate(TripCommand command) {
        String title = command.title().strip();
        LocalDate start = command.startDate();
        LocalDate end = command.endDate();
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_TRIP_PERIOD, "종료일은 시작일보다 빠를 수 없습니다.");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_TRIP_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_TRIP_PERIOD, "여행 기간은 최대 366일입니다.");
        }
        try {
            return new ValidatedTrip(title, start, end, ZoneId.of(command.timezone().strip()).getId());
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_TIMEZONE);
        }
    }

    public record TripCommand(String title, LocalDate startDate, LocalDate endDate, String timezone) {}
    private record ValidatedTrip(String title, LocalDate startDate, LocalDate endDate, String timezone) {}
}
