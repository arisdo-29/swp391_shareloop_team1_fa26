import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Pagination } from '../components/Pagination';
import { Alert, Button, Field, PageHeader, Select, SkeletonRows, TextArea } from '../components/ui';
import { userFacingErrorMessage, type ApiError } from '../services/http';
import {
  systemReportApi,
  type SystemReport,
  type SystemReportStatus,
} from '../services/systemReportApi';
import { formatDateTime } from '../utils/boostMessages';

const userPath = '/me/system-reports';
const adminPath = '/admin/system-reports';
const pageSize = 20;
const linkClass = 'font-semibold text-primary hover:underline';

function reportError(error: unknown, fallback: string) {
  return userFacingErrorMessage((error as ApiError | null)?.message, fallback);
}

function Status({ status }: { status: SystemReportStatus }) {
  const resolved = status === 'RESOLVED';
  return (
    <span
      className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ${resolved ? 'bg-primary-faint text-primary' : 'bg-warning-soft text-warning'}`}
    >
      {resolved ? 'Đã xử lý' : 'Chờ xử lý'}
    </span>
  );
}

function NewReportForm() {
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving || !title.trim() || !description.trim()) return;
    setSaving(true);
    setError('');
    try {
      const report = await systemReportApi.submit(title.trim(), description.trim());
      navigate(`${userPath}/${report.id}`);
    } catch (error) {
      setError(reportError(error, 'Không gửi được báo cáo. Vui lòng thử lại.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} className="card mb-8 space-y-4 p-5 sm:p-6">
      <h2 className="text-lg font-bold">Gửi báo cáo lỗi</h2>
      <Field
        label="Tiêu đề"
        required
        maxLength={150}
        value={title}
        onChange={(event) => setTitle(event.target.value)}
      />
      <TextArea
        label="Mô tả lỗi"
        hint="Ghi rõ lỗi xảy ra ở trang nào, bạn đã làm gì và điều gì xảy ra sau đó."
        required
        maxLength={2000}
        value={description}
        onChange={(event) => setDescription(event.target.value)}
      />
      {error && <Alert tone="error">{error}</Alert>}
      <Button type="submit" disabled={saving || !title.trim() || !description.trim()}>
        {saving ? 'Đang gửi…' : 'Gửi báo cáo'}
      </Button>
    </form>
  );
}

function ReportList({ inAdmin }: { inAdmin: boolean }) {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<SystemReportStatus | ''>('');
  const [result, setResult] = useState<Awaited<ReturnType<typeof systemReportApi.listMine>>>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    const request = inAdmin
      ? systemReportApi.listAdmin(page, pageSize, status)
      : systemReportApi.listMine(page, pageSize);
    request
      .then((response) => {
        if (active) setResult(response);
      })
      .catch((error: unknown) => {
        if (active) setError(reportError(error, 'Không tải được danh sách báo cáo.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [attempt, inAdmin, page, status]);

  return (
    <section aria-label={inAdmin ? 'Danh sách báo cáo hệ thống' : 'Báo cáo đã gửi'}>
      {inAdmin ? (
        <div className="mb-4 max-w-xs">
          <Select
            label="Trạng thái"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as SystemReportStatus | '');
              setPage(0);
            }}
          >
            <option value="">Tất cả trạng thái</option>
            <option value="OPEN">Chờ xử lý</option>
            <option value="RESOLVED">Đã xử lý</option>
          </Select>
        </div>
      ) : (
        <h2 className="mb-4 text-lg font-bold">Báo cáo đã gửi</h2>
      )}
      {loading ? (
        <div className="card p-5">
          <SkeletonRows rows={3} label="Đang tải báo cáo" />
        </div>
      ) : error ? (
        <div className="space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={() => setAttempt((attempt) => attempt + 1)}>
            Thử lại
          </Button>
        </div>
      ) : result?.items.length ? (
        <ul className="card divide-y divide-border">
          {result.items.map((report) => (
            <li key={report.id}>
              <Link
                to={`${inAdmin ? adminPath : userPath}/${report.id}`}
                className="flex flex-wrap items-center justify-between gap-3 px-5 py-4 transition hover:bg-surface-low"
              >
                <div className="min-w-0">
                  <p className="break-words font-semibold text-text-primary">{report.title}</p>
                  <p className="mt-1 text-sm text-text-secondary">
                    {inAdmin && `${report.reporterName ?? 'Người gửi không còn khả dụng'} · `}
                    {formatDateTime(report.createdAt)}
                  </p>
                </div>
                <Status status={report.status} />
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <p className="card p-5 text-text-secondary">Chưa có báo cáo nào.</p>
      )}
      {!error && result && (
        <Pagination
          page={page}
          size={pageSize}
          totalElements={result.totalElements}
          disabled={loading}
          onPageChange={setPage}
        />
      )}
    </section>
  );
}

function ResolveForm({
  report,
  onResolved,
  reload,
}: {
  report: SystemReport;
  onResolved: (report: SystemReport) => void;
  reload: () => void;
}) {
  const [resolution, setResolution] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [alreadyResolved, setAlreadyResolved] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving || !resolution.trim()) return;
    setSaving(true);
    setError('');
    try {
      onResolved(await systemReportApi.resolve(report.id, resolution.trim()));
    } catch (error) {
      setAlreadyResolved((error as ApiError | null)?.code === 'SYSTEM_REPORT_ALREADY_RESOLVED');
      setError(reportError(error, 'Không lưu được kết quả xử lý. Vui lòng thử lại.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} className="card space-y-4 p-5 sm:p-6">
      <TextArea
        label="Kết quả xử lý"
        hint="Người gửi sẽ đọc được nội dung này khi mở báo cáo."
        required
        maxLength={2000}
        value={resolution}
        onChange={(event) => setResolution(event.target.value)}
      />
      {error && <Alert tone="error">{error}</Alert>}
      {alreadyResolved ? (
        <Button type="button" variant="outline" onClick={reload}>
          Tải lại báo cáo
        </Button>
      ) : (
        <Button type="submit" disabled={saving || !resolution.trim()}>
          {saving ? 'Đang lưu…' : 'Hoàn tất xử lý'}
        </Button>
      )}
    </form>
  );
}

function ReportDetail({ id, inAdmin }: { id: string; inAdmin: boolean }) {
  const [report, setReport] = useState<SystemReport>();
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    const request = inAdmin ? systemReportApi.detailAdmin(id) : systemReportApi.detailMine(id);
    request
      .then((result) => {
        if (active) setReport(result);
      })
      .catch((error: unknown) => {
        if (active) setError(reportError(error, 'Không tải được báo cáo.'));
      });
    return () => {
      active = false;
    };
  }, [attempt, id, inAdmin]);

  function reload() {
    setReport(undefined);
    setError('');
    setAttempt((attempt) => attempt + 1);
  }

  return (
    <div className="space-y-5">
      <Link to={inAdmin ? adminPath : userPath} className={linkClass}>
        Quay lại danh sách báo cáo
      </Link>
      {error ? (
        <div className="space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={reload}>
            Thử lại
          </Button>
        </div>
      ) : report ? (
        <>
          <article className="card space-y-5 p-5 sm:p-6">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <h1 className="min-w-0 break-words text-xl font-bold sm:text-2xl">{report.title}</h1>
              <Status status={report.status} />
            </div>
            <p className="text-sm text-text-secondary">
              {inAdmin && `${report.reporterName ?? 'Người gửi không còn khả dụng'} · `}
              Gửi lúc {formatDateTime(report.createdAt)}
            </p>
            <div>
              <h2 className="mb-2 font-semibold">Mô tả lỗi</h2>
              <p className="whitespace-pre-wrap break-words text-text-secondary">
                {report.description}
              </p>
            </div>
            {report.status === 'RESOLVED' && (
              <div className="border-t border-border pt-4">
                <h2 className="mb-2 font-semibold">Kết quả xử lý</h2>
                <p className="whitespace-pre-wrap break-words text-text-secondary">
                  {report.resolution}
                </p>
                {report.resolvedAt && (
                  <p className="mt-2 text-sm text-text-muted">
                    Cập nhật lúc {formatDateTime(report.resolvedAt)}
                  </p>
                )}
              </div>
            )}
          </article>
          {inAdmin && report.status === 'OPEN' && (
            <ResolveForm report={report} onResolved={setReport} reload={reload} />
          )}
        </>
      ) : (
        <div className="card p-5">
          <SkeletonRows rows={3} label="Đang tải báo cáo" />
        </div>
      )}
    </div>
  );
}

export function SystemReports({ inAdmin = false }: { inAdmin?: boolean }) {
  const { reportId } = useParams();
  const content = reportId ? (
    <ReportDetail key={reportId} id={reportId} inAdmin={inAdmin} />
  ) : (
    <>
      <PageHeader
        title="Báo cáo hệ thống"
        description={
          inAdmin
            ? 'Xem lỗi người dùng đã gửi và phản hồi kết quả xử lý.'
            : 'Gửi lỗi bạn gặp khi sử dụng ShareLoop và theo dõi kết quả xử lý.'
        }
      />
      {!inAdmin && <NewReportForm />}
      <ReportList inAdmin={inAdmin} />
    </>
  );
  return inAdmin ? content : <div className="page-shell max-w-4xl py-6 sm:py-10">{content}</div>;
}
