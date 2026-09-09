package com.tripdiary.trip;

import java.time.LocalDate;
import java.util.UUID;

import com.tripdiary.global.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "trip_days")
public class TripDay extends BaseTimeEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "trip_date", nullable = false)
    private LocalDate date;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    protected TripDay() {}

    TripDay(Trip trip, LocalDate date, int dayNumber) {
        this.id = UUID.randomUUID();
        this.trip = trip;
        this.date = date;
        this.dayNumber = dayNumber;
    }

    public UUID getId() { return id; }
    public LocalDate getDate() { return date; }
    public int getDayNumber() { return dayNumber; }
}
