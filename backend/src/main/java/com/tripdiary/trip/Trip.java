package com.tripdiary.trip;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.tripdiary.global.persistence.BaseTimeEntity;
import com.tripdiary.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "trips")
public class Trip extends BaseTimeEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 50)
    private String timezone;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    private final List<TripDay> days = new ArrayList<>();

    protected Trip() {}

    public Trip(User user, String title, LocalDate startDate, LocalDate endDate, String timezone) {
        this.id = UUID.randomUUID();
        this.user = user;
        changeDetails(title, startDate, endDate, timezone);
        buildDays();
    }

    void changeDetails(String title, LocalDate startDate, LocalDate endDate, String timezone) {
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.timezone = timezone;
    }

    void clearDays() {
        days.clear();
    }

    void buildDays() {
        int dayNumber = 1;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            days.add(new TripDay(this, date, dayNumber++));
        }
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getTimezone() { return timezone; }
    public List<TripDay> getDays() { return List.copyOf(days); }
}
