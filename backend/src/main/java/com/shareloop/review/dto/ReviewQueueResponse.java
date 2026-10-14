package com.shareloop.review.dto;

import java.util.List;

public record ReviewQueueResponse(List<ReviewQueueItem> items, List<String> checklist, int page, int size) {}
