package com.shareloop.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.catalog.dto.AdminCategoryRequest;
import com.shareloop.catalog.dto.AdminCategoryResponse;
import com.shareloop.catalog.entity.ItemCategory;
import com.shareloop.catalog.repository.ItemCategoryRepository;
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

    // id do DB sinh nên unit test phải tự gán để kiểm targetId.
    private static ItemCategory withId(ItemCategory category, Long id) {
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
