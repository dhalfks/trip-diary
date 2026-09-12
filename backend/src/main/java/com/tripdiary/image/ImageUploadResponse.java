package com.tripdiary.image;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ImageUploadResponse(UUID imageId, ImageStatus status, String method,
                                  URI uploadUrl, Map<String, List<String>> headers, Instant expiresAt) {}
