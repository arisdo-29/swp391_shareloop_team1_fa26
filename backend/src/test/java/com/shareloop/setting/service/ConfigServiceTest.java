package com.shareloop.setting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.entity.WebsiteAttribute;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConfigServiceTest {

    private final WebsiteAttributeRepository repository = mock(WebsiteAttributeRepository.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final ConfigService service = new ConfigService(repository, activityLogService);

    @Test
    void getIntReturnsStoredInteger() {
        when(repository.findConfigValueByAttrKey(ConfigKey.OTP_TTL_MINUTES.getAttrKey()))
                .thenReturn(Optional.of("10"));

        assertThat(service.getInt(ConfigKey.OTP_TTL_MINUTES)).isEqualTo(10);
    }

    @Test
    void getIntReturnsDefaultWhenConfigIsMissing() {
        when(repository.findConfigValueByAttrKey(ConfigKey.OTP_RESEND_COOLDOWN_SECONDS.getAttrKey()))
                .thenReturn(Optional.empty());

        assertThat(service.getInt(ConfigKey.OTP_RESEND_COOLDOWN_SECONDS)).isEqualTo(60);
    }

    @Test
    void getIntRejectsNonIntegerStoredValue() {
        when(repository.findConfigValueByAttrKey(ConfigKey.OTP_MAX_FAILED.getAttrKey()))
                .thenReturn(Optional.of("five"));

        assertThatIllegalStateException()
                .isThrownBy(() -> service.getInt(ConfigKey.OTP_MAX_FAILED))
                .withMessage("Configuration value for key 'otp_max_failed' must be an integer: 'five'");
    }

    @Test
    void getIntLooksUpExpectedKey() {
        when(repository.findConfigValueByAttrKey(ConfigKey.PENDING_ACCOUNT_TTL_HOURS.getAttrKey()))
                .thenReturn(Optional.of("24"));

        service.getInt(ConfigKey.PENDING_ACCOUNT_TTL_HOURS);

        org.mockito.Mockito.verify(repository).findConfigValueByAttrKey("pending_account_ttl_hours");
    }

    @Test
    void getRawReportsMissingKeyByName() {
        when(repository.findConfigValueByAttrKey("missing_config")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRaw("missing_config"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Missing config: missing_config")
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_CONFIG_NOT_FOUND);
    }

    @Test
    void getDecimalReadsThresholdAndReportsInvalidTypeWithKey() {
        when(repository.findConfigValueByAttrKey("chat_ai_threshold")).thenReturn(Optional.of("0.7"));
        assertThat(service.getDecimal(ConfigKey.CHAT_AI_THRESHOLD)).isEqualByComparingTo("0.7");

        when(repository.findConfigValueByAttrKey("chat_ai_threshold")).thenReturn(Optional.of("abc"));
        assertThatThrownBy(() -> service.getDecimal(ConfigKey.CHAT_AI_THRESHOLD))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Configuration 'chat_ai_threshold' must be decimal: 'abc'")
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_CONFIG_INVALID_TYPE);
    }

    @Test
    void getBooleanAcceptsOnlyTrueOrFalseIgnoringCase() {
        when(repository.findConfigValueByAttrKey("feature_enabled")).thenReturn(Optional.of("TrUe"));
        assertThat(service.getBoolean("feature_enabled")).isTrue();

        when(repository.findConfigValueByAttrKey("feature_enabled")).thenReturn(Optional.of("yes"));
        assertThatThrownBy(() -> service.getBoolean("feature_enabled"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Configuration 'feature_enabled' must be boolean: 'yes'");
    }

    @Test
    void updateRejectsInvalidDecimalAndOutOfRangeThreshold() {
        assertThatThrownBy(() -> service.update(ConfigKey.CHAT_AI_THRESHOLD, "abc", null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Configuration 'chat_ai_threshold' must be a decimal: 'abc'")
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_CONFIG_INVALID_TYPE);

        assertThatThrownBy(() -> service.update(ConfigKey.CHAT_AI_THRESHOLD, "1.5", null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Configuration 'chat_ai_threshold' must be within 0..1: '1.5'")
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.SETTING_CONFIG_OUT_OF_RANGE);
    }

    @Test
    void updatedDecimalIsReturnedOnTheNextRead() {
        AtomicReference<String> storedValue = new AtomicReference<>("0.7");
        var attribute = mock(WebsiteAttribute.class);
        when(attribute.getAttrValue()).thenAnswer(invocation -> storedValue.get());
        doAnswer(invocation -> {
                    storedValue.set(invocation.getArgument(0));
                    return null;
                })
                .when(attribute)
                .updateValue(anyString());
        when(repository.findFirstByAttrGroupAndAttrKeyAndIsActiveTrue("CONFIG", "chat_ai_threshold"))
                .thenReturn(Optional.of(attribute));
        when(repository.findConfigValueByAttrKey("chat_ai_threshold"))
                .thenAnswer(invocation -> Optional.of(storedValue.get()));

        service.update(ConfigKey.CHAT_AI_THRESHOLD, "0.8", null, 1L);

        assertThat(service.getDecimal(ConfigKey.CHAT_AI_THRESHOLD)).isEqualByComparingTo("0.8");
    }

    @Test
    void listIncludesEveryKeyAndUsesDefaultWhenNoActiveValueExists() {
        when(repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("CONFIG"))
                .thenReturn(List.of());

        var configs = service.listConfigs();

        assertThat(configs).hasSize(ConfigKey.values().length);
        assertThat(configs).anySatisfy(config -> {
            assertThat(config.key()).isEqualTo("post_fee");
            assertThat(config.value()).isEqualTo("5");
            assertThat(config.defaultValue()).isEqualTo("5");
        });
    }

    @Test
    void listUsesActiveDatabaseValueAndKeepsDefaultValue() {
        var attribute = mock(WebsiteAttribute.class);
        when(attribute.getAttrKey()).thenReturn("post_fee");
        when(attribute.getAttrValue()).thenReturn("8");
        when(repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("CONFIG"))
                .thenReturn(List.of(attribute));
        when(repository.findFirstByAttrGroupAndAttrKeyAndIsActiveTrue("CONFIG", "post_fee"))
                .thenReturn(Optional.of(attribute));

        assertThat(service.listConfigs())
                .filteredOn(config -> config.key().equals("post_fee"))
                .singleElement()
                .satisfies(config -> {
                    assertThat(config.value()).isEqualTo("8");
                    assertThat(config.defaultValue()).isEqualTo("5");
                });
        assertThat(service.get(ConfigKey.POST_FEE)).isEqualTo("8");
    }

    @Test
    void lexiconReturnsEmptyListWhenNoRulesExist() {
        when(repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("BANNED_KEYWORD"))
                .thenReturn(List.of());

        assertThat(service.lexicon()).isEmpty();
    }

    @Test
    void checklistReturnsEmptyListWhenNoQuestionsExist() {
        when(repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("CHECKLIST"))
                .thenReturn(List.of());

        assertThat(service.checklistItems()).isEmpty();
    }

    @Test
    void checklistItemsIncludeIdsKeysAndStatementsInRepositoryOrder() {
        var first = mock(WebsiteAttribute.class);
        when(first.getId()).thenReturn(12L);
        when(first.getAttrKey()).thenReturn("photos");
        when(first.getAttrValue()).thenReturn("Ảnh đúng món");
        var second = mock(WebsiteAttribute.class);
        when(second.getId()).thenReturn(11L);
        when(second.getAttrKey()).thenReturn("category");
        when(second.getAttrValue()).thenReturn("Danh mục đúng");
        when(repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("CHECKLIST"))
                .thenReturn(List.of(first, second));

        assertThat(service.checklistItems())
                .containsExactly(
                        new com.shareloop.setting.dto.ChecklistItem(12L, "photos", "Ảnh đúng món"),
                        new com.shareloop.setting.dto.ChecklistItem(11L, "category", "Danh mục đúng"));
    }

    @Test
    void rejectReasonsReturnsEmptyListWhenNoReasonsExist() {
        when(repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("REJECT_REASON"))
                .thenReturn(List.of());

        assertThat(service.rejectReasons()).isEmpty();
    }

    @Test
    void rejectReasonsReturnsActiveReasonsInConfiguredOrder() {
        var reason1 = mock(WebsiteAttribute.class);
        when(reason1.getAttrValue()).thenReturn("Ảnh không đúng món");
        var reason2 = mock(WebsiteAttribute.class);
        when(reason2.getAttrValue()).thenReturn("Chứa thông tin liên hệ");
        when(repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("REJECT_REASON"))
                .thenReturn(List.of(reason1, reason2));

        assertThat(service.rejectReasons()).containsExactly("Ảnh không đúng món", "Chứa thông tin liên hệ");
    }

    @Test
    void updateConfigLogsActivityWithAdminIdAndReason() {
        var attribute = mock(WebsiteAttribute.class);
        when(attribute.getAttrValue()).thenReturn("5");
        when(repository.findFirstByAttrGroupAndAttrKeyAndIsActiveTrue("CONFIG", "post_fee"))
                .thenReturn(Optional.of(attribute));

        service.update(ConfigKey.POST_FEE, "10", "Tăng phí", 9L);

        org.mockito.Mockito.verify(activityLogService)
                .log(
                        org.mockito.ArgumentMatchers.eq("CONFIG_UPDATE"),
                        org.mockito.ArgumentMatchers.eq(9L),
                        org.mockito.ArgumentMatchers.eq("CONFIG"),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.eq(Map.of("key", "post_fee", "oldValue", "5", "newValue", "10")),
                        org.mockito.ArgumentMatchers.eq("Tăng phí"));
    }
}
