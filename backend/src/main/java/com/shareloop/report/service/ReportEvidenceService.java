package com.shareloop.report.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.media.MediaErrorCode;
import com.shareloop.media.service.MediaService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.AdminReportDetail.Evidence;
import com.shareloop.report.entity.Report;
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
        return describeWithUrl(stored, "/api/v1/admin/reports/" + reportId + "/evidence/");
    }

    /** Như {@link #describe} nhưng link ảnh trỏ tới API của bên bị phản ánh thay vì API Admin. */
    @Transactional(readOnly = true)
    public List<Evidence> describeForRespondent(long reportId, String stored) {
        return describeWithUrl(stored, "/api/v1/reports/" + reportId + "/evidence/");
    }

    private List<Evidence> describeWithUrl(String stored, String urlPrefix) {
        var ids = ids(stored);
        if (ids.isEmpty()) return List.of();
        var available = media.findAvailableEvidence(ids);
        return ids.stream()
                .map(id -> new Evidence(id, available.contains(id) ? urlPrefix + id : null, available.contains(id)))
                .toList();
    }

    @Transactional(readOnly = true)
    public MediaService.MediaContent read(long reportId, long mediaId) {
        var report =
                reports.findById(reportId).orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));
        return readLinkedMedia(report, mediaId);
    }

    /**
     * Bên bị phản ánh xem ảnh của hồ sơ để giải trình. Không phải bên bị phản ánh hoặc hồ sơ chưa có hạn giải trình
     * thì báo REPORT_NOT_FOUND, để người ngoài không dò được hồ sơ nào tồn tại.
     */
    @Transactional(readOnly = true)
    public MediaService.MediaContent readForRespondent(long userId, long reportId, long mediaId) {
        var report = reports.findById(reportId)
                .filter(found -> found.isVisibleToRespondent(userId))
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));
        return readLinkedMedia(report, mediaId);
    }

    // Ảnh phải thuộc đúng hồ sơ này; quyền xem hồ sơ đã được kiểm ở hàm gọi nên đọc qua readEvidenceForAdmin.
    private MediaService.MediaContent readLinkedMedia(Report report, long mediaId) {
        if (!ids(report.getEvidence()).contains(mediaId)
                && !ids(report.getResponseEvidence()).contains(mediaId)) {
            throw new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND);
        }
        return media.readEvidenceForAdmin(mediaId);
    }
}
