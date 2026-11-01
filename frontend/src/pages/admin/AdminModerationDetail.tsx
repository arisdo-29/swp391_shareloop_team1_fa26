import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, EmptyState, PageHeader } from '../../components/ui';
import { adminModerationApi, type ReviewItemDetail } from '../../services/adminModerationApi';
import {
  adminChecklistApi,
  type AdminChecklistEntry,
} from '../../services/adminChecklistApi';
import type { ApiError } from '../../services/http';
import { adminErrorMessage } from './adminErrors';
import {
  conditionLabels,
  displayModerationNote,
  displayRuleFlag,
  offerTypeLabels,
  operatingStatusLabels,
} from './moderationDisplay';
import {
  ModerationChecklistContent,
  ModerationImageGallery,
  RejectReasonsContent,
} from './ModerationParts';

const NO_DATA = 'Chưa có dữ liệu';

function activeReviewChecklist(entries: AdminChecklistEntry[]) {
  return [...entries]
    .filter((entry) => entry.active)
    .sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id)
    .map((entry) => ({
      id: entry.id,
      key: entry.key,
      statement: entry.question,
      description: entry.description,
    }));
}

function sameChecklist(
  left: ReviewItemDetail['checklist'],
  right: ReviewItemDetail['checklist'],
) {
  return left.length === right.length && left.every((entry, index) => (
    entry.id === right[index]?.id &&
    entry.key === right[index]?.key &&
    entry.statement === right[index]?.statement &&
    entry.description === right[index]?.description
  ));
}

function Fact({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs font-semibold text-text-muted">{label}</dt>
      <dd className="mt-0.5 break-words text-sm text-text-primary">{value}</dd>
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-lg bg-white p-5 ring-1 ring-border/80">
      <h2 className="mb-4 text-base font-bold text-text-primary">{title}</h2>
      {children}
    </section>
  );
}

/** Trang duyệt một bài: nội dung bài ở bên trái, checklist và nút duyệt hoặc từ chối cố định ở bên phải. */
export function AdminModerationDetail() {
  const { itemId: pathItemId } = useParams();
  const itemId = Number(pathItemId);
  const validItemId = Number.isSafeInteger(itemId) && itemId > 0;
  const navigate = useNavigate();
  const [detail, setDetail] = useState<ReviewItemDetail | null>(null);
  const [rejectReasons, setRejectReasons] = useState<string[]>([]);
  const [answers, setAnswers] = useState<boolean[]>([]);
  const [mode, setMode] = useState<'approve' | 'reject'>('approve');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const inFlight = useRef(false);

  useEffect(() => {
    if (!validItemId) return;
    let active = true;
    setLoading(true);
    setLoadError('');
    Promise.all([
      adminModerationApi.detail(itemId),
      adminModerationApi.rejectReasons(),
      adminChecklistApi.list(),
    ])
      .then(([response, reasons, checklistEntries]) => {
        if (!active) return;
        const currentChecklist = activeReviewChecklist(checklistEntries);
        setDetail({ ...response, checklist: currentChecklist });
        setRejectReasons(reasons);
        setAnswers(currentChecklist.map(() => false));
      })
      .catch((caught) => {
        if (!active) return;
        setDetail(null);
        const status = (caught as Partial<ApiError>).status;
        setLoadError(
          status === 404
            ? 'Không tìm thấy bài đăng này. Bài có thể đã bị xóa.'
            : adminErrorMessage(caught, 'Không tải được chi tiết bài đăng.'),
        );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [itemId, validItemId, reloadKey]);

  const backToList = (message?: string) =>
    navigate('/admin/moderation', { state: message ? { notice: message } : null });

  const approve = async () => {
    if (!detail || inFlight.current || answers.some((answer) => !answer)) return;
    inFlight.current = true;
    setBusy(true);
    setActionError('');
    try {
      const currentChecklist = activeReviewChecklist(await adminChecklistApi.list());
      if (!sameChecklist(detail.checklist, currentChecklist)) {
        setDetail({ ...detail, checklist: currentChecklist });
        setAnswers(currentChecklist.map(() => false));
        setNotice('Checklist đã thay đổi. Vui lòng kiểm tra và tích lại các điều kiện.');
        return;
      }
      await adminModerationApi.approve(detail.id, {
        answers: detail.checklist.map((entry, index) => ({ id: entry.id, passed: answers[index] })),
      });
      backToList(`Đã duyệt bài "${detail.title}".`);
    } catch (caught) {
      const apiError = caught as ApiError;
      if (apiError.code === 'REVIEW_CHECKLIST_OUTDATED') {
        // Checklist đã đổi từ lúc mở trang: tải lại để admin tích theo bộ câu hỏi mới.
        setNotice('Checklist đã thay đổi. Vui lòng kiểm tra và tích lại các điều kiện.');
        setReloadKey((key) => key + 1);
      } else {
        setActionError(adminErrorMessage(caught, 'Không duyệt được bài đăng.'));
      }
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  const reject = async (reason: string) => {
    if (!detail || inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setActionError('');
    try {
      await adminModerationApi.reject(detail.id, { reason });
      backToList(`Đã từ chối bài "${detail.title}".`);
    } catch (caught) {
      setActionError(adminErrorMessage(caught, 'Không từ chối được bài đăng.'));
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  if (!validItemId) return <Alert tone="error">Đường dẫn bài đăng không hợp lệ.</Alert>;

  const backLink = (
    <Link to="/admin/moderation">
      <Button variant="outline">
        Về danh sách
      </Button>
    </Link>
  );

  if (loading) return <EmptyState title="Đang tải bài đăng" text="Vui lòng chờ trong giây lát." />;
  if (loadError || !detail) {
    return (
      <>
        <PageHeader title="Duyệt bài" action={backLink} />
        <div className="space-y-3" role="alert">
          <Alert tone="error">{loadError || 'Không tải được chi tiết bài đăng.'}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            Thử tải lại
          </Button>
        </div>
      </>
    );
  }

  const reviewable = detail.status === 'PENDING_REVIEW';
  return (
    <>
      <PageHeader
        title={detail.title}
        description={`Đăng lúc ${new Date(detail.createdAt).toLocaleString('vi-VN')}`}
        action={backLink}
      />
      {notice ? (
        <div className="mb-4" role="status">
          <Alert tone="warning">{notice}</Alert>
        </div>
      ) : null}
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px] lg:items-start">
        <div className="min-w-0 space-y-5">
          <Section title="Hình ảnh">
            <ModerationImageGallery images={detail.images} defectImages={detail.defectImages} />
          </Section>
          <ItemFacts detail={detail} />
          <DonorAndModeration detail={detail} />
        </div>

        <aside className="rounded-lg bg-white p-5 ring-1 ring-border/80 lg:sticky lg:top-24 lg:max-h-[calc(100dvh-120px)] lg:overflow-y-auto">
          {reviewable ? (
            <ReviewPanel
              detail={detail}
              mode={mode}
              answers={answers}
              busy={busy}
              error={actionError}
              rejectReasons={rejectReasons}
              onToggle={(index, checked) =>
                setAnswers((current) => current.map((value, i) => (i === index ? checked : value)))
              }
              onModeChange={(next) => {
                setMode(next);
                setActionError('');
              }}
              onApprove={() => void approve()}
              onReject={(reason) => void reject(reason)}
              onBack={() => backToList()}
            />
          ) : (
            <Alert tone="warning">
              Bài này không còn ở trạng thái chờ duyệt nên không thể duyệt hoặc từ chối thêm.
            </Alert>
          )}
        </aside>
      </div>
    </>
  );
}

function ItemFacts({ detail }: { detail: ReviewItemDetail }) {
  return (
    <Section title="Thông tin bài đăng">
      <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-2">
        <Fact label="Hình thức" value={offerTypeLabels[detail.offerType] ?? NO_DATA} />
        {detail.offerType === 'SWAP' ? (
          <Fact label="Món muốn đổi" value={detail.desiredItem?.trim() || NO_DATA} />
        ) : null}
        <Fact label="Danh mục" value={detail.categoryName ?? NO_DATA} />
        <Fact label="Khu vực" value={detail.areaName ?? NO_DATA} />
        <Fact label="Tình trạng" value={conditionLabels[detail.condition] ?? NO_DATA} />
        <Fact label="Thương hiệu" value={detail.brand?.trim() || 'Không có'} />
        {detail.operatingStatus ? (
          <Fact label="Trạng thái hoạt động" value={operatingStatusLabels[detail.operatingStatus]} />
        ) : null}
        {detail.defectNote?.trim() ? <Fact label="Ghi chú lỗi" value={detail.defectNote} /> : null}
        {detail.attributes
          .filter((attribute) => attribute.name && attribute.name !== 'Trạng thái hoạt động')
          .map((attribute) => (
            <Fact key={attribute.attributeId} label={attribute.name!} value={attribute.value} />
          ))}
      </dl>
      <div className="mt-5">
        <p className="text-xs font-semibold text-text-muted">Mô tả đầy đủ</p>
        <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-text-primary">
          {detail.description?.trim() || NO_DATA}
        </p>
      </div>
    </Section>
  );
}

function DonorAndModeration({ detail }: { detail: ReviewItemDetail }) {
  const flag = displayRuleFlag(detail);
  const note = displayModerationNote(detail);
  return (
    <>
      <Section title="Người đăng">
        <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-2">
          <Fact label="Họ tên" value={detail.donorName || 'Tài khoản đã xóa'} />
          <Fact
            label="Uy tín"
            value={detail.donorTrustStars == null ? NO_DATA : `${detail.donorTrustStars}/5 sao`}
          />
        </dl>
      </Section>
      <Section title="Kết quả kiểm duyệt tự động">
        <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-2">
          <Fact
            label="Phát hiện vi phạm"
            value={flag ? 'Có dấu hiệu vi phạm' : 'Không phát hiện vi phạm'}
          />
        </dl>
        {note && note !== flag ? (
          <p className="mt-4 rounded-md border border-warning/20 bg-warning-soft p-3 text-sm leading-6 text-warning">
            {note}
          </p>
        ) : null}
        {detail.matchedTerms.length ? (
          <div className="mt-4">
            <p className="text-xs font-semibold text-text-muted">Từ khóa khớp</p>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {detail.matchedTerms.map((term) => (
                <span key={term} className="rounded-sm bg-warning-soft px-2 py-1 text-xs font-semibold text-warning">
                  {term}
                </span>
              ))}
            </div>
          </div>
        ) : null}
      </Section>
    </>
  );
}

function ReviewPanel({
  detail,
  mode,
  answers,
  busy,
  error,
  rejectReasons,
  onToggle,
  onModeChange,
  onApprove,
  onReject,
  onBack,
}: {
  detail: ReviewItemDetail;
  mode: 'approve' | 'reject';
  answers: boolean[];
  busy: boolean;
  error: string;
  rejectReasons: string[];
  onToggle: (index: number, checked: boolean) => void;
  onModeChange: (mode: 'approve' | 'reject') => void;
  onApprove: () => void;
  onReject: (reason: string) => void;
  onBack: () => void;
}) {
  if (mode === 'reject') {
    return (
      <RejectReasonsContent
        reasons={rejectReasons}
        disabled={busy}
        error={error}
        onCancel={() => onModeChange('approve')}
        onConfirm={onReject}
      />
    );
  }
  return (
    <div>
      <ModerationChecklistContent
        cancelLabel="Quay lại"
        checklist={detail.checklist}
        answers={answers}
        disabled={busy}
        error={error}
        onToggle={onToggle}
        onCancel={onBack}
        onConfirm={onApprove}
      />
      <div className="mt-3 border-t border-border/70 pt-3">
        <Button variant="danger" className="w-full" disabled={busy} onClick={() => onModeChange('reject')}>
          Từ chối bài đăng
        </Button>
      </div>
    </div>
  );
}
