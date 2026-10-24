import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import {
  Avatar,
  Button,
  EmptyState,
  Field,
  Icon,
  IconButton,
  ItemImage,
  SearchField,
  Select,
  StatusBadge,
  TextArea,
  TypeBadge,
} from '../../components/ui';
import {
  classifyAmbiguousContact,
  contactSafetyLayer1,
  hasAmbiguousContactSignal,
} from '../../utils/contactSafety';
import { chatApi, type MessageResponse, type PinnedItemResponse } from '../../services/chatApi';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import {
  requestApi,
  toBackendUserId,
  type RequestContactResponse,
  type RequestMediaResponse,
  type RequestMediaStage,
  type RequestResponse,
} from '../../services/requestApi';
import type { ApiError } from '../../services/http';
import { ReportDialog } from '../../components/ReportDialog';
import { canChat, canConfirmHandover, canSchedule, canSelectPendingRequest, requestStatusLabel } from '../../utils/request';
import { mediaApi } from '../../services/mediaApi';
import { openDispute, respondDispute, type ReportReason } from '../../services/reportApi';
import { resolveMediaUrl } from '../../services/mediaUrl';
import {
  canConfirmMeetingSchedule,
  minimumMeetingTimeValue,
  parseLocalMeetingTime,
  scheduleApiErrorMessage,
  toLocalMeetingTimeValue,
  validateLocalMeetingTime,
  validateMeetingPlace,
  validateStoredMeetingTime,
} from '../../utils/meetingSchedule';
import {
  CHAT_POLL_INTERVAL_MS,
  mergeChatMessages,
  messageOfferStatusLabel,
  requestConversationPath,
  shouldRefreshConversationDetail,
} from '../../utils/chatMessages';

const apiConditionLabel: Record<ItemResponse['condition'], string> = {
  NEW: 'Mới',
  LIKE_NEW: 'Như mới',
  GOOD: 'Còn tốt',
  DEFECTIVE: 'Có lỗi',
};

const disputeReasonLabels: Record<ReportReason, string> = {
  NOT_AS_DESCRIBED: 'Không đúng mô tả',
  COUNTERFEIT: 'Hàng giả hoặc hàng nhái',
  PROHIBITED_ITEM: 'Mặt hàng bị cấm',
  NO_SHOW: 'Không đến theo lịch hẹn',
  WRONG_CONTACT_INFO: 'Thông tin liên hệ không đúng',
  HARASSMENT: 'Quấy rối',
  OTHER: 'Lý do khác',
};

const disputeErrorLabels: Record<string, string> = {
  VALIDATION_FAILED: 'Nội dung chưa hợp lệ. Vui lòng kiểm tra lý do, ghi chú và số lượng minh chứng.',
  MEDIA_INVALID_FILE: 'Danh sách minh chứng có tệp không hợp lệ hoặc bị trùng.',
  UNAUTHORIZED: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  DISPUTE_NOT_A_PARTY: 'Bạn không phải là một bên của giao dịch này.',
  DISPUTE_NOT_RESPONDENT: 'Chỉ người bị khiếu nại mới có thể gửi giải trình.',
  DISPUTE_REQUEST_NOT_COMPLETED: 'Giao dịch chưa hoàn tất nên chưa thể khiếu nại.',
  DISPUTE_WINDOW_EXPIRED: 'Đã quá thời hạn gửi khiếu nại.',
  DISPUTE_RESPONSE_WINDOW_EXPIRED: 'Đã quá thời hạn gửi giải trình.',
  DISPUTE_NOT_FOUND: 'Không tìm thấy khiếu nại này.',
  DISPUTE_ALREADY_CONCLUDED: 'Khiếu nại đã được quản trị viên kết luận.',
  DISPUTE_ALREADY_RESPONDED: 'Bạn đã gửi giải trình cho khiếu nại này.',
  REPORT_TARGET_NOT_FOUND: 'Không tìm thấy giao dịch cần khiếu nại.',
  REPORT_TARGET_REQUIRED: 'Chưa chọn đối tượng cần báo cáo.',
  REPORT_CANNOT_REPORT_SELF: 'Bạn không thể tự báo cáo chính mình hoặc bài đăng của mình.',
  REPORT_NOT_PARTICIPANT: 'Bạn không phải là bên liên quan của khiếu nại.',
  REPORT_RESPONSE_NOT_ALLOWED: 'Khiếu nại này không còn nhận giải trình.',
  REPORT_ALREADY_REPORTED: 'Bạn đã gửi khiếu nại cho giao dịch này.',
  REPORT_NOT_FOUND: 'Không tìm thấy báo cáo hoặc khiếu nại.',
  REPORT_ALREADY_CONCLUDED: 'Báo cáo hoặc khiếu nại đã được kết luận.',
  MEDIA_NOT_OWNER: 'Có minh chứng không thuộc quyền sở hữu của bạn.',
  MEDIA_NOT_FOUND: 'Không tìm thấy một hoặc nhiều minh chứng đã tải lên.',
  DISPUTE_INVALID_EVIDENCE: 'Minh chứng không hợp lệ hoặc không thuộc quyền sở hữu của bạn.',
};

function disputeErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.code && disputeErrorLabels[apiError.code]) return disputeErrorLabels[apiError.code];
  if (apiError.status === 401) return disputeErrorLabels.UNAUTHORIZED;
  if (apiError.status === 0) return 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';
  return 'Không thể gửi thông tin lúc này. Vui lòng thử lại.';
}

const NOT_AS_DESCRIBED_REPORTED_NOTICE = 'Đã gửi báo cáo món đồ không đúng mô tả. Admin sẽ xem xét.';

export function Messages() {
  const user = useAppSelector(selectCurrentUser)!;
  const backendUserId = toBackendUserId(user.id);
  if (!backendUserId) {
    return (
      <div className="page-shell">
        <EmptyState icon="messages" title="Chưa đăng nhập" text="Vui lòng đăng nhập lại để xem tin nhắn." />
      </div>
    );
  }
  return <ApiMessages currentUserId={backendUserId} />;
}

export interface ApiConversation {
  request: RequestResponse;
  item?: ItemResponse;
  offeredItem?: ItemResponse;
}

function requestStatusClass(status: RequestResponse['status']) {
  if (status === 'REJECTED' || status === 'CANCELLED' || status === 'DISPUTED') {
    return 'bg-red-50 text-error';
  }
  if (status === 'COMPLETED') return 'bg-emerald-50 text-success';
  if (status === 'RESERVED') return 'bg-primary-soft text-primary';
  return 'bg-amber-50 text-warning';
}

// Chủ bài lấy từ yêu cầu trước, vì bài đã giữ chỗ hoặc đã trao đổi không còn tải được qua API công khai.
function donorIdOf(conversation: ApiConversation) {
  return conversation.request.donorId ?? conversation.item?.donorId;
}

function requestParticipantName(conversation: ApiConversation, currentUserId: number) {
  if (donorIdOf(conversation) === currentUserId) {
    return conversation.request.receiverName?.trim() || 'Người gửi yêu cầu';
  }
  return conversation.request.donorName?.trim() || 'Chủ bài đăng';
}

function avatarInitials(name: string) {
  if (name === 'Chủ bài đăng') return '';
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0])
    .join('')
    .toUpperCase();
}

function canSelectRequest(
  conversation: ApiConversation,
  conversations: ApiConversation[],
  currentUserId: number,
) {
  return canSelectPendingRequest(
    conversation.request.status,
    donorIdOf(conversation),
    currentUserId,
    conversations
      .filter((entry) => entry.request.itemId === conversation.request.itemId)
      .map((entry) => entry.request.status),
  );
}

function mergeConversationRequests(
  current: ApiConversation[],
  refreshedRequests: RequestResponse[],
) {
  const requestById = new Map(refreshedRequests.map((request) => [request.id, request]));
  return current.map((conversation) => {
    const refreshed = requestById.get(conversation.request.id);
    return refreshed
      ? { ...conversation, request: { ...conversation.request, ...refreshed } }
      : conversation;
  });
}

export function RequestDetailsTabs({
  activeTab,
  onChange,
}: {
  activeTab: 'info' | 'schedule';
  onChange: (tab: 'info' | 'schedule') => void;
}) {
  return (
    <div className="grid grid-cols-2 gap-1 rounded-md bg-surface-low p-1" role="tablist" aria-label="Chi tiết yêu cầu">
      {([
        ['info', 'Thông tin'],
        ['schedule', 'Lịch giao nhận'],
      ] as const).map(([value, label]) => (
        <button
          key={value}
          type="button"
          role="tab"
          aria-selected={activeTab === value}
          onClick={() => onChange(value)}
          className={`rounded px-3 py-2 text-xs font-semibold transition ${
            activeTab === value
              ? 'bg-white text-primary shadow-sm ring-1 ring-border/70'
              : 'text-text-muted hover:text-text-primary'
          }`}
        >
          {label}
        </button>
      ))}
    </div>
  );
}

// Khi chưa có tiêu đề món đồ thì gọi theo loại yêu cầu, không in id kỹ thuật ra màn hình.
function requestFallbackTitle(request: RequestResponse) {
  return request.type === 'SWAP' ? 'Yêu cầu trao đổi' : 'Yêu cầu nhận đồ';
}

function conversationTitle(conversation: ApiConversation) {
  return (
    conversation.item?.title ??
    (conversation.request.itemTitle?.trim() || requestFallbackTitle(conversation.request))
  );
}

function ApiMessages({ currentUserId }: { currentUserId: number }) {
  const navigate = useNavigate();
  const { requestId, disputeId } = useParams();
  const requestedId = requestId ? Number(requestId) : null;
  const responseDisputeId = disputeId ? Number(disputeId) : null;
  const [conversations, setConversations] = useState<ApiConversation[]>([]);
  const [selectedRequestId, setSelectedRequestId] = useState<number | null>(null);
  const [messages, setMessages] = useState<MessageResponse[]>([]);
  const [message, setMessage] = useState('');
  const [chatImage, setChatImage] = useState<File | null>(null);
  const [loadingConversations, setLoadingConversations] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [conversationError, setConversationError] = useState('');
  const [messageError, setMessageError] = useState('');
  const [conversationReloadKey, setConversationReloadKey] = useState(0);
  const [messageReloadKey, setMessageReloadKey] = useState(0);
  const [sending, setSending] = useState(false);
  const [selectCandidateId, setSelectCandidateId] = useState<number | null>(null);
  const [selectSubmitting, setSelectSubmitting] = useState(false);
  const [selectError, setSelectError] = useState('');
  const [mobileChat, setMobileChat] = useState(false);
  const [conversationQuery, setConversationQuery] = useState('');
  const [detailsTab, setDetailsTab] = useState<'info' | 'schedule'>('info');
  const [rightPanelCollapsed, setRightPanelCollapsed] = useState(false);
  const [mobileDetailsOpen, setMobileDetailsOpen] = useState(false);
  const [deliveryMethod, setDeliveryMethod] = useState<'IN_PERSON' | 'SHIPPING'>('IN_PERSON');
  const [meetingPlace, setMeetingPlace] = useState('');
  const [meetingTime, setMeetingTime] = useState('');
  const [scheduleNow, setScheduleNow] = useState(() => new Date());
  const [agreeToWaiver, setAgreeToWaiver] = useState(false);
  const [asDescribed, setAsDescribed] = useState('');
  const [notAsDescribedReason, setNotAsDescribedReason] = useState('');
  const [logisticsLoading, setLogisticsLoading] = useState(false);
  const [logisticsSubmitting, setLogisticsSubmitting] = useState(false);
  const [logisticsError, setLogisticsError] = useState('');
  const [logisticsNotice, setLogisticsNotice] = useState('');
  const [logisticsReloadKey, setLogisticsReloadKey] = useState(0);
  const [contact, setContact] = useState<RequestContactResponse | null>(null);
  const [reportOpen, setReportOpen] = useState(false);
  const [reportNotice, setReportNotice] = useState('');
  const [pinnedItems, setPinnedItems] = useState<PinnedItemResponse[]>([]);
  const [pinnedItemsLoading, setPinnedItemsLoading] = useState(false);
  const [pinnedItemsError, setPinnedItemsError] = useState('');
  const [pinnedItemsReloadKey, setPinnedItemsReloadKey] = useState(0);
  const [shareItemId, setShareItemId] = useState('');
  const [shareableItems, setShareableItems] = useState<ItemResponse[]>([]);
  const [disputeOpen, setDisputeOpen] = useState(false);
  const [disputeReason, setDisputeReason] = useState<ReportReason | ''>('');
  const [disputeNote, setDisputeNote] = useState('');
  const [disputeEvidence, setDisputeEvidence] = useState<File[]>([]);
  const [disputeSubmitting, setDisputeSubmitting] = useState(false);
  const [disputeError, setDisputeError] = useState('');
  const [disputeNotice, setDisputeNotice] = useState('');
  const [responseNote, setResponseNote] = useState('');
  const [responseEvidence, setResponseEvidence] = useState<File[]>([]);
  const [responseSubmitting, setResponseSubmitting] = useState(false);
  const [responseError, setResponseError] = useState('');
  const [responseNotice, setResponseNotice] = useState('');
  const [requestMedia, setRequestMedia] = useState<RequestMediaResponse[]>([]);
  const [requestMediaFiles, setRequestMediaFiles] = useState<File[]>([]);
  const [requestMediaStage, setRequestMediaStage] = useState<RequestMediaStage>('HANDOVER');
  const [requestMediaLoading, setRequestMediaLoading] = useState(false);
  const [requestMediaSubmitting, setRequestMediaSubmitting] = useState(false);
  const [requestMediaError, setRequestMediaError] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const chatImageInputRef = useRef<HTMLInputElement>(null);
  const activeRequestId = useRef<number | null>(null);
  const selectInFlight = useRef(false);
  const sendInFlight = useRef(false);
  const requestGroupsLoadedAt = useRef(new Map<number, number>());

  useEffect(() => {
    const intervalId = window.setInterval(() => setScheduleNow(new Date()), 30_000);
    return () => window.clearInterval(intervalId);
  }, []);

  useEffect(() => {
    let active = true;
    setLoadingConversations(true);
    setConversationError('');

    Promise.all([requestApi.mine(currentUserId), itemApi.mine()])
      .then(async ([sentRequests, ownedItems]) => {
        if (!active) return;
        setShareableItems(ownedItems.filter((item) => item.status === 'APPROVED'));
        const receivedGroups = await Promise.all(ownedItems.map((item) => requestApi.forItem(item.id, currentUserId)));
        const loadedAt = Date.now();
        ownedItems.forEach((item) => requestGroupsLoadedAt.current.set(item.id, loadedAt));
        const requestById = new Map<number, RequestResponse>();
        [...sentRequests, ...receivedGroups.flat()].forEach((request) => {
          requestById.set(request.id, request);
        });
        if (requestedId !== null) {
          if (!Number.isSafeInteger(requestedId) || requestedId <= 0) throw new Error('Invalid request');
          const detail = await requestApi.get(requestedId, currentUserId);
          requestById.set(detail.id, detail);
        }

        const itemById = new Map(ownedItems.map((item) => [item.id, item]));
        const missingItemIds = [...new Set(
          [...requestById.values()]
            .flatMap((request) => [request.itemId, request.offeredItemId])
            .filter((itemId): itemId is number => itemId !== undefined && !itemById.has(itemId)),
        )];
        const missingItems = await Promise.allSettled(missingItemIds.map((id) => itemApi.detail(id)));
        missingItems.forEach((result) => {
          if (result.status === 'fulfilled') itemById.set(result.value.id, result.value);
        });

        const nextConversations = [...requestById.values()]
          .map((request) => ({
            request,
            item: itemById.get(request.itemId),
            offeredItem: request.offeredItemId ? itemById.get(request.offeredItemId) : undefined,
          }))
          .sort((a, b) => b.request.createdAt.localeCompare(a.request.createdAt));
        if (!active) return;
        setConversations(nextConversations);
        setSelectedRequestId((current) => requestedId ?? (current && nextConversations.some(({ request }) => request.id === current) ? current : (nextConversations[0]?.request.id ?? null)));
        if (requestedId !== null) setMobileChat(true);
      })
      .catch(() => {
        if (!active) return;
        setConversations([]);
        setSelectedRequestId(null);
        setConversationError('Không tải được danh sách cuộc trò chuyện.');
      })
      .finally(() => {
        if (active) setLoadingConversations(false);
      });

    return () => {
      active = false;
    };
  }, [conversationReloadKey, currentUserId, requestedId]);

  const selected = conversations.find(({ request }) => request.id === selectedRequestId);
  const selectCandidate = conversations.find(({ request }) => request.id === selectCandidateId);
  const selectedParticipantName = selected ? requestParticipantName(selected, currentUserId) : '';
  const selectedItemTitle = selected ? (selected.item?.title ?? selected.request.itemTitle?.trim()) : undefined;
  const chatEnabled = selected ? canChat(selected.request.status) : false;
  const requestMediaEnabled = selected
    ? ['LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER', 'COMPLETED', 'DISPUTED'].includes(selected.request.status)
    : false;
  const selectedItemId = selected?.request.itemId;
  const selectedItemDonorId = selected ? donorIdOf(selected) : undefined;

  useEffect(() => {
    if (!selectedItemId || selectedItemDonorId !== currentUserId) return;
    const loadedAt = requestGroupsLoadedAt.current.get(selectedItemId);
    if (loadedAt && Date.now() - loadedAt < CHAT_POLL_INTERVAL_MS) return;
    let active = true;
    requestApi
      .forItem(selectedItemId, currentUserId)
      .then((requests) => {
        if (active) {
          requestGroupsLoadedAt.current.set(selectedItemId, Date.now());
          setConversations((current) => mergeConversationRequests(current, requests));
        }
      })
      .catch(() => {
        // Danh sách hiện tại vẫn dùng được; Backend sẽ kiểm tra lại khi xác nhận chọn.
      });
    return () => {
      active = false;
    };
  }, [currentUserId, selectedItemDonorId, selectedItemId, selectedRequestId]);

  useEffect(() => {
    setRequestMedia([]);
    setRequestMediaFiles([]);
    setRequestMediaError('');
    setRequestMediaStage(selected?.request.status === 'DISPUTED' ? 'DISPUTE' : 'HANDOVER');
    if (selectedRequestId === null || !requestMediaEnabled) return;
    let active = true;
    setRequestMediaLoading(true);
    requestApi.media(selectedRequestId)
      .then((response) => {
        if (active) setRequestMedia(response);
      })
      .catch((error: ApiError) => {
        if (active) setRequestMediaError(error.message || 'Không tải được ảnh minh chứng giao nhận.');
      })
      .finally(() => {
        if (active) setRequestMediaLoading(false);
      });
    return () => {
      active = false;
    };
  }, [requestMediaEnabled, selected?.request.status, selectedRequestId]);

  useEffect(() => {
    activeRequestId.current = selectedRequestId;
    setMessages([]);
    setMessage('');
    setChatImage(null);
    if (chatImageInputRef.current) chatImageInputRef.current.value = '';
    setShareItemId('');
    if (selectedRequestId === null || !chatEnabled) {
      setMessages([]);
      setMessageError('');
      setLoadingMessages(false);
      return;
    }

    let active = true;
    let firstLoad = true;
    let pollingId: number | undefined;
    let afterId: number | undefined;
    let pollCount = 1;
    const loadMessages = async () => {
      if (document.visibilityState === 'hidden') {
        pollingId = window.setTimeout(loadMessages, CHAT_POLL_INTERVAL_MS);
        return;
      }
      if (firstLoad) setLoadingMessages(true);
      try {
        const shouldRefreshDetail = shouldRefreshConversationDetail(pollCount);
        pollCount += 1;
        const [response, detail] = await Promise.all([
          chatApi.list(selectedRequestId, afterId),
          shouldRefreshDetail
            ? requestApi.get(selectedRequestId, currentUserId).catch(() => null)
            : Promise.resolve(null),
        ]);
        if (!active) return;
        if (detail) {
          setConversations((current) => current.map((entry) => entry.request.id === detail.id ? { ...entry, request: detail } : entry));
        }
        if (response.length) afterId = Math.max(afterId ?? 0, ...response.map((entry) => entry.id));
        setMessages((current) => mergeChatMessages(current, response));
        setMessageError('');
      } catch {
        if (active) setMessageError('Không tải được tin nhắn. Vui lòng thử lại.');
      } finally {
        if (active && firstLoad) setLoadingMessages(false);
        firstLoad = false;
        if (active) pollingId = window.setTimeout(loadMessages, CHAT_POLL_INTERVAL_MS);
      }
    };

    void loadMessages();
    return () => {
      active = false;
      if (pollingId !== undefined) window.clearTimeout(pollingId);
    };
  }, [chatEnabled, currentUserId, messageReloadKey, selectedRequestId]);

  useEffect(() => {
    if (selectedRequestId === null || !chatEnabled) {
      setPinnedItems([]);
      setPinnedItemsLoading(false);
      setPinnedItemsError('');
      return;
    }
    let active = true;
    setPinnedItems([]);
    setPinnedItemsLoading(true);
    setPinnedItemsError('');
    chatApi
      .pinnedItems(selectedRequestId)
      .then((response) => {
        if (active) setPinnedItems(response);
      })
      .catch((error: ApiError) => {
        if (active) {
          setPinnedItems([]);
          setPinnedItemsError(error.message || 'Không tải được thẻ sản phẩm ghim.');
        }
      })
      .finally(() => {
        if (active) setPinnedItemsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [chatEnabled, pinnedItemsReloadKey, selectedRequestId]);

  useEffect(() => {
    if (selectedRequestId === null) return;
    let active = true;
    setLogisticsLoading(true);
    setLogisticsError('');
    setLogisticsNotice('');
    setContact(null);
    setAsDescribed('');
    setNotAsDescribedReason('');
    requestApi
      .get(selectedRequestId, currentUserId)
      .then((detail) => {
        if (!active) return;
        setConversations((current) => current.map((entry) => entry.request.id === detail.id ? { ...entry, request: detail } : entry));
        setDeliveryMethod(detail.deliveryMethod === 'SHIPPING' ? 'SHIPPING' : 'IN_PERSON');
        setMeetingPlace(detail.meetingPlace ?? '');
        setMeetingTime(detail.meetingTime ? toLocalMeetingTimeValue(detail.meetingTime) : '');
        setAgreeToWaiver(false);
      })
      .catch((error: ApiError) => {
        if (active) setLogisticsError(error.message || 'Không tải được thông tin giao nhận.');
      })
      .finally(() => {
        if (active) setLogisticsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [currentUserId, logisticsReloadKey, selectedRequestId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length, selectedRequestId]);

  const selectedMeetingTime = selected?.request.meetingTime;
  const selectedMeetingPlace = selected?.request.meetingPlace;
  const selectedDeliveryMethod = selected?.request.deliveryMethod;
  useEffect(() => {
    setMeetingTime(selectedMeetingTime ? toLocalMeetingTimeValue(selectedMeetingTime) : '');
    setMeetingPlace(selectedMeetingPlace ?? '');
    setDeliveryMethod(selectedDeliveryMethod === 'SHIPPING' ? 'SHIPPING' : 'IN_PERSON');
    setAgreeToWaiver(false);
  }, [selectedMeetingTime, selectedMeetingPlace, selectedDeliveryMethod]);

  const selectedDonorId = selected ? donorIdOf(selected) : undefined;
  const selectedAsDonor = selectedDonorId === currentUserId;
  const reportTargetUserId = selectedDonorId
    ? selectedDonorId === currentUserId
      ? selected!.request.receiverId
      : selectedDonorId
    : null;
  const currentPartyConfirmedAt = selected
    ? selectedAsDonor
      ? selected.request.donorScheduleOkAt
      : selected.request.receiverScheduleOkAt
    : undefined;
  const currentHandoverAt = selected
    ? selectedAsDonor
      ? selected.request.donorHandoverAt
      : selected.request.receiverHandoverAt
    : undefined;

  const updateSelectedRequest = (request: RequestResponse) => {
    setConversations((current) => current.map((entry) => entry.request.id === request.id ? { ...entry, request } : entry));
  };

  const confirmRequestSelection = async () => {
    if (
      !selectCandidate ||
      selectInFlight.current ||
      !canSelectRequest(selectCandidate, conversations, currentUserId)
    ) return;

    selectInFlight.current = true;
    setSelectSubmitting(true);
    setSelectError('');
    try {
      const refreshedRequests = await requestApi.forItem(selectCandidate.request.itemId, currentUserId);
      setConversations((current) => mergeConversationRequests(current, refreshedRequests));
      const refreshedCandidate = refreshedRequests.find((request) => request.id === selectCandidate.request.id);
      const canStillSelect = Boolean(
        refreshedCandidate &&
        canSelectPendingRequest(
          refreshedCandidate.status,
          donorIdOf(selectCandidate),
          currentUserId,
          refreshedRequests.map((request) => request.status),
        ),
      );
      if (!canStillSelect) {
        setSelectError('Bài đăng đã có người được chọn hoặc yêu cầu này không còn khả dụng. Danh sách đã được cập nhật.');
        return;
      }

      const updated = await requestApi.select(selectCandidate.request.id, currentUserId);
      updateSelectedRequest({ ...selectCandidate.request, ...updated });
      setSelectedRequestId(updated.id);
      setMobileChat(true);
      setSelectCandidateId(null);
      navigate(`/requests/${updated.id}`, { replace: true });
      setConversationReloadKey((key) => key + 1);
    } catch (error) {
      const apiError = error as ApiError;
      const errorMessages: Record<string, string> = {
        REQUEST_FORBIDDEN: 'Bạn không có quyền chọn người cho bài đăng này.',
        REQUEST_NOT_FOUND: 'Không tìm thấy yêu cầu này.',
        REQUEST_ITEM_NOT_FOUND: 'Không tìm thấy bài đăng liên quan.',
        REQUEST_INVALID_STATE_TRANSITION: 'Bài đăng đã có người được chọn hoặc yêu cầu này không còn khả dụng. Danh sách đã được cập nhật.',
      };
      setSelectError(errorMessages[apiError.code] || 'Không thể chọn người này. Vui lòng tải lại danh sách và thử lại.');
      if (apiError.status === 409 || apiError.status === 404) {
        setConversationReloadKey((key) => key + 1);
      }
    } finally {
      selectInFlight.current = false;
      setSelectSubmitting(false);
    }
  };

  const saveSchedule = async () => {
    if (!selected || logisticsSubmitting || !canSchedule(selected.request.status) || selected.request.contactRevealedAt) return;
    const now = new Date();
    const placeError = validateMeetingPlace(meetingPlace);
    const timeError = validateLocalMeetingTime(meetingTime, now);
    const parsedMeetingTime = parseLocalMeetingTime(meetingTime);
    if (placeError || timeError || !parsedMeetingTime) {
      setScheduleNow(now);
      setLogisticsError(placeError || timeError || 'Thời gian giao nhận không hợp lệ.');
      return;
    }
    setLogisticsSubmitting(true);
    setLogisticsError('');
    try {
      const request = await requestApi.updateSchedule(selected.request.id, currentUserId, {
        deliveryMethod,
        meetingPlace: meetingPlace.trim(),
        meetingTime: parsedMeetingTime.toISOString(),
        agreeToWaiver,
      });
      updateSelectedRequest(request);
      setAgreeToWaiver(false);
    } catch (error) {
      setLogisticsError(scheduleApiErrorMessage(error, 'Không cập nhật được lịch giao nhận.'));
    } finally {
      setLogisticsSubmitting(false);
    }
  };

  const confirmLogistics = async () => {
    if (!selected || logisticsSubmitting || selected.request.status !== 'AWAITING_LOGISTICS') return;
    const now = new Date();
    const placeError = validateMeetingPlace(meetingPlace);
    const timeError = validateLocalMeetingTime(meetingTime, now);
    const storedTimeError = validateStoredMeetingTime(selected.request.meetingTime, now);
    const savedMeetingTime = selected.request.meetingTime
      ? toLocalMeetingTimeValue(selected.request.meetingTime)
      : '';
    const hasUnsavedSchedule =
      meetingPlace.trim() !== (selected.request.meetingPlace ?? '').trim() ||
      meetingTime !== savedMeetingTime ||
      deliveryMethod !== selected.request.deliveryMethod;
    if (placeError || timeError || storedTimeError || !agreeToWaiver || hasUnsavedSchedule) {
      setScheduleNow(now);
      setLogisticsError(
        placeError ||
          timeError ||
          storedTimeError ||
          (!agreeToWaiver
            ? 'Vui lòng đồng ý cam kết trách nhiệm giao nhận.'
            : 'Thông tin lịch đã thay đổi. Vui lòng lưu lịch hẹn trước khi xác nhận.'),
      );
      return;
    }
    setLogisticsSubmitting(true);
    setLogisticsError('');
    try {
      const request = await requestApi.confirmLogistics(selected.request.id, currentUserId, { agreeToWaiver });
      updateSelectedRequest(request);
      setAgreeToWaiver(false);
    } catch (error) {
      setLogisticsError(scheduleApiErrorMessage(error, 'Không xác nhận được lịch giao nhận.'));
    } finally {
      setLogisticsSubmitting(false);
    }
  };

  const loadContact = async () => {
    if (!selected?.request.contactRevealedAt) return;
    setLogisticsSubmitting(true);
    setLogisticsError('');
    try {
      const response = await requestApi.getContact(selected.request.id, currentUserId);
      if (activeRequestId.current === selected.request.id) setContact(response);
    } catch (error) {
      const apiError = error as ApiError;
      if (activeRequestId.current === selected.request.id) setLogisticsError(apiError.message || 'Chưa thể xem thông tin liên lạc.');
    } finally {
      setLogisticsSubmitting(false);
    }
  };

  const confirmApiHandover = async () => {
    if (!selected || logisticsSubmitting || !canConfirmHandover(selected.request.status) || !selected.request.contactRevealedAt || currentHandoverAt) return;
    if (!selectedAsDonor && (!asDescribed || (asDescribed === 'no' && !notAsDescribedReason.trim()))) {
      setLogisticsError('Vui lòng đánh giá món đồ và nhập lý do nếu không đúng mô tả.');
      return;
    }
    setLogisticsSubmitting(true);
    setLogisticsError('');
    setLogisticsNotice('');
    // Ghi nhớ trước khi gửi vì ô chọn bị ẩn sau khi xác nhận xong
    const reportsNotAsDescribed = !selectedAsDonor && asDescribed === 'no';
    try {
      const request = await requestApi.confirmHandover(
        selected.request.id,
        currentUserId,
        selectedAsDonor ? {} : { asDescribed: asDescribed === 'yes', notAsDescribedReason: asDescribed === 'no' ? notAsDescribedReason.trim() : undefined },
      );
      updateSelectedRequest(request);
      // Backend tự mở báo cáo khi bên nhận chọn Không đúng mô tả (FR-85)
      if (reportsNotAsDescribed) setLogisticsNotice(NOT_AS_DESCRIBED_REPORTED_NOTICE);
    } catch (error) {
      const apiError = error as ApiError;
      setLogisticsError(apiError.message || 'Không xác nhận được việc bàn giao.');
    } finally {
      setLogisticsSubmitting(false);
    }
  };

  const cancelPendingRequest = async () => {
    if (!selected || logisticsSubmitting || selected.request.status !== 'PENDING') return;
    setLogisticsSubmitting(true);
    setLogisticsError('');
    try {
      updateSelectedRequest(await requestApi.cancel(selected.request.id, currentUserId));
    } catch (error) {
      setLogisticsError((error as ApiError).message || 'Không cập nhật được yêu cầu.');
    } finally {
      setLogisticsSubmitting(false);
    }
  };

  const send = async () => {
    const content = message.trim();
    if (
      (!content && !shareItemId && !chatImage) ||
      selectedRequestId === null ||
      !chatEnabled ||
      sendInFlight.current
    )
      return;
    const refItemId = shareItemId ? Number(shareItemId) : undefined;
    if (refItemId !== undefined && (!Number.isSafeInteger(refItemId) || refItemId <= 0)) {
      setMessageError('Mã bài chia sẻ phải là số nguyên dương.');
      return;
    }
    if (chatImage && shareItemId) {
      setMessageError('Chỉ gửi một ảnh hoặc một thẻ sản phẩm trong mỗi tin nhắn.');
      return;
    }
    if (chatImage && !requestMediaEnabled) {
      setMessageError('Chỉ được gửi ảnh sau khi hai bên đã chốt lịch hẹn.');
      return;
    }
    const layer1 = contactSafetyLayer1(content);
    if (!selected?.request.contactRevealedAt && layer1.blocked) {
      setMessageError('Tin nhắn có chứa thông tin liên hệ không được phép.');
      return;
    }

    sendInFlight.current = true;
    setSending(true);
    setMessageError('');
    try {
      if (!selected?.request.contactRevealedAt && hasAmbiguousContactSignal(content) && (await classifyAmbiguousContact(content))) {
        setMessageError('Tin nhắn có dấu hiệu chứa thông tin liên hệ không được phép.');
        return;
      }
      const uploaded = chatImage ? await mediaApi.upload(chatImage) : null;
      const sent = await chatApi.send(selectedRequestId, {
        content: content || null,
        mediaId: uploaded?.mediaId ?? null,
        refItemId: refItemId ?? null,
      });
      if (activeRequestId.current !== selectedRequestId) return;
      if (sent.moderation && sent.moderation !== 'PASS') {
        setMessageError(sent.blockedReason || 'Tin nhắn không vượt qua kiểm duyệt.');
        return;
      }
      setMessages((current) => (current.some((entry) => entry.id === sent.id) ? current : [...current, sent]));
      setMessage('');
      setChatImage(null);
      if (chatImageInputRef.current) chatImageInputRef.current.value = '';
      setShareItemId('');
    } catch (error) {
      const apiError = error as ApiError;
      if (activeRequestId.current !== selectedRequestId) return;
      setMessageError(apiError.message || 'Không gửi được tin nhắn. Vui lòng thử lại.');
    } finally {
      sendInFlight.current = false;
      setSending(false);
    }
  };

  const uploadEvidence = async (files: File[]) => {
    const uploaded = await Promise.all(files.map((file) => mediaApi.upload(file)));
    return uploaded.map((entry) => entry.mediaId);
  };

  const submitDispute = async () => {
    if (!selected || !disputeReason || disputeSubmitting || selected.request.status !== 'COMPLETED') return;
    setDisputeSubmitting(true);
    setDisputeError('');
    try {
      const evidenceMediaIds = disputeEvidence.length ? await uploadEvidence(disputeEvidence) : undefined;
      const response = await openDispute(selected.request.id, {
        reason: disputeReason,
        note: disputeNote.trim() || undefined,
        evidenceMediaIds,
      });
      setDisputeOpen(false);
      setDisputeReason('');
      setDisputeNote('');
      setDisputeEvidence([]);
      setDisputeNotice(
        `Khiếu nại của bạn đã được gửi. Hạn giải trình: ${new Date(response.responseDueAt).toLocaleString('vi-VN')}.`,
      );
      setConversationReloadKey((key) => key + 1);
    } catch (error) {
      setDisputeError(disputeErrorMessage(error));
    } finally {
      setDisputeSubmitting(false);
    }
  };

  const submitDisputeResponse = async () => {
    if (!responseDisputeId || !Number.isSafeInteger(responseDisputeId) || responseDisputeId <= 0 || !responseNote.trim() || responseSubmitting) return;
    setResponseSubmitting(true);
    setResponseError('');
    try {
      const evidenceMediaIds = responseEvidence.length ? await uploadEvidence(responseEvidence) : undefined;
      await respondDispute(responseDisputeId, { note: responseNote.trim(), evidenceMediaIds });
      setResponseNote('');
      setResponseEvidence([]);
      setResponseNotice('Giải trình đã được gửi đến quản trị viên.');
    } catch (error) {
      setResponseError(disputeErrorMessage(error));
    } finally {
      setResponseSubmitting(false);
    }
  };

  const attachRequestMedia = async () => {
    if (!selected || !requestMediaEnabled || !requestMediaFiles.length || requestMediaSubmitting) return;
    setRequestMediaSubmitting(true);
    setRequestMediaError('');
    try {
      const mediaIds = await uploadEvidence(requestMediaFiles);
      const response = await requestApi.attachMedia(selected.request.id, {
        mediaIds,
        stage: requestMediaStage,
      });
      setRequestMedia(response);
      setRequestMediaFiles([]);
    } catch (error) {
      const apiError = error as ApiError;
      const messages: Record<string, string> = {
        REQUEST_MEDIA_REQUIRED: 'Vui lòng chọn ít nhất một ảnh minh chứng.',
        REQUEST_MEDIA_STAGE_INVALID: 'Giai đoạn minh chứng không hợp lệ.',
        REQUEST_FORBIDDEN: 'Bạn không có quyền xem hoặc bổ sung minh chứng cho giao dịch này.',
        REQUEST_NOT_FOUND: 'Không tìm thấy giao dịch.',
        REQUEST_INVALID_STATE_TRANSITION: 'Giao dịch chưa đến giai đoạn được phép bổ sung minh chứng.',
        MEDIA_NOT_OWNER: 'Có ảnh không thuộc quyền sở hữu của bạn.',
        MEDIA_NOT_FOUND: 'Không tìm thấy một hoặc nhiều ảnh đã tải lên.',
      };
      setRequestMediaError(messages[apiError.code] || apiError.message || 'Không bổ sung được ảnh minh chứng.');
    } finally {
      setRequestMediaSubmitting(false);
    }
  };

  const meetingPlaceValidation = validateMeetingPlace(meetingPlace);
  const meetingTimeValidation = validateLocalMeetingTime(meetingTime, scheduleNow);
  const meetingFormValid = !meetingPlaceValidation && !meetingTimeValidation;
  const savedMeetingTimeValue = selected?.request.meetingTime
    ? toLocalMeetingTimeValue(selected.request.meetingTime)
    : '';
  const hasUnsavedSchedule = selected
    ? meetingPlace.trim() !== (selected.request.meetingPlace ?? '').trim() ||
      meetingTime !== savedMeetingTimeValue ||
      deliveryMethod !== selected.request.deliveryMethod
    : false;
  const savedScheduleValid = Boolean(
    selected &&
    !validateMeetingPlace(selected.request.meetingPlace ?? '') &&
    !validateStoredMeetingTime(selected.request.meetingTime, scheduleNow),
  );
  const canConfirmSchedule = Boolean(
    selected?.request.status === 'AWAITING_LOGISTICS' &&
    canConfirmMeetingSchedule({
      meetingPlace,
      localMeetingTime: meetingTime,
      storedMeetingTime: selected.request.meetingTime,
      agreed: agreeToWaiver,
      hasUnsavedChanges: hasUnsavedSchedule,
      now: scheduleNow,
    }) &&
    savedScheduleValid &&
    !currentPartyConfirmedAt &&
    !logisticsSubmitting,
  );
  const normalizedConversationQuery = conversationQuery.trim().toLocaleLowerCase('vi-VN');
  const filteredConversations = normalizedConversationQuery
    ? conversations.filter((conversation) => {
        const searchable = [
          conversationTitle(conversation),
          requestParticipantName(conversation, currentUserId),
          requestStatusLabel[conversation.request.status],
          conversation.request.type,
        ].join(' ').toLocaleLowerCase('vi-VN');
        return searchable.includes(normalizedConversationQuery);
      })
    : conversations;

  return (
    <div className="page-shell py-5 sm:py-7">
      <div className="overflow-hidden rounded-xl bg-white shadow-md ring-1 ring-border/80 lg:grid lg:h-[calc(100dvh-140px)] lg:min-h-[680px] lg:grid-cols-[290px_minmax(0,1fr)]">
        <aside className={`${mobileChat ? 'hidden lg:flex' : 'flex'} min-h-0 flex-col overflow-hidden border-r border-border`}>
          <div className="flex h-16 items-center justify-between border-b border-border px-5">
            <h1 className="text-lg font-bold">Tin nhắn</h1>
            <span className="text-xs text-text-muted">{conversations.length} cuộc trò chuyện</span>
          </div>
          <div className="border-b border-border p-3">
            <SearchField
              aria-label="Tìm kiếm cuộc trò chuyện"
              placeholder="Tìm món đồ hoặc người liên quan"
              value={conversationQuery}
              onChange={(event) => setConversationQuery(event.target.value)}
            />
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto">
            {loadingConversations ? (
              <div className="p-5 text-sm text-text-muted">Đang tải cuộc trò chuyện...</div>
            ) : conversationError ? (
              <div className="space-y-3 p-5 text-sm text-error">
                <p>{conversationError}</p>
                <Button variant="outline" size="sm" onClick={() => setConversationReloadKey((key) => key + 1)}>
                  Thử tải lại
                </Button>
              </div>
            ) : filteredConversations.length ? (
              filteredConversations.map((conversation) => {
                const { request } = conversation;
                const participantName = requestParticipantName(conversation, currentUserId);
                const selectable = canSelectRequest(conversation, conversations, currentUserId);
                const openDetails = () => {
                  setSelectedRequestId(request.id);
                  setMobileChat(true);
                  setMobileDetailsOpen(false);
                  setReportOpen(false);
                  setReportNotice('');
                  setDisputeNotice('');
                  navigate(requestConversationPath(request.id));
                };
                return (
                  <article
                    key={request.id}
                    className={`border-b border-border/70 p-3 transition ${request.id === selectedRequestId ? 'bg-primary-faint ring-1 ring-inset ring-primary/25' : 'hover:bg-background'}`}
                  >
                    <button type="button" onClick={openDetails} className="flex w-full gap-3 text-left">
                      <Avatar
                        user={{ name: participantName, avatarInitials: avatarInitials(participantName), avatarUrl: undefined }}
                      />
                      <span className="min-w-0 flex-1">
                        <strong className="block truncate text-sm">{conversationTitle(conversation)}</strong>
                        <span className="mt-1 block truncate text-xs text-text-muted">{participantName}</span>
                        <span className="mt-2 flex items-center justify-between gap-2">
                          <span className={`rounded-sm px-2 py-1 text-[11px] font-semibold ${requestStatusClass(request.status)}`}>
                            {requestStatusLabel[request.status]}
                          </span>
                          <span className="shrink-0 text-[10px] text-text-muted">
                            {new Date(request.createdAt).toLocaleDateString('vi-VN')}
                          </span>
                        </span>
                      </span>
                    </button>
                    <div className="mt-2 flex flex-wrap justify-end gap-2">
                      <Button size="sm" variant="outline" onClick={openDetails}>Xem chi tiết</Button>
                      {selectable ? (
                        <Button
                          size="sm"
                          disabled={selectSubmitting}
                          onClick={() => {
                            setSelectError('');
                            setSelectCandidateId(request.id);
                          }}
                        >
                          Chọn người này
                        </Button>
                      ) : null}
                    </div>
                  </article>
                );
              })
            ) : (
              <div className="p-5">
                <EmptyState
                  title={conversationQuery ? 'Không tìm thấy cuộc trò chuyện' : 'Chưa có yêu cầu'}
                  text={conversationQuery ? 'Thử tìm bằng tên món đồ, người liên quan hoặc trạng thái khác.' : 'Các yêu cầu nhận hoặc trao đổi món đồ sẽ xuất hiện tại đây.'}
                />
              </div>
            )}
          </div>
        </aside>

        {selected ? (
          <section className={`${mobileChat ? 'flex' : 'hidden lg:flex'} min-h-[680px] flex-col overflow-hidden lg:min-h-0 xl:grid xl:grid-rows-[64px_auto_minmax(0,1fr)_auto] ${rightPanelCollapsed ? 'xl:grid-cols-[minmax(0,1fr)]' : 'xl:grid-cols-[minmax(0,1fr)_340px]'}`}>
            <header className="flex h-16 shrink-0 items-center gap-3 border-b border-border px-4 sm:px-5 xl:col-start-1 xl:row-start-1">
              <IconButton icon="arrow" label="Quay lại" className="rotate-180 lg:hidden" onClick={() => setMobileChat(false)} />
              <div className="min-w-0 flex-1">
                <h2 className="truncate text-sm font-bold">{conversationTitle(selected)}</h2>
                <p className="mt-1 truncate text-xs text-text-muted">Trao đổi với {selectedParticipantName}</p>
              </div>
              <span className="rounded-sm bg-primary-faint px-2.5 py-1 text-xs font-semibold text-primary">{requestStatusLabel[selected.request.status]}</span>
              <Button size="sm" variant="outline" className="xl:hidden" onClick={() => setMobileDetailsOpen(true)}>
                Chi tiết
              </Button>
              <Button size="sm" variant="ghost" className="hidden xl:inline-flex" onClick={() => setRightPanelCollapsed((current) => !current)}>
                {rightPanelCollapsed ? 'Mở thông tin' : 'Thu gọn'}
              </Button>
            </header>
            {mobileDetailsOpen ? (
              <button
                type="button"
                aria-label="Đóng bảng chi tiết"
                className="fixed inset-0 z-30 bg-black/30 xl:hidden"
                onClick={() => setMobileDetailsOpen(false)}
              />
            ) : null}
            <aside
              aria-label="Thông tin yêu cầu"
              className={`${mobileDetailsOpen ? 'fixed inset-x-3 bottom-3 top-20 z-40 flex' : 'hidden'} min-h-0 flex-col overflow-hidden rounded-xl border border-border bg-white shadow-xl xl:static xl:col-start-2 xl:row-span-4 xl:row-start-1 xl:rounded-none xl:border-y-0 xl:border-r-0 xl:border-l xl:shadow-none ${rightPanelCollapsed ? 'xl:hidden' : 'xl:flex'}`}
            >
              <div className="shrink-0 border-b border-border p-4">
                <div className="mb-3 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-[11px] font-semibold uppercase tracking-wide text-text-muted">{requestFallbackTitle(selected.request)}</p>
                    <h3 className="mt-1 truncate text-sm font-bold">{conversationTitle(selected)}</h3>
                  </div>
                  <button
                    type="button"
                    aria-label="Đóng bảng chi tiết"
                    className="grid size-9 shrink-0 place-items-center rounded-md text-text-muted hover:bg-surface-low"
                    onClick={() => {
                      setMobileDetailsOpen(false);
                      setRightPanelCollapsed(true);
                    }}
                  >
                    <Icon name="close" className="size-4" />
                  </button>
                </div>
                <RequestDetailsTabs activeTab={detailsTab} onChange={setDetailsTab} />
              </div>
              <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
              {detailsTab === 'info' ? (
                <div className="p-4">
              <div className="flex flex-wrap items-start gap-3">
                <Avatar
                  user={{
                    name: selectedParticipantName,
                    avatarInitials: avatarInitials(selectedParticipantName),
                    avatarUrl: undefined,
                  }}
                />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <strong className="text-sm">{requestFallbackTitle(selected.request)}</strong>
                    <TypeBadge type={selected.request.type === 'SWAP' ? 'trade' : 'gift'} />
                    <span className={`rounded-sm px-2 py-1 text-[11px] font-semibold ${requestStatusClass(selected.request.status)}`}>
                      {requestStatusLabel[selected.request.status]}
                    </span>
                  </div>
                  <p className="mt-1 text-xs text-text-muted">
                    Gửi lúc {new Date(selected.request.createdAt).toLocaleString('vi-VN')}
                  </p>
                  <p className="mt-2 text-sm leading-6 text-text-secondary">
                    {selected.request.type === 'GIVE'
                      ? (selectedItemTitle ? `Đề nghị nhận món “${selectedItemTitle}”.` : 'Đề nghị nhận món này.')
                      : (selectedItemTitle ? `Đề nghị trao đổi với món “${selectedItemTitle}”.` : 'Đề nghị trao đổi với món này.')}
                  </p>
                  <p className="mt-1 text-xs text-text-muted">Yêu cầu này chưa có nội dung ghi chú riêng.</p>
                </div>
              </div>
              {selected.request.type === 'SWAP' ? (
                selected.offeredItem ? (
                  <button
                    type="button"
                    onClick={() => navigate(`/items/${selected.offeredItem!.id}`)}
                    className="mt-3 flex w-full items-center gap-3 rounded-md bg-surface-low p-3 text-left transition hover:bg-primary-faint"
                  >
                    <ItemImage
                      src={selected.offeredItem.images[0]}
                      alt={selected.offeredItem.title}
                      className="size-16 shrink-0 rounded-md object-cover"
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block text-[11px] font-semibold uppercase tracking-wide text-text-muted">Món đề xuất trao đổi</span>
                      <strong className="mt-1 block truncate text-sm">{selected.offeredItem.title}</strong>
                      <span className="mt-1 line-clamp-2 block text-xs leading-5 text-text-muted">{selected.offeredItem.description}</span>
                      <span className="mt-1 block text-xs font-semibold text-text-secondary">
                        {apiConditionLabel[selected.offeredItem.condition]}
                      </span>
                    </span>
                  </button>
                ) : (
                  <p className="mt-3 rounded-md bg-surface-low p-3 text-sm text-text-secondary">
                    Món đề xuất trao đổi:{' '}
                    <strong className="text-text-primary">
                      {selected.request.offeredItemTitle ?? 'Không tải được chi tiết món đề xuất'}
                    </strong>
                  </p>
                )
              ) : null}
                  <div className="mt-4 grid gap-2">
                    <Button size="sm" variant="outline" onClick={() => navigate(`/items/${selected.request.itemId}`)}>
                      Xem chi tiết bài đăng
                    </Button>
                    {reportTargetUserId && reportTargetUserId !== currentUserId ? (
                      <Button size="sm" variant="ghost" icon="shield" onClick={() => setReportOpen(true)}>
                        Báo cáo người dùng
                      </Button>
                    ) : null}
                  </div>
                </div>
              ) : (
              <div className="p-4">
              {logisticsLoading ? (
                <p className="text-xs text-text-muted">Đang tải thông tin giao nhận...</p>
              ) : (
                <div className="space-y-3">
                  {selected.request.status === 'PENDING' ? (
                    <div className="flex items-center gap-3 text-sm">
                      <p>Yêu cầu đang chờ chủ bài chọn người nhận.</p>
                      {selectedAsDonor ? (
                        canSelectRequest(selected, conversations, currentUserId) ? (
                          <Button
                            size="sm"
                            disabled={selectSubmitting}
                            onClick={() => {
                              setSelectError('');
                              setSelectCandidateId(selected.request.id);
                            }}
                          >
                            Chọn người này
                          </Button>
                        ) : (
                          <span className="text-xs text-text-muted">Bài đăng đã có yêu cầu được chọn.</span>
                        )
                      ) : (
                        <Button size="sm" variant="outline" disabled={logisticsSubmitting} onClick={() => void cancelPendingRequest()}>Hủy yêu cầu</Button>
                      )}
                    </div>
                  ) : null}
                  {canConfirmHandover(selected.request.status) && !selectedAsDonor && !currentHandoverAt ? (
                    <div className="space-y-2">
                      <Select label="Món đồ có đúng mô tả không?" value={asDescribed} disabled={logisticsSubmitting} onChange={(event) => setAsDescribed(event.target.value)}>
                        <option value="">Chọn đánh giá</option>
                        <option value="yes">Đúng mô tả</option>
                        <option value="no">Không đúng mô tả</option>
                      </Select>
                      {asDescribed === 'no' ? <TextArea label="Lý do không đúng mô tả" value={notAsDescribedReason} disabled={logisticsSubmitting} onChange={(event) => setNotAsDescribedReason(event.target.value)} /> : null}
                    </div>
                  ) : null}
                  <div className="grid gap-2">
                    <Select
                      label="Hình thức"
                      value={deliveryMethod}
                      onChange={(event) => setDeliveryMethod(event.target.value as 'IN_PERSON' | 'SHIPPING')}
                      disabled={!canSchedule(selected.request.status) || Boolean(selected.request.contactRevealedAt) || logisticsSubmitting}
                    >
                      <option value="IN_PERSON">Gặp trực tiếp</option>
                      <option value="SHIPPING">Giao hàng</option>
                    </Select>
                    <Field
                      label="Địa điểm"
                      value={meetingPlace}
                      maxLength={255}
                      error={meetingPlace ? meetingPlaceValidation ?? undefined : undefined}
                      onChange={(event) => {
                        setMeetingPlace(event.target.value);
                        setLogisticsError('');
                      }}
                      disabled={!canSchedule(selected.request.status) || Boolean(selected.request.contactRevealedAt) || logisticsSubmitting}
                    />
                    <Field
                      label="Thời gian"
                      type="datetime-local"
                      value={meetingTime}
                      min={minimumMeetingTimeValue(scheduleNow)}
                      error={meetingTime ? meetingTimeValidation ?? undefined : undefined}
                      onChange={(event) => {
                        setMeetingTime(event.target.value);
                        setScheduleNow(new Date());
                        setLogisticsError('');
                      }}
                      disabled={!canSchedule(selected.request.status) || Boolean(selected.request.contactRevealedAt) || logisticsSubmitting}
                    />
                  </div>
                  <label className="flex items-start gap-2 text-xs text-text-secondary">
                    <input
                      type="checkbox"
                      checked={agreeToWaiver}
                      onChange={(event) => setAgreeToWaiver(event.target.checked)}
                      disabled={!canSchedule(selected.request.status) || Boolean(selected.request.contactRevealedAt) || logisticsSubmitting}
                    />
                    <span>Tôi đồng ý cam kết trách nhiệm giao nhận.</span>
                  </label>
                  {logisticsError ? (
                    <div className="flex items-center justify-between gap-3 text-xs text-error">
                      <p>{logisticsError}</p>
                      <button type="button" className="shrink-0 font-semibold underline" onClick={() => setLogisticsReloadKey((key) => key + 1)}>
                        Tải lại
                      </button>
                    </div>
                  ) : null}
                  {logisticsNotice ? <p className="text-xs font-semibold text-success">{logisticsNotice}</p> : null}
                  {contact ? (
                    <div className="rounded-md bg-primary-faint p-3 text-xs leading-5 text-text-secondary">
                      <strong className="text-text-primary">{contact.fullName}</strong>
                      <p>{contact.email}</p>
                      <p>{contact.phone}</p>
                    </div>
                  ) : null}
                  {requestMediaEnabled ? (
                    <div className="rounded-md border border-border bg-background p-3">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <strong className="text-sm">Minh chứng giao dịch</strong>
                        <span className="text-xs text-text-muted">
                          {requestMediaLoading ? 'Đang tải...' : `${requestMedia.length} ảnh`}
                        </span>
                      </div>
                      {requestMedia.length ? (
                        <div className="mt-3 flex gap-2 overflow-x-auto">
                          {requestMedia.map((entry) => (
                            <a
                              key={entry.mediaId}
                              href={resolveMediaUrl(entry.url)}
                              target="_blank"
                              rel="noreferrer"
                              className="shrink-0"
                              title={`${entry.stage === 'HANDOVER' ? 'Giao nhận' : 'Khiếu nại'} · ${new Date(entry.createdAt).toLocaleString('vi-VN')}`}
                            >
                              <ItemImage
                                src={entry.url}
                                alt="Ảnh minh chứng"
                                className="size-16 rounded-md object-cover"
                              />
                            </a>
                          ))}
                        </div>
                      ) : !requestMediaLoading ? (
                        <p className="mt-2 text-xs text-text-muted">Chưa có ảnh minh chứng.</p>
                      ) : null}
                      <div className="mt-3 grid gap-2">
                        <Select
                          label="Giai đoạn"
                          value={requestMediaStage}
                          disabled={requestMediaSubmitting}
                          onChange={(event) => setRequestMediaStage(event.target.value as RequestMediaStage)}
                        >
                          <option value="HANDOVER">Giao nhận</option>
                          <option value="DISPUTE">Khiếu nại</option>
                        </Select>
                        <Field
                          label="Bổ sung ảnh"
                          type="file"
                          accept="image/*"
                          multiple
                          disabled={requestMediaSubmitting}
                          onChange={(event) => setRequestMediaFiles(Array.from(event.target.files ?? []))}
                        />
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={requestMediaSubmitting || !requestMediaFiles.length}
                          onClick={() => void attachRequestMedia()}
                        >
                          {requestMediaSubmitting ? 'Đang tải...' : 'Thêm ảnh'}
                        </Button>
                      </div>
                      {requestMediaFiles.length ? (
                        <p className="mt-2 text-xs text-text-muted">Đã chọn {requestMediaFiles.length} ảnh.</p>
                      ) : null}
                      {requestMediaError ? <p className="mt-2 text-xs text-error">{requestMediaError}</p> : null}
                    </div>
                  ) : null}
                  <div className="flex flex-wrap gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={!canSchedule(selected.request.status) || Boolean(selected.request.contactRevealedAt) || !meetingFormValid || logisticsSubmitting}
                      onClick={() => void saveSchedule()}
                    >
                      Lưu lịch hẹn
                    </Button>
                    <Button
                      size="sm"
                      disabled={!canConfirmSchedule}
                      onClick={() => void confirmLogistics()}
                    >
                      {currentPartyConfirmedAt ? 'Đã xác nhận lịch' : 'Xác nhận lịch'}
                    </Button>
                    {selected.request.contactRevealedAt ? (
                      <Button size="sm" variant="ghost" disabled={logisticsSubmitting} onClick={() => void loadContact()}>
                        Xem liên hệ
                      </Button>
                    ) : null}
                    {canConfirmHandover(selected.request.status) && selected.request.contactRevealedAt ? (
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={Boolean(currentHandoverAt) || logisticsSubmitting}
                        onClick={() => void confirmApiHandover()}
                      >
                        {currentHandoverAt ? 'Đã xác nhận bàn giao' : 'Xác nhận bàn giao'}
                      </Button>
                    ) : null}
                    {selected.request.status === 'COMPLETED' ? (
                      <Button
                        size="sm"
                        variant="outline"
                        icon="shield"
                        onClick={() => {
                          setDisputeError('');
                          setDisputeNotice('');
                          setDisputeOpen(true);
                        }}
                      >
                        Gửi khiếu nại
                      </Button>
                    ) : null}
                  </div>
                </div>
              )}
              </div>
              )}
              </div>
            </aside>
            {reportNotice || disputeNotice ? (
              <div role="status" className="shrink-0 space-y-2 border-b border-border bg-emerald-50 px-4 py-3 text-xs text-success sm:px-5 xl:col-start-1 xl:row-start-2">
                {reportNotice ? <p>{reportNotice}</p> : null}
                {disputeNotice ? <p>{disputeNotice}</p> : null}
              </div>
            ) : null}
            <div className={`min-h-0 flex-1 space-y-3 overflow-y-auto bg-background px-4 pb-6 pt-5 sm:px-6 xl:col-start-1 ${reportNotice || disputeNotice ? 'xl:row-start-3' : 'xl:row-start-2 xl:row-span-2'}`}>
              {pinnedItemsLoading ? (
                <p className="text-center text-xs text-text-muted">Đang tải thẻ sản phẩm ghim...</p>
              ) : pinnedItemsError ? (
                <div className="flex items-center justify-center gap-3 text-xs text-error">
                  <p>{pinnedItemsError}</p>
                  <button
                    type="button"
                    className="shrink-0 font-semibold underline"
                    onClick={() => setPinnedItemsReloadKey((key) => key + 1)}
                  >
                    Thử tải lại
                  </button>
                </div>
              ) : !pinnedItems.length ? (
                <p className="text-center text-xs text-text-muted">Cuộc trò chuyện này không có sản phẩm ghim.</p>
              ) : null}
              {pinnedItems.length ? (
                <div className="mb-4 flex gap-3 overflow-x-auto rounded-lg bg-white p-3 ring-1 ring-border/70">
                  {pinnedItems.map((pinnedItem) => (
                    <button
                      key={pinnedItem.id}
                      type="button"
                      className="flex min-w-[240px] items-center gap-3 rounded-md p-2 text-left transition hover:bg-surface-low"
                      onClick={() => navigate(`/items/${pinnedItem.id}`)}
                    >
                      <ItemImage
                        src={pinnedItem.imageUrl ?? undefined}
                        alt={pinnedItem.title}
                        className="size-14 shrink-0 rounded-md object-cover"
                      />
                      <span className="min-w-0 flex-1">
                        <strong className="block truncate text-sm">{pinnedItem.title}</strong>
                        <span className="mt-1 flex flex-wrap gap-1.5">
                          <TypeBadge type={pinnedItem.offerType === 'GIVE' ? 'gift' : 'trade'} />
                          <StatusBadge status={pinnedItem.status} />
                        </span>
                      </span>
                    </button>
                  ))}
                </div>
              ) : null}
              {loadingMessages ? (
                <p className="text-center text-sm text-text-muted">Đang tải tin nhắn...</p>
              ) : messages.length ? (
                messages.map((entry) => {
                  const mine = entry.senderId === currentUserId;
                  const system = entry.kind.toUpperCase() === 'SYSTEM';
                  if (entry.moderation && entry.moderation !== 'PASS') {
                    return (
                      <div key={entry.id} className="flex justify-center">
                        <span className="max-w-xl rounded-md bg-red-50 px-3 py-2 text-center text-xs text-error">
                          {entry.blockedReason || 'Tin nhắn này không vượt qua kiểm duyệt.'}
                        </span>
                      </div>
                    );
                  }
                  if (system) {
                    return (
                      <div key={entry.id} className="flex justify-center">
                        <span className="rounded-md bg-surface-container px-3 py-1.5 text-center text-[11px] text-text-muted">{entry.content}</span>
                      </div>
                    );
                  }
                  return (
                    <div key={entry.id} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
                      <div className={`max-w-[82%] rounded-lg px-3.5 py-2.5 text-sm leading-6 sm:max-w-[70%] ${mine ? 'rounded-br-sm bg-primary text-white' : 'rounded-bl-sm bg-white text-text-primary ring-1 ring-border/70'}`}>
                        {entry.itemCard ? (
                          <button type="button" className="mb-2 flex w-full items-center gap-3 rounded-md bg-white p-3 text-left text-text-primary" onClick={() => navigate(`/items/${entry.itemCard!.id}`)}>
                            <ItemImage src={entry.itemCard.imageUrl ?? undefined} alt={entry.itemCard.title} className="size-14 rounded-md object-cover" />
                            <span className="min-w-0">
                              <strong className="block break-words">{entry.itemCard.title}</strong>
                              <span className="mt-1 flex flex-wrap gap-1.5">
                                <TypeBadge type={entry.itemCard.offerType === 'GIVE' ? 'gift' : 'trade'} />
                                <StatusBadge status={entry.itemCard.status} />
                              </span>
                            </span>
                          </button>
                        ) : null}
                        {entry.mediaId ? (
                          <a
                            href={resolveMediaUrl(`/api/v1/media/${entry.mediaId}`)}
                            target="_blank"
                            rel="noreferrer"
                            className="mb-2 block"
                          >
                            <img
                              src={resolveMediaUrl(`/api/v1/media/${entry.mediaId}`)}
                              alt="Ảnh trong cuộc trò chuyện"
                              className="max-h-72 max-w-full rounded-md object-contain"
                            />
                          </a>
                        ) : null}
                        {entry.content ? <p className="whitespace-pre-wrap break-words">{entry.content}</p> : null}
                        {messageOfferStatusLabel(entry.offerStatus) ? (
                          <span className={`mt-1.5 inline-flex rounded-sm px-2 py-0.5 text-[10px] font-semibold ${mine ? 'bg-white/15 text-white' : 'bg-primary-faint text-primary'}`}>
                            {messageOfferStatusLabel(entry.offerStatus)}
                          </span>
                        ) : null}
                        <span className={`mt-1 block text-[10px] ${mine ? 'text-white/65' : 'text-text-muted'}`}>{new Date(entry.createdAt).toLocaleString('vi-VN')}</span>
                      </div>
                    </div>
                  );
                })
              ) : (
                <EmptyState title={chatEnabled ? "Chưa có tin nhắn" : "Chưa mở chat"} text={chatEnabled ? "Hãy gửi lời chào để bắt đầu cuộc trò chuyện." : "Chat chỉ mở khi yêu cầu được chọn."} />
              )}
              <div ref={messagesEndRef} aria-hidden="true" />
            </div>
            <div className="shrink-0 border-t border-border bg-white p-3 sm:p-4 xl:col-start-1 xl:row-start-4">
              <Select label="Chia sẻ bài của bạn" value={shareItemId} disabled={sending || !chatEnabled || !!chatImage} onChange={(event) => setShareItemId(event.target.value)}>
                <option value="">Không đính kèm bài đăng</option>
                {shareableItems.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}
              </Select>
              <div className="mb-3 flex flex-wrap items-center gap-3 text-xs">
                <label className={`inline-flex cursor-pointer items-center gap-2 rounded-md border border-border px-3 py-2 font-semibold ${sending || !chatEnabled || !requestMediaEnabled || !!shareItemId ? 'pointer-events-none opacity-50' : 'hover:bg-surface-low'}`}>
                  <Icon name="image" className="size-4" />
                  Đính kèm ảnh
                  <input
                    ref={chatImageInputRef}
                    className="sr-only"
                    type="file"
                    accept="image/*"
                    disabled={sending || !chatEnabled || !requestMediaEnabled || !!shareItemId}
                    onChange={(event) => {
                      setChatImage(event.target.files?.[0] ?? null);
                      if (messageError) setMessageError('');
                    }}
                  />
                </label>
                {chatImage ? (
                  <span className="flex min-w-0 items-center gap-2 text-text-secondary">
                    <span className="max-w-56 truncate">{chatImage.name}</span>
                    <button
                      type="button"
                      className="font-semibold text-error underline"
                      disabled={sending}
                      onClick={() => {
                        setChatImage(null);
                        if (chatImageInputRef.current) chatImageInputRef.current.value = '';
                      }}
                    >
                      Bỏ ảnh
                    </button>
                  </span>
                ) : !requestMediaEnabled && chatEnabled ? (
                  <span className="text-text-muted">Có thể gửi ảnh sau khi hai bên chốt lịch hẹn.</span>
                ) : null}
              </div>
              {messageError ? (
                <div className="mb-2 flex items-center justify-between gap-3 text-xs text-error">
                  <p>{messageError}</p>
                  <button type="button" className="shrink-0 font-semibold underline" onClick={() => setMessageReloadKey((key) => key + 1)}>
                    Tải lại
                  </button>
                </div>
              ) : null}
              <form
                className="flex items-end gap-2"
                onSubmit={(event) => {
                  event.preventDefault();
                  void send();
                }}
              >
                <Field
                  className="h-11"
                  value={message}
                  maxLength={2000}
                  onChange={(event) => {
                    setMessage(event.target.value);
                    if (messageError) setMessageError('');
                  }}
                  placeholder="Nhập tin nhắn..."
                  disabled={sending || !chatEnabled}
                />
                <Button aria-label="Gửi tin nhắn" icon="send" disabled={sending || !chatEnabled || (!message.trim() && !shareItemId && !chatImage)}>
                  {sending ? 'Đang gửi' : 'Gửi'}
                </Button>
              </form>
            </div>
          </section>
        ) : (
          <section className="hidden min-h-[680px] place-items-center lg:grid lg:min-h-0">
            <EmptyState title="Chọn cuộc trò chuyện" text="Chọn một yêu cầu đã được chấp nhận để xem tin nhắn." />
          </section>
        )}
      </div>
      {selectCandidate ? (
        <SelectRequestDialog
          conversation={selectCandidate}
          participantName={requestParticipantName(selectCandidate, currentUserId)}
          submitting={selectSubmitting}
          error={selectError}
          onCancel={() => {
            if (selectSubmitting) return;
            setSelectCandidateId(null);
            setSelectError('');
          }}
          onConfirm={() => void confirmRequestSelection()}
        />
      ) : null}
      {reportOpen && selected && reportTargetUserId && reportTargetUserId !== currentUserId ? (
        <ReportDialog
          target={{ targetType: 'USER', targetUserId: reportTargetUserId, requestId: selected.request.id }}
          contextLabel="Báo cáo người dùng trong yêu cầu này"
          onClose={() => setReportOpen(false)}
          onSuccess={() => {
            setReportOpen(false);
            setReportNotice('Báo cáo đã được gửi đến quản trị viên để xem xét.');
          }}
        />
      ) : null}
      {disputeOpen && selected ? (
        <div className="fixed inset-0 z-40 grid place-items-end bg-black/30 sm:place-items-center sm:p-4" role="dialog" aria-modal="true">
          <div className="max-h-[92dvh] w-full overflow-y-auto rounded-t-xl bg-white p-5 shadow-lg sm:max-w-lg sm:rounded-xl sm:p-6">
            <h2 className="text-xl font-bold">Gửi khiếu nại giao dịch</h2>
            <p className="mt-1 text-sm text-text-muted">{conversationTitle(selected)}</p>
            <Select
              className="mt-5"
              label="Lý do"
              value={disputeReason}
              disabled={disputeSubmitting}
              onChange={(event) => setDisputeReason(event.target.value as ReportReason | '')}
            >
              <option value="">Chọn lý do khiếu nại</option>
              {Object.entries(disputeReasonLabels).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </Select>
            <TextArea
              className="mt-4"
              label="Nội dung (không bắt buộc)"
              value={disputeNote}
              maxLength={500}
              disabled={disputeSubmitting}
              onChange={(event) => setDisputeNote(event.target.value)}
            />
            <p className="mt-1 text-right text-xs text-text-muted">{disputeNote.length}/500</p>
            <Field
              className="mt-4"
              label="Minh chứng"
              type="file"
              accept="image/*"
              multiple
              disabled={disputeSubmitting}
              onChange={(event) => setDisputeEvidence(Array.from(event.target.files ?? []))}
            />
            {disputeEvidence.length ? (
              <p className="mt-2 text-xs text-text-muted">Đã chọn {disputeEvidence.length} ảnh.</p>
            ) : null}
            {disputeError ? <div className="mt-4 rounded-md bg-red-50 p-3 text-xs text-error">{disputeError}</div> : null}
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="ghost" disabled={disputeSubmitting} onClick={() => setDisputeOpen(false)}>Hủy</Button>
              <Button disabled={disputeSubmitting || !disputeReason} onClick={() => void submitDispute()}>
                {disputeSubmitting ? 'Đang gửi...' : 'Gửi khiếu nại'}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
      {responseDisputeId !== null ? (
        <div className="fixed inset-0 z-40 grid place-items-end bg-black/30 sm:place-items-center sm:p-4" role="dialog" aria-modal="true">
          <div className="max-h-[92dvh] w-full overflow-y-auto rounded-t-xl bg-white p-5 shadow-lg sm:max-w-lg sm:rounded-xl sm:p-6">
            <h2 className="text-xl font-bold">Giải trình khiếu nại</h2>
            <p className="mt-1 text-sm text-text-muted">Gửi nội dung giải trình cho khiếu nại liên quan đến bạn.</p>
            {responseNotice ? (
              <div className="mt-5 space-y-4">
                <div className="rounded-md bg-emerald-50 p-3 text-sm text-success">{responseNotice}</div>
                <div className="flex justify-end">
                  <Button onClick={() => navigate('/messages')}>Về trang Tin nhắn</Button>
                </div>
              </div>
            ) : (
              <>
                <TextArea
                  className="mt-5"
                  label="Nội dung giải trình"
                  value={responseNote}
                  maxLength={1000}
                  disabled={responseSubmitting}
                  onChange={(event) => setResponseNote(event.target.value)}
                />
                <p className="mt-1 text-right text-xs text-text-muted">{responseNote.length}/1000</p>
                <Field
                  className="mt-4"
                  label="Minh chứng"
                  type="file"
                  accept="image/*"
                  multiple
                  disabled={responseSubmitting}
                  onChange={(event) => setResponseEvidence(Array.from(event.target.files ?? []))}
                />
                {responseEvidence.length ? (
                  <p className="mt-2 text-xs text-text-muted">Đã chọn {responseEvidence.length} ảnh.</p>
                ) : null}
                {(!Number.isSafeInteger(responseDisputeId) || responseDisputeId <= 0) ? (
                  <div className="mt-4 rounded-md bg-red-50 p-3 text-xs text-error">Đường dẫn khiếu nại không hợp lệ.</div>
                ) : responseError ? (
                  <div className="mt-4 rounded-md bg-red-50 p-3 text-xs text-error">{responseError}</div>
                ) : null}
                <div className="mt-6 flex justify-end gap-2">
                  <Button variant="ghost" disabled={responseSubmitting} onClick={() => navigate('/messages')}>Hủy</Button>
                  <Button
                    disabled={responseSubmitting || !responseNote.trim() || !Number.isSafeInteger(responseDisputeId) || responseDisputeId <= 0}
                    onClick={() => void submitDisputeResponse()}
                  >
                    {responseSubmitting ? 'Đang gửi...' : 'Gửi giải trình'}
                  </Button>
                </div>
              </>
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}

export function SelectRequestDialog({
  conversation,
  participantName,
  submitting,
  error,
  onCancel,
  onConfirm,
}: {
  conversation: ApiConversation;
  participantName: string;
  submitting: boolean;
  error: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !submitting) onCancel();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onCancel, submitting]);

  return (
    <div className="fixed inset-0 z-50 grid place-items-end bg-black/30 sm:place-items-center sm:p-4" role="presentation">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="select-request-title"
        className="max-h-[92dvh] w-full overflow-y-auto rounded-t-xl bg-white p-5 shadow-lg sm:max-w-lg sm:rounded-xl sm:p-6"
      >
        <h2 id="select-request-title" className="text-xl font-bold">Xác nhận chọn người giao dịch</h2>
        <div className="mt-4 flex items-center gap-3 rounded-md bg-surface-low p-3">
          <Avatar user={{ name: participantName, avatarInitials: avatarInitials(participantName), avatarUrl: undefined }} />
          <div className="min-w-0 flex-1">
            <strong className="block truncate text-sm">{participantName}</strong>
            <p className="mt-1 text-xs text-text-muted">
              {requestFallbackTitle(conversation.request)}
            </p>
          </div>
        </div>
        <div className="mt-4 space-y-2 text-sm leading-6 text-text-secondary">
          <p>
            Bài đăng: <strong className="text-text-primary">{conversation.item?.title ?? conversation.request.itemTitle ?? 'Không xác định'}</strong>
          </p>
          <p>Gửi lúc: {new Date(conversation.request.createdAt).toLocaleString('vi-VN')}</p>
          {conversation.request.type === 'SWAP' ? (
            <p>
              Món đề xuất: <strong className="text-text-primary">{conversation.offeredItem?.title ?? conversation.request.offeredItemTitle ?? 'Không xác định'}</strong>
            </p>
          ) : null}
        </div>
        <p className="mt-4 rounded-md bg-amber-50 p-3 text-xs leading-5 text-warning">
          Khi bạn xác nhận chọn người này để giao dịch, yêu cầu sẽ được chấp nhận và các yêu cầu đang chờ khác của cùng bài đăng sẽ tự động bị từ chối.
        </p>
        {error ? <p role="alert" className="mt-4 rounded-md bg-red-50 p-3 text-sm text-error">{error}</p> : null}
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="ghost" autoFocus disabled={submitting} onClick={onCancel}>Hủy</Button>
          <Button disabled={submitting} onClick={onConfirm}>
            {submitting ? 'Đang chọn...' : 'Xác nhận chọn'}
          </Button>
        </div>
      </div>
    </div>
  );
}

