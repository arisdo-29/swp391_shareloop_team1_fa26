package com.shareloop.setting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.entity.WebsiteAttribute;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
import java.util.List;
import java.util.Optional;
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
    void updateStoresConfigAndWritesAuditLog() {
        var attribute = mock(WebsiteAttribute.class);
        when(attribute.getAttrValue()).thenReturn("5");
        when(repository.findFirstByAttrGroupAndAttrKeyAndIsActiveTrue("CONFIG", "post_fee"))
                .thenReturn(Optional.of(attribute));

        var response = service.update(ConfigKey.POST_FEE, "8", "Điều chỉnh phí", 17L);

        assertThat(response.value()).isEqualTo("8");
        verify(attribute).updateValue("8");
        verify(repository).save(attribute);
        verify(activityLogService)
                .log(
                        17L,
                        "CONFIG_UPDATED",
                        "CONFIG",
                        null,
                        java.util.Map.of("key", "post_fee", "oldValue", "5", "newValue", "8"),
                        "Điều chỉnh phí");
    }

    @Test
    void updateRejectsUnknownOrUnseededConfigKey() {
        when(repository.findFirstByAttrGroupAndAttrKeyAndIsActiveTrue("CONFIG", "post_fee"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(ConfigKey.POST_FEE, "8", "reason", 17L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.CONFIG_KEY_NOT_FOUND);
        verify(activityLogService, never())
                .log(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyMap(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void updateRejectsNonNumericConfigBeforeSaving() {
        assertThatThrownBy(() -> service.update(ConfigKey.POST_FEE, "abc", "reason", 17L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.CONFIG_VALUE_INVALID);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(activityLogService, never())
                .log(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyMap(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void updateRejectsThresholdOutsideSupportedRange() {
        assertThatThrownBy(() -> service.update(ConfigKey.CHAT_AI_THRESHOLD, "1.1", "reason", 17L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(SettingErrorCode.CONFIG_VALUE_INVALID);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateAllowsUnlimitedDiamondItemLimit() {
        var attribute = mock(WebsiteAttribute.class);
        when(attribute.getAttrValue()).thenReturn("10");
        when(repository.findFirstByAttrGroupAndAttrKeyAndIsActiveTrue("CONFIG", "max_active_items_diamond"))
                .thenReturn(Optional.of(attribute));

        assertThat(service.update(ConfigKey.MAX_ACTIVE_ITEMS_DIAMOND, "-1", "unlimited", 17L)
                        .value())
                .isEqualTo("-1");
    }
}
