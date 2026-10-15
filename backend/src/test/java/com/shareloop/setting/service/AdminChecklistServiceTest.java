package com.shareloop.setting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.AdminChecklistReorderRequest;
import com.shareloop.setting.dto.AdminChecklistRequest;
import com.shareloop.setting.entity.WebsiteAttribute;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminChecklistServiceTest {

    private final WebsiteAttributeRepository repository = mock(WebsiteAttributeRepository.class);
    private final AdminChecklistService service = new AdminChecklistService(repository);

    @Test
    void createStoresQuestionAndCalculatesSortOrder() {
        WebsiteAttribute existingItem = existing(1L, "chk_1", "Câu 1", 10, true);
        when(repository.findByAttrGroupOrderBySortOrderAscIdAsc("CHECKLIST")).thenReturn(List.of(existingItem));
        when(repository.saveAndFlush(any(WebsiteAttribute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new AdminChecklistRequest("Câu hỏi mới?"));

        ArgumentCaptor<WebsiteAttribute> captor = ArgumentCaptor.forClass(WebsiteAttribute.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAttrGroup()).isEqualTo("CHECKLIST");
        assertThat(captor.getValue().getAttrValue()).isEqualTo("Câu hỏi mới?");
        assertThat(captor.getValue().getSortOrder()).isEqualTo(20);
        assertThat(result.question()).isEqualTo("Câu hỏi mới?");
        assertThat(result.sortOrder()).isEqualTo(20);
    }

    @Test
    void createRejectsBlankQuestion() {
        assertThatThrownBy(() -> service.create(new AdminChecklistRequest("  ")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_CHECKLIST_INVALID_VALUE);
    }

    @Test
    void updateModifiesExistingQuestion() {
        WebsiteAttribute existing = existing(1L, "chk_1", "Câu cũ", 10, true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(WebsiteAttribute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.update(1L, new AdminChecklistRequest("Câu sửa?"));

        assertThat(result.question()).isEqualTo("Câu sửa?");
        assertThat(existing.getAttrValue()).isEqualTo("Câu sửa?");
    }

    @Test
    void updateThrowsNotFoundWhenIdDoesNotExist() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new AdminChecklistRequest("Câu sửa?")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_CHECKLIST_NOT_FOUND);
    }

    @Test
    void setActiveDeactivatesWhenMultipleActiveExist() {
        WebsiteAttribute item1 = existing(1L, "chk_1", "Câu 1", 10, true);
        WebsiteAttribute item2 = existing(2L, "chk_2", "Câu 2", 20, true);
        when(repository.findById(1L)).thenReturn(Optional.of(item1));
        when(repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("CHECKLIST"))
                .thenReturn(List.of(item1, item2));
        when(repository.saveAndFlush(any(WebsiteAttribute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.setActive(1L, false);

        assertThat(result.active()).isFalse();
        assertThat(item1.isActive()).isFalse();
    }

    @Test
    void setActiveThrowsWhenDeactivatingTheLastActiveItem_BR_A06() {
        WebsiteAttribute item1 = existing(1L, "chk_1", "Câu 1 duy nhất", 10, true);
        when(repository.findById(1L)).thenReturn(Optional.of(item1));
        when(repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("CHECKLIST"))
                .thenReturn(List.of(item1));

        assertThatThrownBy(() -> service.setActive(1L, false))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_CHECKLIST_AT_LEAST_ONE_REQUIRED);
    }

    @Test
    void reorderUpdatesSortOrderForAllItems() {
        WebsiteAttribute item1 = existing(1L, "chk_1", "Câu 1", 10, true);
        WebsiteAttribute item2 = existing(2L, "chk_2", "Câu 2", 20, true);
        when(repository.findByAttrGroupOrderBySortOrderAscIdAsc("CHECKLIST")).thenReturn(List.of(item1, item2));

        var result = service.reorder(new AdminChecklistReorderRequest(List.of(2L, 1L)));

        assertThat(item2.getSortOrder()).isEqualTo(10);
        assertThat(item1.getSortOrder()).isEqualTo(20);
        assertThat(result).hasSize(2);
    }

    @Test
    void reorderRejectsIncompleteOrDuplicateIds() {
        WebsiteAttribute item1 = existing(1L, "chk_1", "Câu 1", 10, true);
        WebsiteAttribute item2 = existing(2L, "chk_2", "Câu 2", 20, true);
        when(repository.findByAttrGroupOrderBySortOrderAscIdAsc("CHECKLIST")).thenReturn(List.of(item1, item2));

        assertThatThrownBy(() -> service.reorder(new AdminChecklistReorderRequest(List.of(1L, 1L))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_CHECKLIST_INVALID_VALUE);

        assertThatThrownBy(() -> service.reorder(new AdminChecklistReorderRequest(List.of(1L))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_CHECKLIST_INVALID_VALUE);
    }

    @Test
    void listReturnsAllItemsIncludingInactive() {
        WebsiteAttribute active = existing(1L, "chk_1", "Câu 1", 10, true);
        WebsiteAttribute inactive = existing(2L, "chk_2", "Câu 2", 20, false);
        when(repository.findByAttrGroupOrderBySortOrderAscIdAsc("CHECKLIST")).thenReturn(List.of(active, inactive));

        List<?> list = service.listAll();
        assertThat(list).hasSize(2);
    }

    private static WebsiteAttribute existing(Long id, String key, String question, int sortOrder, boolean active) {
        WebsiteAttribute attribute = mock(WebsiteAttribute.class);
        when(attribute.getId()).thenReturn(id);
        when(attribute.getAttrGroup()).thenReturn("CHECKLIST");
        when(attribute.getAttrKey()).thenReturn(key);
        when(attribute.getAttrValue()).thenReturn(question);
        when(attribute.getSortOrder()).thenReturn(sortOrder);
        when(attribute.isActive()).thenReturn(active);
        org.mockito.Mockito.doAnswer(invocation -> {
                    when(attribute.getAttrValue()).thenReturn(invocation.getArgument(0));
                    return null;
                })
                .when(attribute)
                .updateChecklist(any(String.class));
        org.mockito.Mockito.doAnswer(invocation -> {
                    when(attribute.getSortOrder()).thenReturn(invocation.getArgument(0));
                    return null;
                })
                .when(attribute)
                .updateSortOrder(org.mockito.ArgumentMatchers.anyInt());
        org.mockito.Mockito.doAnswer(invocation -> {
                    when(attribute.isActive()).thenReturn(false);
                    return null;
                })
                .when(attribute)
                .deactivate();
        org.mockito.Mockito.doAnswer(invocation -> {
                    when(attribute.isActive()).thenReturn(true);
                    return null;
                })
                .when(attribute)
                .activate();
        return attribute;
    }
}
