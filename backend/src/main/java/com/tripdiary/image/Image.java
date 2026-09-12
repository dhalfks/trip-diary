package com.tripdiary.image;

import java.util.UUID;
import com.tripdiary.diary.DiaryEntry;
import com.tripdiary.global.persistence.BaseTimeEntity;
import com.tripdiary.storage.StorageType;
import jakarta.persistence.*;

@Entity
@Table(name = "images", uniqueConstraints = @UniqueConstraint(name = "uk_images_storage_key", columnNames = {"storage_type", "storage_key"}))
public class Image extends BaseTimeEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "diary_entry_id", nullable = false) private DiaryEntry diaryEntry;
    @Column(name = "original_file_name", nullable = false, length = 255) private String originalFileName;
    @Column(name = "storage_key", nullable = false, length = 1024) private String storageKey;
    @Column(name = "content_type", nullable = false, length = 255) private String contentType;
    @Column(name = "file_size", nullable = false) private long fileSize;
    @Enumerated(EnumType.STRING) @Column(name = "storage_type", nullable = false, length = 30) private StorageType storageType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ImageStatus status = ImageStatus.COMPLETED;

    protected Image() {}

    public Image(DiaryEntry diaryEntry, String originalFileName, String storageKey,
                 String contentType, long fileSize, StorageType storageType) {
        this.id = UUID.randomUUID();
        this.diaryEntry = diaryEntry;
        this.originalFileName = originalFileName;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.storageType = storageType;
    }

    public static Image pending(DiaryEntry entry, String originalFileName, String key, String contentType, long fileSize, StorageType storageType) {
        Image image = new Image(entry, originalFileName, key, contentType, fileSize, storageType);
        image.status = ImageStatus.PENDING;
        return image;
    }

    public void completeUpload() { this.status = ImageStatus.COMPLETED; }
    public ImageStatus getStatus() { return status; }
    public UUID getId() { return id; }
    public DiaryEntry getDiaryEntry() { return diaryEntry; }
    public String getOriginalFileName() { return originalFileName; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public StorageType getStorageType() { return storageType; }
}
