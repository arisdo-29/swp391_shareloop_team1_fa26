package com.shareloop.setting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigServiceTest {

    private WebsiteAttributeRepository websiteAttributeRepository;
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        websiteAttributeRepository = mock(WebsiteAttributeRepository.class);
        configService = new ConfigService(websiteAttributeRepository);
    }

    @Test
    void getIntReturnsStoredInteger() {
        when(websiteAttributeRepository.findConfigValueByAttrKey(ConfigKey.OTP_TTL_MINUTES.getAttrKey()))
                .thenReturn(Optional.of("10"));

        assertEquals(10, configService.getInt(ConfigKey.OTP_TTL_MINUTES));
    }

    @Test
    void getIntReturnsDefaultWhenConfigIsMissing() {
        when(websiteAttributeRepository.findConfigValueByAttrKey(ConfigKey.OTP_RESEND_COOLDOWN_SECONDS.getAttrKey()))
                .thenReturn(Optional.empty());

        assertEquals(60, configService.getInt(ConfigKey.OTP_RESEND_COOLDOWN_SECONDS));
    }

    @Test
    void getIntRejectsNonIntegerStoredValue() {
        when(websiteAttributeRepository.findConfigValueByAttrKey(ConfigKey.OTP_MAX_FAILED.getAttrKey()))
                .thenReturn(Optional.of("five"));

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> configService.getInt(ConfigKey.OTP_MAX_FAILED));

        assertEquals("Configuration value for key 'otp_max_failed' must be an integer: 'five'", exception.getMessage());
    }

    @Test
    void getIntLooksUpExpectedKey() {
        when(websiteAttributeRepository.findConfigValueByAttrKey(ConfigKey.PENDING_ACCOUNT_TTL_HOURS.getAttrKey()))
                .thenReturn(Optional.of("24"));

        configService.getInt(ConfigKey.PENDING_ACCOUNT_TTL_HOURS);

        verify(websiteAttributeRepository).findConfigValueByAttrKey("pending_account_ttl_hours");
    }
}
