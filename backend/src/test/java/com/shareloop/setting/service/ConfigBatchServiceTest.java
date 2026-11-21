package com.shareloop.setting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.setting.dto.BatchUpdateConfigResponse;
import com.shareloop.setting.dto.ConfigChangeItem;
import com.shareloop.setting.entity.WebsiteAttribute;
import com.shareloop.setting.mapper.ConfigPreviewMapper;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
import com.shareloop.setting.rules.ConfigRuleEngine;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Không dùng Spring context: repository và ActivityLogService là mock, engine và mapper là bản thật. */
class ConfigBatchServiceTest {

    private final WebsiteAttributeRepository repository = mock(WebsiteAttributeRepository.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final ConfigPreviewMapper previewMapper = new ConfigPreviewMapper();
    private final ConfigService configService =
            new ConfigService(repository, activityLogService, new ConfigRuleEngine(), previewMapper);
    private final ConfigBatchService batchService = new ConfigBatchService(configService, previewMapper);

    @Test
    void updateBatch_whenSecondLogFails_propagatesExceptionAndReturnsNoSuccessResult() {
        // given: boost_days 3 -> 4 kéo boost_max_days 6 -> 8 nên có hai dòng nhật ký; dòng thứ hai ném lỗi
        givenStoredAttribute("boost_days", "3");
        givenStoredAttribute("boost_max_days", "6");
        doNothing()
                .doThrow(new IllegalStateException("ghi nhật ký lỗi"))
                .when(activityLogService)
                .log(eq("CONFIG_UPDATE"), eq(9L), eq("CONFIG"), isNull(), anyMap(), eq("Đổi gói"));
        AtomicReference<BatchUpdateConfigResponse> response = new AtomicReference<>();

        // when / then: ngoại lệ lan ra ngoài, không có kết quả thành công nào được trả về
        assertThatThrownBy(() -> response.set(batchService.updateBatch(
                        List.of(new ConfigChangeItem("boost_days", "4")), "Đổi gói", false, 9L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ghi nhật ký lỗi");
        assertThat(response.get()).isNull();
        verify(activityLogService, times(2))
                .log(eq("CONFIG_UPDATE"), eq(9L), eq("CONFIG"), isNull(), anyMap(), eq("Đổi gói"));
    }

    private void givenStoredAttribute(String key, String value) {
        WebsiteAttribute attribute = mock(WebsiteAttribute.class);
        when(attribute.getAttrValue()).thenReturn(value);
        when(repository.findFirstByAttrGroupAndAttrKeyAndIsActiveTrue("CONFIG", key))
                .thenReturn(Optional.of(attribute));
    }
}
