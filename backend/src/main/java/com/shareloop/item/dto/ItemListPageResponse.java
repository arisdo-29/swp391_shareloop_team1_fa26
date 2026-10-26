package com.shareloop.item.dto;

import java.util.List;

public record ItemListPageResponse(List<ItemResponse> items, int page, int size, long totalElements, int totalPages) {}
