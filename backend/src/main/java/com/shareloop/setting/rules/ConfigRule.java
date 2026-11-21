package com.shareloop.setting.rules;

/**
 * Một luật liên khoá. before là giá trị đang lưu (không đổi), after là giá trị sau khi áp dụng thay đổi. Luật đồng bộ
 * ghi vào after qua RuleResult.sync, luật kiểm tra chỉ ghi lỗi hoặc cảnh báo vào result.
 */
@FunctionalInterface
public interface ConfigRule {

    void apply(ConfigSnapshot before, ConfigSnapshot after, RuleResult result);
}
