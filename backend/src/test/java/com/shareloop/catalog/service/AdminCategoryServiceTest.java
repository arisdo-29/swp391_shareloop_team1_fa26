package com.shareloop.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.catalog.CatalogErrorCode;
import com.shareloop.catalog.dto.AdminCategoryReorderRequest;
import com.shareloop.catalog.dto.AdminCategoryRequest;
import com.shareloop.catalog.dto.AdminCategoryResponse;
import com.shareloop.catalog.entity.ItemCategory;
import com.shareloop.catalog.repository.ItemCategoryRepository;
import com.shareloop.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminCategoryServiceTest {

    private static final Long ADMIN_ID = 1L;

    @Mock
    ItemCategoryRepository repository;

    @Mock
    ActivityLogService activityLogService;

    @InjectMocks
    AdminCategoryService service;

    @Test
    void create_whenValid_logsCategoryCreate() {
        // given
        when(repository.saveAndFlush(any(ItemCategory.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        // when
        AdminCategoryResponse response =
                service.create(new AdminCategoryRequest("  Sách  ", null, false, true, 1), ADMIN_ID);

        // then
        assertThat(response.name()).isEqualTo("Sách");
        verify(activityLogService)
                .log(
                        ActivityType.CATEGORY_CREATE.name(),
                        ADMIN_ID,
                        "CATEGORY",
                        5L,
                        Map.<String, Object>of("name", "Sách", "active", true),
                        null);
    }

    @Test
    void setActive_whenHiding_logsCategoryToggleWithActiveFalse() {
        // given
        ItemCategory category = withId(new ItemCategory("Sách", null, false, true, 1), 5L);
        when(repository.findById(5L)).thenReturn(Optional.of(category));
        when(repository.findByParentIdAndIsActiveTrue(anyLong())).thenReturn(List.of());
        when(repository.saveAndFlush(category)).thenReturn(category);

        // when
        service.setActive(5L, false, ADMIN_ID);

        // then
        verify(activityLogService)
                .log(
                        ActivityType.CATEGORY_TOGGLE.name(),
                        ADMIN_ID,
                        "CATEGORY",
                        5L,
                        Map.<String, Object>of("name", "Sách", "active", false),
                        null);
    }

    @Test
    void create_whenSortOrderMissing_appendsAfterLastCategory() {
        // given
        ItemCategory lastCategory = withId(new ItemCategory("Thể thao", null, false, true, 8), 8L);
        when(repository.findFirstByOrderBySortOrderDesc()).thenReturn(Optional.of(lastCategory));
        when(repository.saveAndFlush(any(ItemCategory.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 9L));

        // when
        AdminCategoryResponse response =
                service.create(new AdminCategoryRequest("Sách", null, false, true, null), ADMIN_ID);

        // then
        assertThat(response.sortOrder()).isEqualTo(9);
    }

    @Test
    void create_whenNoCategoryYet_startsFromOne() {
        // given
        when(repository.findFirstByOrderBySortOrderDesc()).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ItemCategory.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        // when
        AdminCategoryResponse response =
                service.create(new AdminCategoryRequest("Sách", null, false, true, null), ADMIN_ID);

        // then
        assertThat(response.sortOrder()).isEqualTo(1);
    }

    @Test
    void update_whenSortOrderMissing_keepsCurrentOrder() {
        // given
        ItemCategory category = withId(new ItemCategory("Sách", null, false, true, 4), 5L);
        when(repository.findById(5L)).thenReturn(Optional.of(category));
        when(repository.saveAndFlush(category)).thenReturn(category);

        // when
        AdminCategoryResponse response =
                service.update(5L, new AdminCategoryRequest("Sách cũ", null, false, true, null), ADMIN_ID);

        // then
        assertThat(response.sortOrder()).isEqualTo(4);
        assertThat(response.name()).isEqualTo("Sách cũ");
    }

    @Test
    void reorder_whenListIsComplete_assignsPositionsStartingFromOne() {
        // given
        ItemCategory books = withId(new ItemCategory("Sách", null, false, true, 1), 1L);
        ItemCategory clothes = withId(new ItemCategory("Quần áo", null, false, true, 2), 2L);
        ItemCategory toys = withId(new ItemCategory("Đồ chơi", null, false, true, 3), 3L);
        when(repository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(books, clothes, toys));

        // when
        service.reorder(new AdminCategoryReorderRequest(List.of(3L, 1L, 2L)), ADMIN_ID);

        // then
        assertThat(toys.getSortOrder()).isEqualTo(1);
        assertThat(books.getSortOrder()).isEqualTo(2);
        assertThat(clothes.getSortOrder()).isEqualTo(3);
        verify(repository).saveAllAndFlush(List.of(books, clothes, toys));
        verify(activityLogService)
                .log(
                        ActivityType.CATEGORY_REORDER.name(),
                        ADMIN_ID,
                        "CATEGORY",
                        null,
                        Map.<String, Object>of("categoryIds", List.of(3L, 1L, 2L)),
                        null);
    }

    @Test
    void reorder_whenCategoryMissingFromList_throwsInvalidSortOrder() {
        // given
        ItemCategory books = withId(new ItemCategory("Sách", null, false, true, 1), 1L);
        ItemCategory clothes = withId(new ItemCategory("Quần áo", null, false, true, 2), 2L);
        when(repository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(books, clothes));

        // when / then
        assertThatThrownBy(() -> service.reorder(new AdminCategoryReorderRequest(List.of(2L)), ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(CatalogErrorCode.CATALOG_INVALID_SORT_ORDER);
        verify(repository, never()).saveAllAndFlush(any());
    }

    @Test
    void reorder_whenIdRepeatedOrUnknown_throwsInvalidSortOrder() {
        // given
        ItemCategory books = withId(new ItemCategory("Sách", null, false, true, 1), 1L);
        ItemCategory clothes = withId(new ItemCategory("Quần áo", null, false, true, 2), 2L);
        when(repository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(books, clothes));

        // when / then
        assertThatThrownBy(() -> service.reorder(new AdminCategoryReorderRequest(List.of(1L, 1L)), ADMIN_ID))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.reorder(new AdminCategoryReorderRequest(List.of(1L, 99L)), ADMIN_ID))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).saveAllAndFlush(any());
    }

    // id do DB sinh nên unit test phải tự gán để kiểm targetId.
    private static ItemCategory withId(ItemCategory category, Long id) {
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
