package com.tripdiary.itinerary;

import java.math.BigDecimal;
import java.util.UUID;
import com.tripdiary.global.persistence.BaseTimeEntity;
import com.tripdiary.trip.Trip;
import jakarta.persistence.*;

@Entity @Table(name = "places")
public class Place extends BaseTimeEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "trip_id", nullable = false) private Trip trip;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 300) private String address;
    @Column(precision = 9, scale = 6) private BigDecimal latitude;
    @Column(precision = 9, scale = 6) private BigDecimal longitude;
    protected Place() {}
    public Place(Trip trip, String name, String address, BigDecimal latitude, BigDecimal longitude) { id = UUID.randomUUID(); this.trip = trip; change(name, address, latitude, longitude); }
    public void change(String name, String address, BigDecimal latitude, BigDecimal longitude) { this.name = name; this.address = address; this.latitude = latitude; this.longitude = longitude; }
    public UUID getId() { return id; } public Trip getTrip() { return trip; } public String getName() { return name; }
    public String getAddress() { return address; } public BigDecimal getLatitude() { return latitude; } public BigDecimal getLongitude() { return longitude; }
}
