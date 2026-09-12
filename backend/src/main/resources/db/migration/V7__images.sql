CREATE TABLE images (
    id UUID PRIMARY KEY,
    diary_entry_id UUID NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(1024) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_images_diary_entry FOREIGN KEY (diary_entry_id) REFERENCES diary_entries (id) ON DELETE CASCADE,
    CONSTRAINT uk_images_storage_key UNIQUE (storage_type, storage_key),
    CONSTRAINT ck_images_original_file_name_not_blank CHECK (length(trim(original_file_name)) > 0),
    CONSTRAINT ck_images_storage_key_not_blank CHECK (length(trim(storage_key)) > 0),
    CONSTRAINT ck_images_content_type_not_blank CHECK (length(trim(content_type)) > 0),
    CONSTRAINT ck_images_file_size_non_negative CHECK (file_size >= 0),
    CONSTRAINT ck_images_storage_type CHECK (storage_type IN ('LOCAL', 'S3'))
);

CREATE INDEX idx_images_diary_created ON images (diary_entry_id, created_at, id);
