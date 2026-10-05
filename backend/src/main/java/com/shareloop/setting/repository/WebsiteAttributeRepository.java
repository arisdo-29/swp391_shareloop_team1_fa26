package com.shareloop.setting.repository;

import com.shareloop.setting.entity.WebsiteAttribute;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebsiteAttributeRepository extends JpaRepository<WebsiteAttribute, Long> {

    List<WebsiteAttribute> findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc(String attrGroup);

    java.util.Optional<WebsiteAttribute> findFirstByAttrGroupAndAttrKeyAndIsActiveTrue(
            String attrGroup, String attrKey);
}
