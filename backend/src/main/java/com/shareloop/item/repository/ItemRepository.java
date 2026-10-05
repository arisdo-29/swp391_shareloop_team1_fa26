package com.shareloop.item.repository;

import com.shareloop.item.entity.Item;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByDonorIdOrderByCreatedAtDesc(Long donorId);
}
