package com.shareloop.admin.dto;

/**
 * Người dùng xuất hiện trong một cảnh báo bất thường.
 *
 * @param id id người dùng
 * @param fullName họ tên; người dùng đã xoá mềm thì là "Người dùng #id"
 * @param email email; null khi người dùng đã xoá mềm
 */
public record AnomalyUser(Long id, String fullName, String email) {}
