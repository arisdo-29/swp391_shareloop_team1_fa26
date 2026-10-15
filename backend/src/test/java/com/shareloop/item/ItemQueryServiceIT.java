package com.shareloop.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.item.entity.Item;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.repository.ItemRepository;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ItemQueryServiceIT extends IntegrationTest {

    @Autowired
    ItemQueryService service;

    @Autowired
    ItemRepository items;

    @Autowired
    JdbcClient jdbc;

    @Test
    void countsOnlyActiveApprovedItemsOfRequestedDonor() {
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active = true ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long donorId = createUser("item-query-donor@shareloop.local", "0900000801");
        long otherDonorId = createUser("item-query-other@shareloop.local", "0900000802");

        Item approved = item(donorId, categoryId, areaId);
        approved.approve();
        items.saveAndFlush(approved);

        items.saveAndFlush(item(donorId, categoryId, areaId));

        Item hidden = item(donorId, categoryId, areaId);
        hidden.approve();
        hidden.deactivate();
        items.saveAndFlush(hidden);

        Item deleted = item(donorId, categoryId, areaId);
        deleted.approve();
        deleted.markDeleted();
        items.saveAndFlush(deleted);

        Item otherDonorItem = item(otherDonorId, categoryId, areaId);
        otherDonorItem.approve();
        items.saveAndFlush(otherDonorItem);

        assertThat(service.countApprovedByDonor(donorId)).isEqualTo(1);
        assertThat(service.isApproved(approved.getId())).isTrue();
        assertThat(service.isApproved(hidden.getId())).isFalse();
    }

    private long createUser(String email, String phone) {
        return jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status) "
                        + "VALUES (:email, 'unused', 'Test', :phone, 'ACTIVE') RETURNING id")
                .param("email", email)
                .param("phone", phone)
                .query(Long.class)
                .single();
    }

    private Item item(long donorId, long categoryId, long areaId) {
        return new Item(
                donorId, categoryId, areaId, OfferType.GIVE, "Bài test", "Mô tả", null, ItemCondition.GOOD, null, null);
    }
}
