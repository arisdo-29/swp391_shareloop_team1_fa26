import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import {
  Alert,
  Avatar,
  Button,
  EmptyState,
  Field,
  Icon,
  ItemImage,
  SearchField,
  Select,
  SkeletonRows,
  StatusBadge,
  TextArea,
  TypeBadge,
} from '../../components/ui';
import { DialogFrame } from '../../components/Dialog';
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
import {
  canCancelTransaction,
  canChat,
  canViewChat,
  canConfirmHandover,
  canReportHandoverIssue,
  canSchedule,
  canOpenComplaint,
  canSelectPendingRequest,
  formatDeadline,
  isRequestUnread,
  markRequestChatSeen,
  requestStatusLabel,
} from '../../utils/request';
import { chatImageForbiddenMessage } from '../../utils/apiErrorMessages';
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
import { ReputationBadge } from '../../components/ReputationBadge';

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
  DISPUTE_WINDOW_EXPIRED: 'Đã quá thời hạn 24 giờ để khiếu nại giao dịch.',
  DISPUTE_RESPONSE_WINDOW_EXPIRED: 'Đã quá thời hạn 48 giờ để gửi giải trình.',
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
        <EmptyState title="Chưa đăng nhập" text="Vui lòng đăng nhập lại để xem tin nhắn." />
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

function requestStatusTextClass(status: RequestResponse['status']) {
  if (status === 'REJECTED' || status === 'CANCELLED' || status === 'DISPUTED') return 'text-error';
  if (status === 'COMPLETED') return 'text-success';
  if (status === 'PENDING') return 'text-text-muted';
  return 'text-secondary-hover';
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
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelSubmitting, setCancelSubmitting] = useState(false);
  const [cancelError, setCancelError] = useState('');
  const [issueOpen, setIssueOpen] = useState(false);
  const [issueNote, setIssueNote] = useState('');
  const [issueEvidence, setIssueEvidence] = useState<File[]>([]);
  const [issueSubmitting, setIssueSubmitting] = useState(false);
  const [issueError, setIssueError] = useState('');
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
  const isCancelled = selected?.request.status === 'CANCELLED';
  const chatViewable = selected ? canViewChat(selected.request.status) : false;
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
    if (selectedRequestId === null || !chatViewable) {
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
  }, [chatViewable, currentUserId, messageReloadKey, selectedRequestId]);

  useEffect(() => {
    if (selectedRequestId === null || !chatViewable) {
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
  }, [chatViewable, pinnedItemsReloadKey, selectedRequestId]);

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
  // Người nhận trong giao dịch Cho tặng không gửi ảnh được (backend trả CHAT_IMAGE_GIVE_RECEIVER_FORBIDDEN)
  const isGiftReceiver = selected?.request.type === 'GIVE' && !selectedAsDonor;
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

  const cancelReasonError = (error: unknown) => {
    const apiError = error as Partial<ApiError>;
    const messages: Record<string, string> = {
      REQUEST_FORBIDDEN: 'Bạn không phải một bên của giao dịch này.',
      REQUEST_NOT_FOUND: 'Không tìm thấy giao dịch này.',
      REQUEST_INVALID_STATE_TRANSITION: 'Giao dịch ở trạng thái hiện tại không thể huỷ hoặc đã thay đổi. Vui lòng tải lại.',
      VALIDATION_FAILED: 'Lý do huỷ phải từ 5 đến 500 ký tự.',
    };
    return (apiError.code && messages[apiError.code]) || 'Không huỷ được giao dịch lúc này. Vui lòng thử lại.';
  };

  const submitCancelTransaction = async () => {
    const reason = cancelReason.trim();
    if (!selected || cancelSubmitting || !canCancelTransaction(selected.request.status) || reason.length < 5) return;
    setCancelSubmitting(true);
    setCancelError('');
    try {
      const request = await requestApi.cancelTransaction(selected.request.id, currentUserId, reason);
      updateSelectedRequest(request);
      setCancelOpen(false);
      setCancelReason('');
      setLogisticsNotice('Đã huỷ giao dịch. Bài đăng được mở lại cho người khác.');
    } catch (error) {
      setCancelError(cancelReasonError(error));
      if ((error as ApiError).status === 409) setLogisticsReloadKey((key) => key + 1);
    } finally {
      setCancelSubmitting(false);
    }
  };

  const submitHandoverIssue = async () => {
    const note = issueNote.trim();
    if (!selected || issueSubmitting || note.length < 5) return;
    setIssueSubmitting(true);
    setIssueError('');
    try {
      const evidenceMediaIds = issueEvidence.length ? await uploadEvidence(issueEvidence) : undefined;
      const request = await requestApi.reportHandoverIssue(selected.request.id, currentUserId, { note, evidenceMediaIds });
      updateSelectedRequest(request);
      setIssueOpen(false);
      setIssueNote('');
      setIssueEvidence([]);
      setLogisticsNotice('Đã gửi báo cáo. Admin sẽ xem xét và báo kết quả cho hai bên.');
    } catch (error) {
      const apiError = error as ApiError;
      setIssueError(
        apiError.code === 'REQUEST_INVALID_STATE_TRANSITION'
          ? 'Chỉ báo được khi đối phương đã xác nhận bàn giao còn bạn thì chưa. Vui lòng tải lại.'
          : disputeErrorMessage(error),
      );
      if (apiError.status === 409) setLogisticsReloadKey((key) => key + 1);
    } finally {
      setIssueSubmitting(false);
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
    if (chatImage && isGiftReceiver) {
      setMessageError(chatImageForbiddenMessage('CHAT_IMAGE_GIVE_RECEIVER_FORBIDDEN') ?? '');
      return;
    }
    if (shareItemId && isGiftReceiver) {
      setMessageError('Người nhận đồ Cho tặng không thể chia sẻ bài đăng.');
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
      setMessageError(
        chatImageForbiddenMessage(apiError.code) || apiError.message || 'Không gửi được tin nhắn. Vui lòng thử lại.',
      );
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
        `Khiếu nại của bạn đã được gửi. Đối tác có 48 giờ để giải trình (hạn: ${new Date(response.responseDueAt).toLocaleString('vi-VN')}).`,
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

  const otherPartyConfirmedAt = selected
    ? selectedAsDonor
      ? selected.request.receiverScheduleOkAt
      : selected.request.donorScheduleOkAt
    : undefined;
  const otherPartyHandoverAt = selected
    ? selectedAsDonor
      ? selected.request.receiverHandoverAt
      : selected.request.donorHandoverAt
    : undefined;
  const scheduleLocked = !selected || !canSchedule(selected.request.status) || Boolean(selected.request.contactRevealedAt) || logisticsSubmitting;
  useEffect(() => {
    if (selectedRequestId) {
      markRequestChatSeen(selectedRequestId);
    }
  }, [selectedRequestId]);

  const openConversation = (requestId: number) => {
    setSelectedRequestId(requestId);
    markRequestChatSeen(requestId);
    setMobileChat(true);
    setMobileDetailsOpen(false);
    setReportOpen(false);
    setReportNotice('');
    setDisputeNotice('');
    navigate(requestConversationPath(requestId));
  };

  return (
    <div className="page-shell py-5 sm:py-7">
      <div className="grid gap-4 lg:h-[calc(100dvh-140px)] lg:min-h-[640px] lg:grid-cols-[280px_minmax(0,1fr)] xl:grid-cols-[280px_minmax(0,1fr)_360px]">
        <aside
          aria-label="Cuộc trò chuyện"
          className={`${mobileChat ? 'hidden lg:flex' : 'flex'} min-h-0 flex-col overflow-hidden rounded-lg bg-white ring-1 ring-border/80`}
        >
          <div className="px-4 pb-3 pt-4">
            <div className="flex items-baseline justify-between gap-3">
              <h1 className="text-xl font-bold">Tin nhắn</h1>
              <span className="text-xs text-text-muted">{conversations.length} cuộc</span>
            </div>
            <div className="mt-3">
              <SearchField
                aria-label="Tìm kiếm cuộc trò chuyện"
                placeholder="Tìm món hoặc người"
                value={conversationQuery}
                onChange={(event) => setConversationQuery(event.target.value)}
              />
            </div>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
            {loadingConversations ? (
              <SkeletonRows rows={5} label="Đang tải cuộc trò chuyện" />
            ) : conversationError ? (
              <div className="space-y-3 p-3 text-sm text-error">
                <p>{conversationError}</p>
                <Button variant="outline" onClick={() => setConversationReloadKey((key) => key + 1)}>
                  Thử tải lại
                </Button>
              </div>
            ) : filteredConversations.length ? (
              filteredConversations.map((conversation) => {
                const { request } = conversation;
                const participantName = requestParticipantName(conversation, currentUserId);
                const active = request.id === selectedRequestId;
                const unread = !active && isRequestUnread(request.lastMessageAt, request.id);
                return (
                  <div key={request.id} className={`rounded-md ${active ? 'bg-primary-faint' : 'hover:bg-surface-low'}`}>
                    <button
                      type="button"
                      aria-current={active ? 'true' : undefined}
                      onClick={() => openConversation(request.id)}
                      className="grid w-full grid-cols-[40px_minmax(0,1fr)] gap-3 p-2.5 text-left"
                    >
                      <div className="relative">
                        <Avatar user={{ name: participantName, avatarInitials: avatarInitials(participantName), avatarUrl: undefined }} />
                        {unread ? (
                          <span
                            className="absolute -right-0.5 -top-0.5 size-2.5 rounded-full bg-error ring-2 ring-white"
                            title="Tin nhắn mới"
                          />
                        ) : null}
                      </div>
                      <span className="min-w-0">
                        <span className="flex items-baseline justify-between gap-2">
                          <strong className={`truncate text-sm ${unread ? 'font-bold text-text-primary' : ''}`}>
                            {participantName}
                          </strong>
                          <span className="shrink-0 text-2xs text-text-muted">
                            {request.lastMessageAt
                              ? new Date(request.lastMessageAt).toLocaleDateString('vi-VN')
                              : new Date(request.createdAt).toLocaleDateString('vi-VN')}
                          </span>
                        </span>
                        <span className={`mt-0.5 block truncate text-xs ${unread ? 'font-semibold text-text-primary' : 'text-text-secondary'}`}>
                          {conversationTitle(conversation)}
                        </span>
                        <span className="mt-0.5 flex items-center justify-between gap-1">
                          <span className={`text-xs font-semibold ${requestStatusTextClass(request.status)}`}>
                            {requestStatusLabel[request.status]}
                          </span>
                          {unread ? (
                            <span className="rounded-full bg-error/10 px-1.5 py-0.2 text-2xs font-bold text-error">
                              Mới
                            </span>
                          ) : null}
                        </span>
                      </span>
                    </button>
                    {canSelectRequest(conversation, conversations, currentUserId) ? (
                      <div className="px-2.5 pb-2.5 pl-[62px]">
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
                      </div>
                    ) : null}
                  </div>
                );
              })
            ) : (
              <div className="p-2">
                <EmptyState
                  title={conversationQuery ? 'Không tìm thấy cuộc trò chuyện' : 'Chưa có yêu cầu'}
                  text={conversationQuery ? 'Thử tìm bằng tên món đồ, người liên quan hoặc trạng thái khác.' : 'Các yêu cầu nhận hoặc trao đổi món đồ sẽ xuất hiện tại đây.'}
                />
              </div>
            )}
          </div>
        </aside>

        {selected ? (
          <>
            <section
              aria-label={`Cuộc trò chuyện với ${selectedParticipantName}`}
              className={`${mobileChat ? 'flex' : 'hidden lg:flex'} min-h-[640px] min-w-0 flex-col overflow-hidden rounded-lg bg-white ring-1 ring-border/80 lg:min-h-0`}
            >
              <header className="flex shrink-0 flex-wrap items-center gap-3 border-b border-border px-4 py-3">
                <Button variant="outline" size="sm" className="lg:hidden" onClick={() => setMobileChat(false)}>
                  Quay lại
                </Button>
                <span className="hidden sm:inline-flex">
                  <Avatar
                    size="sm"
                    user={{ name: selectedParticipantName, avatarInitials: avatarInitials(selectedParticipantName), avatarUrl: undefined }}
                  />
                </span>
                <div className="min-w-0 flex-1">
                  <strong className="block truncate text-sm">{selectedParticipantName}</strong>
                  {selectedAsDonor ? (
                    <div className="mt-0.5">
                      <ReputationBadge
                        trustStars={selected.request.receiverTrustStars ?? selected.request.trustStars}
                        rank={selected.request.receiverRank ?? selected.request.rank}
                        asDescribedRate={selected.request.receiverAsDescribedRate ?? selected.request.asDescribedRate}
                        compact
                      />
                    </div>
                  ) : null}
                </div>
                {reportTargetUserId && reportTargetUserId !== currentUserId ? (
                  <Button variant="ghost" size="sm" className="hidden sm:inline-flex" onClick={() => setReportOpen(true)}>
                    Báo cáo
                  </Button>
                ) : null}
                <Button variant="outline" size="sm" className="xl:hidden" onClick={() => setMobileDetailsOpen(true)}>
                  Tiến trình
                </Button>
              </header>

              <button
                type="button"
                onClick={() => navigate(`/items/${selected.request.itemId}`)}
                className="mx-4 mt-3 flex shrink-0 items-center gap-3 rounded-md bg-surface-low p-2.5 text-left transition hover:bg-primary-faint"
              >
                <ItemImage
                  src={selected.item?.images[0] ?? selected.request.itemImage ?? undefined}
                  alt={conversationTitle(selected)}
                  className="h-11 w-14 shrink-0 rounded-md object-cover"
                />
                <span className="min-w-0 flex-1">
                  <strong className="block truncate text-sm">{conversationTitle(selected)}</strong>
                  <span className="mt-0.5 block text-xs text-text-secondary">
                    {selectedAsDonor ? 'Bài của bạn' : 'Bạn đang xin món này'} · {requestStatusLabel[selected.request.status]}
                  </span>
                </span>
                <TypeBadge type={selected.request.type === 'SWAP' ? 'trade' : 'gift'} />
              </button>

              {reportNotice || disputeNotice ? (
                <div role="status" className="mx-4 mt-3 space-y-1 rounded-md bg-success-soft px-3 py-2 text-xs text-success">
                  {reportNotice ? <p>{reportNotice}</p> : null}
                  {disputeNotice ? <p>{disputeNotice}</p> : null}
                </div>
              ) : null}

              <div className="min-h-0 flex-1 space-y-2.5 overflow-y-auto px-4 py-4">
                {pinnedItemsError ? (
                  <div className="flex items-center justify-center gap-3 text-xs text-error">
                    <p>{pinnedItemsError}</p>
                    <button type="button" className="shrink-0 font-semibold underline" onClick={() => setPinnedItemsReloadKey((key) => key + 1)}>
                      Thử tải lại
                    </button>
                  </div>
                ) : null}
                {!pinnedItemsLoading && pinnedItems.length ? (
                  <div className="flex gap-2 overflow-x-auto pb-1">
                    {pinnedItems.map((pinnedItem) => (
                      <button
                        key={pinnedItem.id}
                        type="button"
                        className="flex min-w-[220px] items-center gap-3 rounded-md p-2 text-left ring-1 ring-border/70 transition hover:bg-surface-low"
                        onClick={() => navigate(`/items/${pinnedItem.id}`)}
                      >
                        <ItemImage src={pinnedItem.imageUrl ?? undefined} alt={pinnedItem.title} className="size-12 shrink-0 rounded-md object-cover" />
                        <span className="min-w-0 flex-1">
                          <strong className="block truncate text-xs">{pinnedItem.title}</strong>
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
                  messages.map((entry) => (
                    <ChatMessage
                      key={entry.id}
                      entry={entry}
                      mine={entry.senderId === currentUserId}
                      onOpenItem={(itemId) => navigate(`/items/${itemId}`)}
                    />
                  ))
                ) : (
                  <p className="mx-auto max-w-sm py-10 text-center text-sm leading-6 text-text-muted">
                    {isCancelled
                      ? 'Giao dịch đã bị huỷ.'
                      : chatEnabled
                        ? 'Chưa có tin nhắn. Hãy gửi lời chào để bắt đầu.'
                        : 'Cuộc trò chuyện mở khi chủ bài chọn yêu cầu này.'}
                  </p>
                )}
                <div ref={messagesEndRef} aria-hidden="true" />
              </div>

              <div className="shrink-0 space-y-2 border-t border-border px-4 py-3">
                {isCancelled ? (
                  <div role="status" className="rounded-md border border-border bg-surface-low px-3 py-2 text-center text-xs text-text-secondary">
                    Giao dịch đã bị huỷ. Cuộc trò chuyện đang ở chế độ chỉ xem.
                  </div>
                ) : null}
                {messageError ? (
                  <div role="alert" className="flex items-center justify-between gap-3 rounded-md bg-error-soft px-3 py-2 text-xs text-error">
                    <p>{messageError}</p>
                    <button type="button" className="shrink-0 font-semibold underline" onClick={() => setMessageReloadKey((key) => key + 1)}>
                      Tải lại
                    </button>
                  </div>
                ) : null}
                {chatImage ? (
                  <p className="flex min-w-0 items-center gap-2 text-xs text-text-secondary">
                    <span className="max-w-56 truncate">Ảnh: {chatImage.name}</span>
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
                  </p>
                ) : null}
                {shareableItems.length && !isGiftReceiver ? (
                  <select
                    aria-label="Chia sẻ bài của bạn"
                    className="input min-h-10 py-2 text-xs"
                    value={shareItemId}
                    disabled={sending || !chatEnabled || !!chatImage}
                    onChange={(event) => setShareItemId(event.target.value)}
                  >
                    <option value="">Không kèm bài đăng của bạn</option>
                    {shareableItems.map((item) => (
                      <option key={item.id} value={item.id}>
                        Kèm bài: {item.title}
                      </option>
                    ))}
                  </select>
                ) : null}
                <form
                  className="flex items-center gap-2"
                  onSubmit={(event) => {
                    event.preventDefault();
                    void send();
                  }}
                >
                  <label className="min-w-0 flex-1">
                    <span className="sr-only">Tin nhắn</span>
                    <input
                      className="input"
                      value={message}
                      maxLength={2000}
                      onChange={(event) => {
                        setMessage(event.target.value);
                        if (messageError) setMessageError('');
                      }}
                      placeholder={isCancelled ? 'Giao dịch đã huỷ (chỉ xem)' : chatEnabled ? 'Nhập tin nhắn...' : 'Chưa thể nhắn tin'}
                      disabled={sending || !chatEnabled}
                    />
                  </label>
                  {isGiftReceiver ? null : (
                  <label
                    title={requestMediaEnabled ? 'Gửi một ảnh' : 'Mở sau khi chốt lịch hẹn'}
                    className={`inline-flex min-h-11 shrink-0 cursor-pointer items-center rounded-md border border-border px-3 text-xs font-semibold text-text-secondary ${sending || !chatEnabled || !requestMediaEnabled || !!shareItemId ? 'pointer-events-none opacity-40' : 'hover:bg-surface-low'}`}
                  >
                    Gửi ảnh
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
                  )}
                  <Button aria-label="Gửi tin nhắn" disabled={sending || !chatEnabled || (!message.trim() && !shareItemId && !chatImage)}>
                    {sending ? 'Đang gửi' : 'Gửi'}
                  </Button>
                </form>
                {chatEnabled && !requestMediaEnabled && !isGiftReceiver ? (
                  <p className="text-2xs text-text-muted">Gửi ảnh được sau khi hai bên chốt lịch hẹn.</p>
                ) : null}
              </div>
            </section>

            {mobileDetailsOpen ? (
              <button
                type="button"
                aria-label="Đóng bảng tiến trình"
                className="fixed inset-0 z-30 bg-black/45 xl:hidden"
                onClick={() => setMobileDetailsOpen(false)}
              />
            ) : null}
            <aside
              aria-label="Tiến trình giao dịch"
              className={`${mobileDetailsOpen ? 'fixed inset-x-3 bottom-3 top-20 z-40 flex' : 'hidden'} min-h-0 flex-col overflow-hidden rounded-lg bg-white shadow-lg ring-1 ring-border/80 xl:static xl:flex xl:shadow-none`}
            >
              <div className="flex shrink-0 items-center justify-between gap-3 px-5 pb-2 pt-4">
                <h2 className="text-lg font-bold">Tiến trình giao dịch</h2>
                <button
                  type="button"
                  aria-label="Đóng bảng tiến trình"
                  className="grid size-11 shrink-0 place-items-center rounded-md text-text-muted transition hover:bg-surface-low xl:hidden"
                  onClick={() => setMobileDetailsOpen(false)}
                >
                  <Icon name="close" className="size-4" />
                </button>
              </div>
              <div className="min-h-0 flex-1 space-y-5 overflow-y-auto overscroll-contain px-5 pb-5">
                <TransactionSteps status={selected.request.status} />

                {logisticsLoading ? <p className="text-xs text-text-muted">Đang tải thông tin giao nhận...</p> : null}

                {selected.request.status === 'PENDING' ? (
                  <div className="space-y-3 text-sm">
                    <p className="leading-6 text-text-secondary">
                      {selectedAsDonor
                        ? 'Người này đang chờ bạn chọn. Chọn xong thì cuộc trò chuyện mới mở.'
                        : 'Yêu cầu đang chờ chủ bài chọn người nhận.'}
                    </p>
                    {selectedAsDonor ? (
                      canSelectRequest(selected, conversations, currentUserId) ? (
                        <Button
                          disabled={selectSubmitting}
                          onClick={() => {
                            setSelectError('');
                            setSelectCandidateId(selected.request.id);
                          }}
                        >
                          Chọn người này
                        </Button>
                      ) : (
                        <p className="text-xs text-text-muted">Bài đăng đã có yêu cầu được chọn.</p>
                      )
                    ) : (
                      <Button variant="outline" disabled={logisticsSubmitting} onClick={() => void cancelPendingRequest()}>
                        Hủy yêu cầu
                      </Button>
                    )}
                  </div>
                ) : null}

                {selected.request.status === 'REJECTED' || selected.request.status === 'CANCELLED' ? (
                  <Alert tone="warning">
                    {selected.request.status === 'REJECTED'
                      ? 'Chủ bài đã chọn người khác cho món này.'
                      : selected.request.cancelReason
                        ? `Yêu cầu này đã bị hủy. Lý do: ${selected.request.cancelReason}`
                        : 'Yêu cầu này đã bị hủy.'}
                  </Alert>
                ) : null}

                {canSchedule(selected.request.status) && !selected.request.contactRevealedAt ? (
                  <div className="space-y-3">
                    <div>
                      <h3 className="text-sm font-bold">Hẹn lịch giao nhận</h3>
                      <p className="mt-1 text-xs leading-5 text-text-secondary">
                        Một bên lưu lịch, cả hai cùng xác nhận. Khi đó hai bên mới thấy số điện thoại của nhau.
                      </p>
                    </div>
                    {otherPartyConfirmedAt ? (
                      <Alert tone="info">{selectedParticipantName} đã xác nhận lịch đang lưu.</Alert>
                    ) : null}
                    <fieldset className="grid grid-cols-2 gap-2">
                      <legend className="mb-1.5 text-xs font-semibold text-text-secondary">Hình thức</legend>
                      {([
                        ['IN_PERSON', 'Gặp trực tiếp', 'Hẹn tại chỗ công cộng'],
                        ['SHIPPING', 'Giao hàng', 'Gửi qua đơn vị vận chuyển'],
                      ] as const).map(([value, label, hint]) => (
                        <button
                          key={value}
                          type="button"
                          aria-pressed={deliveryMethod === value}
                          disabled={scheduleLocked}
                          onClick={() => setDeliveryMethod(value)}
                          className={`flex flex-col gap-0.5 rounded-md border p-2.5 text-left transition disabled:opacity-50 ${deliveryMethod === value ? 'border-primary bg-primary-faint' : 'border-border hover:bg-surface-low'}`}
                        >
                          <strong className="text-sm">{label}</strong>
                          <span className="text-2xs text-text-muted">{hint}</span>
                        </button>
                      ))}
                    </fieldset>
                    <Field
                      label={deliveryMethod === 'SHIPPING' ? 'Địa chỉ nhận hàng' : 'Địa điểm gặp'}
                      value={meetingPlace}
                      maxLength={255}
                      error={meetingPlace ? meetingPlaceValidation ?? undefined : undefined}
                      onChange={(event) => {
                        setMeetingPlace(event.target.value);
                        setLogisticsError('');
                      }}
                      disabled={scheduleLocked}
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
                      disabled={scheduleLocked}
                    />
                    <label className="flex items-start gap-2 text-xs leading-5 text-text-secondary">
                      <input
                        type="checkbox"
                        className="mt-0.5 size-4 accent-primary"
                        checked={agreeToWaiver}
                        onChange={(event) => setAgreeToWaiver(event.target.checked)}
                        disabled={scheduleLocked}
                      />
                      <span>Tôi đồng ý cam kết trách nhiệm giao nhận.</span>
                    </label>
                    <div className="flex flex-wrap gap-2">
                      <Button variant="outline" disabled={scheduleLocked || !meetingFormValid} onClick={() => void saveSchedule()}>
                        Lưu lịch hẹn
                      </Button>
                      <Button disabled={!canConfirmSchedule} onClick={() => void confirmLogistics()}>
                        {currentPartyConfirmedAt ? 'Đã xác nhận lịch' : 'Xác nhận lịch'}
                      </Button>
                    </div>
                  </div>
                ) : null}

                {selected.request.contactRevealedAt ? (
                  <div className="space-y-3">
                    {selected.request.meetingTime || selected.request.meetingPlace ? (
                      <div className="rounded-md bg-surface-low px-3.5 py-3 text-sm">
                        <strong className="block">
                          {selected.request.deliveryMethod === 'SHIPPING' ? 'Giao hàng' : 'Gặp'}{' '}
                          {selected.request.meetingTime ? new Date(selected.request.meetingTime).toLocaleString('vi-VN') : ''}
                        </strong>
                        <span className="text-text-secondary">{selected.request.meetingPlace}</span>
                      </div>
                    ) : null}
                    {contact ? (
                      <div className="rounded-md border border-border px-3.5 py-3 text-sm leading-6">
                        <strong className="block">Liên hệ {contact.fullName}</strong>
                        <span className="block">{contact.phone}</span>
                        <span className="block text-text-secondary">{contact.email}</span>
                      </div>
                    ) : (
                      <Button variant="outline" disabled={logisticsSubmitting} onClick={() => void loadContact()}>
                        Xem số điện thoại
                      </Button>
                    )}
                  </div>
                ) : null}

                {canConfirmHandover(selected.request.status) && selected.request.contactRevealedAt ? (
                  <div className="space-y-3">
                    {otherPartyHandoverAt && !currentHandoverAt ? (
                      <Alert tone="warning">
                        {selectedParticipantName} đã xác nhận {selectedAsDonor ? 'đã nhận đồ' : 'đã giao đồ'}. Hãy xác nhận phía bạn.
                      </Alert>
                    ) : null}
                    {!selectedAsDonor && !currentHandoverAt ? (
                      <>
                        <Select
                          label="Món đồ có đúng mô tả không?"
                          value={asDescribed}
                          disabled={logisticsSubmitting}
                          onChange={(event) => setAsDescribed(event.target.value)}
                        >
                          <option value="">Chọn đánh giá</option>
                          <option value="yes">Đúng mô tả</option>
                          <option value="no">Không đúng mô tả</option>
                        </Select>
                        {asDescribed === 'no' ? (
                          <TextArea
                            label="Lý do không đúng mô tả"
                            value={notAsDescribedReason}
                            disabled={logisticsSubmitting}
                            onChange={(event) => setNotAsDescribedReason(event.target.value)}
                          />
                        ) : null}
                      </>
                    ) : null}
                    {!currentHandoverAt ? (
                      <p className="text-xs font-semibold text-warning">Sau khi hoàn tất, bạn chỉ có 24 giờ để khiếu nại.</p>
                    ) : null}
                    <Button className="w-full" disabled={Boolean(currentHandoverAt) || logisticsSubmitting} onClick={() => void confirmApiHandover()}>
                      {currentHandoverAt
                        ? selectedAsDonor ? 'Bạn đã xác nhận đã giao' : 'Bạn đã xác nhận đã nhận'
                        : selectedAsDonor ? 'Tôi đã giao đồ' : 'Tôi đã nhận được đồ'}
                    </Button>
                    {canReportHandoverIssue(selected.request.status, otherPartyHandoverAt, currentHandoverAt) ? (
                      <Button
                        variant="outline"
                        className="w-full"
                        disabled={logisticsSubmitting}
                        onClick={() => {
                          setIssueError('');
                          setIssueOpen(true);
                        }}
                      >
                        {selectedAsDonor ? 'Tôi chưa giao được' : 'Tôi chưa nhận được'}
                      </Button>
                    ) : null}
                  </div>
                ) : null}

                {selected.request.status === 'COMPLETED' ? (
                  <div className="space-y-3">
                    <Alert tone="success">
                      Giao dịch đã hoàn tất
                      {selected.request.completedAt ? ` lúc ${new Date(selected.request.completedAt).toLocaleString('vi-VN')}` : ''}.
                    </Alert>
                    {!canOpenComplaint(selected.request.status, selected.request.complaintDeadline) ? (
                      <p className="text-sm text-text-secondary">Đã hết hạn khiếu nại.</p>
                    ) : (
                      <div className="space-y-2">
                        {selected.request.complaintDeadline ? (
                          <p className="text-sm font-semibold text-text-primary">
                            Khiếu nại được đến {formatDeadline(selected.request.complaintDeadline)}
                          </p>
                        ) : null}
                        <p className="text-xs text-text-secondary">
                          Chỉ khiếu nại khi món đồ có vấn đề. Đối tác sẽ có 48 giờ để giải trình.
                        </p>
                        <Button
                          variant="outline"
                          className="w-full"
                          onClick={() => {
                            setDisputeError('');
                            setDisputeNotice('');
                            setDisputeOpen(true);
                          }}
                        >
                          Khiếu nại giao dịch
                        </Button>
                      </div>
                    )}
                  </div>
                ) : null}

                {selected.request.status === 'DISPUTED' ? (
                  <Alert tone="warning">Giao dịch đang có khiếu nại. Admin sẽ xem xét và báo kết quả cho hai bên.</Alert>
                ) : null}

                {logisticsError ? (
                  <div role="alert" className="flex items-center justify-between gap-3 rounded-md bg-error-soft px-3 py-2 text-xs text-error">
                    <p>{logisticsError}</p>
                    <button type="button" className="shrink-0 font-semibold underline" onClick={() => setLogisticsReloadKey((key) => key + 1)}>
                      Tải lại
                    </button>
                  </div>
                ) : null}
                {logisticsNotice ? <p className="text-xs font-semibold text-success">{logisticsNotice}</p> : null}

                {requestMediaEnabled ? (
                  <section className="space-y-2 border-t border-border pt-4">
                    <div className="flex items-baseline justify-between gap-2">
                      <h3 className="text-sm font-bold">Ảnh giao nhận</h3>
                      <span className="text-xs text-text-muted">{requestMediaLoading ? 'Đang tải...' : `${requestMedia.length} ảnh`}</span>
                    </div>
                    <p className="text-xs leading-5 text-text-muted">Chụp món đồ lúc giao. Admin xem khi có khiếu nại.</p>
                    {requestMedia.length ? (
                      <div className="flex gap-2 overflow-x-auto">
                        {requestMedia.map((entry) => (
                          <a
                            key={entry.mediaId}
                            href={resolveMediaUrl(entry.url)}
                            target="_blank"
                            rel="noreferrer"
                            className="shrink-0"
                            title={`${entry.stage === 'HANDOVER' ? 'Giao nhận' : 'Khiếu nại'} · ${new Date(entry.createdAt).toLocaleString('vi-VN')}`}
                          >
                            <ItemImage src={entry.url} alt="Ảnh minh chứng" className="size-14 rounded-md object-cover" />
                          </a>
                        ))}
                      </div>
                    ) : null}
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
                      label="Chọn ảnh"
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
                      {requestMediaSubmitting ? 'Đang tải...' : requestMediaFiles.length ? `Thêm ${requestMediaFiles.length} ảnh` : 'Thêm ảnh'}
                    </Button>
                    {requestMediaError ? <p className="text-xs text-error">{requestMediaError}</p> : null}
                  </section>
                ) : null}

                <section className="space-y-3 border-t border-border pt-4 text-sm">
                  <h3 className="text-sm font-bold">{requestFallbackTitle(selected.request)}</h3>
                  <p className="text-xs text-text-muted">Gửi lúc {new Date(selected.request.createdAt).toLocaleString('vi-VN')}</p>
                  {selected.request.type === 'SWAP' ? (
                    selected.offeredItem ? (
                      <button
                        type="button"
                        onClick={() => navigate(`/items/${selected.offeredItem!.id}`)}
                        className="flex w-full items-center gap-3 rounded-md bg-surface-low p-2.5 text-left transition hover:bg-primary-faint"
                      >
                        <ItemImage src={selected.offeredItem.images[0]} alt={selected.offeredItem.title} className="size-12 shrink-0 rounded-md object-cover" />
                        <span className="min-w-0 flex-1">
                          <span className="block text-2xs font-semibold text-text-muted">Món mang đổi</span>
                          <strong className="block truncate text-sm">{selected.offeredItem.title}</strong>
                          <span className="block text-xs text-text-secondary">{apiConditionLabel[selected.offeredItem.condition]}</span>
                        </span>
                      </button>
                    ) : (
                      <p className="rounded-md bg-surface-low p-2.5 text-text-secondary">
                        Món mang đổi:{' '}
                        <strong className="text-text-primary">{selected.request.offeredItemTitle ?? 'Không tải được chi tiết món'}</strong>
                      </p>
                    )
                  ) : null}
                  <Button
                    variant="outline"
                    className="w-full"
                    onClick={() => navigate(`/items/${selected.request.itemId}?from=messages`)}
                  >
                    Xem bài đăng
                  </Button>
                  {reportTargetUserId && reportTargetUserId !== currentUserId ? (
                    <Button variant="ghost" className="w-full" onClick={() => setReportOpen(true)}>
                      Báo cáo người dùng
                    </Button>
                  ) : null}
                </section>

                {canCancelTransaction(selected.request.status) ? (
                  <section className="space-y-2 border-t border-border pt-4">
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={cancelSubmitting}
                      onClick={() => {
                        setCancelError('');
                        setCancelOpen(true);
                      }}
                    >
                      Huỷ giao dịch
                    </Button>
                    <p className="text-xs leading-5 text-text-muted">
                      Cần nêu lý do. Bài đăng mở lại cho người khác, không mất phí đăng lần nữa.
                    </p>
                  </section>
                ) : null}
              </div>
            </aside>
          </>
        ) : (
          <section className="hidden place-items-center rounded-lg bg-white ring-1 ring-border/80 lg:grid xl:col-span-2">
            <EmptyState title="Chọn cuộc trò chuyện" text="Chọn một yêu cầu ở cột bên trái để xem tin nhắn." />
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
      {cancelOpen && selected ? (
        <DialogFrame labelledBy="cancel-transaction-title" busy={cancelSubmitting} onClose={() => setCancelOpen(false)}>
          <h2 id="cancel-transaction-title" className="text-xl font-bold">Huỷ giao dịch</h2>
          <p className="mt-1 text-sm text-text-muted">{conversationTitle(selected)}</p>
          <p className="mt-3 text-sm leading-6 text-text-secondary">
            Bên còn lại sẽ nhận được lý do này. Bài đăng mở lại cho người khác, không mất phí đăng lần nữa.
          </p>
          <TextArea
            className="mt-4"
            label="Lý do huỷ (bắt buộc)"
            value={cancelReason}
            maxLength={500}
            disabled={cancelSubmitting}
            onChange={(event) => setCancelReason(event.target.value)}
          />
          <p className="mt-1 text-right text-xs text-text-muted">{cancelReason.length}/500</p>
          {cancelError ? <div role="alert" className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">{cancelError}</div> : null}
          <div className="mt-6 flex justify-end gap-2">
            <Button variant="ghost" disabled={cancelSubmitting} onClick={() => setCancelOpen(false)}>Giữ giao dịch</Button>
            <Button disabled={cancelSubmitting || cancelReason.trim().length < 5} onClick={() => void submitCancelTransaction()}>
              {cancelSubmitting ? 'Đang huỷ...' : 'Huỷ giao dịch'}
            </Button>
          </div>
        </DialogFrame>
      ) : null}
      {issueOpen && selected ? (
        <DialogFrame labelledBy="handover-issue-title" busy={issueSubmitting} onClose={() => setIssueOpen(false)}>
          <h2 id="handover-issue-title" className="text-xl font-bold">
            {selectedAsDonor ? 'Tôi chưa giao được' : 'Tôi chưa nhận được'}
          </h2>
          <p className="mt-1 text-sm text-text-muted">{conversationTitle(selected)}</p>
          <p className="mt-3 text-sm leading-6 text-text-secondary">
            {selectedParticipantName} đã xác nhận bàn giao nhưng phía bạn thì chưa xong. Giao dịch sẽ chuyển sang khiếu nại để Admin xem xét.
          </p>
          <TextArea
            className="mt-4"
            label="Mô tả sự cố (bắt buộc)"
            value={issueNote}
            maxLength={1000}
            disabled={issueSubmitting}
            onChange={(event) => setIssueNote(event.target.value)}
          />
          <p className="mt-1 text-right text-xs text-text-muted">{issueNote.length}/1000</p>
          <Field
            className="mt-4"
            label="Ảnh minh chứng"
            type="file"
            accept="image/*"
            multiple
            disabled={issueSubmitting}
            onChange={(event) => setIssueEvidence(Array.from(event.target.files ?? []))}
          />
          {issueEvidence.length ? <p className="mt-2 text-xs text-text-muted">Đã chọn {issueEvidence.length} ảnh.</p> : null}
          {issueError ? <div role="alert" className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">{issueError}</div> : null}
          <div className="mt-6 flex justify-end gap-2">
            <Button variant="ghost" disabled={issueSubmitting} onClick={() => setIssueOpen(false)}>Đóng</Button>
            <Button disabled={issueSubmitting || issueNote.trim().length < 5} onClick={() => void submitHandoverIssue()}>
              {issueSubmitting ? 'Đang gửi...' : 'Gửi báo cáo'}
            </Button>
          </div>
        </DialogFrame>
      ) : null}
      {disputeOpen && selected ? (
        <DialogFrame>
          <h2 className="text-xl font-bold">Gửi khiếu nại giao dịch</h2>
          <p className="mt-1 text-sm text-text-muted">{conversationTitle(selected)}</p>
          <div className="mt-3 rounded-md bg-warning-soft p-3 text-xs text-warning">
            Lưu ý: Thời hạn gửi khiếu nại là 24 giờ kể từ lúc hoàn tất giao dịch. Sau khi bạn gửi khiếu nại, đối tác sẽ có 48 giờ để gửi giải trình kèm minh chứng đối chất.
          </div>
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
          {disputeError ? <div className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">{disputeError}</div> : null}
          <div className="mt-6 flex justify-end gap-2">
            <Button variant="ghost" disabled={disputeSubmitting} onClick={() => setDisputeOpen(false)}>Hủy</Button>
            <Button disabled={disputeSubmitting || !disputeReason} onClick={() => void submitDispute()}>
              {disputeSubmitting ? 'Đang gửi...' : 'Gửi khiếu nại'}
            </Button>
          </div>
        </DialogFrame>
      ) : null}
      {responseDisputeId !== null ? (
        <DialogFrame>
          <h2 className="text-xl font-bold">Giải trình khiếu nại</h2>
          <p className="mt-1 text-sm text-text-secondary">
            Bạn có 48 giờ kể từ lúc nhận thông báo để gửi giải trình. Giờ hết hạn ghi trong thông báo khiếu nại.
          </p>
          {responseNotice ? (
            <div className="mt-5 space-y-4">
              <div className="rounded-md bg-success-soft p-3 text-sm text-success">{responseNotice}</div>
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
                <div className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">Đường dẫn khiếu nại không hợp lệ.</div>
              ) : responseError ? (
                <div className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">{responseError}</div>
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
        </DialogFrame>
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
    <DialogFrame labelledBy="select-request-title">
      <h2 id="select-request-title" className="text-xl font-bold">Xác nhận chọn người giao dịch</h2>
      <div className="mt-4 flex items-center gap-3 rounded-md bg-surface-low p-3">
        <Avatar user={{ name: participantName, avatarInitials: avatarInitials(participantName), avatarUrl: undefined }} />
        <div className="min-w-0 flex-1">
          <strong className="block truncate text-sm">{participantName}</strong>
          <div className="mt-1">
            <ReputationBadge
              trustStars={conversation.request.receiverTrustStars ?? conversation.request.trustStars}
              rank={conversation.request.receiverRank ?? conversation.request.rank}
              asDescribedRate={conversation.request.receiverAsDescribedRate ?? conversation.request.asDescribedRate}
            />
          </div>
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
      <p className="mt-4 rounded-md bg-warning-soft p-3 text-xs leading-5 text-warning">
        Khi bạn xác nhận chọn người này để giao dịch, yêu cầu sẽ được chấp nhận và các yêu cầu đang chờ khác của cùng bài đăng sẽ tự động bị từ chối.
      </p>
      {error ? <p role="alert" className="mt-4 rounded-md bg-error-soft p-3 text-sm text-error">{error}</p> : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" autoFocus disabled={submitting} onClick={onCancel}>Hủy</Button>
        <Button disabled={submitting} onClick={onConfirm}>
          {submitting ? 'Đang chọn...' : 'Xác nhận chọn'}
        </Button>
      </div>
    </DialogFrame>
  );
}

const transactionSteps = ['Được chọn', 'Chốt lịch', 'Giao nhận', 'Hoàn tất'] as const;

// Số bước đã xong theo trạng thái yêu cầu; -1 là chưa vào giao dịch (đang chờ, bị từ chối, đã hủy).
function completedStepCount(status: RequestResponse['status']) {
  if (status === 'RESERVED' || status === 'AWAITING_LOGISTICS') return 1;
  if (status === 'LOGISTICS_CONFIRMED' || status === 'AWAITING_HANDOVER') return 2;
  if (status === 'COMPLETED' || status === 'DISPUTED') return 4;
  return -1;
}

/** Thanh 4 bước của giao dịch. Chỉ hiển thị trạng thái backend trả về, không tự đổi trạng thái. */
function TransactionSteps({ status }: { status: RequestResponse['status'] }) {
  const done = completedStepCount(status);
  if (done < 0) return null;
  return (
    <ol className="grid grid-cols-4 gap-1.5" aria-label={`Đã xong ${Math.min(done, 4)} trên 4 bước`}>
      {transactionSteps.map((label, index) => {
        const state = index < done ? 'done' : index === done ? 'current' : 'todo';
        return (
          <li key={label} aria-current={state === 'current' ? 'step' : undefined} className="flex flex-col gap-1.5">
            <span
              className={`h-1.5 rounded-full ${state === 'done' ? 'bg-primary' : state === 'current' ? 'bg-secondary' : 'bg-surface-high'}`}
            />
            <span
              className={`text-2xs font-semibold ${state === 'done' ? 'text-primary' : state === 'current' ? 'text-secondary-hover' : 'text-text-muted'}`}
            >
              {label}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

function ChatMessage({
  entry,
  mine,
  onOpenItem,
}: {
  entry: MessageResponse;
  mine: boolean;
  onOpenItem: (itemId: number) => void;
}) {
  if (entry.moderation && entry.moderation !== 'PASS') {
    return (
      <div className="flex justify-center">
        <span className="max-w-xl rounded-md bg-error-soft px-3 py-2 text-center text-xs text-error">
          {entry.blockedReason || 'Tin nhắn này không vượt qua kiểm duyệt.'}
        </span>
      </div>
    );
  }
  if (entry.kind.toUpperCase() === 'SYSTEM') {
    return (
      <div className="flex justify-center">
        <span className="rounded-full bg-primary-faint px-3 py-1.5 text-center text-2xs text-text-secondary">{entry.content}</span>
      </div>
    );
  }
  const offerLabel = messageOfferStatusLabel(entry.offerStatus);
  return (
    <div className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
      <div
        className={`max-w-[82%] rounded-lg px-3.5 py-2.5 text-sm leading-6 sm:max-w-[70%] ${mine ? 'rounded-br-sm bg-primary text-white' : 'rounded-bl-sm bg-surface-low text-text-primary'}`}
      >
        {entry.itemCard ? (
          <button
            type="button"
            className="mb-2 flex w-full items-center gap-3 rounded-md bg-white p-2.5 text-left text-text-primary"
            onClick={() => onOpenItem(entry.itemCard!.id)}
          >
            <ItemImage src={entry.itemCard.imageUrl ?? undefined} alt={entry.itemCard.title} className="size-12 rounded-md object-cover" />
            <span className="min-w-0">
              <strong className="block break-words text-sm">{entry.itemCard.title}</strong>
              <span className="mt-1 flex flex-wrap gap-1.5">
                <TypeBadge type={entry.itemCard.offerType === 'GIVE' ? 'gift' : 'trade'} />
                <StatusBadge status={entry.itemCard.status} />
              </span>
            </span>
          </button>
        ) : null}
        {entry.mediaId ? (
          <a href={resolveMediaUrl(`/api/v1/media/${entry.mediaId}`)} target="_blank" rel="noreferrer" className="mb-2 block">
            <img
              src={resolveMediaUrl(`/api/v1/media/${entry.mediaId}`)}
              alt="Ảnh trong cuộc trò chuyện"
              className="max-h-72 max-w-full rounded-md object-contain"
            />
          </a>
        ) : null}
        {entry.content ? <p className="whitespace-pre-wrap break-words">{entry.content}</p> : null}
        {offerLabel ? (
          <span className={`mt-1.5 inline-flex rounded-sm px-2 py-0.5 text-2xs font-semibold ${mine ? 'bg-white/15 text-white' : 'bg-white text-primary'}`}>
            {offerLabel}
          </span>
        ) : null}
        <span className={`mt-1 block text-2xs ${mine ? 'text-white/70' : 'text-text-muted'}`}>
          {new Date(entry.createdAt).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' })}
        </span>
      </div>
    </div>
  );
}
