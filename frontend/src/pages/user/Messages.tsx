import { useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { actions, selectCurrentUser, selectData } from '../../app/store';
import {
  Avatar,
  Button,
  ConditionBadge,
  EmptyState,
  Field,
  Icon,
  IconButton,
  Select,
  StatusBadge,
  TextArea,
  TypeBadge,
} from '../../components/ui';
import { progressIndex } from '../../utils/transaction';
import type { AppDispatch } from '../../app/store';
import type { Complaint, Handover, Item, Transaction, User } from '../../types/domain';
import { fileToDataUrl } from '../../utils/files';
import {
  classifyAmbiguousContact,
  contactSafetyLayer1,
  hasAmbiguousContactSignal,
} from '../../utils/contactSafety';

const steps = ['Trao doi', 'De xuat lich', 'Chot lich', 'Ban giao', 'Hoan tat'];
export function Messages() {
  const data = useAppSelector(selectData);
  const user = useAppSelector(selectCurrentUser)!;
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const conversations = data.conversations.filter(
    (c) => c.participantIds.includes(user.id) || user.role === 'admin',
  );
  const [selectedId, setSelectedId] = useState(conversations[0]?.id ?? '');
  const [mobileChat, setMobileChat] = useState(false);
  const [message, setMessage] = useState('');
  const [showSchedule, setShowSchedule] = useState(false);
  const [showComplaint, setShowComplaint] = useState(false);
  const [showConfirmComplete, setShowConfirmComplete] = useState(false);
  const [showContext, setShowContext] = useState(true);
  const [date, setDate] = useState('2026-09-28');
  const [time, setTime] = useState('18:30');
  const [method, setMethod] = useState<'Gặp trực tiếp' | 'Giao hàng'>('Gặp trực tiếp');
  const [district, setDistrict] = useState(user.district);
  const [address, setAddress] = useState('');
  const [note, setNote] = useState('');
  const [evidence, setEvidence] = useState<string[]>([]);
  const [complaintReason, setComplaintReason] = useState('');
  const [complaintContent, setComplaintContent] = useState('');
  const [complaintEvidence, setComplaintEvidence] = useState<string[]>([]);
  const [complaintNotice, setComplaintNotice] = useState('');
  const [complaintError, setComplaintError] = useState('');
  const [responseText, setResponseText] = useState('');
  const [responseEvidence, setResponseEvidence] = useState<string[]>([]);
  const [messageWarning, setMessageWarning] = useState('');
  const [sending, setSending] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const districts = data.districts
    .filter((entry) => entry.status === 'active')
    .map((entry) => entry.name);
  const conv = conversations.find((c) => c.id === selectedId);
  const tx = data.transactions.find((t) => t.id === conv?.transactionId);
  const item = data.items.find((i) => i.id === conv?.itemId);
  const selectedRequest = tx?.selectedRequestId
    ? data.itemRequests.find((entry) => entry.id === tx.selectedRequestId)
    : undefined;
  const offeredItemId = tx?.offeredItemId ?? tx?.sourceItemId ?? selectedRequest?.offeredItemId;
  const sourceItem = offeredItemId
    ? data.items.find((entry) => entry.id === offeredItemId)
    : undefined;
  const owner = data.users.find((entry) => entry.id === tx?.ownerId);
  const requester = data.users.find((entry) => entry.id === tx?.requesterId);
  const other = data.users.find((u) => u.id === conv?.participantIds.find((id) => id !== user.id));
  const messages = useMemo(
    () => data.messages.filter((m) => m.convId === selectedId),
    [data.messages, selectedId],
  );
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [selectedId, messages.length]);
  useEffect(() => {
    if (tx?.type === 'trade' && (!offeredItemId || !sourceItem)) {
      console.warn('Invalid trade transaction item data', {
        transactionId: tx.id,
        itemId: tx.itemId,
        offeredItemId,
      });
    }
  }, [offeredItemId, sourceItem, tx]);
  const handover = data.handovers.find((h) => h.id === tx?.handoverId);
  const activeComplaint = tx
    ? data.complaints.find(
        (complaint) =>
          complaint.transactionId === tx.id &&
          !['resolved', 'rejected'].includes(complaint.status),
      )
    : undefined;
  const myActiveComplaint = tx
    ? data.complaints.find(
        (complaint) =>
          complaint.transactionId === tx.id &&
          complaint.reporterId === user.id &&
          !['resolved', 'rejected'].includes(complaint.status),
      )
    : undefined;
  const complaintExpired =
    Boolean(tx?.completedAt) &&
    Boolean(tx?.complaintDeadline) &&
    Date.now() > new Date(tx!.complaintDeadline!).getTime();
  const canChat = Boolean(conv && tx && conv.participantIds.includes(user.id));
  const blockedMessageText =
    'Tin nhắn có chứa hoặc có dấu hiệu chứa thông tin liên hệ không được phép.';
  const send = async () => {
    if (!message.trim() || !conv) return;
    if (!canChat) {
      setMessageWarning(
        'Bạn chỉ có thể nhắn tin sau khi chủ bài đăng chọn bạn để giao dịch.',
      );
      return;
    }
    const text = message.trim();
    const layer1 = contactSafetyLayer1(text);
    if (layer1.blocked) {
      setMessageWarning(blockedMessageText);
      return;
    }
    if (hasAmbiguousContactSignal(text)) {
      setSending(true);
      try {
        if (await classifyAmbiguousContact(text)) {
          setMessageWarning(blockedMessageText);
          return;
        }
      } finally {
        setSending(false);
      }
    }
    dispatch(actions.sendMessage({ convId: conv.id, sender: user.id, text }));
    setMessageWarning('');
    setMessage('');
  };
  const select = (id: string) => {
    setSelectedId(id);
    setMobileChat(true);
  };
  const openItemDetail = (itemId: Item['id']) => {
    navigate(`/items/${String(itemId)}`);
  };
  const propose = () => {
    if (!tx || !address.trim()) return;
    dispatch(
      actions.proposeHandover({
        transactionId: tx.id,
        date,
        time,
        district,
        address,
        method,
        note,
        proposedBy: user.id,
      }),
    );
    setShowSchedule(false);
  };
  const confirmHandover = () => {
    if (!tx) return;
    dispatch(actions.confirmTransactionSide({ transactionId: tx.id, userId: user.id, evidence }));
    setEvidence([]);
    setShowConfirmComplete(false);
  };
  const submitComplaint = () => {
    if (!tx) return;
    if (!complaintReason.trim() || !complaintContent.trim()) {
      setComplaintError('Vui lòng nhập lý do và mô tả chi tiết.');
      return;
    }
    if (myActiveComplaint) {
      setComplaintError('Bạn đã có khiếu nại đang chờ xử lý cho giao dịch này.');
      return;
    }
    if (complaintExpired) {
      setComplaintError('Thời hạn khiếu nại cho giao dịch này đã kết thúc.');
      return;
    }
    dispatch(
      actions.createComplaint({
        transactionId: tx.id,
        reporterId: user.id,
        reason: complaintReason,
        content: complaintContent,
        evidence: complaintEvidence,
      }),
    );
    setComplaintReason('');
    setComplaintContent('');
    setComplaintEvidence([]);
    setComplaintError('');
    setComplaintNotice('Khiếu nại đã được gửi. Đang chờ xử lý.');
    setShowComplaint(false);
  };
  const submitComplaintResponse = () => {
    if (!activeComplaint || !responseText.trim()) return;
    dispatch(
      actions.respondToComplaint({
        complaintId: activeComplaint.id,
        respondentId: user.id,
        response: responseText,
        evidence: responseEvidence,
      }),
    );
    setResponseText('');
    setResponseEvidence([]);
  };
  return (
    <div className="page-shell py-5 sm:py-7">
      <div className="overflow-hidden rounded-xl bg-white shadow-md ring-1 ring-border/80 lg:grid lg:h-[calc(100dvh-140px)] lg:min-h-[680px] lg:grid-cols-[330px_1fr]">
        <aside className={`${mobileChat ? 'hidden lg:flex' : 'flex'} min-h-0 flex-col overflow-hidden border-r border-border`}>
          <div className="flex h-16 items-center justify-between border-b border-border px-5">
            <h1 className="text-lg font-bold">Tin nhắn</h1>
            <span className="text-xs text-text-muted">{conversations.length} cuộc trò chuyện</span>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto">
            {conversations.map((c) => {
              const cTx = data.transactions.find((t) => t.id === c.transactionId);
              const txItem = data.items.find((i) => i.id === c.itemId);
              const participant = data.users.find(
                (u) => u.id === c.participantIds.find((id) => id !== user.id),
              );
              const active = c.id === selectedId;
              return (
                <button
                  key={c.id}
                  onClick={() => select(c.id)}
                  className={`flex w-full gap-3 border-b border-border/70 p-4 text-left transition ${active ? 'bg-primary-faint' : 'hover:bg-background'}`}
                >
                  <Avatar user={participant ?? user} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <strong className="truncate text-sm">
                        {participant?.name ?? 'Thành viên'}
                      </strong>
                      <span className="shrink-0 text-[10px] text-text-muted">
                        {new Date(c.lastMessageAt).toLocaleDateString('vi-VN')}
                      </span>
                    </div>
                    <div className="mt-1 flex min-w-0 items-center gap-2">
                      {cTx || txItem ? <TypeBadge type={cTx?.type ?? txItem!.type} /> : null}
                      <span className="truncate text-xs font-medium text-primary">
                        {txItem?.title}
                      </span>
                    </div>
                    <p className="mt-1 truncate text-xs text-text-muted">{c.lastMessage}</p>
                  </div>
                </button>
              );
            })}
          </div>
        </aside>
        {conv && tx && item ? (
          <section
            className={`${mobileChat ? 'flex' : 'hidden lg:flex'} min-h-[680px] flex-col overflow-hidden lg:min-h-0`}
          >
            <header className="flex h-16 shrink-0 items-center gap-3 border-b border-border px-4 sm:px-5">
              <IconButton
                icon="arrow"
                label="Quay lại"
                className="rotate-180 lg:hidden"
                onClick={() => setMobileChat(false)}
              />
              {other ? <Avatar user={other} /> : null}
              <div className="min-w-0 flex-1">
                <h2 className="truncate text-sm font-bold">{other?.name ?? 'Cuộc trò chuyện'}</h2>
                <div className="mt-1 flex min-w-0 items-center gap-2">
                  <TypeBadge type={tx.type} />
                  <span className="truncate text-xs text-text-muted">{item.title}</span>
                </div>
              </div>
              <StatusBadge status={tx.status} />
            </header>
            <div className="shrink-0 border-b border-border bg-background/70">
              <button
                onClick={() => setShowContext(!showContext)}
                className="flex w-full items-center justify-between px-4 py-3 text-left sm:px-5"
              >
                <span className="flex min-w-0 items-center gap-3">
                  <img
                    src={item.images[0]}
                    alt=""
                    className="size-11 shrink-0 rounded-md object-cover"
                  />
                  <span className="min-w-0">
                    <strong className="block truncate text-sm">Thông tin giao dịch</strong>
                    <span className="text-xs text-text-muted">
                      Giao dich mien phi
                    </span>
                  </span>
                </span>
                <Icon
                  name="chevronDown"
                  className={`size-4 transition ${showContext ? 'rotate-180' : ''}`}
                />
              </button>
              {showContext ? (
                <TransactionContext
                  tx={tx}
                  item={item}
                  sourceItem={sourceItem}
                  owner={owner}
                  requester={requester}
                  partnerEmail={other?.email}
                  partnerPhone={other?.phone}
                  userId={user.id}
                  handover={handover}
                  dispatch={dispatch}
                  onSchedule={() => setShowSchedule(true)}
                  onComplaint={() => setShowComplaint(true)}
                  complaint={activeComplaint}
                  complaintExpired={complaintExpired}
                  complaintNotice={complaintNotice}
                  user={user}
                  responseText={responseText}
                  setResponseText={setResponseText}
                  responseEvidence={responseEvidence}
                  setResponseEvidence={setResponseEvidence}
                  onComplaintResponse={submitComplaintResponse}
                  evidence={evidence}
                  setEvidence={setEvidence}
                  onConfirm={() => setShowConfirmComplete(true)}
                  onOpenItem={openItemDetail}
                />
              ) : null}
            </div>
            {!['WAITING_HANDOVER', 'SENDER_CONFIRMED', 'RECEIVER_CONFIRMED', 'COMPLETED'].includes(tx.status) ? (
              <div className="shrink-0 border-b border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900 sm:px-6">
                🔒 Thông tin liên hệ đang được bảo vệ.<br />
                Số điện thoại, email và đường dẫn chỉ được phép sau khi cả hai xác nhận lịch hẹn.
              </div>
            ) : (
              <div className="shrink-0 border-b border-emerald-200 bg-emerald-50 px-4 py-3 text-xs font-semibold text-emerald-800 sm:px-6">
                🔓 Thông tin liên hệ đã được mở khóa sau khi cả hai xác nhận lịch hẹn.
              </div>
            )}
            <div className="min-h-0 flex-1 space-y-3 overflow-x-hidden overflow-y-auto bg-background px-4 pb-6 pt-5 sm:px-6">
              {messages.map((msg) => {
                const mine = msg.sender === user.id;
                if (msg.sender === 'system')
                  return (
                    <div key={msg.id} className="flex justify-center">
                      <span className="rounded-md bg-surface-container px-3 py-1.5 text-center text-[11px] text-text-muted">
                        {msg.text}
                      </span>
                    </div>
                  );
                return (
                  <div key={msg.id} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
                    <div
                      className={`max-w-[82%] rounded-lg px-3.5 py-2.5 text-sm leading-6 sm:max-w-[70%] ${mine ? 'rounded-br-sm bg-primary text-white' : 'rounded-bl-sm bg-white text-text-primary ring-1 ring-border/70'}`}
                    >
                      {msg.type === 'handover_card' ? (
                        <span className="flex items-center gap-2">
                          <Icon name="calendar" className="size-4" />
                          {msg.text}
                        </span>
                      ) : (
                        msg.text
                      )}
                      <span
                        className={`mt-1 block text-[10px] ${mine ? 'text-white/65' : 'text-text-muted'}`}
                      >
                        {msg.time}
                      </span>
                    </div>
                  </div>
                );
              })}
              <div ref={messagesEndRef} aria-hidden="true" />
            </div>
            <div className="relative bottom-auto z-10 shrink-0 border-t border-border bg-white p-3 sm:p-4">
              {messageWarning ? (
                <div className="mb-2 rounded-md bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-900 ring-1 ring-amber-100">
                  <p className="font-semibold">{messageWarning}</p>
                  <p>Vui lòng trao đổi và hoàn tất giao dịch trong ShareLoop.</p>
                </div>
              ) : null}
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  send();
                }}
                className="flex items-end gap-2"
              >
                <Field
                  className="h-11"
                  value={message}
                  onChange={(e) => {
                    setMessage(e.target.value);
                    if (messageWarning) setMessageWarning('');
                  }}
                  placeholder={
                    canChat
                      ? 'Nhập tin nhắn...'
                      : 'Bạn chỉ có thể nhắn tin sau khi chủ bài đăng chọn bạn để giao dịch.'
                  }
                  disabled={!canChat || sending}
                />
                <Button aria-label="Gửi tin nhắn" icon="send" disabled={!canChat || sending}>
                  {sending ? 'Đang kiểm tra' : 'Gửi'}
                </Button>
              </form>
            </div>
          </section>
        ) : (
          <div className="hidden place-items-center lg:grid">
            <EmptyState
              icon="messages"
              title="Chọn một cuộc trò chuyện"
              text="Tin nhắn và thông tin giao dịch sẽ hiển thị tại đây."
            />
          </div>
        )}
      </div>
      {showSchedule && tx ? (
        <div
          className="fixed inset-0 z-40 grid place-items-end bg-black/30 p-0 sm:place-items-center sm:p-4"
          role="dialog"
          aria-modal="true"
        >
          <div className="max-h-[92dvh] w-full overflow-y-auto rounded-t-xl bg-white p-5 shadow-lg sm:max-w-lg sm:rounded-xl sm:p-6">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-xl font-bold">Chốt lịch giao nhận</h2>
                <p className="mt-1 text-sm text-text-muted">
                  Người còn lại cần đồng ý trước khi lịch được xác nhận.
                </p>
              </div>
              <IconButton icon="close" label="Đóng" onClick={() => setShowSchedule(false)} />
            </div>
            <div className="mt-6 grid gap-4 sm:grid-cols-2">
              <Field
                label="Ngày"
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
              />
              <Field
                label="Giờ"
                type="time"
                value={time}
                onChange={(e) => setTime(e.target.value)}
              />
              <Select
                label="Phương thức"
                value={method}
                onChange={(e) => setMethod(e.target.value as typeof method)}
              >
                <option>Gặp trực tiếp</option>
                <option>Giao hàng</option>
              </Select>
              <Select label="Quận" value={district} onChange={(e) => setDistrict(e.target.value)}>
                {districts.map((d) => (
                  <option key={d}>{d}</option>
                ))}
              </Select>
            </div>
            <Field
              className="mt-4"
              label="Địa điểm cụ thể"
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              placeholder="Tên địa điểm, số nhà, đường..."
            />
            <TextArea
              className="mt-4"
              label="Ghi chú"
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setShowSchedule(false)}>
                Hủy
              </Button>
              <Button onClick={propose}>Gửi đề xuất lịch</Button>
            </div>
          </div>
        </div>
      ) : null}
      {showComplaint && tx ? (
        <div
          className="fixed inset-0 z-40 grid place-items-end bg-black/30 p-0 sm:place-items-center sm:p-4"
          role="dialog"
          aria-modal="true"
        >
          <div className="max-h-[92dvh] w-full overflow-y-auto rounded-t-xl bg-white p-5 shadow-lg sm:max-w-lg sm:rounded-xl sm:p-6">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-xl font-bold">Gửi khiếu nại</h2>
                <p className="mt-1 text-sm text-text-muted">
                  Khiếu nại sẽ được chuyển đến quản trị viên để xử lý theo giao dịch {tx.id}.
                </p>
              </div>
              <IconButton icon="close" label="Đóng" onClick={() => setShowComplaint(false)} />
            </div>
            <Field
              className="mt-5"
              label="Lý do"
              value={complaintReason}
              onChange={(event) => setComplaintReason(event.target.value)}
            />
            <div className="mt-3 flex flex-wrap gap-2">
              {[
                'Không nhận được món đồ',
                'Món đồ không đúng mô tả',
                'Đối phương không đến điểm hẹn',
                'Hành vi không phù hợp',
                'Gian lận',
                'Khác',
              ].map((reason) => (
                <Button
                  key={reason}
                  type="button"
                  size="sm"
                  variant={complaintReason === reason ? 'primary' : 'outline'}
                  onClick={() => setComplaintReason(reason)}
                >
                  {reason}
                </Button>
              ))}
            </div>
            <TextArea
              className="mt-4"
              label="Nội dung"
              value={complaintContent}
              onChange={(event) => setComplaintContent(event.target.value)}
            />
            {complaintError ? (
              <div className="mt-3 rounded-md bg-red-50 p-3 text-xs text-error">{complaintError}</div>
            ) : null}
            <label className="mt-4 inline-flex cursor-pointer items-center gap-2 rounded-md border border-border bg-white px-3 py-2 text-sm font-semibold text-text-secondary hover:border-primary">
              <Icon name="image" className="size-4" />
              Thêm ảnh/video minh chứng
              <input
                className="hidden"
                type="file"
                accept="image/*,video/*"
                multiple
                onChange={async (event) => {
                  const files = Array.from(event.target.files ?? []);
                  setComplaintEvidence([
                    ...complaintEvidence,
                    ...(await Promise.all(files.map(fileToDataUrl))),
                  ]);
                }}
              />
            </label>
            {complaintEvidence.length ? (
              <div className="mt-3 grid grid-cols-4 gap-2">
                {complaintEvidence.map((source, index) => (
                  <span key={source.slice(-16) + index} className="block aspect-square overflow-hidden rounded-md bg-surface-low">
                    {source.startsWith('data:video') ? (
                      <video src={source} className="size-full object-cover" />
                    ) : (
                      <img src={source} alt="Minh chứng" className="size-full object-cover" />
                    )}
                  </span>
                ))}
              </div>
            ) : null}
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setShowComplaint(false)}>
                Hủy
              </Button>
              <Button onClick={submitComplaint}>Gửi khiếu nại</Button>
            </div>
          </div>
        </div>
      ) : null}
      {showConfirmComplete && tx ? (
        <div
          className="fixed inset-0 z-40 grid place-items-end bg-black/30 p-0 sm:place-items-center sm:p-4"
          role="dialog"
          aria-modal="true"
        >
          <div className="w-full rounded-t-xl bg-white p-5 shadow-lg sm:max-w-md sm:rounded-xl sm:p-6">
            <h2 className="text-xl font-bold">Xác nhận hoàn tất</h2>
            <p className="mt-2 text-sm leading-6 text-text-muted">
              {tx.type === 'trade'
                ? 'Bạn xác nhận đã hoàn tất trao đổi với bên còn lại?'
                : user.id === tx.ownerId
                  ? 'Bạn xác nhận đã giao món đồ này?'
                  : 'Bạn xác nhận đã nhận được món đồ này?'}
            </p>
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setShowConfirmComplete(false)}>
                Hủy
              </Button>
              <Button onClick={confirmHandover}>Xác nhận</Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

type ContextProps = {
  tx: Transaction;
  item: Item;
  sourceItem?: Item;
  owner?: User;
  requester?: User;
  partnerEmail?: string;
  partnerPhone?: string;
  userId: string;
  handover: Handover | undefined;
  dispatch: AppDispatch;
  onSchedule: () => void;
  onComplaint: () => void;
  complaint?: Complaint;
  complaintExpired: boolean;
  complaintNotice: string;
  user: { id: string };
  responseText: string;
  setResponseText: Dispatch<SetStateAction<string>>;
  responseEvidence: string[];
  setResponseEvidence: Dispatch<SetStateAction<string[]>>;
  onComplaintResponse: () => void;
  evidence: string[];
  setEvidence: Dispatch<SetStateAction<string[]>>;
  onConfirm: () => void;
  onOpenItem: (itemId: Item['id']) => void;
};
function TransactionContext({
  tx,
  item,
  sourceItem,
  owner,
  requester,
  partnerEmail,
  partnerPhone,
  userId,
  handover,
  dispatch,
  onSchedule,
  onComplaint,
  complaint,
  complaintExpired,
  complaintNotice,
  user,
  responseText,
  setResponseText,
  responseEvidence,
  setResponseEvidence,
  onComplaintResponse,
  evidence,
  setEvidence,
  onConfirm,
  onOpenItem,
}: ContextProps) {
  const index = progressIndex(tx.status);
  const isOwner = userId === tx.ownerId;
  const currentUserConfirmed = isOwner ? tx.completedByOwner : tx.completedByRequester;
  const canConfirm = Boolean(handover && handover.status === 'confirmed') &&
    ['WAITING_HANDOVER', 'SENDER_CONFIRMED', 'RECEIVER_CONFIRMED'].includes(tx.status) &&
    !currentUserConfirmed;
  const terminal = ['COMPLETED', 'CANCELLED', 'DISPUTED'].includes(tx.status);
  const requestedItem = item;
  const offeredItem = sourceItem;
  return (
    <div className="border-t border-border px-4 pb-4 pt-3 sm:px-5">
      <div className="grid grid-cols-5 gap-1">
        {steps.map((step, i) => (
          <div key={step} className="min-w-0">
            <div className={`h-1 rounded-full ${i <= index ? 'bg-primary' : 'bg-surface-high'}`} />
            <span className="mt-1.5 hidden truncate text-[9px] text-text-muted sm:block">
              {step}
            </span>
          </div>
        ))}
      </div>
      <div className="mt-3 rounded-md bg-white p-3 text-xs ring-1 ring-border/70">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="font-semibold text-text-secondary">Hình thức</span>
          <TypeBadge type={tx.type} />
        </div>
        {tx.type === 'trade' ? (
          <div className="mt-3 grid gap-2 sm:grid-cols-[1fr_auto_1fr] sm:items-center">
            {offeredItem ? (
              <>
                <TransactionItemSummary
                  label="Món của bạn"
                  item={isOwner ? requestedItem : offeredItem}
                  onOpen={onOpenItem}
                />
                <div className="flex items-center justify-center gap-1 text-[11px] font-bold uppercase tracking-[0.08em] text-text-muted">
                  <span className="hidden sm:inline">Trao đổi với</span>
                  <Icon name="transaction" className="size-4" />
                </div>
                <TransactionItemSummary
                  label="Món của đối phương"
                  item={isOwner ? offeredItem : requestedItem}
                  onOpen={onOpenItem}
                />
              </>
            ) : (
              <TransactionItemSummary
                label="Món trao đổi"
                item={requestedItem}
                onOpen={onOpenItem}
              />
            )}
          </div>
        ) : (
          <div className="mt-3 space-y-2">
            <div className="grid gap-2 sm:grid-cols-[1fr_auto_1fr] sm:items-center">
              <TransactionParticipant label="Người cho" participant={owner} currentUserId={userId} />
              <div className="flex items-center justify-center gap-1 text-[11px] font-bold uppercase tracking-[0.08em] text-text-muted">
                <span className="hidden sm:inline">Cho tặng</span>
                <Icon name="arrow" className="size-4" />
              </div>
              <TransactionParticipant label="Người nhận" participant={requester} currentUserId={userId} />
            </div>
            <TransactionItemSummary label="Món đồ" item={item} onOpen={onOpenItem} />
          </div>
        )}
      </div>
      {handover ? (
        <div className="mt-3 flex flex-col gap-2 rounded-md bg-white p-3 text-xs ring-1 ring-border/70 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <strong>
              {handover.date} lúc {handover.time}
            </strong>
            <p className="mt-1 text-text-muted">
              {handover.method} · {handover.address}, {handover.district}
            </p>
          </div>
          {handover.status === 'proposed' && handover.proposedBy !== userId ? (
            <Button
              size="sm"
              onClick={() => dispatch(actions.acceptHandover({ handoverId: handover.id, userId }))}
            >
              Đồng ý lịch
            </Button>
          ) : (
            <span className="font-semibold text-success">
              {handover.status === 'confirmed' ? 'Đã thống nhất' : 'Chờ phản hồi'}
            </span>
          )}
        </div>
      ) : null}
      {['WAITING_HANDOVER', 'SENDER_CONFIRMED', 'RECEIVER_CONFIRMED', 'COMPLETED'].includes(tx.status) && handover ? (
        <div className="mt-3 rounded-md bg-emerald-50 p-3 text-xs text-success ring-1 ring-emerald-100">
          <p className="font-bold">Thong tin lien he da mo khoa</p>
          <p className="mt-1">Email: {partnerEmail ?? 'Chua cap nhat'}</p>
          <p>So dien thoai: {partnerPhone ?? 'Chua cap nhat'}</p>
          <p>
            Thoi gian: {handover.date} {handover.time}
          </p>
          <p>
            Dia diem: {handover.address}, {handover.district}
          </p>
        </div>
      ) : null}
      {handover?.status === 'confirmed' ? (
        <div className="mt-3 rounded-md bg-primary-faint p-3 text-xs leading-5 text-primary">
          <p className="font-bold">Lịch hẹn đã được xác nhận</p>
          <p>{handover.date} lúc {handover.time} · {handover.address}, {handover.district}</p>
          <p>Người giao: {owner?.name ?? 'Thành viên'} · Người nhận/trao đổi: {requester?.name ?? 'Thành viên'}</p>
        </div>
      ) : null}
      {(tx.status === 'SENDER_CONFIRMED' || tx.status === 'RECEIVER_CONFIRMED') && !terminal ? (
        <div className="mt-3 rounded-md bg-amber-50 p-3 text-xs leading-5 text-warning">
          {currentUserConfirmed ? <span className="block">Bạn đã xác nhận.</span> : null}
          <span className="block">Đang chờ bên còn lại xác nhận.</span>
        </div>
      ) : null}
      {tx.autoConfirmAt && !terminal ? (
        <p className="mt-3 text-[11px] leading-5 text-text-muted">
          Nếu không có khiếu nại, giao dịch sẽ được hệ thống tự xác nhận sau thời hạn quy định.
        </p>
      ) : null}
      <div className="mt-3 flex flex-wrap gap-2">
        {!terminal ? (
          <>
            <Button size="sm" variant="outline" icon="calendar" onClick={onSchedule}>
              {handover ? 'Đề xuất lịch khác' : 'Chốt lịch giao nhận'}
            </Button>
          </>
        ) : null}
        {tx.status === 'COMPLETED' &&
        tx.complaintDeadline &&
        Date.now() <= new Date(tx.complaintDeadline).getTime() ? (
          <Button size="sm" variant="outline" icon="shield" onClick={onComplaint}>
            Gửi khiếu nại
          </Button>
        ) : null}
      </div>
      {complaintNotice ? (
        <div className="mt-3 rounded-md bg-emerald-50 p-3 text-xs text-success">{complaintNotice}</div>
      ) : null}
      {tx.status === 'COMPLETED' && complaintExpired ? (
        <div className="mt-3 rounded-md bg-surface-low p-3 text-xs text-text-muted">
          Thời hạn khiếu nại cho giao dịch này đã kết thúc.
        </div>
      ) : null}
      {complaint ? (
        <div className="mt-3 rounded-md border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-900">
          <p className="font-bold">Khiếu nại đang chờ xử lý: {complaint.reason}</p>
          <p>{complaint.content}</p>
          {complaint.reportedUserId === user.id ? (
            <div className="mt-3 rounded-md bg-white p-3 ring-1 ring-amber-100">
              <p className="font-semibold">Bạn có 3 ngày để phản hồi khiếu nại.</p>
              <TextArea
                className="mt-2"
                label="Phản hồi"
                value={responseText}
                onChange={(event) => setResponseText(event.target.value)}
              />
              <label className="mt-2 inline-flex cursor-pointer items-center gap-1 rounded-md border border-border bg-white px-2.5 py-1.5 text-[11px] font-semibold text-text-secondary hover:border-primary">
                <Icon name="image" className="size-4" />
                Thêm bằng chứng
                <input
                  className="hidden"
                  type="file"
                  accept="image/*,video/*"
                  multiple
                  onChange={async (event) => {
                    const files = Array.from(event.target.files ?? []);
                    setResponseEvidence([
                      ...responseEvidence,
                      ...(await Promise.all(files.map(fileToDataUrl))),
                    ]);
                  }}
                />
              </label>
              {complaint.response ? (
                <p className="mt-2 text-success">Bạn đã phản hồi khiếu nại này.</p>
              ) : (
                <Button className="mt-3" size="sm" onClick={onComplaintResponse} disabled={!responseText.trim()}>
                  Gửi phản hồi
                </Button>
              )}
            </div>
          ) : null}
        </div>
      ) : null}
      {['WAITING_HANDOVER', 'SENDER_CONFIRMED', 'RECEIVER_CONFIRMED'].includes(tx.status) && !terminal ? (
        <div className="mt-3 rounded-md border border-dashed border-border p-3">
          <p className="text-xs font-semibold">
            {isOwner ? 'Xác nhận đã giao đồ' : 'Xác nhận đã nhận đồ'}
          </p>
          <p className="mt-1 text-[11px] text-text-muted">
            Thêm ảnh hoặc video làm bằng chứng trước khi xác nhận.
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            {evidence.map((source, i) => (
              <span
                key={source.slice(-16) + i}
                className="relative block size-16 overflow-hidden rounded-md bg-surface-low"
              >
                {source.startsWith('data:video') ? (
                  <video
                    src={source}
                    className="size-full object-cover"
                    aria-label="Video bằng chứng"
                  />
                ) : (
                  <img src={source} alt="Ảnh bằng chứng" className="size-full object-cover" />
                )}
                <button
                  type="button"
                  onClick={() => setEvidence(evidence.filter((_, x) => x !== i))}
                  aria-label="Xóa bằng chứng"
                  className="absolute right-1 top-1 grid size-5 place-items-center rounded-full bg-white text-error"
                >
                  <Icon name="close" className="size-3" />
                </button>
              </span>
            ))}
            <label className="inline-flex cursor-pointer items-center gap-1 rounded-md border border-border bg-white px-2.5 py-1.5 text-[11px] font-semibold text-text-secondary hover:border-primary">
              <Icon name="image" className="size-4" />
              Thêm bằng chứng
              <input
                className="hidden"
                type="file"
                accept="image/*,video/*"
                multiple
                onChange={async (event) => {
                  const files = Array.from(event.target.files ?? []);
                  setEvidence([...evidence, ...(await Promise.all(files.map(fileToDataUrl)))]);
                }}
              />
            </label>
            <Button size="sm" onClick={onConfirm} disabled={!canConfirm}>
              {isOwner ? 'Đã giao đồ' : 'Đã nhận đồ'}
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function TransactionParticipant({
  label,
  participant,
  currentUserId,
}: {
  label: string;
  participant?: User;
  currentUserId: string;
}) {
  const displayUser = participant ?? {
    name: 'Thành viên',
    avatarInitials: '?',
    avatarUrl: undefined,
  };
  return (
    <div className="flex min-w-0 items-center gap-3 rounded-md bg-background p-3">
      <Avatar user={displayUser} />
      <div className="min-w-0">
        <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-text-muted">
          {label}
        </p>
        <div className="mt-1 flex min-w-0 items-center gap-2">
          <p className="truncate text-sm font-bold text-text-primary">
            {participant?.name ?? 'Thành viên'}
          </p>
          {participant?.id === currentUserId ? (
            <span className="shrink-0 rounded-sm bg-primary-soft px-1.5 py-0.5 text-[10px] font-bold text-primary">
              Bạn
            </span>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function TransactionItemSummary({
  label,
  item,
  onOpen,
}: {
  label: string;
  item: Item;
  onOpen: (itemId: Item['id']) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onOpen(item.id)}
      className="group flex min-w-0 items-center gap-3 rounded-md bg-background p-3 transition hover:bg-primary-faint"
    >
      <img src={item.images[0]} alt="" className="size-12 shrink-0 rounded-md object-cover" />
      <div className="min-w-0 flex-1">
        <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-text-muted">
          {label}
        </p>
        <p className="mt-1 truncate text-sm font-bold text-text-primary group-hover:text-primary">
          {item.title}
        </p>
        <div className="mt-1 flex min-w-0 flex-wrap items-center gap-1.5">
          <ConditionBadge condition={item.condition} />
          <span className="truncate text-xs text-text-muted">{item.district}</span>
        </div>
        <span className="mt-1 inline-flex items-center gap-1 text-[11px] font-bold text-primary">
          Xem chi tiết
          <Icon name="arrow" className="size-3" />
        </span>
      </div>
    </button>
  );
}
