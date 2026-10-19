package com.shareloop.catalog.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.catalog.dto.AdminAttributeRequest;
import com.shareloop.catalog.entity.AttributeDataType;
import com.shareloop.catalog.entity.ItemAttribute;
import com.shareloop.catalog.entity.ItemCategory;
import com.shareloop.catalog.repository.ItemAttributeRepository;
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
class AdminCategoryAttributeServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final long CATEGORY_ID = 2L;

    @Mock
    ItemAttributeRepository attributeRepository;

    @Mock
    ItemCategoryRepository categoryRepository;

    @Mock
    ActivityLogService activityLogService;

    @InjectMocks
    AdminCategoryAttributeService service;

    @Test
    void create_whenCategoryActive_logsCategoryAttributeCreate() {
        // given
        when(categoryRepository.findByIdAndIsActiveTrue(CATEGORY_ID))
                .thenReturn(Optional.of(new ItemCategory("Sách", null, false, true, 1)));
        when(attributeRepository.saveAndFlush(any(ItemAttribute.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 8L));
        AdminAttributeRequest request =
                new AdminAttributeRequest("Tác giả", AttributeDataType.TEXT, List.of(), false, 1);

        // when
        service.create(CATEGORY_ID, request, ADMIN_ID);

        // then
        verify(activityLogService)
                .log(
                        ActivityType.CATEGORY_ATTRIBUTE_CREATE.name(),
                        ADMIN_ID,
                        "CATEGORY_ATTRIBUTE",
                        8L,
                        Map.<String, Object>of("categoryId", CATEGORY_ID, "name", "Tác giả", "active", true),
                        null);
    }

    // id do DB sinh nên unit test phải tự gán để kiểm targetId.
    private static ItemAttribute withId(ItemAttribute attribute, Long id) {
        ReflectionTestUtils.setField(attribute, "id", id);
        return attribute;
    }
}
