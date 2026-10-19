package com.shareloop.report.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shareloop.media.MediaErrorCode;
import com.shareloop.media.service.MediaService;
import com.shareloop.report.entity.*;
import com.shareloop.report.repository.ReportRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportEvidenceServiceTest {
    final MediaService media = mock(MediaService.class);
    final ReportRepository reports = mock(ReportRepository.class);
    final ReportEvidenceService service = new ReportEvidenceService(media, reports);

    @Test
    void describesStoredIdsWithoutLeakingStorageKeysAndKeepsUnavailableEvidence() {
        when(media.findAvailableEvidence(List.of(12L, 15L))).thenReturn(List.of(12L));
        var result = service.describe(7L, "[12, 15]");
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().url()).isEqualTo("/api/v1/admin/reports/7/evidence/12");
        assertThat(result.getLast().id()).isEqualTo(15L);
        assertThat(result.getLast().available()).isFalse();
        assertThat(result.getLast().url()).isNull();
        assertThat(service.describe(7L, null)).isEmpty();
        assertThat(service.describe(7L, "[]")).isEmpty();
    }

    @Test
    void onlyReadsMediaLinkedToEitherPartyOfThisReport() {
        var report =
                new Report(1L, TargetType.USER, null, 2L, null, ReportReason.OTHER, "Nội dung", ReportStage.BEFORE);
        report.setEvidence("[12]");
        report.respond("Giải trình", "[15]", java.time.Instant.now());
        when(reports.findById(7L)).thenReturn(Optional.of(report));
        assertThatThrownBy(() -> service.read(7L, 99L))
                .hasFieldOrPropertyWithValue("errorCode", MediaErrorCode.MEDIA_NOT_FOUND);
        verifyNoInteractions(media);
        service.read(7L, 12L);
        service.read(7L, 15L);
        verify(media).readEvidenceForAdmin(12L);
        verify(media).readEvidenceForAdmin(15L);
    }
}
