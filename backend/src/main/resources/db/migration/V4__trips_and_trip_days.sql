CREATE TABLE trips (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    timezone VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_trips_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_trips_date_range CHECK (end_date >= start_date)
);

CREATE INDEX idx_trips_user_created_at ON trips (user_id, created_at DESC, id DESC);

CREATE TABLE trip_days (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL,
    trip_date DATE NOT NULL,
    day_number INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_trip_days_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT uk_trip_days_trip_date UNIQUE (trip_id, trip_date),
    CONSTRAINT uk_trip_days_trip_number UNIQUE (trip_id, day_number),
    CONSTRAINT ck_trip_days_number CHECK (day_number > 0)
);

CREATE INDEX idx_trip_days_trip_date ON trip_days (trip_id, trip_date);
