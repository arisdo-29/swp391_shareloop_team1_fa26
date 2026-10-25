package com.shareloop.item.repository;

import java.util.Objects;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Pageable that supports offsets which are not a multiple of the page size. */
public record OffsetLimitPageable(long offset, int pageSize, Sort sort) implements Pageable {

    public OffsetLimitPageable {
        if (offset < 0 || pageSize < 1) {
            throw new IllegalArgumentException("offset must be non-negative and pageSize must be positive");
        }
        sort = Objects.requireNonNullElse(sort, Sort.unsorted());
    }

    @Override
    public int getPageNumber() {
        return Math.toIntExact(offset / pageSize);
    }

    @Override
    public int getPageSize() {
        return pageSize;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetLimitPageable(offset + pageSize, pageSize, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious() ? new OffsetLimitPageable(offset - pageSize, pageSize, sort) : first();
    }

    @Override
    public Pageable first() {
        return new OffsetLimitPageable(0, pageSize, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative");
        }
        return new OffsetLimitPageable((long) pageNumber * pageSize, pageSize, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
