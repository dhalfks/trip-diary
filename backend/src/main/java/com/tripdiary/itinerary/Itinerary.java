package com.tripdiary.itinerary;

import java.time.LocalTime;
import java.util.UUID;
import com.tripdiary.global.persistence.BaseTimeEntity;
import com.tripdiary.trip.TripDay;
import jakarta.persistence.*;

@Entity @Table(name = "itineraries")
public class Itinerary extends BaseTimeEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "trip_day_id", nullable = false) private TripDay tripDay;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "place_id") private Place place;
    @Column(nullable = false, length = 120) private String title;
    @Column(length = 1000) private String notes;
    @Column(name = "start_time") private LocalTime startTime;
    @Column(name = "end_time") private LocalTime endTime;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    protected Itinerary() {}
    public Itinerary(TripDay day, String title, String notes, LocalTime start, LocalTime end, Place place, int order) { id = UUID.randomUUID(); tripDay = day; sortOrder = order; change(title, notes, start, end, place); }
    public void change(String title, String notes, LocalTime start, LocalTime end, Place place) { this.title = title; this.notes = notes; startTime = start; endTime = end; this.place = place; }
    public void changeSortOrder(int value) { sortOrder = value; }
    public UUID getId() { return id; } public TripDay getTripDay() { return tripDay; } public Place getPlace() { return place; }
    public String getTitle() { return title; } public String getNotes() { return notes; } public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; } public int getSortOrder() { return sortOrder; }
}
