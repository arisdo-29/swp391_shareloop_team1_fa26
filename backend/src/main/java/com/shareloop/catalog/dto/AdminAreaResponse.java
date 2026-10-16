package com.shareloop.catalog.dto;

public record AdminAreaResponse(Long id, String name, Long parentId, short level, boolean active) {}
