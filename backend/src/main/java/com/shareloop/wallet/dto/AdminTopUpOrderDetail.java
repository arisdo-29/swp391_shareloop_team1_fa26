package com.shareloop.wallet.dto;

import java.util.List;
import java.util.Map;

/**
 * Chi tiết một đơn nạp cho Admin.
 *
 * @param callbackData chỉ các khoá VNPay an toàn để đối soát; không có chữ ký bảo mật
 * @param ledgerLines các dòng sổ cái đã cộng Credit cho đơn; rỗng khi chưa cộng
 */
public record AdminTopUpOrderDetail(
        AdminTopUpOrderResponse order, Map<String, String> callbackData, List<TopUpLedgerLine> ledgerLines) {}
