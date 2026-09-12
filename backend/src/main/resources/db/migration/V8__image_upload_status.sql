-- Preserve metadata registered before the direct-upload flow was introduced.
ALTER TABLE images ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE images ADD CONSTRAINT ck_images_status CHECK (status IN ('PENDING', 'COMPLETED'));
