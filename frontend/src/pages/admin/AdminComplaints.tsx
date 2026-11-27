import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  Alert,
  Avatar,
  Button,
  EmptyState,
  Icon,
  ItemImage,
  PageHeader,
  Select,
  SkeletonRows,
  TextArea,
} from '../../components/ui';
import { DialogFrame } from '../../components/Dialog';
import { Link } from 'react-router-dom';
import {
  reportApi,
  type AdminReportDetail,
  type AdminReportPage,
  type ReportDecision,
  type ReportStatus,
  type ReportTargetType,
  type ReportEvidence,
  type ReportResponse,
} from '../../services/reportApi';
import type { ApiError } from '../../services/http';
import { formatDeadline } from '../../utils/request';
import { adminRequestApi, type AdminRequestDetail } from '../../services/adminRequestApi';
import { adminItemApi, type AdminItemRow } from '../../services/adminItemApi';
import { DisputeItemOutcomes } from '../../components/admin/DisputeItemOutcomes';
import {
  defaultItemChoices,
  disputeItemRows,
  type ItemChoice,
} from '../../utils/disputeItemOutcomes';
import { disputeReasonLabels } from '../../utils/disputeErrors';
import { useRealtimeRefresh } from '../../utils/useRealtimeRefresh';
import { DashboardGrid, DashboardStatCard, type DashboardAccent } from './AdminDashboardCards';
import './admin-complaints.css';

const statusLabels: Record<ReportStatus, string> = {
  OPEN: 'Chờ xử lý',
  AWAITING_RESPONSE: 'Chờ giải trình',
  RESOLVED: 'Đã chấp thuận',
  DISMISSED: 'Đã bác bỏ',
};
const targetLabels: Record<ReportTargetType, string> = {
  ITEM: 'Báo cáo bài đăng',
  USER: 'Báo cáo người dùng',
  REQUEST: 'Khiếu nại giao dịch',
};
const filters: { value: ReportStatus | ''; label: string }[] = [
  { value: '', label: 'Tất cả' },
  { value: 'OPEN', label: 'Chờ xử lý' },
  { value: 'AWAITING_RESPONSE', label: 'Chờ giải trình' },
  { value: 'RESOLVED', label: 'Đã chấp thuận' },
  { value: 'DISMISSED', label: 'Đã bác bỏ' },
];
const decisionLabels: Record<ReportDecision, string> = {
  UPHOLD: 'Chấp thuận · Có cơ sở',
  DISMISS: 'Bác bỏ · Không có cơ sở',
};
function reportReasonLabel(report: ReportResponse) {
  if (report.targetType === 'REQUEST' && report.reason === 'NO_SHOW') return 'Sự cố giao nhận';
  return disputeReasonLabels[report.reason] ?? report.reason;
}

const date = (value: string | null) =>
  value ? new Date(value).toLocaleString('vi-VN') : 'Chưa có';

interface ComplaintListEnrichment {
  detail: AdminReportDetail | null;
  item: AdminItemRow | null;
}

function reportCode(id: number) {
  return `HS-${id}`;
}
// Báo cáo Từ chối giao nhận (báo cáo bài kèm giao dịch): người báo đã bị trừ sao tự động nên không có ô phạt thêm (F7).
const isRefusalReport = (report: ReportResponse) =>
  report.targetType === 'ITEM' && report.reason === 'NOT_AS_DESCRIBED' && report.requestId != null;

const concluded = (status: ReportStatus) => status === 'RESOLVED' || status === 'DISMISSED';
const statusTones: Record<ReportStatus, string> = {
  OPEN: 'bg-warning-soft text-warning',
  AWAITING_RESPONSE: 'bg-info-soft text-info',
  RESOLVED: 'bg-success-soft text-success',
  DISMISSED: 'bg-error-soft text-error',
};

type ComplaintStatisticKey = 'total' | ReportStatus;
type ComplaintStatisticValue = number | null | 'error';

const emptyStatistics: Record<ComplaintStatisticKey, ComplaintStatisticValue> = {
  total: null,
  OPEN: null,
  AWAITING_RESPONSE: null,
  RESOLVED: null,
  DISMISSED: null,
};

const statisticCards: Array<{
  key: ComplaintStatisticKey;
  label: string;
  note: string;
  icon: 'complaints' | 'expiring' | 'lookup' | 'completed' | 'banned';
  accent?: DashboardAccent;
}> = [
  { key: 'total', label: 'Tổng hồ sơ', note: 'Tất cả hồ sơ trong hệ thống.', icon: 'complaints' },
  {
    key: 'OPEN',
    label: 'Chờ xử lý',
    note: 'Hồ sơ đang chờ xem xét.',
    icon: 'expiring',
    accent: 'gold',
  },
  {
    key: 'AWAITING_RESPONSE',
    label: 'Chờ giải trình',
    note: 'Hồ sơ đang chờ bên liên quan phản hồi.',
    icon: 'lookup',
    accent: 'blue',
  },
  {
    key: 'RESOLVED',
    label: 'Đã chấp thuận',
    note: 'Hồ sơ đã được xác định có cơ sở.',
    icon: 'completed',
    accent: 'green',
  },
  {
    key: 'DISMISSED',
    label: 'Đã bác bỏ',
    note: 'Hồ sơ đã được xác định không có cơ sở.',
    icon: 'banned',
    accent: 'red',
  },
];

// Cùng kiểu StatusBadge trong ui.tsx nhưng theo bộ trạng thái của hồ sơ khiếu nại.
function ReportStatusBadge({ status }: { status: ReportStatus }) {
  return (
    <span
      className={`inline-flex whitespace-nowrap rounded-sm px-2 py-1 text-xs font-semibold ${statusTones[status]}`}
    >
      {statusLabels[status]}
    </span>
  );
}

function DetailRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-0.5 sm:grid-cols-[9rem_1fr] sm:gap-3">
      <dt className="text-text-secondary">{label}</dt>
      <dd className="min-w-0 break-words text-text-primary">{children}</dd>
    </div>
  );
}

function reportedUserName(detail: AdminReportDetail) {
  return detail.reportedUser
    ? (detail.reportedUser.fullName ?? 'Người dùng')
    : 'Không còn xác định được';
}

// Hệ quả của kết luận đã lưu, khớp với xử lý ở backend (ReportService.conclude).
function consequences(detail: AdminReportDetail) {
  const upheld = detail.report.status === 'RESOLVED';
  const parts = [
    detail.starsDeducted > 0 ? `Trừ ${detail.starsDeducted} sao uy tín` : 'Không trừ sao',
  ];
  if (detail.report.targetType === 'ITEM') parts.push(upheld ? 'Ẩn bài đăng' : 'Không ẩn bài');
  if (detail.report.targetType === 'REQUEST') {
    // Bác khiếu nại thì giao dịch tranh chấp chạy tiếp hoặc được công nhận hoàn tất tuỳ từng
    // trường hợp ở backend (RequestService.resolveDisputedRequest), nên không ghi cứng "Hoàn tất".
    parts.push(
      upheld
        ? 'Giao dịch đang tranh chấp (nếu có) bị huỷ'
        : 'Giao dịch đang tranh chấp (nếu có) được xử lý tiếp, không bị huỷ',
    );
  }
  return parts.join(' · ');
}
function errorMessage(error: ApiError) {
  if (error.status === 401) return '';
  if (error.status === 403) return 'Tài khoản hiện tại không có quyền xử lý khiếu nại.';
  if (error.status === 404) return 'Hồ sơ khiếu nại không còn tồn tại.';
  return error.message || 'Không tải được khiếu nại. Vui lòng thử lại.';
}

function EvidenceImage({ reportId, evidence }: { reportId: number; evidence: ReportEvidence }) {
  const [source, setSource] = useState('');
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    if (!evidence.available || !evidence.url) return;
    const controller = new AbortController();
    let objectUrl = '';
    reportApi
      .evidence(reportId, evidence.id, controller.signal)
      .then((blob) => {
        if (controller.signal.aborted) return;
        objectUrl = URL.createObjectURL(blob);
        setSource(objectUrl);
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true);
      });
    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [reportId, evidence.id, evidence.available, evidence.url]);
  return (
    <div className="rounded-md bg-background p-2">
      {failed || !evidence.available || !evidence.url ? (
        <p className="p-3 text-xs text-text-muted">Ảnh bằng chứng không còn khả dụng.</p>
      ) : source ? (
        <a
          href={source}
          target="_blank"
          rel="noreferrer"
          title="Mở ảnh kích thước đầy đủ"
          className="block cursor-zoom-in"
        >
          <img
            src={source}
            alt="Ảnh bằng chứng"
            className="max-h-48 w-full rounded-md object-contain"
            onError={() => setFailed(true)}
          />
        </a>
      ) : (
        <p className="p-3 text-xs">Đang tải ảnh bằng chứng…</p>
      )}
    </div>
  );
}

function EvidenceGallery({
  reportId,
  title,
  evidence,
}: {
  reportId: number;
  title: string;
  evidence: ReportEvidence[];
}) {
  return (
    <div>
      <p className="mb-2 font-semibold text-text-secondary">
        {title} ({evidence.length})
      </p>
      {evidence.length === 0 && (
        <p className="text-sm text-text-secondary">Không có ảnh đính kèm.</p>
      )}
      <div className="grid grid-cols-2 gap-2">
        {evidence.map((entry) => (
          <EvidenceImage
            key={`${reportId}-${entry.id}-${entry.available}`}
            reportId={reportId}
            evidence={entry}
          />
        ))}
      </div>
    </div>
  );
}

interface ComplaintItemDisplay {
  id: number;
  title: string | null;
  imageUrl: string | null;
}

function ComplaintItem({
  item,
  compact = false,
  target,
}: {
  item: ComplaintItemDisplay | null;
  compact?: boolean;
  target?: string;
}) {
  if (!item?.title) return <span className="text-text-muted">—</span>;
  const detailTarget = target ?? `/admin/items/${item.id}`;
  return (
    <div className={`admin-complaint-item ${compact ? 'admin-complaint-item-compact' : ''}`}>
      <Link
        to={detailTarget}
        aria-label={`Xem chi tiết ${item.title}`}
        className="admin-complaint-item-image"
      >
        <ItemImage
          src={item.imageUrl ?? undefined}
          alt={item.title}
          className="size-14 rounded-md object-cover"
        />
      </Link>
      <div className="min-w-0">
        <Link
          to={detailTarget}
          title={item.title}
          className="admin-complaint-item-title font-semibold text-primary hover:underline"
        >
          {item.title}
        </Link>
        {!compact ? (
          <Link to={detailTarget} className="admin-complaint-item-detail">
            Xem chi tiết bài đăng
          </Link>
        ) : null}
      </div>
    </div>
  );
}

function itemFromSummary(
  item: AdminItemRow | null,
  fallbackId?: number | null,
  fallbackTitle?: string | null,
) {
  if (item) return { id: item.id, title: item.title, imageUrl: item.imageUrl };
  if (fallbackId !== null && fallbackId !== undefined && fallbackTitle) {
    return { id: fallbackId, title: fallbackTitle, imageUrl: null };
  }
  return null;
}

function listReportedUser(report: ReportResponse, enrichment?: ComplaintListEnrichment) {
  return (
    enrichment?.detail?.reportedUser?.fullName ??
    (report.targetType === 'USER' ? report.targetName : null) ??
    '—'
  );
}

function avatarInitials(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map((part) => part.charAt(0))
    .join('')
    .toLocaleUpperCase('vi-VN');
}

function ComplaintTarget({
  report,
  enrichment,
  item,
}: {
  report: ReportResponse;
  enrichment?: ComplaintListEnrichment;
  item: ComplaintItemDisplay | null;
}) {
  if (report.targetType === 'USER') {
    const userId = enrichment?.detail?.reportedUser?.id ?? report.targetUserId;
    const name = listReportedUser(report, enrichment);
    const content = (
      <>
        <Avatar
          user={{ name, avatarInitials: avatarInitials(name), avatarUrl: undefined }}
          size="md"
        />
        <span className="admin-complaint-target-name" title={name}>
          {name}
        </span>
      </>
    );
    return userId !== null ? (
      <Link
        to={`/admin/users/${userId}`}
        className="admin-complaint-target admin-complaint-target-user"
      >
        {content}
      </Link>
    ) : (
      <div className="admin-complaint-target admin-complaint-target-user">{content}</div>
    );
  }

  const target =
    report.targetType === 'REQUEST' && report.requestId !== null
      ? `/admin/transactions/${report.requestId}`
      : item
        ? `/admin/items/${item.id}`
        : undefined;
  return <ComplaintItem item={item} compact target={target} />;
}

export function AdminComplaints() {
  const [status, setStatus] = useState<ReportStatus | ''>('');
  const [targetType, setTargetType] = useState<ReportTargetType | ''>('');
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  useRealtimeRefresh(['NOTIFICATION'], () => setReload((value) => value + 1));
  const [statistics, setStatistics] = useState(emptyStatistics);
  const [list, setList] = useState<AdminReportPage | null>(null);
  const [listEnrichment, setListEnrichment] = useState<Record<number, ComplaintListEnrichment>>({});
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const opener = useRef<HTMLButtonElement>(null);
  const [detail, setDetail] = useState<AdminReportDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [decision, setDecision] = useState<ReportDecision | ''>('');
  const [note, setNote] = useState('');
  const [deliberateFalse, setDeliberateFalse] = useState(false);
  // Khiếu nại sự cố giao nhận đang DISPUTED: chọn cách xử lý từng món khi chấp thuận (GN-14)
  const [requestDetail, setRequestDetail] = useState<AdminRequestDetail | null>(null);
  const [relatedRequestDetail, setRelatedRequestDetail] = useState<AdminRequestDetail | null>(null);
  const [relatedItem, setRelatedItem] = useState<AdminItemRow | null>(null);
  const [itemChoices, setItemChoices] = useState<Record<number, ItemChoice>>({});
  // Khiếu nại do bên kia không đồng ý đề nghị huỷ không tố ai: bên bị trừ sao ('' là không trừ ai)
  const [penalizedUserId, setPenalizedUserId] = useState('');
  const [decisionStep, setDecisionStep] = useState<'review' | 'choose' | 'confirm'>('review');
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const [notice, setNotice] = useState('');
  const [noticeTone, setNoticeTone] = useState<'success' | 'warning'>('success');

  useEffect(() => {
    const controller = new AbortController();
    setStatistics(emptyStatistics);

    const loadStatistic = (key: ComplaintStatisticKey, statisticStatus?: ReportStatus) => {
      reportApi
        .list({ page: 0, size: 1, status: statisticStatus }, controller.signal)
        .then((result) => {
          if (!controller.signal.aborted) {
            setStatistics((current) => ({ ...current, [key]: result.totalElements }));
          }
        })
        .catch(() => {
          if (!controller.signal.aborted) {
            setStatistics((current) => ({ ...current, [key]: 'error' }));
          }
        });
    };

    loadStatistic('total');
    loadStatistic('OPEN', 'OPEN');
    loadStatistic('AWAITING_RESPONSE', 'AWAITING_RESPONSE');
    loadStatistic('RESOLVED', 'RESOLVED');
    loadStatistic('DISMISSED', 'DISMISSED');

    return () => controller.abort();
  }, [reload]);

  useEffect(() => {
    const controller = new AbortController();
    setListLoading(true);
    setListError('');
    setListEnrichment({});
    reportApi
      .list(
        { page, size: 20, status: status || undefined, targetType: targetType || undefined },
        controller.signal,
      )
      .then((result) => {
        if (controller.signal.aborted) return;
        if (page > 0 && page >= result.totalPages) {
          setPage(Math.max(0, result.totalPages - 1));
          return;
        }
        setList(result);
        void Promise.all(
          result.items.map(async (report) => {
            let reportDetail: AdminReportDetail | null = null;
            try {
              reportDetail = await reportApi.detail(report.id, controller.signal);
            } catch {
              if (controller.signal.aborted) return null;
            }
            const itemId = reportDetail?.relatedItemId ?? report.itemId;
            let item: AdminItemRow | null = null;
            if (itemId !== null) {
              try {
                item = (await adminItemApi.detail(itemId)).summary;
              } catch {
                item = null;
              }
            }
            return [report.id, { detail: reportDetail, item }] as const;
          }),
        ).then((entries) => {
          if (controller.signal.aborted) return;
          setListEnrichment(
            Object.fromEntries(
              entries.filter(
                (entry): entry is readonly [number, ComplaintListEnrichment] => entry !== null,
              ),
            ),
          );
        });
      })
      .catch((error: ApiError) => {
        if (!controller.signal.aborted) setListError(errorMessage(error));
      })
      .finally(() => {
        if (!controller.signal.aborted) setListLoading(false);
      });
    return () => controller.abort();
  }, [page, status, targetType, reload]);

  useEffect(() => {
    if (selectedId === null) return;
    const controller = new AbortController();
    setDetail(null);
    setDetailLoading(true);
    setDetailError('');
    setDecisionStep('review');
    setNote('');
    setDeliberateFalse(false);
    setDecision('');
    setRequestDetail(null);
    setRelatedRequestDetail(null);
    setRelatedItem(null);
    setItemChoices({});
    setPenalizedUserId('');
    reportApi
      .detail(selectedId, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setDetail(result);
      })
      .catch((error: ApiError) => {
        if (!controller.signal.aborted) setDetailError(errorMessage(error));
      })
      .finally(() => {
        if (!controller.signal.aborted) setDetailLoading(false);
      });
    return () => controller.abort();
  }, [selectedId, reload]);

  // Tải các món của giao dịch khi hồ sơ là khiếu nại giao dịch đang DISPUTED, để Admin chọn cách xử lý từng món
  useEffect(() => {
    const requestId = detail?.report.targetType === 'REQUEST' ? detail.report.requestId : null;
    if (!detail || requestId === null) return;
    let active = true;
    adminRequestApi
      .detail(requestId)
      .then((result) => {
        if (!active) return;
        setRelatedRequestDetail(result);
        if (concluded(detail.report.status) || result.status !== 'DISPUTED') return;
        setRequestDetail(result);
        // Lý do chung (OTHER) là đề nghị huỷ không tố ai: không bên nào bị coi là vi phạm nên mặc định Mở lại
        const violatorId =
          detail.report.reason === 'OTHER' ? null : (detail.reportedUser?.id ?? null);
        setItemChoices(defaultItemChoices(disputeItemRows(result), violatorId));
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [detail]);

  useEffect(() => {
    const itemId = detail?.relatedItemId;
    if (itemId === null || itemId === undefined) return;
    let active = true;
    adminItemApi
      .detail(itemId)
      .then((result) => {
        if (active) setRelatedItem(result.summary);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [detail?.relatedItemId]);

  useEffect(() => {
    if (selectedId === null) opener.current?.focus();
    else closeButton.current?.focus();
  }, [selectedId]);

  async function submit() {
    if (
      !detail ||
      concluded(detail.report.status) ||
      detail.responseWindowOpen ||
      submittingRef.current ||
      decisionStep !== 'confirm' ||
      !decision ||
      note.length > 500
    )
      return;
    submittingRef.current = true;
    setSubmitting(true);
    setDetailError('');
    try {
      await reportApi.conclude(detail.report.id, {
        decision,
        note: note.trim(),
        deliberateFalseReport: decision === 'DISMISS' && deliberateFalse ? true : undefined,
        itemOutcomes:
          decision === 'UPHOLD' && requestDetail
            ? disputeItemRows(requestDetail).map((row) => ({
                itemId: row.itemId,
                choice: itemChoices[row.itemId] ?? 'REOPEN',
              }))
            : undefined,
        penalizedUserId:
          decision === 'UPHOLD' && requestDetail && isCancelWithoutAccusation && penalizedUserId
            ? Number(penalizedUserId)
            : undefined,
      });
      setNoticeTone('success');
      setNotice('Đã kết luận khiếu nại.');
      setDetail(null);
      setReload((value) => value + 1);
    } catch (error) {
      const failure = error as ApiError;
      if (failure.code === 'REPORT_RESPONSE_WINDOW_OPEN') {
        // Dữ liệu trên trang đã cũ (mở hồ sơ trước khi nó chuyển sang chờ giải trình): tải lại để thấy hạn giải trình
        setNoticeTone('warning');
        setNotice('Bên bị báo cáo vẫn đang trong hạn giải trình, chưa thể kết luận.');
        setDetail(null);
        setReload((value) => value + 1);
      } else if (failure.status === 409) {
        setNoticeTone('warning');
        setNotice('Hồ sơ đã được quản trị viên khác xử lý. Đang tải lại trạng thái.');
        setDetail(null);
        setReload((value) => value + 1);
      } else {
        setDetailError(errorMessage(failure));
        if (failure.status === 404 || failure.status === 403 || failure.status === 401)
          setDetail(null);
      }
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  // D-07, BC-03: báo cáo bài hoặc người dùng đang Chờ xử lý, chưa có giải trình thì Admin yêu cầu giải trình được.
  // Khiếu nại giao dịch tự chờ giải trình lúc mở nên không có nút này. Backend kiểm lại.
  const canRequestResponse =
    !!detail &&
    detail.report.status === 'OPEN' &&
    detail.report.targetType !== 'REQUEST' &&
    !detail.response;

  async function requestResponse() {
    if (!detail || !canRequestResponse || submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    setDetailError('');
    try {
      await reportApi.requestResponse(detail.report.id);
      setNoticeTone('success');
      setNotice('Đã gửi yêu cầu giải trình. Xem hạn phản hồi trong hồ sơ.');
      setDetail(null);
      setReload((value) => value + 1);
    } catch (error) {
      const failure = error as ApiError;
      if (failure.status === 409) {
        setNoticeTone('warning');
        setNotice('Hồ sơ không còn ở trạng thái Chờ xử lý. Đang tải lại trạng thái.');
        setDetail(null);
        setReload((value) => value + 1);
      } else {
        setDetailError(errorMessage(failure));
        if (failure.status === 404 || failure.status === 403 || failure.status === 401)
          setDetail(null);
      }
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  const closeDetail = () => setSelectedId(null);
  const title = detail?.report.targetName;
  const isCancelWithoutAccusation =
    detail?.report.targetType === 'REQUEST' && detail.report.reason === 'OTHER';
  const detailIsConcluded = detail && concluded(detail.report.status);
  const modalMainItem: ComplaintItemDisplay | null = relatedRequestDetail?.item
    ? {
        id: relatedRequestDetail.item.itemId,
        title: relatedRequestDetail.item.title,
        imageUrl: relatedRequestDetail.item.imageUrl,
      }
    : itemFromSummary(relatedItem, detail?.relatedItemId, detail?.relatedItemTitle);
  const modalOfferedItem: ComplaintItemDisplay | null = relatedRequestDetail?.offeredItem
    ? {
        id: relatedRequestDetail.offeredItem.itemId,
        title: relatedRequestDetail.offeredItem.title,
        imageUrl: relatedRequestDetail.offeredItem.imageUrl,
      }
    : null;

  return (
    <section className="admin-complaints-page">
      <div className="admin-complaints-header">
        <PageHeader
          title="Khiếu nại và báo cáo"
          description="Xem xét sự việc, đối chiếu minh chứng và kết luận từng hồ sơ."
        />
      </div>
      {notice && selectedId === null && (
        <div className="mb-4" role="status">
          <Alert tone={noticeTone}>{notice}</Alert>
        </div>
      )}
      <section className="admin-dash-vars" aria-label="Thống kê hồ sơ">
        <DashboardGrid columns={5}>
          {statisticCards.map((card) => {
            const value = statistics[card.key];
            return (
              <DashboardStatCard
                key={card.key}
                icon={card.icon}
                label={card.label}
                value={
                  typeof value === 'number'
                    ? value.toLocaleString('vi-VN')
                    : value === 'error'
                      ? '—'
                      : '...'
                }
                loading={value === null}
                variant="task"
                accent={card.accent}
                pending={typeof value === 'number' && value > 0}
                note={
                  value === 'error'
                    ? 'Chưa tải được số liệu.'
                    : value === null
                      ? 'Đang tải số liệu.'
                      : card.note
                }
              />
            );
          })}
        </DashboardGrid>
      </section>
      <div className="admin-complaints-filters">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div className="min-w-0">
            <p className="label">Trạng thái</p>
            <div className="flex flex-wrap gap-2" role="group" aria-label="Lọc trạng thái">
              {filters.map(({ value, label }) => (
                <Button
                  key={value}
                  size="sm"
                  variant={status === value ? 'primary' : 'outline'}
                  aria-pressed={status === value}
                  disabled={submitting}
                  onClick={() => {
                    setStatus(value);
                    setPage(0);
                    closeDetail();
                  }}
                >
                  {label}
                </Button>
              ))}
            </div>
          </div>
          <div className="admin-complaints-filter-type">
            <Select
              label="Loại hồ sơ"
              value={targetType}
              disabled={submitting}
              onChange={(event) => {
                setTargetType(event.target.value as ReportTargetType | '');
                setPage(0);
                closeDetail();
              }}
            >
              <option value="">Tất cả loại hồ sơ</option>
              <option value="REQUEST">Khiếu nại giao dịch</option>
              <option value="ITEM">Báo cáo bài đăng</option>
              <option value="USER">Báo cáo người dùng</option>
            </Select>
          </div>
        </div>
        {status === 'AWAITING_RESPONSE' && (
          <p className="text-sm text-text-secondary">
            Mở hồ sơ để xem hạn giải trình. Hồ sơ đã quá hạn có thể được kết luận.
          </p>
        )}
      </div>
      {listLoading ? (
        <SkeletonRows label="Đang tải khiếu nại" />
      ) : listError ? (
        <div role="alert" className="space-y-3">
          <Alert tone="warning">{listError}</Alert>
          <Button onClick={() => setReload((value) => value + 1)}>Thử lại</Button>
        </div>
      ) : list?.items.length ? (
        <>
          <div className="admin-complaints-table-wrap">
            <table className="admin-complaints-table" aria-label="Danh sách hồ sơ">
              <thead>
                <tr>
                  <th>Mã hồ sơ</th>
                  <th>Đối tượng báo cáo</th>
                  <th>Loại khiếu nại/báo cáo</th>
                  <th>Người gửi báo cáo</th>
                  <th>Người bị báo cáo</th>
                  <th>Lý do báo cáo</th>
                  <th>Ngày gửi</th>
                  <th>Trạng thái xử lý</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {list.items.map((report) => {
                  const enrichment = listEnrichment[report.id];
                  const relatedItem = itemFromSummary(
                    enrichment?.item ?? null,
                    enrichment?.detail?.relatedItemId ?? report.itemId,
                    enrichment?.detail?.relatedItemTitle ??
                      (report.targetType === 'USER' ? null : report.targetName),
                  );
                  return (
                    <tr key={report.id}>
                      <td className="admin-complaints-code">{reportCode(report.id)}</td>
                      <td>
                        <ComplaintTarget
                          report={report}
                          enrichment={enrichment}
                          item={relatedItem}
                        />
                      </td>
                      <td>{targetLabels[report.targetType]}</td>
                      <td>
                        <span className="admin-complaints-person">
                          {report.reporterName || 'Người dùng đã xóa'}
                        </span>
                        {report.newReporterAccount ? (
                          <span className="admin-complaints-new-account">Tài khoản mới</span>
                        ) : null}
                      </td>
                      <td className="admin-complaints-person">
                        {listReportedUser(report, enrichment)}
                      </td>
                      <td className="admin-complaints-reason" title={reportReasonLabel(report)}>
                        {reportReasonLabel(report)}
                      </td>
                      <td className="admin-complaints-time">{date(report.createdAt)}</td>
                      <td className="admin-complaints-status-cell">
                        <ReportStatusBadge status={report.status} />
                      </td>
                      <td className="admin-complaints-action">
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={submitting}
                          aria-label="Xem chi tiết"
                          onClick={(event) => {
                            opener.current = event.currentTarget;
                            setNotice('');
                            setDetail(null);
                            setDetailError('');
                            setSelectedId(report.id);
                          }}
                        >
                          Xem chi tiết
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
            <span className="text-sm text-text-secondary">
              {list.totalElements} hồ sơ · Trang {page + 1}/{list.totalPages}
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                disabled={page === 0 || submitting}
                onClick={() => setPage((value) => value - 1)}
              >
                Trước
              </Button>
              <Button
                variant="outline"
                disabled={page + 1 >= list.totalPages || submitting}
                onClick={() => setPage((value) => value + 1)}
              >
                Sau
              </Button>
            </div>
          </div>
        </>
      ) : (
        <EmptyState title="Không có hồ sơ" text="Không có hồ sơ phù hợp với bộ lọc hiện tại." />
      )}
      {selectedId !== null && (
        <DialogFrame
          labelledBy="report-detail-title"
          size="xl"
          className="admin-complaints-dialog md:max-w-5xl"
          busy={submitting}
          onClose={closeDetail}
        >
          <header className="admin-complaints-dialog-header mb-5 flex items-start justify-between gap-3 border-b border-border pb-4">
            <div className="min-w-0">
              <p className="mb-1 text-xs font-semibold text-text-secondary">
                Chi tiết hồ sơ {detail ? reportCode(detail.report.id) : ''}
                {detail ? ` · ${targetLabels[detail.report.targetType]}` : ''}
              </p>
              <div className="flex flex-wrap items-center gap-2">
                <h2 id="report-detail-title" className="section-title break-words">
                  {title || 'Hồ sơ khiếu nại'}
                </h2>
                {detail && <ReportStatusBadge status={detail.report.status} />}
              </div>
              {detail && (
                <p className="mt-1 text-sm text-text-secondary">
                  Gửi lúc {date(detail.report.createdAt)}
                </p>
              )}
            </div>
            <button
              ref={closeButton}
              type="button"
              aria-label="Đóng hồ sơ"
              disabled={submitting}
              onClick={closeDetail}
              className="grid size-11 shrink-0 place-items-center rounded-md border border-border text-text-primary hover:bg-primary-faint disabled:opacity-40"
            >
              <Icon name="close" className="size-5" />
            </button>
          </header>
          {notice && (
            <div className="mb-4" role="status">
              <Alert tone={noticeTone}>{notice}</Alert>
            </div>
          )}
          {detailLoading && <SkeletonRows label="Đang tải hồ sơ" />}
          {detailError && (
            <div role="alert" className="mb-4 space-y-2">
              <Alert tone="warning">{detailError}</Alert>
              {!detail && (
                <Button onClick={() => setReload((value) => value + 1)}>Thử tải lại</Button>
              )}
            </div>
          )}
          {detail && (
            <div className="space-y-4 text-sm">
              {detail.responseWindowOpen && (
                <Alert tone="info">
                  {detail.responseDueAt
                    ? `Hạn giải trình: ${formatDeadline(detail.responseDueAt)}. `
                    : ''}
                  Chỉ kết luận được khi bên bị báo cáo đã giải trình hoặc đã hết hạn.
                </Alert>
              )}
              <dl className="grid gap-3 rounded-lg bg-surface-container/50 p-4 sm:grid-cols-2">
                <div className="min-w-0">
                  <dt className="label">Người gửi</dt>
                  <dd className="break-words font-semibold">
                    <Link
                      to={`/admin/users/${detail.reporter.id}`}
                      className="text-primary hover:underline"
                    >
                      {detail.reporter.fullName ?? 'Người dùng'}
                    </Link>
                    {detail.report.newReporterAccount ? (
                      <span className="ml-2 rounded-sm bg-warning-soft px-2 py-0.5 text-2xs font-bold text-warning">
                        Tài khoản mới
                      </span>
                    ) : null}
                  </dd>
                </div>
                <div className="min-w-0">
                  <dt className="label">Người bị báo cáo</dt>
                  <dd className="break-words font-semibold">
                    {detail.reportedUser ? (
                      <Link
                        to={`/admin/users/${detail.reportedUser.id}`}
                        className="text-primary hover:underline"
                      >
                        {reportedUserName(detail)}
                      </Link>
                    ) : (
                      reportedUserName(detail)
                    )}
                  </dd>
                </div>
                {detail.relatedItemId !== null && (
                  <div className="min-w-0">
                    <dt className="label">Bài đăng liên quan</dt>
                    <dd className="break-words">
                      <Link
                        to={`/admin/items/${detail.relatedItemId}`}
                        className="font-semibold text-primary hover:underline"
                      >
                        {detail.relatedItemTitle ?? 'Xem bài đăng liên quan'}
                      </Link>
                    </dd>
                  </div>
                )}
                {detail.report.requestId !== null && (
                  <div className="min-w-0">
                    <dt className="label">Giao dịch liên quan</dt>
                    <dd>
                      <Link
                        to={`/admin/transactions/${detail.report.requestId}`}
                        className="font-semibold text-primary hover:underline"
                      >
                        Xem chi tiết giao dịch
                      </Link>
                    </dd>
                  </div>
                )}
              </dl>
              {modalMainItem ? (
                <section className="admin-complaints-related-items">
                  <div className="admin-complaints-related-heading">
                    <div>
                      <h3 className="font-semibold">Bài đăng liên quan</h3>
                      <p className="text-xs text-text-secondary">
                        {relatedRequestDetail?.type === 'SWAP'
                          ? 'Hai món đồ trong giao dịch trao đổi'
                          : 'Món đồ được cho tặng hoặc bị báo cáo'}
                      </p>
                    </div>
                    {detail.report.requestId !== null ? (
                      <Link
                        to={`/admin/transactions/${detail.report.requestId}`}
                        className="admin-complaints-related-link"
                      >
                        Xem giao dịch
                      </Link>
                    ) : null}
                  </div>
                  <div
                    className={`admin-complaints-related-grid ${modalOfferedItem ? 'sm:grid-cols-2' : ''}`}
                  >
                    <div className="admin-complaints-related-card">
                      <p className="label">
                        {relatedRequestDetail?.type === 'SWAP'
                          ? 'Món đồ chủ bài'
                          : 'Món đồ liên quan'}
                      </p>
                      <ComplaintItem item={modalMainItem} />
                    </div>
                    {modalOfferedItem ? (
                      <div className="admin-complaints-related-card">
                        <p className="label">Món đem đổi</p>
                        <ComplaintItem item={modalOfferedItem} />
                      </div>
                    ) : null}
                  </div>
                </section>
              ) : null}
              <div className="grid gap-4 md:grid-cols-2">
                <section className="min-w-0 space-y-3 rounded-lg border border-border p-4">
                  <h3 className="font-semibold">Nội dung người gửi</h3>
                  <p className="font-semibold text-primary">{reportReasonLabel(detail.report)}</p>
                  <p className="whitespace-pre-wrap break-words leading-6">
                    {detail.report.description}
                  </p>
                  <EvidenceGallery
                    reportId={detail.report.id}
                    title="Ảnh người gửi"
                    evidence={detail.evidence}
                  />
                </section>
                <section className="min-w-0 space-y-3 rounded-lg border border-border p-4">
                  <h3 className="font-semibold">Giải trình của bên bị báo cáo</h3>
                  {detail.response ? (
                    <>
                      <p className="whitespace-pre-wrap break-words leading-6">{detail.response}</p>
                      {detail.respondedAt && (
                        <p className="text-xs text-text-secondary">
                          Gửi lúc {date(detail.respondedAt)}
                        </p>
                      )}
                      <EvidenceGallery
                        reportId={detail.report.id}
                        title="Ảnh giải trình"
                        evidence={detail.responseEvidence}
                      />
                    </>
                  ) : (
                    <p className="text-text-secondary">Chưa có giải trình.</p>
                  )}
                </section>
              </div>
              {detailIsConcluded ? (
                <section className="rounded-lg border border-border bg-surface-container/50 p-4">
                  <h3 className="mb-3 font-semibold">Kết quả xử lý</h3>
                  <dl className="space-y-2">
                    <DetailRow label="Kết luận">
                      {detail.report.status === 'RESOLVED' ? 'Có cơ sở' : 'Không có cơ sở'}
                    </DetailRow>
                    <DetailRow
                      label={
                        detail.report.status === 'RESOLVED' ? 'Người bị xử lý' : 'Người bị báo cáo'
                      }
                    >
                      {reportedUserName(detail)}
                    </DetailRow>
                    <DetailRow label="Hệ quả">{consequences(detail)}</DetailRow>
                    {detail.resolution?.trim() && (
                      <DetailRow label="Ghi chú">
                        <span className="whitespace-pre-wrap">{detail.resolution}</span>
                      </DetailRow>
                    )}
                  </dl>
                  <p className="mt-3 border-t border-border pt-3 text-text-secondary">
                    {detail.resolvedByName ?? 'Quản trị viên'} kết luận lúc{' '}
                    {date(detail.resolvedAt)}
                  </p>
                </section>
              ) : (
                <section className="space-y-4 rounded-lg border border-border bg-surface-container/50 p-4">
                  <h3 className="font-semibold">Bước tiếp theo</h3>
                  {detail.responseWindowOpen ? (
                    <p className="text-text-secondary">
                      Cập nhật hồ sơ để kiểm tra xem bên bị báo cáo đã gửi giải trình chưa.
                    </p>
                  ) : detail.report.status === 'AWAITING_RESPONSE' ? (
                    <Alert tone="warning">
                      {detail.responseDueAt && detail.responseWindowOpen === false
                        ? `Đã hết hạn giải trình lúc ${formatDeadline(detail.responseDueAt)}.`
                        : 'Hồ sơ đang chờ giải trình. Kiểm tra lại trạng thái trước khi kết luận.'}
                    </Alert>
                  ) : (
                    <p className="text-text-secondary">
                      {canRequestResponse
                        ? 'Yêu cầu bên bị báo cáo giải trình nếu cần thêm thông tin.'
                        : 'Đối chiếu thông tin từ hai bên trước khi đưa ra kết luận.'}
                    </p>
                  )}
                  {decisionStep === 'review' && (
                    <div className="flex flex-wrap gap-2">
                      {canRequestResponse && (
                        <Button disabled={submitting} onClick={() => void requestResponse()}>
                          Yêu cầu giải trình
                        </Button>
                      )}
                      <Button
                        variant={canRequestResponse ? 'outline' : 'primary'}
                        disabled={submitting || detail.responseWindowOpen}
                        onClick={() => setDecisionStep('choose')}
                      >
                        Kết luận
                      </Button>
                      {detail.responseWindowOpen && (
                        <Button variant="outline" onClick={() => setReload((value) => value + 1)}>
                          Cập nhật hồ sơ
                        </Button>
                      )}
                    </div>
                  )}
                  {decisionStep === 'choose' && (
                    <>
                      {detail.openTransactionPartnerName ? (
                        <Alert tone="warning">
                          {`Bài đang có giao dịch với ${detail.openTransactionPartnerName}. `}
                          {detail.seriousReason
                            ? 'Chấp thuận thì giao dịch sẽ bị huỷ.'
                            : 'Chấp thuận thì giao dịch vẫn tiếp tục.'}
                        </Alert>
                      ) : null}
                      <fieldset className="grid gap-2 sm:grid-cols-2">
                        <legend className="label">Chọn kết luận</legend>
                        {(Object.keys(decisionLabels) as ReportDecision[]).map((value) => (
                          <label
                            key={value}
                            className={`flex cursor-pointer items-center gap-2 rounded-md border p-3 font-semibold ${decision === value ? 'border-primary bg-primary-faint' : 'border-border bg-surface'}`}
                          >
                            <input
                              type="radio"
                              name="report-decision"
                              value={value}
                              checked={decision === value}
                              onChange={() => setDecision(value)}
                              className="accent-primary"
                            />
                            {decisionLabels[value]}
                          </label>
                        ))}
                      </fieldset>
                      {decision === 'UPHOLD' && requestDetail ? (
                        <DisputeItemOutcomes
                          rows={disputeItemRows(requestDetail)}
                          choices={itemChoices}
                          onChange={(itemId, choice) =>
                            setItemChoices((current) => ({ ...current, [itemId]: choice }))
                          }
                        />
                      ) : null}
                      {decision === 'UPHOLD' && requestDetail && isCancelWithoutAccusation ? (
                        <Select
                          label="Bên bị trừ 1 sao"
                          hint="Đề nghị huỷ này không tố ai. Mặc định huỷ giao dịch mà không phạt bên nào."
                          value={penalizedUserId}
                          onChange={(event) => setPenalizedUserId(event.target.value)}
                        >
                          <option value="">Không trừ sao ai</option>
                          {requestDetail.donor ? (
                            <option value={requestDetail.donor.userId ?? undefined}>
                              {requestDetail.donor.name ?? 'Chủ bài'} (chủ bài)
                            </option>
                          ) : null}
                          <option value={requestDetail.receiver.userId ?? undefined}>
                            {requestDetail.receiver.name ?? 'Người nhận'} (người nhận)
                          </option>
                        </Select>
                      ) : null}
                      {decision === 'DISMISS' && !isRefusalReport(detail.report) ? (
                        <label className="flex items-center gap-2 text-sm">
                          <input
                            type="checkbox"
                            checked={deliberateFalse}
                            onChange={(event) => setDeliberateFalse(event.target.checked)}
                            className="accent-primary"
                          />
                          Báo cáo sai có chủ ý (trừ 1 sao người báo)
                        </label>
                      ) : null}
                      <TextArea
                        label="Ghi chú kết luận (không bắt buộc)"
                        hint="Tối đa 500 ký tự."
                        value={note}
                        maxLength={500}
                        onChange={(event) => setNote(event.target.value)}
                      />
                      <div className="flex flex-wrap gap-2">
                        <Button variant="outline" onClick={() => setDecisionStep('review')}>
                          Quay lại
                        </Button>
                        <Button disabled={!decision} onClick={() => setDecisionStep('confirm')}>
                          Kiểm tra kết luận
                        </Button>
                      </div>
                    </>
                  )}
                  {decisionStep === 'confirm' && decision && (
                    <div className="space-y-3 rounded-md border border-border bg-surface p-4">
                      <p className="font-semibold">{decisionLabels[decision]}</p>
                      <p>
                        {decision === 'UPHOLD'
                          ? 'Hệ thống áp dụng chế tài theo loại hồ sơ và quy định hiện hành.'
                          : 'Hệ thống đóng hồ sơ và giữ nguyên sao uy tín.'}
                      </p>
                      {note.trim() && (
                        <p className="whitespace-pre-wrap break-words text-text-secondary">
                          {note.trim()}
                        </p>
                      )}
                      <div className="flex flex-wrap gap-2">
                        <Button
                          variant="outline"
                          disabled={submitting}
                          onClick={() => setDecisionStep('choose')}
                        >
                          Quay lại
                        </Button>
                        <Button disabled={submitting} onClick={() => void submit()}>
                          {submitting ? 'Đang gửi…' : 'Xác nhận gửi'}
                        </Button>
                      </div>
                    </div>
                  )}
                </section>
              )}
            </div>
          )}
        </DialogFrame>
      )}
    </section>
  );
}
