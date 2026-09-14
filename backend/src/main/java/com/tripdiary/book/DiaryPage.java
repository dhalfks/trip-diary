package com.tripdiary.book;

import java.util.Map;
import java.util.UUID;
import com.tripdiary.global.persistence.BaseTimeEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "diary_pages", uniqueConstraints = @UniqueConstraint(name = "uk_diary_pages_order", columnNames = {"diary_id", "page_order"}))
public class DiaryPage extends BaseTimeEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "diary_id", nullable = false) private TravelDiary diary;
    @Column(name = "page_order", nullable = false) private int pageOrder;
    @Enumerated(EnumType.STRING) @Column(name = "page_type", nullable = false, length = 20) private PageType pageType;
    @Enumerated(EnumType.STRING) @Column(name = "layout_type", nullable = false, length = 20) private LayoutType layoutType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private Map<String, Object> content;

    protected DiaryPage() {}

    DiaryPage(TravelDiary diary, int pageOrder, DiaryTemplate.PagePlan plan) {
        this.id = UUID.randomUUID(); this.diary = diary; this.pageOrder = pageOrder;
        this.pageType = plan.pageType(); this.layoutType = plan.layoutType(); this.content = Map.copyOf(plan.content());
    }

    public UUID getId() { return id; }
    public int getPageOrder() { return pageOrder; }
    public PageType getPageType() { return pageType; }
    public LayoutType getLayoutType() { return layoutType; }
    public Map<String, Object> getContent() { return Map.copyOf(content); }
}
