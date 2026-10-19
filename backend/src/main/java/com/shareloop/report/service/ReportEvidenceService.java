package com.shareloop.report.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.media.MediaErrorCode;
import com.shareloop.media.service.MediaService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.AdminReportDetail.Evidence;
import com.shareloop.report.repository.ReportRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ReportEvidenceService {
    private final MediaService media;
    private final ReportRepository reports;
    private final JsonMapper json = JsonMapper.builder().build();

    public ReportEvidenceService(MediaService media, ReportRepository reports) {
        this.media = media;
        this.reports = reports;
    }

    @Transactional
    public String attach(List<Long> ids, long ownerId) {
        if (ids == null || ids.isEmpty()) return null;
        media.attachPrivateEvidence(ids, ownerId);
        return ids.toString();
    }

    public List<Long> ids(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        return Arrays.stream(json.readValue(stored, long[].class)).boxed().toList();
    }

    @Transactional(readOnly = true)
    public List<Evidence> describe(long reportId, String stored) {
        var ids = ids(stored);
        if (ids.isEmpty()) return List.of();
        var available = media.findAvailableEvidence(ids);
        return ids.stream()
                .map(id -> new Evidence(
                        id,
                        available.contains(id) ? "/api/v1/admin/reports/" + reportId + "/evidence/" + id : null,
                        available.contains(id)))
                .toList();
    }

    @Transactional(readOnly = true)
    public MediaService.MediaContent read(long reportId, long mediaId) {
        var report =
                reports.findById(reportId).orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));
        if (!ids(report.getEvidence()).contains(mediaId)
                && !ids(report.getResponseEvidence()).contains(mediaId)) {
            throw new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND);
        }
        return media.readEvidenceForAdmin(mediaId);
    }
}
