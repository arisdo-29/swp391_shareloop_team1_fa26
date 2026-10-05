package com.shareloop.setting.repository;

import com.shareloop.setting.entity.WebsiteAttribute;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebsiteAttributeRepository extends JpaRepository<WebsiteAttribute, Long> {

    List<WebsiteAttribute> findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc(String attrGroup);

    Optional<WebsiteAttribute> findFirstByAttrGroupAndAttrKeyAndIsActiveTrue(String attrGroup, String attrKey);

    @Query(value = """
                    SELECT attr_value
                    FROM website_attributes
                    WHERE attr_group = 'CONFIG'
                      AND attr_key = :attrKey
                      AND is_active = TRUE
                      AND is_deleted = FALSE
                    """, nativeQuery = true)
    Optional<String> findConfigValueByAttrKey(@Param("attrKey") String attrKey);
}
