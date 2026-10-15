package com.shareloop.setting.entity;

import com.shareloop.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/** Entity lát cắt các cột cấu hình cần đọc từ website_attributes. */
@Entity
@Table(name = "website_attributes")
@SQLRestriction("is_deleted = false")
public class WebsiteAttribute extends BaseEntity {

    @Column(name = "attr_group", nullable = false, length = 30)
    private String attrGroup;

    @Column(name = "attr_key", nullable = false, length = 100)
    private String attrKey;

    @Column(name = "attr_value", nullable = false)
    private String attrValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra", columnDefinition = "jsonb")
    private Map<String, Object> extra;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected WebsiteAttribute() {}

    public WebsiteAttribute(
            String attrGroup, String attrKey, String attrValue, Map<String, Object> extra, int sortOrder) {
        this.attrGroup = attrGroup;
        this.attrKey = attrKey;
        this.attrValue = attrValue;
        this.extra = extra == null ? null : Map.copyOf(extra);
        this.sortOrder = sortOrder;
    }

    public String getAttrGroup() {
        return attrGroup;
    }

    public String getAttrKey() {
        return attrKey;
    }

    public String getAttrValue() {
        return attrValue;
    }

    public Map<String, Object> getExtra() {
        return extra == null ? Map.of() : Map.copyOf(extra);
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void updateValue(String attrValue) {
        this.attrValue = attrValue;
    }

    public void updateLexicon(String attrKey, String keyword, Map<String, Object> extra) {
        this.attrKey = attrKey;
        this.attrValue = keyword;
        this.extra = Map.copyOf(extra);
    }
}
