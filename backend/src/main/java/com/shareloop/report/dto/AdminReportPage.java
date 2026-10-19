package com.shareloop.report.dto;

import java.util.List;

public record AdminReportPage(List<ReportResponse> items, int page, int size, long totalElements, int totalPages) {}
