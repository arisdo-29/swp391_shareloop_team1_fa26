import { AdminConfigs } from './AdminConfigs';
import { AdminUserPicker } from './AdminUserPicker';
import type { AdminUserSummary } from '../../services/adminUserApi';
import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react';
import {
  Alert,
  Button,
  EmptyState,
  Field,
  Icon,
  PageHeader,
  Select,
  TextArea,
  SkeletonRows,
} from '../../components/ui';
import { DialogFrame } from '../../components/Dialog';
import { Pagination } from '../../components/Pagination';
import { adminLexiconApi, type AdminLexiconEntry, type AdminLexiconRequest, type LexiconMatch } from '../../services/adminLexiconApi';
import {
  adminCategoryApi,
  type AdminCategoryAttributeRequest,
  type AdminCategoryAttributeResponse,
  type AdminCategoryRequest,
  type AdminCategoryResponse,
} from '../../services/adminCategoryApi';
import { adminAreaApi, type AdminAreaRequest, type AdminAreaResponse } from '../../services/adminAreaApi';
import type { AttributeDataType } from '../../services/catalogApi';
import {
  ADMIN_CHECKLIST_QUESTION_MAX_LENGTH,
  adminChecklistApi,
  type AdminChecklistEntry,
} from '../../services/adminChecklistApi';
import {
  adminAuditApi,
  type ActivityLogParams,
  type ActivityLogResponse,
} from '../../services/adminAuditApi';
import type { ApiError } from '../../services/http';

function Panel({
  title,
  description,
  action,
  children,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section>
      <PageHeader title={title} description={description} action={action} />
      {children}
    </section>
  );
}

export function AdminSettings() {
  return <AdminConfigs />;
}

const AUDIT_LOG_PAGE_SIZE = 20;

type AuditPaginationItem = number | 'start-ellipsis' | 'end-ellipsis';

function auditPaginationItems(currentPage: number, totalPages: number): AuditPaginationItem[] {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, index) => index);

  const lastPage = totalPages - 1;
  let rangeStart: number;
  let rangeEnd: number;

  if (currentPage <= 2) {
    rangeStart = 1;
    rangeEnd = 3;
  } else if (currentPage >= lastPage - 2) {
    rangeStart = lastPage - 3;
    rangeEnd = lastPage - 1;
  } else {
    rangeStart = currentPage - 1;
    rangeEnd = currentPage + 1;
  }

  const items: AuditPaginationItem[] = [0];
  if (rangeStart > 1) items.push('start-ellipsis');
  for (let pageIndex = rangeStart; pageIndex <= rangeEnd; pageIndex += 1) items.push(pageIndex);
  if (rangeEnd < lastPage - 1) items.push('end-ellipsis');
  items.push(lastPage);
  return items;
}

export function AdminAuditLogs() {
  const [actionTypes, setActionTypes] = useState<{ type: string; label: string }[]>([]);
  const [logs, setLogs] = useState<ActivityLogResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [actorUser, setActorUser] = useState<AdminUserSummary | null>(null);
  const [actionType, setActionType] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [filters, setFilters] = useState<Omit<ActivityLogParams, 'page' | 'size'>>({});

  useEffect(() => {
    let active = true;
    adminAuditApi.types().then((types) => { if (active) setActionTypes(types); }).catch(() => { /* Backend cũ vẫn dùng danh sách nhãn sẵn có. */ });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');

    adminAuditApi
      .list({ ...filters, page, size: AUDIT_LOG_PAGE_SIZE })
      .then((response) => {
        if (!active) return;

        const responseTotalPages = Math.max(response.totalPages, 0);
        const lastPage = Math.max(responseTotalPages - 1, 0);
        const responsePage = Math.min(Math.max(response.number, 0), lastPage);

        setTotalElements(response.totalElements);
        setTotalPages(responseTotalPages);

        if (page !== responsePage) {
          setPage(responsePage);
          return;
        }

        setLogs(response.content);
      })
      .catch((apiError: ApiError) => {
        if (!active) return;
        setLogs([]);
        setTotalElements(0);
        setTotalPages(0);
        setError(activityLogErrorMessage(apiError));
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [filters, page, reloadKey]);

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (from && to && from > to) {
      setError('Khoảng ngày không hợp lệ');
      return;
    }
    setError('');
    setPage(0);
    setFilters({
      actorId: actorUser?.id,
      type: actionType || undefined,
      from: from || undefined,
      to: to || undefined,
    });
  };

  return (
    <Panel title="Nhật ký hành động">
      <form onSubmit={applyFilters} className="mb-5 rounded-lg bg-white p-4 ring-1 ring-border/80">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          <div>
            <span className="label">Người thực hiện</span>
            <AdminUserPicker searchLabel="Tìm người thực hiện" selected={actorUser} disabled={loading} onSelect={setActorUser} />
          </div>
          <Select label="Hành động" value={actionType} onChange={(event) => setActionType(event.target.value)}>
            <option value="">Tất cả hành động</option>
            {(actionTypes.length ? actionTypes.map((entry) => [entry.type, entry.label]) : Object.entries(activityLogTypeLabels)).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </Select>
          <Field label="Từ ngày" type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
          <Field label="Đến ngày" type="date" value={to} onChange={(event) => setTo(event.target.value)} />
          <div className="flex items-end">
            <Button type="submit" className="w-full" disabled={loading}>Lọc nhật ký</Button>
          </div>
        </div>
      </form>

      {loading ? (
        <SkeletonRows label="Đang tải nhật ký" />
      ) : error ? (
        <div className="rounded-md bg-error-soft p-4 text-sm leading-6 text-error">
          <p>{error}</p>
          <Button className="mt-3" variant="outline" onClick={() => setReloadKey((value) => value + 1)}>
            Thử lại
          </Button>
        </div>
      ) : (
        <>
          {logs.length === 0 ? (
            <EmptyState title="Chưa có nhật ký" text="Không có hành động nào phù hợp với bộ lọc hiện tại." />
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Thời gian</th>
                    <th>Người thực hiện</th>
                    <th>Hành động</th>
                    <th>Đối tượng</th>
                    <th>Chi tiết</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => (
                    <tr key={log.id}>
                      <td className="whitespace-nowrap">{formatActivityLogTime(log.createdAt)}</td>
                      <td>{log.actorName ?? (log.actorId ? 'Quản trị viên' : 'Hệ thống')}</td>
                      <td>{log.typeLabel ?? activityLogTypeLabel(log.type)}</td>
                      <td>{activityLogTarget(log)}</td>
                      <td><ActivityLogDetail log={log} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="mt-4 flex flex-col gap-3 rounded-lg bg-white p-3 text-sm text-text-secondary ring-1 ring-border/80 sm:flex-row sm:items-center sm:justify-between">
            <p>
              {totalElements.toLocaleString('vi-VN')} bản ghi · Trang {page + 1}/{Math.max(totalPages, 1)}
            </p>
            <nav
              aria-label="Phân trang nhật ký"
              className="flex w-full flex-wrap items-center gap-2 sm:w-auto sm:justify-end"
            >
              <Button
                className="min-w-24 flex-1 sm:flex-none"
                variant="outline"
                disabled={page === 0}
                onClick={() => setPage((value) => Math.max(0, value - 1))}
              >
                Trước
              </Button>
              {auditPaginationItems(page, Math.max(totalPages, 1)).map((item) =>
                typeof item === 'number' ? (
                  <Button
                    key={item}
                    aria-label={`Trang ${item + 1}`}
                    aria-current={item === page ? 'page' : undefined}
                    className="min-w-11 px-3"
                    size="sm"
                    variant={item === page ? 'primary' : 'outline'}
                    onClick={() => setPage(item)}
                  >
                    {item + 1}
                  </Button>
                ) : (
                  <span key={item} aria-hidden="true" className="px-1 text-text-muted">
                    …
                  </span>
                ),
              )}
              <Button
                className="min-w-24 flex-1 sm:flex-none"
                variant="outline"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((value) => Math.min(Math.max(totalPages - 1, 0), value + 1))}
              >
                Sau
              </Button>
            </nav>
          </div>
        </>
      )}
    </Panel>
  );
}

const activityLogTypeLabels: Record<string, string> = {
  APPROVE_ITEM: 'Duyệt bài',
  REJECT_ITEM: 'Từ chối bài',
  REVIEW_APPROVE: 'Duyệt bài',
  REVIEW_REJECT: 'Từ chối bài',
  CONFIG_UPDATE: 'Cập nhật cấu hình',
  ADMIN_ACTION: 'Thao tác quản trị',
  REVIEW: 'Kiểm duyệt bài',
  AI_CALL: 'Gọi trợ lý AI',
  POINTS: 'Thay đổi điểm uy tín',
  STARS: 'Thay đổi sao uy tín',
  SECURITY: 'Sự kiện bảo mật',
};

function activityLogTypeLabel(type: string) {
  return activityLogTypeLabels[type.trim().toUpperCase()] ?? 'Khác';
}

interface ActivityLogCheck {
  passed: boolean;
  question: string;
}

function ActivityLogDetail({ log }: { log: ActivityLogResponse }) {
  const [expanded, setExpanded] = useState(false);
  const content = log.detail?.trim() || log.reason?.trim() || 'Không có thông tin chi tiết';
  const checks = activityLogChecks(log, content);
  const isStructured = checks.length > 0 || /^[{[]/.test(content);

  if (!isStructured) {
    return <p className="max-w-xl line-clamp-2 leading-5">{content}</p>;
  }

  const passedCount = checks.filter((check) => check.passed).length;
  const summary = checks.length
    ? `Đạt đủ ${passedCount}/${checks.length} mục kiểm duyệt`
    : 'Có dữ liệu chi tiết';

  return (
    <div className="max-w-xl">
      <p className={expanded ? 'leading-5' : 'line-clamp-2 leading-5'}>{summary}</p>
      <button
        type="button"
        className="mt-1 text-xs font-semibold text-primary hover:text-primary-hover"
        aria-expanded={expanded}
        onClick={() => setExpanded((value) => !value)}
      >
        {expanded ? 'Thu gọn' : 'Xem chi tiết'}
      </button>
      {expanded ? (
        checks.length ? (
          <ul className="mt-2 max-h-48 space-y-1.5 overflow-y-auto rounded-md bg-surface-low p-2 text-xs leading-5">
            {checks.map((check, index) => (
              <li key={`${check.question}-${index}`} className="flex items-start gap-2">
                <span className={check.passed ? 'font-bold text-success' : 'font-bold text-error'}>
                  {check.passed ? '✓' : '✗'}
                </span>
                <span>{check.question}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-2 rounded-md bg-surface-low p-2 text-xs leading-5 text-text-muted">
            Dữ liệu chi tiết đã được ghi nhận.
          </p>
        )
      ) : null}
    </div>
  );
}

function activityLogChecks(log: ActivityLogResponse, content: string): ActivityLogCheck[] {
  const dataChecks = activityLogChecksFromValue(log.data);
  if (dataChecks.length) return dataChecks;

  if (/^[{[]/.test(content)) {
    try {
      const parsedChecks = activityLogChecksFromValue(JSON.parse(content) as unknown);
      if (parsedChecks.length) return parsedChecks;
    } catch {
      // Backend cũ có thể trả Map.toString(), được xử lý bằng biểu thức bên dưới.
    }
  }

  if (!/checks|passed|question/i.test(content)) return [];
  return [...content.matchAll(/\{passed=(true|false),\s*question=([^}]*)}/gi)].flatMap((match) => {
    const question = match[2]?.trim();
    return question ? [{ passed: match[1].toLowerCase() === 'true', question }] : [];
  });
}

function activityLogChecksFromValue(value: unknown): ActivityLogCheck[] {
  const entries = Array.isArray(value)
    ? value
    : value && typeof value === 'object' && Array.isArray((value as Record<string, unknown>).checks)
      ? (value as Record<string, unknown>).checks as unknown[]
      : [];

  return entries.flatMap((entry) => {
    if (!entry || typeof entry !== 'object') return [];
    const record = entry as Record<string, unknown>;
    const question = typeof record.statement === 'string' ? record.statement.trim() : typeof record.question === 'string' ? record.question.trim() : '';
    const passed = record.passed === true || record.passed === 'true';
    return question ? [{ passed, question }] : [];
  });
}

const activityLogTargetLabels: Record<string, string> = {
  ITEM: 'Bài đăng',
  USER: 'Người dùng',
  REQUEST: 'Yêu cầu',
  REPORT: 'Báo cáo',
  CONFIG: 'Cấu hình',
};

function activityLogTarget(log: ActivityLogResponse) {
  if (log.targetTitle) return log.targetTitle;
  // Không có tên hiển thị thì chỉ nêu loại đối tượng, không in id kỹ thuật ra màn hình.
  return activityLogTargetLabels[log.targetType?.trim().toUpperCase() ?? ''] ?? 'Đối tượng';
}

function formatActivityLogTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Không xác định';
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    dateStyle: 'short',
    timeStyle: 'medium',
  }).format(date);
}

function activityLogErrorMessage(error: ApiError) {
  if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (error.status === 403) return 'Tài khoản hiện tại không có quyền xem nhật ký hành động.';
  if (error.status === 400) return 'Khoảng ngày không hợp lệ';
  if (error.status === 0) return 'Không thể kết nối máy chủ. Vui lòng kiểm tra kết nối và thử lại.';
  return error.message || 'Không tải được nhật ký hành động. Vui lòng thử lại.';
}

export function AdminContentPage({
  kind,
}: {
  kind: 'categories' | 'checklist' | 'keywords' | 'districts';
}) {
  if (kind === 'keywords') return <ApiAdminKeywords />;
  if (kind === 'categories') return <ApiAdminCategories />;
  if (kind === 'checklist') return <ApiAdminChecklist />;
  return <ApiAdminAreas />;
}

const lexiconGroupLabels: Record<string, string> = {
  WEAPONS: 'Vũ khí',
  DRUGS: 'Ma túy',
  CONTACT: 'Thông tin liên hệ',
  OWNERSHIP_TRANSFER: 'Đồ cần sang tên giấy tờ',
  CASH_EQUIVALENT: 'Đồ quy ra tiền mặt',
  FOOD_BEVERAGE: 'Thực phẩm và đồ uống',
};

const lexiconLevelLabels: Record<string, string> = {
  BLOCK: 'Chặn đăng',
  REVIEW: 'Cần duyệt thủ công',
};

const lexiconScopeLabels: Record<string, string> = {
  POST: 'Bài đăng',
  CHAT: 'Tin nhắn',
  MESSAGE: 'Tin nhắn',
  BOTH: 'Bài đăng và tin nhắn',
};

const LEXICON_PAGE_SIZE = 20;

function getLexiconLabel(labels: Record<string, string>, value: string) {
  return labels[value.trim().toUpperCase()] ?? 'Khác';
}

function ApiAdminKeywords() {
  const [entries, setEntries] = useState<AdminLexiconEntry[]>([]);
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<AdminLexiconEntry | null | 'new'>(null);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [testText, setTestText] = useState('');
  const [testMatches, setTestMatches] = useState<LexiconMatch[]>([]);
  const [testing, setTesting] = useState(false);
  const pageEntries = entries.slice(
    page * LEXICON_PAGE_SIZE,
    (page + 1) * LEXICON_PAGE_SIZE,
  );

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminLexiconApi.list()
      .then((response) => {
        if (active) setEntries(response);
      })
      .catch((apiError: ApiError) => {
        if (active) setError(apiError.message || 'Không tải được từ điển kiểm duyệt.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [reloadKey]);

  const save = async (input: AdminLexiconRequest) => {
    setSavingId(editing === 'new' ? 0 : editing?.id ?? null);
    setError('');
    try {
      const saved = editing === 'new'
        ? await adminLexiconApi.create(input)
        : await adminLexiconApi.update(editing!.id, input);
      setEntries((current) => editing === 'new'
        ? [...current, saved]
        : current.map((entry) => entry.id === saved.id ? saved : entry));
      setEditing(null);
      setNotice(editing === 'new' ? 'Đã thêm từ khóa.' : 'Đã cập nhật từ khóa.');
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không lưu được từ khóa.');
    } finally {
      setSavingId(null);
    }
  };

  const toggleActive = async (entry: AdminLexiconEntry) => {
    setSavingId(entry.id);
    setError('');
    try {
      const updated = await adminLexiconApi.setActive(entry.id, !entry.active);
      setEntries((current) => current.map((item) => item.id === updated.id ? updated : item));
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không cập nhật được trạng thái từ khóa.');
    } finally {
      setSavingId(null);
    }
  };

  const testLexicon = async () => {
    if (!testText.trim()) return;
    setTesting(true);
    setError('');
    try {
      setTestMatches(await adminLexiconApi.test(testText.trim()));
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không kiểm tra được nội dung.');
    } finally {
      setTesting(false);
    }
  };

  return (
    <Panel
      title="Từ khóa kiểm duyệt"
      action={<Button onClick={() => setEditing('new')}>Thêm từ khóa</Button>}
    >
      {notice ? <div className="mb-4"><Alert tone="success">{notice}</Alert></div> : null}
      {error ? <div className="mb-4 space-y-3"><Alert tone="error">{error}</Alert><Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Tải lại</Button></div> : null}
      <section className="mb-5 rounded-lg bg-white p-4 ring-1 ring-border/80">
        <TextArea label="Kiểm tra nội dung" value={testText} onChange={(event) => setTestText(event.target.value)} placeholder="Nhập nội dung cần đối chiếu với từ điển..." />
        <Button className="mt-3" variant="outline" disabled={testing || !testText.trim()} onClick={() => void testLexicon()}>{testing ? 'Đang kiểm tra...' : 'Kiểm tra'}</Button>
        {testMatches.length ? <div className="mt-3 space-y-1 text-xs text-text-secondary">{testMatches.map((match, index) => <p key={`${match.keyword}-${index}`}><strong>{match.keyword}</strong> · {getLexiconLabel(lexiconLevelLabels, match.level)} · {getLexiconLabel(lexiconGroupLabels, match.group)} · {match.matchedAs}</p>)}</div> : null}
      </section>
      {loading ? <SkeletonRows label="Đang tải từ điển" /> : entries.length ? (
        <>
          <div className="table-wrap"><table className="data-table"><thead><tr><th>Từ khóa</th><th>Nhóm</th><th>Mức độ</th><th>Phạm vi</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
            {pageEntries.map((entry) => <tr key={entry.id}><td className="font-semibold">{entry.keyword}</td><td>{getLexiconLabel(lexiconGroupLabels, entry.group)}</td><td>{getLexiconLabel(lexiconLevelLabels, entry.level)}</td><td>{getLexiconLabel(lexiconScopeLabels, entry.scope)}</td><td>{entry.active ? 'Đang hiển thị' : 'Đang ẩn'}</td><td className="space-x-2"><Button variant="ghost" disabled={savingId === entry.id} onClick={() => setEditing(entry)}>Sửa</Button><Button variant="outline" disabled={savingId === entry.id} onClick={() => void toggleActive(entry)}>{entry.active ? 'Tạm ngưng' : 'Kích hoạt'}</Button></td></tr>)}
          </tbody></table></div>
          <Pagination
            page={page}
            size={LEXICON_PAGE_SIZE}
            totalElements={entries.length}
            disabled={savingId !== null}
            onPageChange={setPage}
          />
        </>
      ) : <EmptyState title="Chưa có từ khóa" text="Hiện chưa có mục từ điển kiểm duyệt nào." />}
      {editing ? <ApiLexiconModal entry={editing === 'new' ? null : editing} saving={savingId !== null} onClose={() => setEditing(null)} onSave={(input) => void save(input)} /> : null}
    </Panel>
  );
}

function ApiLexiconModal({ entry, saving, onClose, onSave }: { entry: AdminLexiconEntry | null; saving: boolean; onClose: () => void; onSave: (input: AdminLexiconRequest) => void }) {
  const [keyword, setKeyword] = useState(entry?.keyword ?? '');
  const [group, setGroup] = useState(entry?.group ?? '');
  const [level, setLevel] = useState(entry?.level ?? '');
  const [scope, setScope] = useState(entry?.scope ?? '');
  const [matchMode, setMatchMode] = useState(entry?.matchMode ?? '');
  const [exceptions, setExceptions] = useState(entry?.exceptions.join(', ') ?? '');
  const valid = keyword.trim() && group.trim() && level.trim() && scope.trim();
  const messageScope = scope.trim().toUpperCase() === 'MESSAGE' ? 'MESSAGE' : 'CHAT';
  return <Modal title={entry ? 'Sửa từ khóa' : 'Thêm từ khóa'} onClose={onClose}>
    <div className="space-y-4"><Field label="Từ khóa" value={keyword} onChange={(event) => setKeyword(event.target.value)} /><Select label="Nhóm" value={group} onChange={(event) => setGroup(event.target.value)}><option value="">Chọn nhóm</option>{group && !lexiconGroupLabels[group.trim().toUpperCase()] ? <option value={group}>Khác</option> : null}{Object.entries(lexiconGroupLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select><Select label="Mức độ" value={level} onChange={(event) => setLevel(event.target.value)}><option value="">Chọn mức độ</option>{level && !lexiconLevelLabels[level.trim().toUpperCase()] ? <option value={level}>Khác</option> : null}{Object.entries(lexiconLevelLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select><Select label="Phạm vi" value={scope} onChange={(event) => setScope(event.target.value)}><option value="">Chọn phạm vi</option>{scope && !lexiconScopeLabels[scope.trim().toUpperCase()] ? <option value={scope}>Khác</option> : null}<option value="POST">Bài đăng</option><option value={messageScope}>Tin nhắn</option><option value="BOTH">Bài đăng và tin nhắn</option></Select><Field label="Chế độ khớp (không bắt buộc)" value={matchMode} onChange={(event) => setMatchMode(event.target.value)} /><Field label="Ngoại lệ, phân cách bằng dấu phẩy" value={exceptions} onChange={(event) => setExceptions(event.target.value)} /></div>
    <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" disabled={saving} onClick={onClose}>Hủy</Button><Button disabled={saving || !valid} onClick={() => onSave({ keyword: keyword.trim(), group: group.trim(), level: level.trim(), scope: scope.trim(), ...(matchMode.trim() ? { matchMode: matchMode.trim() } : {}), exceptions: exceptions.split(',').map((value) => value.trim()).filter(Boolean) })}>{saving ? 'Đang lưu...' : 'Lưu'}</Button></div>
  </Modal>;
}

function ApiAdminAreas() {
  const [areas, setAreas] = useState<AdminAreaResponse[]>([]);
  const [editing, setEditing] = useState<AdminAreaResponse | null | 'new'>(null);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminAreaApi.list()
      .then((response) => { if (active) setAreas(response); })
      .catch((apiError: ApiError) => { if (active) setError(apiError.message || 'Không tải được danh sách khu vực.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [reloadKey]);

  const save = async (input: AdminAreaRequest) => {
    setSavingId(editing === 'new' ? 0 : editing?.id ?? null);
    setError('');
    setNotice('');
    try {
      const saved = editing === 'new'
        ? await adminAreaApi.create(input)
        : await adminAreaApi.update(editing!.id, input);
      setAreas((current) => editing === 'new'
        ? [...current, saved]
        : current.map((entry) => entry.id === saved.id ? saved : entry));
      setNotice(editing === 'new' ? 'Đã thêm khu vực.' : 'Đã cập nhật khu vực.');
      setEditing(null);
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không lưu được khu vực.');
    } finally {
      setSavingId(null);
    }
  };

  const toggleActive = async (area: AdminAreaResponse) => {
    setSavingId(area.id);
    setError('');
    setNotice('');
    try {
      const updated = await adminAreaApi.setActive(area.id, !area.active);
      setAreas((current) => current.map((entry) => entry.id === updated.id ? updated : entry));
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không cập nhật được trạng thái khu vực.');
    } finally {
      setSavingId(null);
    }
  };

  return <Panel title="Khu vực" description="Quản lý tỉnh, thành phố và khu vực con dùng cho tìm kiếm và đăng bài." action={<Button onClick={() => setEditing('new')}>Thêm khu vực</Button>}>
    {notice ? <div className="mb-4"><Alert tone="success">{notice}</Alert></div> : null}
    {error ? <div className="mb-4 space-y-3"><Alert tone="error">{error}</Alert><Button size="sm" variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Tải lại</Button></div> : null}
    {loading ? <EmptyState title="Đang tải khu vực" text="Vui lòng chờ trong giây lát." /> : areas.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>Tên</th><th>Khu vực cha</th><th>Cấp</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{areas.map((area) => <tr key={area.id}><td className="font-semibold">{area.name}</td><td>{areas.find((entry) => entry.id === area.parentId)?.name ?? '—'}</td><td>{area.level === 1 ? 'Thành phố' : area.level === 2 ? 'Quận' : area.level}</td><td>{area.active ? 'Đang dùng' : 'Tạm ẩn'}</td><td className="space-x-2"><Button size="sm" variant="ghost" disabled={savingId === area.id} onClick={() => setEditing(area)}>Sửa</Button><Button size="sm" variant="outline" disabled={savingId === area.id} onClick={() => void toggleActive(area)}>{area.active ? 'Tạm ẩn' : 'Kích hoạt'}</Button></td></tr>)}</tbody></table></div> : <EmptyState title="Chưa có khu vực" text="Hiện chưa có khu vực nào." />}
    {editing ? <ApiAreaModal area={editing === 'new' ? null : editing} areas={areas} saving={savingId !== null} onClose={() => setEditing(null)} onSave={(input) => void save(input)} /> : null}
  </Panel>;
}

function ApiAreaModal({ area, areas, saving, onClose, onSave }: { area: AdminAreaResponse | null; areas: AdminAreaResponse[]; saving: boolean; onClose: () => void; onSave: (input: AdminAreaRequest) => void }) {
  const [name, setName] = useState(area?.name ?? '');
  const [parentId, setParentId] = useState(area?.parentId ? String(area.parentId) : '');
  return <Modal title={area ? 'Sửa khu vực' : 'Thêm khu vực'} onClose={onClose}>
    <div className="space-y-4">
      <Field label="Tên khu vực" value={name} onChange={(event) => setName(event.target.value)} disabled={saving} />
      <Select label="Khu vực cha" value={parentId} onChange={(event) => setParentId(event.target.value)} disabled={saving}><option value="">Không có</option>{areas.filter((entry) => entry.id !== area?.id && entry.active).map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</Select>
    </div>
    <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" disabled={saving} onClick={onClose}>Hủy</Button><Button disabled={saving || !name.trim()} onClick={() => onSave({ name: name.trim(), parentId: parentId ? Number(parentId) : null })}>{saving ? 'Đang lưu...' : 'Lưu'}</Button></div>
  </Modal>;
}

function sortAdminChecklist(entries: AdminChecklistEntry[]) {
  return [...entries].sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id);
}

function adminChecklistErrorMessage(error: unknown, fallback: string) {
  const apiError = error as ApiError;
  if (apiError.status === 400) {
    if (apiError.code === 'SETTING_CHECKLIST_AT_LEAST_ONE_REQUIRED') {
      return 'Phải luôn có ít nhất một tiêu chí đang hoạt động.';
    }
    return 'Dữ liệu checklist chưa hợp lệ. Vui lòng kiểm tra và thử lại.';
  }
  if (apiError.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (apiError.status === 403) return 'Bạn không có quyền quản lý checklist.';
  if (apiError.status === 404) return 'Không tìm thấy tiêu chí checklist.';
  if (apiError.status === 0) return 'Không kết nối được máy chủ. Vui lòng thử lại.';
  return fallback;
}

function ApiAdminChecklist() {
  const [entries, setEntries] = useState<AdminChecklistEntry[]>([]);
  const [editing, setEditing] = useState<AdminChecklistEntry | null | 'new'>(null);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [editingError, setEditingError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const mutationLock = useRef(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminChecklistApi.list()
      .then((response) => {
        if (active) setEntries(sortAdminChecklist(response));
      })
      .catch((apiError: ApiError) => {
        if (active) setError(adminChecklistErrorMessage(apiError, 'Không tải được checklist duyệt bài.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [reloadKey]);

  const save = async (question: string) => {
    const trimmedQuestion = question.trim();
    const currentEditing = editing;
    if (
      mutationLock.current ||
      !currentEditing ||
      !trimmedQuestion ||
      trimmedQuestion.length > ADMIN_CHECKLIST_QUESTION_MAX_LENGTH
    ) return;
    mutationLock.current = true;
    setSavingId(currentEditing === 'new' ? 0 : currentEditing.id);
    setEditingError('');
    setNotice('');
    try {
      const saved = currentEditing === 'new'
        ? await adminChecklistApi.create({ question: trimmedQuestion })
        : await adminChecklistApi.update(currentEditing.id, { question: trimmedQuestion });
      setEntries((current) => sortAdminChecklist(currentEditing === 'new'
        ? [...current, saved]
        : current.map((entry) => entry.id === saved.id ? saved : entry)
      ));
      setNotice(currentEditing === 'new' ? 'Đã thêm tiêu chí.' : 'Đã cập nhật tiêu chí.');
      setEditing(null);
    } catch (apiError) {
      setEditingError(adminChecklistErrorMessage(apiError, 'Không lưu được tiêu chí.'));
    } finally {
      mutationLock.current = false;
      setSavingId(null);
    }
  };

  const toggleActive = async (entry: AdminChecklistEntry) => {
    if (mutationLock.current) return;
    mutationLock.current = true;
    setSavingId(entry.id);
    setError('');
    setNotice('');
    try {
      const updated = await adminChecklistApi.setActive(entry.id, !entry.active);
      setEntries((current) => current.map((item) => item.id === updated.id ? updated : item));
      setNotice(updated.active ? 'Đã kích hoạt tiêu chí.' : 'Đã tạm ngưng tiêu chí.');
    } catch (apiError) {
      setError(adminChecklistErrorMessage(apiError, 'Không cập nhật được trạng thái tiêu chí.'));
    } finally {
      mutationLock.current = false;
      setSavingId(null);
    }
  };

  const move = async (index: number, offset: -1 | 1) => {
    const targetIndex = index + offset;
    if (mutationLock.current || targetIndex < 0 || targetIndex >= entries.length) return;
    const reordered = [...entries];
    [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
    const itemIds = reordered.map((entry) => entry.id);
    if (new Set(itemIds).size !== entries.length) {
      setError('Danh sách checklist có ID bị trùng. Vui lòng tải lại dữ liệu.');
      return;
    }
    mutationLock.current = true;
    setSavingId(-1);
    setError('');
    setNotice('');
    try {
      const updated = await adminChecklistApi.reorder(itemIds);
      setEntries(sortAdminChecklist(updated));
      setNotice('Đã cập nhật thứ tự checklist.');
    } catch (apiError) {
      setError(adminChecklistErrorMessage(apiError, 'Không cập nhật được thứ tự checklist. Thứ tự cũ được giữ nguyên.'));
    } finally {
      mutationLock.current = false;
      setSavingId(null);
    }
  };

  return (
    <Panel title="Checklist duyệt bài" description="Quản lý và sắp xếp các tiêu chí mà quản trị viên phải xác nhận khi duyệt bài." action={<Button disabled={loading || savingId !== null} onClick={() => { setEditingError(''); setEditing('new'); }}>Thêm tiêu chí</Button>}>
      {notice ? <div className="mb-4"><Alert tone="success">{notice}</Alert></div> : null}
      {error ? <div className="mb-4 space-y-3"><Alert tone="error">{error}</Alert><Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Tải lại</Button></div> : null}
      {loading ? <SkeletonRows label="Đang tải checklist" /> : entries.length ? (
        <div className="table-wrap"><table className="data-table"><thead><tr><th>Thứ tự</th><th>Tiêu chí</th><th className="whitespace-nowrap">Trạng thái</th><th className="min-w-[430px] whitespace-nowrap">Thao tác</th></tr></thead><tbody>
          {entries.map((entry, index) => <tr key={entry.id}><td>{index + 1}</td><td className="font-semibold">{entry.question}</td><td className="whitespace-nowrap"><span className={`rounded-sm px-2 py-1 text-xs font-semibold ${entry.active ? 'bg-success-soft text-success' : 'bg-surface-low text-text-muted'}`}>{entry.active ? 'Đang hoạt động' : 'Không hoạt động'}</span></td><td><div className="flex flex-nowrap gap-1"><Button className="!px-2 whitespace-nowrap" variant="ghost" title="Đưa mục này lên trên một vị trí" disabled={savingId !== null || index === 0} onClick={() => void move(index, -1)}>Lên</Button><Button className="!px-2 whitespace-nowrap" variant="ghost" title="Đưa mục này xuống dưới một vị trí" disabled={savingId !== null || index === entries.length - 1} onClick={() => void move(index, 1)}>Xuống</Button><Button className="!px-2 whitespace-nowrap" variant="outline" title="Chỉnh sửa câu tiêu chí" disabled={savingId !== null} onClick={() => { setEditingError(''); setEditing(entry); }}>Sửa nội dung</Button><Button className="!px-2 whitespace-nowrap" variant="outline" title={entry.active ? 'Không hiển thị mục này khi duyệt bài (không xoá)' : 'Hiển thị lại mục này khi duyệt bài'} disabled={savingId !== null} onClick={() => void toggleActive(entry)}>{entry.active ? 'Tạm ẩn' : 'Kích hoạt'}</Button></div></td></tr>)}
        </tbody></table></div>
      ) : error ? null : <EmptyState title="Chưa có tiêu chí" text="Hiện chưa có tiêu chí duyệt bài nào." />}
      {editing ? <ChecklistEditModal entry={editing === 'new' ? null : editing} saving={savingId !== null} error={editingError} onClose={() => { if (savingId === null) { setEditing(null); setEditingError(''); } }} onSave={(question) => void save(question)} /> : null}
    </Panel>
  );
}

function ChecklistEditModal({ entry, saving, error, onClose, onSave }: { entry: AdminChecklistEntry | null; saving: boolean; error: string; onClose: () => void; onSave: (question: string) => void }) {
  const [question, setQuestion] = useState(entry?.question ?? '');
  const questionTooLong = question.length > ADMIN_CHECKLIST_QUESTION_MAX_LENGTH;
  return <Modal title={entry ? 'Sửa tiêu chí' : 'Thêm tiêu chí'} onClose={onClose}>
    <TextArea
      label="Nội dung tiêu chí"
      value={question}
      onChange={(event) => setQuestion(event.target.value)}
      disabled={saving}
      error={questionTooLong ? `Nội dung không được vượt quá ${ADMIN_CHECKLIST_QUESTION_MAX_LENGTH} ký tự.` : undefined}
    />
    <p className={`mt-1.5 text-right text-xs ${questionTooLong ? 'text-error' : 'text-text-muted'}`}>
      {question.length}/{ADMIN_CHECKLIST_QUESTION_MAX_LENGTH} ký tự
    </p>
    {error ? <div className="mt-4"><Alert tone="error">{error}</Alert></div> : null}
    <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" disabled={saving} onClick={onClose}>Hủy</Button><Button disabled={saving || !question.trim() || questionTooLong} onClick={() => onSave(question.trim())}>{saving ? 'Đang lưu...' : 'Lưu'}</Button></div>
  </Modal>;
}

const SHOW_CATEGORY_ATTRIBUTES = false;

function sortCategories(categories: AdminCategoryResponse[]) {
  return [...categories].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
}

export function ApiAdminCategories() {
  const [categories, setCategories] = useState<AdminCategoryResponse[]>([]);
  const [editing, setEditing] = useState<AdminCategoryResponse | null | 'new'>(null);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const mutationLock = useRef(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminCategoryApi.list()
      .then((response) => { if (active) setCategories(sortCategories(response)); })
      .catch((apiError: ApiError) => { if (active) setError(apiError.message || 'Không tải được danh mục quản trị.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [reloadKey]);

  const save = async (input: AdminCategoryRequest) => {
    if (mutationLock.current) return;
    mutationLock.current = true;
    setSavingId(editing === 'new' ? 0 : editing?.id ?? null);
    setError('');
    setNotice('');
    try {
      // Danh mục mới tự xuống cuối danh sách, Backend lo việc gán thứ tự.
      const saved = editing === 'new'
        ? await adminCategoryApi.create(input)
        : await adminCategoryApi.update(editing!.id, input);
      setCategories((current) => sortCategories(editing === 'new'
        ? [...current, saved]
        : current.map((entry) => entry.id === saved.id ? saved : entry)));
      setEditing(null);
      setNotice(editing === 'new' ? 'Đã thêm danh mục.' : 'Đã cập nhật danh mục.');
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không lưu được danh mục.');
    } finally {
      mutationLock.current = false;
      setSavingId(null);
    }
  };

  const toggleActive = async (category: AdminCategoryResponse) => {
    if (mutationLock.current) return;
    mutationLock.current = true;
    setSavingId(category.id);
    setError('');
    try {
      const updated = await adminCategoryApi.setActive(category.id, !category.active);
      setCategories((current) => current.map((entry) => entry.id === updated.id ? updated : entry));
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không cập nhật được trạng thái danh mục.');
    } finally {
      mutationLock.current = false;
      setSavingId(null);
    }
  };

  // Đổi chỗ hai danh mục kề nhau rồi gửi cả danh sách id theo thứ tự mới. Lỗi thì giữ nguyên thứ tự cũ.
  const move = async (index: number, offset: -1 | 1) => {
    const targetIndex = index + offset;
    if (mutationLock.current || targetIndex < 0 || targetIndex >= categories.length) return;
    const reordered = [...categories];
    [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
    mutationLock.current = true;
    setSavingId(-1);
    setError('');
    setNotice('');
    try {
      const updated = await adminCategoryApi.reorder(reordered.map((entry) => entry.id));
      setCategories(sortCategories(updated));
      setNotice('Đã cập nhật thứ tự danh mục.');
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không cập nhật được thứ tự danh mục. Thứ tự cũ được giữ nguyên.');
    } finally {
      mutationLock.current = false;
      setSavingId(null);
    }
  };

  const hasParentCategory = categories.some((category) => category.parentId != null);
  const busy = savingId !== null;

  return <Panel title="Danh mục" description="Thứ tự trong bảng cũng là thứ tự danh mục hiển thị cho người dùng." action={<Button disabled={loading || busy} onClick={() => setEditing('new')}>Thêm danh mục</Button>}>
    {notice ? <div className="mb-4"><Alert tone="success">{notice}</Alert></div> : null}
    {error ? <div className="mb-4 space-y-3"><Alert tone="error">{error}</Alert><Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Tải lại</Button></div> : null}
    {loading ? <EmptyState title="Đang tải danh mục" text="Vui lòng chờ trong giây lát." /> : categories.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>Thứ tự</th><th>Tên</th>{hasParentCategory ? <th>Danh mục cha</th> : null}<th>Phân loại</th><th>Trạng thái</th><th className="whitespace-nowrap">Thao tác</th></tr></thead><tbody>{categories.map((category, index) => <tr key={category.id}><td>{index + 1}</td><td className="font-semibold">{category.name}</td>{hasParentCategory ? <td>{categories.find((entry) => entry.id === category.parentId)?.name ?? '—'}</td> : null}<td>{category.restricted ? 'Hạn chế' : 'Thông thường'}</td><td>{category.active ? 'Đang dùng' : 'Tạm ẩn'}</td><td><div className="flex flex-nowrap gap-1"><Button className="!px-2 whitespace-nowrap" variant="ghost" title="Đưa danh mục này lên trên một vị trí" disabled={busy || index === 0} onClick={() => void move(index, -1)}>Lên</Button><Button className="!px-2 whitespace-nowrap" variant="ghost" title="Đưa danh mục này xuống dưới một vị trí" disabled={busy || index === categories.length - 1} onClick={() => void move(index, 1)}>Xuống</Button><Button className="!px-2 whitespace-nowrap" variant="outline" disabled={busy} onClick={() => setEditing(category)}>Sửa</Button><Button className="!px-2 whitespace-nowrap" variant="outline" disabled={busy} onClick={() => void toggleActive(category)}>{category.active ? 'Tạm ẩn' : 'Kích hoạt'}</Button></div></td></tr>)}</tbody></table></div> : <EmptyState title="Chưa có danh mục" text="Hiện chưa có danh mục nào." />}
    {SHOW_CATEGORY_ATTRIBUTES && !loading && categories.length ? <ApiCategoryAttributes categories={categories} /> : null}
    {editing ? <ApiCategoryModal category={editing === 'new' ? null : editing} categories={categories} saving={savingId !== null} onClose={() => setEditing(null)} onSave={(input) => void save(input)} /> : null}
  </Panel>;
}

export function ApiCategoryModal({ category, categories, saving, onClose, onSave }: { category: AdminCategoryResponse | null; categories: AdminCategoryResponse[]; saving: boolean; onClose: () => void; onSave: (input: AdminCategoryRequest) => void }) {
  const [name, setName] = useState(category?.name ?? '');
  const [parentId, setParentId] = useState(category?.parentId ? String(category.parentId) : '');
  const [restricted] = useState(category?.restricted ?? false);
  const [boostable, setBoostable] = useState(category?.boostable ?? false);
  return <Modal title={category ? 'Sửa danh mục' : 'Thêm danh mục'} onClose={onClose}>
    <div className="space-y-4"><Field label="Tên danh mục" value={name} onChange={(event) => setName(event.target.value)} /><Select label="Danh mục cha" value={parentId} onChange={(event) => setParentId(event.target.value)}><option value="">Không có</option>{categories.filter((entry) => entry.id !== category?.id).map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</Select><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={boostable} onChange={(event) => setBoostable(event.target.checked)} />Cho phép đẩy tin</label>{category ? null : <p className="text-xs leading-5 text-text-muted">Danh mục mới tự xếp cuối danh sách. Dùng nút Chuyển lên hoặc Chuyển xuống để đổi vị trí.</p>}</div>
    <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" disabled={saving} onClick={onClose}>Hủy</Button><Button disabled={saving || !name.trim()} onClick={() => onSave({ name: name.trim(), parentId: parentId ? Number(parentId) : null, restricted, boostable })}>{saving ? 'Đang lưu...' : 'Lưu'}</Button></div>
  </Modal>;
}

function ApiCategoryAttributes({ categories }: { categories: AdminCategoryResponse[] }) {
  const [categoryId, setCategoryId] = useState(categories[0]?.id ?? 0);
  const [attributes, setAttributes] = useState<AdminCategoryAttributeResponse[]>([]);
  const [editing, setEditing] = useState<AdminCategoryAttributeResponse | null | 'new'>(null);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!categoryId || categories.some((category) => category.id === categoryId)) return;
    setCategoryId(categories[0]?.id ?? 0);
  }, [categories, categoryId]);

  useEffect(() => {
    if (!categoryId) {
      setAttributes([]);
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    setError('');
    adminCategoryApi.listAttributes(categoryId)
      .then((response) => { if (active) setAttributes(response); })
      .catch((apiError: ApiError) => { if (active) setError(apiError.message || 'Không tải được thuộc tính danh mục.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [categoryId, reloadKey]);

  const save = async (input: AdminCategoryAttributeRequest) => {
    setSavingId(editing === 'new' ? 0 : editing?.id ?? null);
    setError('');
    setNotice('');
    try {
      const saved = editing === 'new'
        ? await adminCategoryApi.createAttribute(categoryId, input)
        : await adminCategoryApi.updateAttribute(categoryId, editing!.id, input);
      setAttributes((current) => editing === 'new'
        ? [...current, saved].sort((left, right) => left.sortOrder - right.sortOrder)
        : current.map((entry) => entry.id === saved.id ? saved : entry).sort((left, right) => left.sortOrder - right.sortOrder));
      setNotice(editing === 'new' ? 'Đã thêm thuộc tính.' : 'Đã cập nhật thuộc tính.');
      setEditing(null);
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không lưu được thuộc tính danh mục.');
    } finally {
      setSavingId(null);
    }
  };

  const toggleActive = async (attribute: AdminCategoryAttributeResponse) => {
    setSavingId(attribute.id);
    setError('');
    setNotice('');
    try {
      const updated = await adminCategoryApi.setAttributeActive(categoryId, attribute.id, !attribute.active);
      setAttributes((current) => current.map((entry) => entry.id === updated.id ? updated : entry));
    } catch (apiError) {
      const response = apiError as ApiError;
      setError(response.message || 'Không cập nhật được trạng thái thuộc tính.');
    } finally {
      setSavingId(null);
    }
  };

  return <section className="mt-8 border-t border-border pt-6">
    <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <Select label="Thuộc tính của danh mục" value={categoryId} onChange={(event) => { setCategoryId(Number(event.target.value)); setEditing(null); setNotice(''); }}>
        {categories.map((category) => <option key={category.id} value={category.id}>{category.name}{category.active ? '' : ' (đang ẩn)'}</option>)}
      </Select>
      <Button onClick={() => setEditing('new')}>Thêm thuộc tính</Button>
    </div>
    {notice ? <div className="mb-4"><Alert tone="success">{notice}</Alert></div> : null}
    {error ? <div className="mb-4 space-y-3"><Alert tone="error">{error}</Alert><Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>Tải lại</Button></div> : null}
    {loading ? <EmptyState title="Đang tải thuộc tính" text="Vui lòng chờ trong giây lát." /> : attributes.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>Tên</th><th>Kiểu</th><th>Lựa chọn</th><th>Bắt buộc</th><th>Thứ tự</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{attributes.map((attribute) => <tr key={attribute.id}><td className="font-semibold">{attribute.name}</td><td>{attribute.dataType}</td><td>{attribute.options.length ? attribute.options.join(', ') : '—'}</td><td>{attribute.required ? 'Có' : 'Không'}</td><td>{attribute.sortOrder}</td><td>{attribute.active ? 'Đang dùng' : 'Tạm ẩn'}</td><td className="space-x-2"><Button variant="ghost" disabled={savingId === attribute.id} onClick={() => setEditing(attribute)}>Sửa</Button><Button variant="outline" disabled={savingId === attribute.id} onClick={() => void toggleActive(attribute)}>{attribute.active ? 'Tạm ẩn' : 'Kích hoạt'}</Button></td></tr>)}</tbody></table></div> : <EmptyState title="Chưa có thuộc tính" text="Danh mục này chưa được cấu hình thuộc tính động." />}
    {editing ? <ApiCategoryAttributeModal attribute={editing === 'new' ? null : editing} saving={savingId !== null} onClose={() => setEditing(null)} onSave={(input) => void save(input)} /> : null}
  </section>;
}

function ApiCategoryAttributeModal({ attribute, saving, onClose, onSave }: { attribute: AdminCategoryAttributeResponse | null; saving: boolean; onClose: () => void; onSave: (input: AdminCategoryAttributeRequest) => void }) {
  const [name, setName] = useState(attribute?.name ?? '');
  const [dataType, setDataType] = useState<AttributeDataType>(attribute?.dataType ?? 'TEXT');
  const [options, setOptions] = useState(attribute?.options.join('\n') ?? '');
  const [required, setRequired] = useState(attribute?.required ?? false);
  const [sortOrder, setSortOrder] = useState(attribute?.sortOrder ?? 0);
  const parsedOptions = options.split(/[,\n]/).map((value) => value.trim()).filter(Boolean);
  const invalidSelect = dataType === 'SELECT' && parsedOptions.length === 0;
  return <Modal title={attribute ? 'Sửa thuộc tính' : 'Thêm thuộc tính'} onClose={onClose}>
    <div className="space-y-4">
      <Field label="Tên thuộc tính" value={name} onChange={(event) => setName(event.target.value)} disabled={saving} />
      <Select label="Kiểu dữ liệu" value={dataType} onChange={(event) => setDataType(event.target.value as AttributeDataType)} disabled={saving}><option value="TEXT">Văn bản</option><option value="NUMBER">Số</option><option value="SELECT">Danh sách lựa chọn</option><option value="BOOLEAN">Có / Không</option></Select>
      {dataType === 'SELECT' ? <TextArea label="Các lựa chọn (mỗi dòng hoặc phân cách bằng dấu phẩy)" value={options} onChange={(event) => setOptions(event.target.value)} disabled={saving} /> : null}
      <Field label="Thứ tự" type="number" min={0} value={sortOrder} onChange={(event) => setSortOrder(Number(event.target.value))} disabled={saving} />
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={required} onChange={(event) => setRequired(event.target.checked)} disabled={saving} />Bắt buộc nhập</label>
    </div>
    <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" disabled={saving} onClick={onClose}>Hủy</Button><Button disabled={saving || !name.trim() || invalidSelect || !Number.isInteger(sortOrder) || sortOrder < 0} onClick={() => onSave({ name: name.trim(), dataType, options: dataType === 'SELECT' ? parsedOptions : [], required, sortOrder })}>{saving ? 'Đang lưu...' : 'Lưu'}</Button></div>
  </Modal>;
}

function Modal({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  const titleId = useId();

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <DialogFrame labelledBy={titleId} size="lg">
      <div className="flex items-center justify-between gap-4">
        <h2 id={titleId} className="text-xl font-bold">{title}</h2>
        <button type="button" aria-label="Đóng" onClick={onClose} className="grid size-9 place-items-center rounded-md text-text-muted hover:bg-surface-low">
          <Icon name="close" className="size-5" />
        </button>
      </div>
      <div className="mt-5">{children}</div>
    </DialogFrame>
  );
}

