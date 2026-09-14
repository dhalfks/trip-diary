package com.tripdiary.book;

import java.io.IOException;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import com.tripdiary.book.DiaryTemplate.*;
import com.tripdiary.diary.DiaryEntryRepository;
import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;
import com.tripdiary.image.*;
import com.tripdiary.storage.ObjectStorageService;
import com.tripdiary.storage.S3Properties;
import com.tripdiary.trip.Trip;
import com.tripdiary.trip.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TravelDiaryService {
    public static final int MAX_DIARIES_PER_TRIP = 5;
    private final TravelDiaryRepository diaries;
    private final TripRepository trips;
    private final DiaryEntryRepository entries;
    private final ImageRepository images;
    private final RuleBasedDiaryGenerator generator;
    private final ObjectStorageService storage;
    private final S3Properties properties;
    private final Clock clock;

    public TravelDiaryService(TravelDiaryRepository diaries, TripRepository trips, DiaryEntryRepository entries,
                              ImageRepository images, RuleBasedDiaryGenerator generator, ObjectStorageService storage,
                              S3Properties properties, Clock clock) {
        this.diaries = diaries; this.trips = trips; this.entries = entries; this.images = images;
        this.generator = generator; this.storage = storage; this.properties = properties; this.clock = clock;
    }

    @Transactional
    public TravelDiaryResponse create(UUID userId, UUID tripId, GenerateCommand command) {
        // Serialize the count + insert per trip so concurrent requests cannot bypass the five-result limit.
        Trip trip = trips.findOwnedForDiaryGeneration(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (diaries.countByTripId(tripId) >= MAX_DIARIES_PER_TRIP) throw new BusinessException(ErrorCode.DIARY_LIMIT_REACHED);
        String title = command.title() == null ? trip.getTitle() + " 여행 다이어리" : command.title().strip();
        if (title.isBlank() || title.length() > 120 || command.templateType() == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        Map<UUID, List<PhotoSnapshot>> photos = images.findAllByDiaryEntryTripDayTripIdAndStatus(tripId, ImageStatus.COMPLETED)
                .stream().collect(Collectors.groupingBy(image -> image.getDiaryEntry().getId(),
                        Collectors.mapping(image -> new PhotoSnapshot(image.getId(), image.getCreatedAt()), Collectors.toList())));
        List<EntrySnapshot> source = entries.findAllForDiaryGeneration(tripId).stream()
                .map(entry -> new EntrySnapshot(entry.getId(), entry.getTripDay().getDate(), entry.getCreatedAt(),
                        entry.getTitle(), entry.getContent(), photos.getOrDefault(entry.getId(), List.of()))).toList();
        var plan = generator.generate(title, trip.getStartDate(), trip.getEndDate(), command.templateType(), command.coverImageId(), source);
        return TravelDiaryResponse.from(diaries.saveAndFlush(new TravelDiary(trip, title, command.templateType(), plan.coverImageId(), plan.pages())));
    }

    public List<TravelDiaryResponse> list(UUID userId, UUID tripId) {
        ownedTrip(userId, tripId);
        return diaries.findAllByTripIdOrderByCreatedAtDescIdDesc(tripId).stream().map(TravelDiaryResponse::from).toList();
    }

    public TravelDiaryResponse.Detail get(UUID userId, UUID tripId, UUID diaryId) {
        ownedTrip(userId, tripId);
        TravelDiary diary = find(tripId, diaryId);
        List<TravelDiaryResponse.PageResponse> pages = diary.getPages().stream().map(TravelDiaryResponse.PageResponse::from).toList();
        Set<UUID> ids = new LinkedHashSet<>();
        for (DiaryPage page : diary.getPages()) {
            Object value = page.getContent().get("imageIds");
            if (value instanceof List<?> values) for (Object id : values) ids.add(UUID.fromString(id.toString()));
        }
        List<ImageViewResponse> resolved = new ArrayList<>();
        if (!ids.isEmpty()) {
            for (Image image : images.findAllByIdInAndDiaryEntryTripDayTripIdAndStatus(ids, tripId, ImageStatus.COMPLETED)) {
                // Deleted images are absent; a temporary storage failure must not hide the saved text/pages.
                if (image.getStorageType() != storage.storageType()) continue;
                try {
                    var expires = clock.instant().plus(properties.presignTtl());
                    resolved.add(new ImageViewResponse(image.getId(), image.getDiaryEntry().getId(), image.getOriginalFileName(),
                            image.getContentType(), image.getFileSize(), image.getCreatedAt(),
                            storage.createDownloadUrl(image.getStorageKey(), properties.presignTtl()), expires));
                } catch (IOException ignored) { /* Preview displays a missing-image placeholder and offers refresh. */ }
            }
        }
        return new TravelDiaryResponse.Detail(TravelDiaryResponse.from(diary), pages, resolved);
    }

    @Transactional
    public void delete(UUID userId, UUID tripId, UUID diaryId) {
        ownedTrip(userId, tripId);
        diaries.delete(find(tripId, diaryId));
        diaries.flush();
    }

    private void ownedTrip(UUID userId, UUID tripId) {
        if (!trips.existsByIdAndUserId(tripId, userId)) throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
    }

    private TravelDiary find(UUID tripId, UUID diaryId) {
        return diaries.findByIdAndTripId(diaryId, tripId).orElseThrow(() -> new BusinessException(ErrorCode.DIARY_NOT_FOUND));
    }

    public record GenerateCommand(String title, TemplateType templateType, UUID coverImageId) {}
}
