package com.shareloop.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.admin.dto.AnomalyReportResponse;
import com.shareloop.admin.dto.AnomalyUser;
import com.shareloop.admin.dto.RepeatedPairAnomaly;
import com.shareloop.reputation.dto.RepeatedPair;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAnomalyServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T03:00:00Z");
    private static final Instant LAST_COMPLETED = Instant.parse("2026-10-20T08:00:00Z");

    @Mock
    ReputationService reputationService;

    @Mock
    UserService userService;

    @Mock
    ConfigService configService;

    AdminAnomalyService service;

    @BeforeEach
    void setUp() {
        service = new AdminAnomalyService(
                reputationService, userService, configService, Clock.fixed(NOW, ZoneOffset.UTC));
        when(configService.getInt(ConfigKey.REPUTATION_PAIR_WINDOW_DAYS)).thenReturn(90);
        when(configService.getInt(ConfigKey.REPUTATION_PAIR_ALERT_THRESHOLD)).thenReturn(5);
    }

    @Test
    @DisplayName("Cửa sổ và ngưỡng đọc từ ConfigService; cặp được gắn họ tên, email của hai người")
    void getAnomalies_repeatedPair_returnsPairWithNames() {
        when(reputationService.findRepeatedPairs(NOW.minus(Duration.ofDays(90)), 5))
                .thenReturn(List.of(new RepeatedPair(3L, 7L, 6, LAST_COMPLETED)));
        when(userService.findBriefs(List.of(3L, 7L)))
                .thenReturn(Map.of(
                        3L, new UserBrief(3L, "Nguyễn Văn An", "an@fpt.edu.vn"),
                        7L, new UserBrief(7L, "Trần Thị Bình", "binh@fpt.edu.vn")));

        AnomalyReportResponse response = service.getAnomalies();

        assertThat(response.generatedAt()).isEqualTo(NOW);
        assertThat(response.pairWindowDays()).isEqualTo(90);
        assertThat(response.pairThreshold()).isEqualTo(5);
        assertThat(response.repeatedPairs())
                .containsExactly(new RepeatedPairAnomaly(
                        new AnomalyUser(3L, "Nguyễn Văn An", "an@fpt.edu.vn"),
                        new AnomalyUser(7L, "Trần Thị Bình", "binh@fpt.edu.vn"),
                        6,
                        LAST_COMPLETED));
    }

    @Test
    @DisplayName("Người dùng đã xoá mềm (không có trong findBriefs) hiển thị 'Người dùng #id'")
    void getAnomalies_deletedUser_showsPlaceholderName() {
        when(reputationService.findRepeatedPairs(any(), anyInt()))
                .thenReturn(List.of(new RepeatedPair(3L, 7L, 6, LAST_COMPLETED)));
        when(userService.findBriefs(List.of(3L, 7L)))
                .thenReturn(Map.of(3L, new UserBrief(3L, "Nguyễn Văn An", "an@fpt.edu.vn")));

        AnomalyReportResponse response = service.getAnomalies();

        assertThat(response.repeatedPairs().get(0).userB()).isEqualTo(new AnomalyUser(7L, "Người dùng #7", null));
    }

    @Test
    @DisplayName("Không có cặp nào vượt ngưỡng: danh sách rỗng, không tra tên người dùng")
    void getAnomalies_noRepeatedPair_returnsEmptyList() {
        when(reputationService.findRepeatedPairs(any(), anyInt())).thenReturn(List.of());

        AnomalyReportResponse response = service.getAnomalies();

        assertThat(response.repeatedPairs()).isEmpty();
        assertThat(response.pairThreshold()).isEqualTo(5);
        verify(userService, never()).findBriefs(any());
    }
}
