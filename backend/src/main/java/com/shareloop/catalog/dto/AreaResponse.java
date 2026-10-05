package com.shareloop.catalog.dto;

import java.util.List;

/** Một nút trong cây khu vực hai cấp. */
public record AreaResponse(Long id, String name, Long parentId, short level, List<AreaResponse> children) {}
