package com.shareloop.setting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
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

    private static final Long ADMIN_ID = 1L;

    private final WebsiteAttributeRepository repository = mock(WebsiteAttributeRepository.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final AdminLexiconService service = new AdminLexiconService(repository, activityLogService);

    @Test
    void createStoresKeywordAndMetadataAndUsesCategoryScopeKey() {
        when(repository.findByAttrGroupOrderBySortOrderAscIdAsc("BANNED_KEYWORD"))
                .thenReturn(List.of());
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(WebsiteAttribute.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(request("  dao  ", "weapons", "block", "post"), ADMIN_ID);

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

        assertThatThrownBy(() -> service.create(request("dao", "WEAPONS", "ALLOW", "POST"), ADMIN_ID))
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

        assertThatThrownBy(() -> service.create(request(" DAO găm ", "weapons", "BLOCK", "post"), ADMIN_ID))
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

        var result = service.create(request("dao", "WEAPONS", "REVIEW", "CHAT"), ADMIN_ID);

        assertThat(result.scope()).isEqualTo("CHAT");
    }

    @Test
    void updateAndToggleReturnNotFoundForOtherIds() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request("dao", "WEAPONS", "BLOCK", "POST"), ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_LEXICON_NOT_FOUND);
        assertThatThrownBy(() -> service.setActive(99L, false, ADMIN_ID))
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

    @Test
    void createRejectsRegexThatDoesNotCompileAndSavesNothing() {
        var request = new AdminLexiconRequest("[abc", "WEAPONS", "BLOCK", "REGEX", List.of(), "POST");

        assertThatThrownBy(() -> service.create(request, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("keyword không phải biểu thức REGEX hợp lệ.")
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_LEXICON_INVALID_VALUE);
        verify(repository, never()).saveAndFlush(any(WebsiteAttribute.class));
    }

    @Test
    void createAcceptsValidRegex() {
        when(repository.findByAttrGroupOrderBySortOrderAscIdAsc("BANNED_KEYWORD"))
                .thenReturn(List.of());
        when(repository.saveAndFlush(any(WebsiteAttribute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(
                new AdminLexiconRequest("0\\d{9}", "CONTACT", "REVIEW", "regex", List.of(), "POST"), ADMIN_ID);

        assertThat(result.matchMode()).isEqualTo("REGEX");
        assertThat(result.keyword()).isEqualTo("0\\d{9}");
    }

    @Test
    void reactivatingStoredRegexThatDoesNotCompileIsRejected() {
        WebsiteAttribute broken = mock(WebsiteAttribute.class);
        when(broken.getId()).thenReturn(5L);
        when(broken.getAttrGroup()).thenReturn("BANNED_KEYWORD");
        when(broken.getAttrValue()).thenReturn("(súng");
        when(broken.getExtra())
                .thenReturn(Map.of(
                        "category", "WEAPONS",
                        "severity", "BLOCK",
                        "matchMode", "REGEX",
                        "exceptions", List.of(),
                        "scope", "POST"));
        when(repository.findById(5L)).thenReturn(Optional.of(broken));

        assertThatThrownBy(() -> service.setActive(5L, true, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_LEXICON_INVALID_VALUE);
        verify(broken, never()).activate();
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_whenValid_logsLexiconCreateWithTermAndSeverity() {
        // given
        when(repository.findByAttrGroupOrderBySortOrderAscIdAsc("BANNED_KEYWORD"))
                .thenReturn(List.of());
        when(repository.saveAndFlush(any(WebsiteAttribute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        service.create(request("dao", "WEAPONS", "BLOCK", "POST"), ADMIN_ID);

        // then
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService)
                .log(
                        eq(ActivityType.LEXICON_CREATE.name()),
                        eq(ADMIN_ID),
                        eq("LEXICON"),
                        any(),
                        data.capture(),
                        isNull());
        assertThat(data.getValue())
                .containsEntry("term", "dao")
                .containsEntry("severity", "BLOCK")
                .containsEntry("active", true);
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
