package com.shareloop.admin.controller;

import com.shareloop.admin.dto.AdminRequestDetail;
import com.shareloop.admin.dto.AdminRequestMessages;
import com.shareloop.admin.service.AdminRequestDetailService;
import com.shareloop.common.security.CurrentUserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chi tiết giao dịch của Admin (chỉ xem). Chỉ Admin truy cập được (/api/v1/admin/** do SecurityConfig bảo vệ). Thao tác
 * huỷ giao dịch vẫn ở AdminRequestController.
 */
@RestController
@RequestMapping("/api/v1/admin/requests")
public class AdminRequestDetailController {

    private final AdminRequestDetailService adminRequestDetailService;

    public AdminRequestDetailController(AdminRequestDetailService adminRequestDetailService) {
        this.adminRequestDetailService = adminRequestDetailService;
    }

    @GetMapping("/{id}")
    public AdminRequestDetail detail(@PathVariable long id) {
        return adminRequestDetailService.getDetail(id);
    }

    // Chỉ trả khi giao dịch có báo cáo hoặc khiếu nại, và ghi nhật ký mỗi lần xem.
    @GetMapping("/{id}/messages")
    public AdminRequestMessages messages(@PathVariable long id, @CurrentUserId Long adminId) {
        return adminRequestDetailService.getMessages(adminId, id);
    }
}
