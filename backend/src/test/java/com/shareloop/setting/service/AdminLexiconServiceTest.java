package com.shareloop.setting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.AdminLexiconRequest;
import com.shareloop.setting.entity.WebsiteAttribute;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminLexiconServiceTest {

    private final WebsiteAttributeRepository repository = mock(WebsiteAttributeRepository.class);
    private final AdminLexiconService service = new AdminLexiconService(repository);

    @Test
    void createStoresKeywordAndMetadataAndUsesCategoryScopeKey() {
        when(repository.findByAttrGroupOrderBySortOrderAscIdAsc("BANNED_KEYWORD"))
                .thenReturn(List.of());
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(WebsiteAttribute.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(request("  dao  ", "weapons", "block", "post"));

        ArgumentCaptor<WebsiteAttribute> saved = ArgumentCaptor.forClass(WebsiteAttribute.class);
        org.mockito.Mockito.verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAttrGroup()).isEqualTo("BANNED_KEYWORD");
        assertThat(saved.getValue().getAttrKey()).startsWith("LEX_").hasSize(68);
        assertThat(saved.getValue().getAttrValue()).isEqualTo("dao");
        assertThat(saved.getValue().getExtra())
                .containsEntry("category", "WEAPONS")
                .containsEntry("severity", "BLOCK")
                .containsEntry("scope", "POST")
                .containsEntry("matchMode", "WORD")
                .containsEntry("exceptions", List.of());
        assertThat(result.keyword()).isEqualTo("dao");
        assertThat(result.active()).isTrue();
    }

    @Test
    void createRejectsLevelOutsideBlockOrReview() {
        when(repository.findByAttrGroupOrderBySortOrderAscIdAsc("BANNED_KEYWORD"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request("dao", "WEAPONS", "ALLOW", "POST")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("level chỉ nhận BLOCK hoặc REVIEW.")
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_LEXICON_INVALID_VALUE);
    }

    @Test
    void createRejectsDuplicateKeywordWithinCategoryAndScopeIgnoringCaseAndSpacing() {
        WebsiteAttribute existing = existing(10L, "dao  găm", "WEAPONS", "REVIEW", "POST", true);
        when(repository.findByAttrGroupOrderBySortOrderAscIdAsc("BANNED_KEYWORD"))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(request(" DAO găm ", "weapons", "BLOCK", "post")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_LEXICON_DUPLICATE);
    }

    @Test
    void sameKeywordCanExistInAnotherScope() {
        WebsiteAttribute existing = existing(10L, "dao", "WEAPONS", "BLOCK", "POST", true);
        when(repository.findByAttrGroupOrderBySortOrderAscIdAsc("BANNED_KEYWORD"))
                .thenReturn(List.of(existing), List.of(existing));
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(WebsiteAttribute.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(request("dao", "WEAPONS", "REVIEW", "CHAT"));

        assertThat(result.scope()).isEqualTo("CHAT");
    }

    @Test
    void updateAndToggleReturnNotFoundForOtherIds() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request("dao", "WEAPONS", "BLOCK", "POST")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_LEXICON_NOT_FOUND);
        assertThatThrownBy(() -> service.setActive(99L, false))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_LEXICON_NOT_FOUND);
    }

    @Test
    void listReturnsActiveAndInactiveRowsInRepositoryOrder() {
        WebsiteAttribute active = existing(1L, "dao", "WEAPONS", "BLOCK", "POST", true);
        WebsiteAttribute inactive = existing(2L, "zalo", "CONTACT", "REVIEW", "BOTH", false);
        when(repository.findByAttrGroupOrderBySortOrderAscIdAsc("BANNED_KEYWORD"))
                .thenReturn(List.of(active, inactive));

        assertThat(service.listAll()).extracting(entry -> entry.keyword()).containsExactly("dao", "zalo");
        assertThat(service.listAll()).extracting(entry -> entry.active()).containsExactly(true, false);
    }

    private static AdminLexiconRequest request(String keyword, String group, String level, String scope) {
        return new AdminLexiconRequest(keyword, group, level, null, List.of(), scope);
    }

    private static WebsiteAttribute existing(
            Long id, String keyword, String group, String level, String scope, boolean active) {
        WebsiteAttribute attribute = mock(WebsiteAttribute.class);
        when(attribute.getId()).thenReturn(id);
        when(attribute.getAttrGroup()).thenReturn("BANNED_KEYWORD");
        when(attribute.getAttrValue()).thenReturn(keyword);
        when(attribute.getExtra())
                .thenReturn(Map.of(
                        "category", group,
                        "severity", level,
                        "matchMode", "WORD",
                        "exceptions", List.of(),
                        "scope", scope));
        when(attribute.getSortOrder()).thenReturn(id.intValue());
        when(attribute.isActive()).thenReturn(active);
        return attribute;
    }
}
