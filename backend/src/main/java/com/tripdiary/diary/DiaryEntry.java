package com.tripdiary.diary;

import java.util.UUID;
import com.tripdiary.global.persistence.BaseTimeEntity;
import com.tripdiary.itinerary.Itinerary;
import com.tripdiary.itinerary.Place;
import com.tripdiary.trip.TripDay;
import jakarta.persistence.*;

@Entity
@Table(name = "diary_entries")
public class DiaryEntry extends BaseTimeEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "trip_day_id", nullable = false) private TripDay tripDay;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "itinerary_id") private Itinerary itinerary;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "place_id") private Place place;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;

    protected DiaryEntry() {}

    public DiaryEntry(TripDay tripDay, String title, String content, Itinerary itinerary, Place place) {
        this.id = UUID.randomUUID();
        this.tripDay = tripDay;
        change(title, content, itinerary, place);
    }

    public void change(String title, String content, Itinerary itinerary, Place place) {
        this.title = title;
        this.content = content;
        this.itinerary = itinerary;
        this.place = place;
    }

    public UUID getId() { return id; }
    public TripDay getTripDay() { return tripDay; }
    public Itinerary getItinerary() { return itinerary; }
    public Place getPlace() { return place; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
}
