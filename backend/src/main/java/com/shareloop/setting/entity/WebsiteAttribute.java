package com.shareloop.setting.entity;

import com.shareloop.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/** Cấu hình và thuộc tính website lưu trong website_attributes. */
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

    protected WebsiteAttribute() {}

    public String getAttrGroup() {
        return attrGroup;
    }

    public String getAttrKey() {
        return attrKey;
    }

    public String getAttrValue() {
        return attrValue;
    }
}
