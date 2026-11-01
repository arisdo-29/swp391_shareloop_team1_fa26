package com.shareloop.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.payment.PaymentGatewayClient;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.service.UserService;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit test chặn nạp thủ công khi provider hiện tại không phải MANUAL (không khởi động Spring, không đụng DB). */
class ManualTopUpServiceTest {

    private PaymentOrderRepository orders;
    private PaymentGatewayClient gatewayClient;
    private ManualTopUpService service;

    @BeforeEach
    void setUp() {
        orders = mock(PaymentOrderRepository.class);
        gatewayClient = mock(PaymentGatewayClient.class);
        when(gatewayClient.provider()).thenReturn(PaymentProvider.VNPAY);
        service = new ManualTopUpService(
                orders,
                mock(CreditService.class),
                mock(ConfigService.class),
                mock(ActivityLogService.class),
                mock(UserService.class),
                gatewayClient,
                Clock.systemUTC());
    }

    @Test
    void markPaid_whenProviderIsVnpay_throwsManualTopUpDisabled() {
        // given: provider hiện tại là VNPAY (setUp)

        // when
        Throwable thrown = catchThrowable(() -> service.markPaid(1L, "code"));

        // then
        assertDisabled(thrown);
    }

    @Test
    void approve_whenProviderIsVnpay_throwsManualTopUpDisabled() {
        // given: provider hiện tại là VNPAY (setUp)

        // when
        Throwable thrown = catchThrowable(() -> service.approve(1L, "code", "ghi chú"));

        // then
        assertDisabled(thrown);
    }

    @Test
    void reject_whenProviderIsVnpay_throwsManualTopUpDisabled() {
        // given: provider hiện tại là VNPAY (setUp)

        // when
        Throwable thrown = catchThrowable(() -> service.reject(1L, "code", "lý do"));

        // then
        assertDisabled(thrown);
    }

    // Lỗi phải ném trước khi đọc hay khoá bất kỳ đơn nào.
    private void assertDisabled(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(WalletErrorCode.WALLET_MANUAL_TOPUP_DISABLED);
        verifyNoInteractions(orders);
    }
}
