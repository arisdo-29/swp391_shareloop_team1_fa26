package com.shareloop.catalog.service;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exposes active required attributes to item without sharing catalog repositories. */
@Service
public class CategoryAttributeService {
    private final JdbcClient jdbcClient;

    public CategoryAttributeService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public Optional<Long> operatingStatusAttributeId(long categoryId) {
        return jdbcClient
                .sql("SELECT id FROM item_attributes WHERE category_id = :categoryId "
                        + "AND name = 'Trạng thái hoạt động' AND is_required AND is_active AND NOT is_deleted")
                .param("categoryId", categoryId)
                .query(Long.class)
                .optional();
    }
}
