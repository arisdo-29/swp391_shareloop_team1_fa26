package com.shareloop.setting.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.BatchUpdateConfigResponse;
import com.shareloop.setting.dto.ConfigChangeItem;
import com.shareloop.setting.dto.ConfigPreviewResponse;
import com.shareloop.setting.dto.ConfigResponse;
import com.shareloop.setting.mapper.ConfigPreviewMapper;
import com.shareloop.setting.rules.ConfigChange;
import com.shareloop.setting.rules.RuleResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xem trước và cập nhật theo lô cho trang cấu hình của Admin. Việc kiểm kiểu, chạy bộ luật và lưu nằm ở
 * {@link ConfigService}, class này chỉ ghép các bước theo đúng thứ tự: khoá dòng, kiểm tra, xác nhận cảnh báo, lưu.
 */
@Service
public class ConfigBatchService {

    private final ConfigService configService;
    private final ConfigPreviewMapper previewMapper;

    public ConfigBatchService(ConfigService configService, ConfigPreviewMapper previewMapper) {
        this.configService = configService;
        this.previewMapper = previewMapper;
    }

    /**
     * Xem trước một thay đổi: cấu hình nào sẽ đổi kèm, lỗi và cảnh báo. Không ghi gì vào DB và không khoá dòng.
     *
     * @throws BusinessException khoá không tồn tại, khoá cố định, giá trị sai kiểu hoặc ngoài khoảng
     */
    // readOnly = true: chỉ đọc, không mở transaction ghi.
    @Transactional(readOnly = true)
    public ConfigPreviewResponse preview(String key, String value) {
        ConfigKey configKey = configService.resolveKey(key);
        return configService.preview(Map.of(configKey, value));
    }

    /**
     * Lưu cả lô hoặc không lưu gì. Khoá dòng, chạy lại bộ luật trên toàn bộ thay đổi, rồi lưu mọi khoá đã đổi (kể cả khoá
     * đồng bộ) và ghi mỗi khoá một dòng nhật ký cùng lý do.
     *
     * @throws BusinessException 400 khi có lỗi luật, gửi trùng khoá hoặc giá trị không hợp lệ,
     *     SETTING_CONFIG_WARNING_NOT_ACKNOWLEDGED (409) khi có cảnh báo mà chưa xác nhận
     */
    // @Transactional: mọi lỗi giữa chừng (kể cả lỗi ghi nhật ký) đều rollback cả lô.
    @Transactional
    public BatchUpdateConfigResponse updateBatch(
            List<ConfigChangeItem> items, String reason, boolean acknowledgedWarnings, Long adminId) {
        Map<ConfigKey, String> requested = toRequestedMap(items);
        configService.validateRequested(requested);
        configService.lockConfigRows();
        RuleResult result = configService.evaluate(requested);
        configService.requireNoErrors(result);
        if (result.hasWarnings() && !acknowledgedWarnings) {
            throw new BusinessException(
                    SettingErrorCode.SETTING_CONFIG_WARNING_NOT_ACKNOWLEDGED,
                    null,
                    null,
                    Map.of("preview", previewMapper.toResponse(result)));
        }
        configService.saveChanges(result.changes(), reason, adminId);
        return new BatchUpdateConfigResponse(toResponses(result.changes()));
    }

    // Một khoá gửi hai lần thì không biết lấy giá trị nào, nên từ chối thay vì lặng lẽ lấy giá trị cuối.
    private Map<ConfigKey, String> toRequestedMap(List<ConfigChangeItem> items) {
        Map<ConfigKey, String> requested = new LinkedHashMap<>();
        for (ConfigChangeItem item : items) {
            ConfigKey key = configService.resolveKey(item.key());
            if (requested.containsKey(key)) {
                throw new BusinessException(
                        SettingErrorCode.SETTING_CONFIG_RULE_VIOLATION,
                        "%s bị gửi nhiều lần trong cùng một lần cập nhật.".formatted(key.label()));
            }
            requested.put(key, item.value());
        }
        return requested;
    }

    private List<ConfigResponse> toResponses(List<ConfigChange> changes) {
        return changes.stream()
                .map(change -> configService.toResponse(change.key(), change.newValue()))
                .toList();
    }
}
