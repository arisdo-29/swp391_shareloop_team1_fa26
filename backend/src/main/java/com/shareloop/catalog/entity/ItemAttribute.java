package com.shareloop.catalog.entity;

import com.shareloop.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "item_attributes")
@SQLRestriction("is_deleted = false")
public class ItemAttribute extends BaseEntity {
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 10)
    private AttributeDataType dataType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "jsonb")
    private List<String> options;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ItemAttribute() {}

    public ItemAttribute(
            Long categoryId,
            String name,
            AttributeDataType dataType,
            List<String> options,
            boolean required,
            int sortOrder) {
        this.categoryId = categoryId;
        update(name, dataType, options, required, sortOrder);
    }

    public void update(String name, AttributeDataType dataType, List<String> options, boolean required, int sortOrder) {
        this.name = name;
        this.dataType = dataType;
        this.options = options == null ? null : List.copyOf(options);
        this.required = required;
        this.sortOrder = sortOrder;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public AttributeDataType getDataType() {
        return dataType;
    }

    public List<String> getOptions() {
        return options == null ? List.of() : List.copyOf(options);
    }

    public boolean isRequired() {
        return required;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
