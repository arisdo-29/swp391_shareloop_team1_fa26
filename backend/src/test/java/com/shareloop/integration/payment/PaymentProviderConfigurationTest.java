package com.shareloop.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.shareloop.setting.service.ConfigService;
import com.shareloop.wallet.entity.PaymentProvider;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

class PaymentProviderConfigurationTest {
    private ApplicationContextRunner runner(Map<String, Object> environment) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new MapPropertySource("payment-test-environment", environment)))
                .withInitializer(context -> {
                    // A previous test context may enable DEBUG; do not dump YAML configuration values.
                    var logging = LoggingSystem.get(getClass().getClassLoader());
                    String loggerName = "org.springframework.boot.env.OriginTrackedYamlLoader";
                    var logger = logging.getLoggerConfiguration(loggerName);
                    var previous = logger != null ? logger.getConfiguredLevel() : null;
                    try {
                        logging.setLogLevel(loggerName, LogLevel.OFF);
                        new ConfigDataApplicationContextInitializer().initialize(context);
                    } finally {
                        logging.setLogLevel(loggerName, previous);
                    }
                })
                .withBean(ConfigService.class, () -> mock(ConfigService.class))
                .withBean(Clock.class, Clock::systemUTC)
                .withUserConfiguration(MockPaymentClient.class, ManualPaymentClient.class, VnPayClient.class);
    }

    private Map<String, Object> vnpayEnvironment() {
        return new HashMap<>(Map.of(
                "PAYMENT_PROVIDER", "vnpay",
                "VNPAY_TMN_CODE", "TESTONLY",
                "VNPAY_HASH_SECRET", "test-secret",
                "VNPAY_PAY_URL", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "VNPAY_RETURN_URL", "https://backend.example/api/v1/payments/vnpay/return",
                "VNPAY_FE_RESULT_URL", "https://frontend.example/credit"));
    }

    @Test
    void defaultProviderIsMockWithoutMerchantConfiguration() {
        runner(Map.of()).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(PaymentGatewayClient.class);
            assertThat(context.getBean(PaymentGatewayClient.class).provider()).isEqualTo(PaymentProvider.MOCK);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"mock", "manual", "vnpay"})
    void configuredProviderCreatesExactlyOneClient(String provider) {
        Map<String, Object> environment = provider.equals("vnpay") ? vnpayEnvironment() : new HashMap<>();
        environment.put("PAYMENT_PROVIDER", provider);
        runner(environment).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(PaymentGatewayClient.class);
            assertThat(context.getBean(PaymentGatewayClient.class).provider().name())
                    .isEqualToIgnoringCase(provider);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void environmentMappingWorksInBothProfiles(String profile) {
        runner(vnpayEnvironment())
                .withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(VnPayClient.class);
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("app.payment.vnpay.tmn-code"))
                            .isEqualTo("TESTONLY");
                    assertThat(environment.getProperty("app.payment.vnpay.hash-secret"))
                            .isEqualTo("test-secret");
                    assertThat(environment.getProperty("app.payment.vnpay.return-url"))
                            .isEqualTo("https://backend.example/api/v1/payments/vnpay/return");
                    assertThat(environment.getProperty("app.payment.vnpay.fe-result-url"))
                            .isEqualTo("https://frontend.example/credit");
                    assertThat(environment.getProperty("app.payment.vnpay.pay-url"))
                            .isEqualTo("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"VNPAY_TMN_CODE", "VNPAY_HASH_SECRET", "VNPAY_RETURN_URL", "VNPAY_PAY_URL"})
    void vnpayFailsClearlyWhenRequiredConfigurationIsBlank(String missing) {
        var environment = vnpayEnvironment();
        environment.put(missing, " ");
        runner(environment)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("VNPAY requires pay-url, return-url, tmn-code and hash-secret"));
    }
}
