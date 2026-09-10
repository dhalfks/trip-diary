CREATE TABLE places (
    id UUID PRIMARY KEY, trip_id UUID NOT NULL, name VARCHAR(120) NOT NULL, address VARCHAR(300),
    latitude NUMERIC(9, 6), longitude NUMERIC(9, 6),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_places_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT ck_places_coordinates_pair CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_places_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_places_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);
CREATE INDEX idx_places_trip_name ON places (trip_id, name, id);

CREATE TABLE itineraries (
    id UUID PRIMARY KEY, trip_day_id UUID NOT NULL, place_id UUID, title VARCHAR(120) NOT NULL,
    notes VARCHAR(1000), start_time TIME, end_time TIME, sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_itineraries_trip_day FOREIGN KEY (trip_day_id) REFERENCES trip_days (id) ON DELETE CASCADE,
    CONSTRAINT fk_itineraries_place FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE SET NULL,
    CONSTRAINT ck_itineraries_times_pair CHECK ((start_time IS NULL) = (end_time IS NULL)),
    CONSTRAINT ck_itineraries_time_range CHECK (end_time IS NULL OR end_time > start_time),
    CONSTRAINT ck_itineraries_sort_order CHECK (sort_order >= 0),
    CONSTRAINT uk_itineraries_day_order UNIQUE (trip_day_id, sort_order)
);
CREATE INDEX idx_itineraries_day_order ON itineraries (trip_day_id, sort_order, id);
