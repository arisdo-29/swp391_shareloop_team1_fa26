package com.shareloop.admin.dto;

import java.time.Instant;

/** Một ảnh hoặc video minh chứng giao nhận. stage là mốc tải lên theo module request. */
public record AdminRequestPhoto(Long mediaId, String url, String stage, String uploaderName, Instant createdAt) {}
