CREATE TABLE travel_diaries (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL,
    title VARCHAR(120) NOT NULL,
    template_type VARCHAR(20) NOT NULL,
    cover_image_id UUID,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_travel_diaries_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_diaries_cover FOREIGN KEY (cover_image_id) REFERENCES images (id) ON DELETE SET NULL,
    CONSTRAINT ck_travel_diaries_title CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_travel_diaries_template CHECK (template_type IN ('CLASSIC', 'PHOTO')),
    CONSTRAINT ck_travel_diaries_status CHECK (status IN ('READY'))
);

CREATE INDEX idx_travel_diaries_trip_created ON travel_diaries (trip_id, created_at DESC, id);

CREATE TABLE diary_pages (
    id UUID PRIMARY KEY,
    diary_id UUID NOT NULL,
    page_order INTEGER NOT NULL,
    page_type VARCHAR(20) NOT NULL,
    layout_type VARCHAR(20) NOT NULL,
    content JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_diary_pages_diary FOREIGN KEY (diary_id) REFERENCES travel_diaries (id) ON DELETE CASCADE,
    CONSTRAINT uk_diary_pages_order UNIQUE (diary_id, page_order),
    CONSTRAINT ck_diary_pages_order CHECK (page_order >= 1),
    CONSTRAINT ck_diary_pages_type CHECK (page_type IN ('COVER', 'ENTRY', 'PHOTOS')),
    CONSTRAINT ck_diary_pages_layout CHECK (layout_type IN ('COVER', 'TEXT', 'PHOTO_TEXT', 'PHOTO_GRID'))
);
