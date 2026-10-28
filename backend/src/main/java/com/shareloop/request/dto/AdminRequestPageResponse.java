package com.shareloop.request.dto;

import java.util.List;

/** Một trang danh sách giao dịch của Admin (docs/api/admin.md). */
public record AdminRequestPageResponse(
        List<AdminRequestRow> items, int page, int size, long totalElements, int totalPages) {}
