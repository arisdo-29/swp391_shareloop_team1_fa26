package com.shareloop.user.dto;

import java.util.List;

/**
 * Nhóm địa chỉ IP có từ min_accounts tài khoản đăng ký trong cửa sổ ngày cấu hình (AD-04).
 *
 * @param ipAddress địa chỉ IP đăng ký
 * @param accountCount số lượng tài khoản đăng ký từ IP này
 * @param accounts danh sách tài khoản thuộc IP này, mới đăng ký nhất lên trước
 */
public record SharedIpGroup(String ipAddress, int accountCount, List<SharedIpAccount> accounts) {}
