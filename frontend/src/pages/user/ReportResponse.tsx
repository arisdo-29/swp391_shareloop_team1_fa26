import { useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Field, PageHeader, TextArea } from '../../components/ui';
import type { ApiError } from '../../services/http';
import { mediaApi } from '../../services/mediaApi';
import { reportApi } from '../../services/reportApi';

function responseErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  const messages: Record<string, string> = {
    VALIDATION_FAILED: 'Nội dung giải trình chưa hợp lệ hoặc vượt quá 2.000 ký tự.',
    REPORT_NOT_PARTICIPANT: 'Bạn không phải là người cần giải trình cho báo cáo này.',
    REPORT_RESPONSE_NOT_ALLOWED: 'Báo cáo này không còn nhận giải trình.',
    REPORT_NOT_FOUND: 'Không tìm thấy báo cáo cần giải trình.',
    MEDIA_NOT_OWNER: 'Có ảnh minh chứng không thuộc quyền sở hữu của bạn.',
    MEDIA_NOT_FOUND: 'Không tìm thấy một hoặc nhiều ảnh minh chứng.',
    MEDIA_NOT_UPLOADED: 'Ảnh minh chứng đã được sử dụng hoặc không còn hợp lệ.',
    MEDIA_INVALID_FILE: 'Danh sách ảnh minh chứng không hợp lệ hoặc bị trùng.',
  };
  if (apiError.code && messages[apiError.code]) return messages[apiError.code];
  if (apiError.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (apiError.status === 0) return 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';
  return 'Không gửi được giải trình. Vui lòng thử lại.';
}

export function ReportResponse() {
  const navigate = useNavigate();
  const { reportId } = useParams();
  const numericReportId = Number(reportId);
  const validReportId = Number.isSafeInteger(numericReportId) && numericReportId > 0;
  const [response, setResponse] = useState('');
  const [evidenceFiles, setEvidenceFiles] = useState<File[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const inFlight = useRef(false);

  const submit = async () => {
    const content = response.trim();
    if (!validReportId || !content || content.length > 2000 || inFlight.current) return;
    inFlight.current = true;
    setSubmitting(true);
    setError('');
    try {
      const uploads = evidenceFiles.length
        ? await Promise.all(evidenceFiles.map((file) => mediaApi.upload(file)))
        : [];
      await reportApi.respond(numericReportId, {
        response: content,
        evidenceMediaIds: uploads.length ? uploads.map((entry) => entry.mediaId) : undefined,
      });
      setSuccess(true);
      setResponse('');
      setEvidenceFiles([]);
    } catch (caught) {
      setError(responseErrorMessage(caught));
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <div className="page-shell">
      <PageHeader
        title="Giải trình báo cáo"
        description={validReportId ? 'Gửi nội dung giải trình cho báo cáo liên quan đến bạn.' : 'Đường dẫn báo cáo không hợp lệ'}
      />
      <section className="mx-auto max-w-2xl space-y-4 rounded-xl bg-white p-5 shadow-sm ring-1 ring-border/80 sm:p-6">
        {!validReportId ? <Alert tone="error">Đường dẫn báo cáo không hợp lệ.</Alert> : success ? (
          <>
            <Alert tone="success">Giải trình đã được gửi đến quản trị viên.</Alert>
            <Button onClick={() => navigate('/messages')}>Về trang Tin nhắn</Button>
          </>
        ) : (
          <>
            <Alert tone="info">
              Bạn có 48 giờ kể từ lúc nhận thông báo để gửi giải trình. Giờ hết hạn ghi trong thông báo, quá hạn thì báo
              cáo không còn nhận giải trình.
            </Alert>
            <TextArea
              label="Nội dung giải trình"
              value={response}
              maxLength={2000}
              disabled={submitting}
              onChange={(event) => setResponse(event.target.value)}
            />
            <p className="text-right text-xs text-text-muted">{response.length}/2000</p>
            <Field
              label="Ảnh minh chứng (không bắt buộc)"
              type="file"
              accept="image/*"
              multiple
              disabled={submitting}
              onChange={(event) => setEvidenceFiles(Array.from(event.target.files ?? []))}
            />
            {evidenceFiles.length ? (
              <p className="text-xs text-text-muted">Đã chọn {evidenceFiles.length} ảnh minh chứng.</p>
            ) : null}
            {error ? <Alert tone="error">{error}</Alert> : null}
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button variant="ghost" disabled={submitting} onClick={() => navigate('/messages')}>Hủy</Button>
              <Button disabled={submitting || !response.trim()} onClick={() => void submit()}>
                {submitting ? 'Đang gửi...' : 'Gửi giải trình'}
              </Button>
            </div>
          </>
        )}
      </section>
    </div>
  );
}
