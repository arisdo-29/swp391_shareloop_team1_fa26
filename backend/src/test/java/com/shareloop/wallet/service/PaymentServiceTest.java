package com.shareloop.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.payment.MockPaymentClient;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.TopUpConfirmResponse;
import com.shareloop.wallet.dto.TopUpOrderResponse;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.mapper.WalletMapper;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import com.shareloop.wallet.repository.PaymentOrderSnapshot;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit test PaymentService: Mockito, không khởi động Spring, không đụng DB. Khoá và cộng tiền thật ở các test IT. */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final long USER_ID = 10L;
    private static final long OTHER_USER_ID = 20L;
    private static final long ORDER_ID = 7L;
    private static final String ORDER_CODE = "abc123";

    @Mock
    PaymentOrderRepository paymentOrderRepository;

    @Mock
    CreditService creditService;

    @Mock
    ConfigService configService;

    @Spy
    MockPaymentClient paymentGatewayClient = new MockPaymentClient();

    @Spy
    WalletMapper walletMapper = new WalletMapper();

    @InjectMocks
    PaymentService paymentService;

    // ---------- createTopUpOrder ----------

    @Test
    void createTopUpOrder_whenBelowMinimum_throwsTopUpAmountInvalid() {
        // given: tối thiểu 10 Credit x 1.000đ = 10.000đ
        givenDefaultTopUpConfig();

        // when
        Throwable thrown = catchThrowable(() -> paymentService.createTopUpOrder(USER_ID, 9_000));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_TOPUP_AMOUNT_INVALID);
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    void createTopUpOrder_whenNotMultipleOfVndPerCredit_throwsTopUpAmountInvalid() {
        // given
        givenDefaultTopUpConfig();

        // when
        Throwable thrown = catchThrowable(() -> paymentService.createTopUpOrder(USER_ID, 15_500));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_TOPUP_AMOUNT_INVALID);
    }

    @Test
    void createTopUpOrder_whenAmountValid_savesPendingOrderWithCreditsAndReturnsRedirectUrl() {
        // given
        givenDefaultTopUpConfig();

        // when
        TopUpOrderResponse response = paymentService.createTopUpOrder(USER_ID, 20_000);

        // then: đơn lưu đúng số Credit, tỉ lệ tại thời điểm nạp và trạng thái PENDING
        ArgumentCaptor<PaymentOrder> savedOrder = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(paymentOrderRepository).save(savedOrder.capture());
        assertThat(savedOrder.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(savedOrder.getValue().getCredits()).isEqualTo(20);
        assertThat(savedOrder.getValue().getVndPerCredit()).isEqualTo(1_000);
        assertThat(savedOrder.getValue().getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        assertThat(response.provider()).isEqualTo(PaymentProvider.MOCK);
        assertThat(response.credits()).isEqualTo(20);
        assertThat(response.orderCode()).hasSize(32);
        assertThat(response.redirectUrl()).endsWith("orderCode=" + response.orderCode());
    }

    // ---------- confirmMockPayment ----------

    @Test
    void confirmMockPayment_whenOrderMissing_throwsOrderNotFound() {
        // given
        when(paymentOrderRepository.findSnapshotByOrderCode(ORDER_CODE)).thenReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> paymentService.confirmMockPayment(USER_ID, ORDER_CODE));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND);
    }

    @Test
    void confirmMockPayment_whenOrderOfOtherUser_throwsOrderNotFoundAndDoesNotTopUp() {
        // given
        when(paymentOrderRepository.findSnapshotByOrderCode(ORDER_CODE))
                .thenReturn(Optional.of(snapshotOf(OTHER_USER_ID, PaymentOrderStatus.PENDING, 20)));

        // when
        Throwable thrown = catchThrowable(() -> paymentService.confirmMockPayment(USER_ID, ORDER_CODE));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND);
        verify(creditService, never()).topUp(anyLong(), anyLong());
    }

    @Test
    void confirmMockPayment_whenOwnOrder_callsTopUpAndReturnsSuccess() {
        // given
        when(paymentOrderRepository.findSnapshotByOrderCode(ORDER_CODE))
                .thenReturn(Optional.of(snapshotOf(USER_ID, PaymentOrderStatus.PENDING, 20)));
        WalletResponse walletAfterTopUp = new WalletResponse(20, 0, 20, true);
        when(creditService.topUp(USER_ID, ORDER_ID)).thenReturn(walletAfterTopUp);

        // when
        TopUpConfirmResponse response = paymentService.confirmMockPayment(USER_ID, ORDER_CODE);

        // then
        assertThat(response.status()).isEqualTo(PaymentOrderStatus.SUCCESS);
        assertThat(response.credits()).isEqualTo(20);
        assertThat(response.wallet()).isEqualTo(walletAfterTopUp);
    }

    // ---------- hàm hỗ trợ ----------

    @Test
    void confirmMockPayment_whenVnpayOrder_rejectsWithoutTopUp() {
        PaymentOrderSnapshot snapshot = org.mockito.Mockito.mock(PaymentOrderSnapshot.class);
        when(snapshot.getUserId()).thenReturn(USER_ID);
        when(snapshot.getProvider()).thenReturn(PaymentProvider.VNPAY);
        when(paymentOrderRepository.findSnapshotByOrderCode(ORDER_CODE)).thenReturn(Optional.of(snapshot));
        assertBusinessError(
                catchThrowable(() -> paymentService.confirmMockPayment(USER_ID, ORDER_CODE)),
                WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_PAYABLE);
        verify(creditService, never()).topUp(anyLong(), anyLong());
    }

    private void givenDefaultTopUpConfig() {
        when(configService.getInt(ConfigKey.VND_PER_CREDIT)).thenReturn(1_000);
        when(configService.getInt(ConfigKey.TOPUP_MIN_CREDITS)).thenReturn(10);
    }

    private void assertBusinessError(Throwable thrown, WalletErrorCode expected) {
        assertThat(thrown)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    // Tạo ảnh chụp giả cho đơn ORDER_ID; projection là interface nên tự viết một lớp ẩn danh là đủ.
    private PaymentOrderSnapshot snapshotOf(long userId, PaymentOrderStatus status, int credits) {
        return new PaymentOrderSnapshot() {
            @Override
            public PaymentProvider getProvider() {
                return PaymentProvider.MOCK;
            }

            @Override
            public Long getId() {
                return ORDER_ID;
            }

            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public PaymentOrderStatus getStatus() {
                return status;
            }

            @Override
            public int getCredits() {
                return credits;
            }
        };
    }
}
