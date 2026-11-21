package com.shareloop.setting.repository;

import com.shareloop.setting.entity.WebsiteAttribute;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebsiteAttributeRepository extends JpaRepository<WebsiteAttribute, Long> {

    List<WebsiteAttribute> findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc(String attrGroup);

    List<WebsiteAttribute> findByAttrGroupOrderBySortOrderAscIdAsc(String attrGroup);

    /**
     * Khoá ghi (SELECT ... FOR UPDATE) mọi dòng của một nhóm, theo thứ tự attr_key. Thứ tự cố định để hai transaction
     * cùng khoá không chờ nhau theo vòng (deadlock); transaction đến sau phải chờ cho tới khi transaction trước xong.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<WebsiteAttribute> findByAttrGroupAndIsActiveTrueOrderByAttrKeyAsc(String attrGroup);

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
