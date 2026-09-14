package com.tripdiary.book;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.tripdiary.global.persistence.BaseTimeEntity;
import com.tripdiary.trip.Trip;
import jakarta.persistence.*;

@Entity
@Table(name = "travel_diaries")
public class TravelDiary extends BaseTimeEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "trip_id", nullable = false) private Trip trip;
    @Column(nullable = false, length = 120) private String title;
    @Enumerated(EnumType.STRING) @Column(name = "template_type", nullable = false, length = 20) private TemplateType templateType;
    @Column(name = "cover_image_id") private UUID coverImageId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DiaryStatus status;
    @OneToMany(mappedBy = "diary", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("pageOrder ASC")
    private List<DiaryPage> pages = new ArrayList<>();

    protected TravelDiary() {}

    public TravelDiary(Trip trip, String title, TemplateType templateType, UUID coverImageId, List<DiaryTemplate.PagePlan> plans) {
        this.id = UUID.randomUUID(); this.trip = trip; this.title = title; this.templateType = templateType;
        this.coverImageId = coverImageId; this.status = DiaryStatus.READY;
        for (DiaryTemplate.PagePlan plan : plans) pages.add(new DiaryPage(this, pages.size() + 1, plan));
    }

    public UUID getId() { return id; }
    public Trip getTrip() { return trip; }
    public String getTitle() { return title; }
    public TemplateType getTemplateType() { return templateType; }
    public UUID getCoverImageId() { return coverImageId; }
    public DiaryStatus getStatus() { return status; }
    public List<DiaryPage> getPages() { return List.copyOf(pages); }
}
