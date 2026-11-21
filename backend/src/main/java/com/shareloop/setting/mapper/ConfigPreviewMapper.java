package com.shareloop.setting.mapper;

import com.shareloop.setting.dto.ConfigChangeResponse;
import com.shareloop.setting.dto.ConfigMessageResponse;
import com.shareloop.setting.dto.ConfigPreviewResponse;
import com.shareloop.setting.rules.ConfigChange;
import com.shareloop.setting.rules.RuleMessage;
import com.shareloop.setting.rules.RuleResult;
import java.util.List;
import org.springframework.stereotype.Component;

/** Kết quả của bộ luật sang DTO, viết tay (không dùng MapStruct). */
@Component
public class ConfigPreviewMapper {

    public ConfigPreviewResponse toResponse(RuleResult result) {
        return new ConfigPreviewResponse(
                result.changes().stream().map(this::toChangeResponse).toList(),
                toMessages(result.errors()),
                toMessages(result.warnings()));
    }

    public List<ConfigMessageResponse> toMessages(List<RuleMessage> messages) {
        return messages.stream().map(this::toMessageResponse).toList();
    }

    private ConfigChangeResponse toChangeResponse(ConfigChange change) {
        boolean synced = change.causedByKey() != null;
        return new ConfigChangeResponse(
                change.key().key(),
                change.key().label(),
                change.oldValue(),
                change.newValue(),
                synced ? change.causedByKey().key() : null,
                synced ? change.causedByKey().label() : null,
                change.note());
    }

    private ConfigMessageResponse toMessageResponse(RuleMessage message) {
        String errorCode =
                message.errorCode() == null ? null : message.errorCode().code();
        return new ConfigMessageResponse(
                message.message(), message.keys().stream().map(key -> key.key()).toList(), errorCode);
    }
}
