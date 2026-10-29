package com.shareloop.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shareloop.catalog.CatalogErrorCode;
import com.shareloop.catalog.entity.ItemCategory;
import com.shareloop.catalog.mapper.CategoryMapper;
import com.shareloop.catalog.repository.ItemCategoryRepository;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Mẫu unit test service: Mockito, không khởi động Spring, không đụng DB. */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    ItemCategoryRepository repository;

    @Spy
    CategoryMapper mapper = new CategoryMapper();

    @Mock
    ItemService itemService;

    @InjectMocks
    CategoryService service;

    @Test
    void listActiveMapsRepositoryResultInOrder() {
        ItemCategory books = new ItemCategory("Sách", null, false, true, 1);
        ItemCategory electronics = new ItemCategory("Đồ điện tử", null, true, false, 2);
        ReflectionTestUtils.setField(books, "id", 1L);
        ReflectionTestUtils.setField(electronics, "id", 2L);
        when(itemService.countPublicItemsByCategory()).thenReturn(Map.of());
        when(repository.findByIsActiveTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of(books, electronics));

        var result = service.listActive();

        assertThat(result).extracting("name").containsExactly("Sách", "Đồ điện tử");
        assertThat(result.get(1).restricted()).isTrue();
        assertThat(result.get(1).boostable()).isFalse();
    }

    @Test
    void getByIdReturnsCategory() {
        when(itemService.countPublicItemsByCategory()).thenReturn(Map.of());
        when(repository.findByIdAndIsActiveTrue(5L))
                .thenReturn(Optional.of(new ItemCategory("Sách", null, false, true, 1)));

        assertThat(service.getById(5L).name()).isEqualTo("Sách");
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        when(repository.findByIdAndIsActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(CatalogErrorCode.CATALOG_CATEGORY_NOT_FOUND));
    }

    @Test
    void listActiveUsesGroupedItemCountsAndDefaultsMissingCategoriesToZero() {
        ItemCategory books = new ItemCategory("Sách", null, false, true, 1);
        ItemCategory toys = new ItemCategory("Đồ chơi", null, false, true, 2);
        ReflectionTestUtils.setField(books, "id", 1L);
        ReflectionTestUtils.setField(toys, "id", 2L);
        when(itemService.countPublicItemsByCategory()).thenReturn(Map.of(1L, 4L));
        when(repository.findByIsActiveTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of(books, toys));

        assertThat(service.listActive()).extracting("activeItemCount").containsExactly(4L, 0L);
    }
}
