CREATE TABLE diary_entries (
    id UUID PRIMARY KEY,
    trip_day_id UUID NOT NULL,
    itinerary_id UUID,
    place_id UUID,
    title VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_diary_entries_trip_day FOREIGN KEY (trip_day_id) REFERENCES trip_days (id) ON DELETE CASCADE,
    CONSTRAINT fk_diary_entries_itinerary FOREIGN KEY (itinerary_id) REFERENCES itineraries (id) ON DELETE SET NULL,
    CONSTRAINT fk_diary_entries_place FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE SET NULL,
    CONSTRAINT ck_diary_entries_title_not_blank CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_diary_entries_content_not_blank CHECK (length(trim(content)) > 0)
);

CREATE INDEX idx_diary_entries_day_updated ON diary_entries (trip_day_id, updated_at DESC, id);
