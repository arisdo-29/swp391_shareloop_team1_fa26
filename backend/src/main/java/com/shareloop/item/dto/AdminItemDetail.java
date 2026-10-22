package com.shareloop.item.dto;

import java.util.List;

/** Bài đăng ở mọi trạng thái cho Admin xem: thông tin chung, cờ is_active và các giá trị thuộc tính động. */
public record AdminItemDetail(ItemResponse item, boolean active, List<ItemAttributeValue> attributes) {}
