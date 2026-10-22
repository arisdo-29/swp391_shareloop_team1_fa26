import { useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
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
  ItemImage,
  SearchField,
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

const steps = ['Trao doi', 'De xuat lich', 'Chot lich', 'Ban giao', 'Hoan tat'];

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

export function Messages() {
  const user = useAppSelector(selectCurrentUser)!;
  const backendUserId = toBackendUserId(user.id);
  return backendUserId ? <ApiMessages currentUserId={backendUserId} /> : <MockMessages />;
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

function requestParticipantName(conversation: ApiConversation, currentUserId: number) {
  if (conversation.item?.donorId === currentUserId) {
    return conversation.request.receiverName?.trim() || `Người dùng #${conversation.request.receiverId}`;
  }
  return 'Chủ bài đăng';
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
    conversation.item?.donorId,
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
  const chatEnabled = selected ? canChat(selected.request.status) : false;
  const requestMediaEnabled = selected
    ? ['LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER', 'COMPLETED', 'DISPUTED'].includes(selected.request.status)
    : false;
  const selectedItemId = selected?.request.itemId;
  const selectedItemDonorId = selected?.item?.donorId;

  useEffect(() => {
    if (!selectedItemId || selectedItemDonorId !== currentUserId) return;
    let active = true;
    requestApi
      .forItem(selectedItemId, currentUserId)
      .then((requests) => {
        if (active) setConversations((current) => mergeConversationRequests(current, requests));
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
    const loadMessages = async () => {
      if (firstLoad) setLoadingMessages(true);
      try {
        const [response, detail] = await Promise.all([
          chatApi.list(selectedRequestId, afterId),
          requestApi.get(selectedRequestId, currentUserId),
        ]);
        if (!active) return;
        setConversations((current) => current.map((entry) => entry.request.id === detail.id ? { ...entry, request: detail } : entry));
        if (response.length) afterId = Math.max(afterId ?? 0, ...response.map((entry) => entry.id));
        setMessages((current) => [...new Map([...current, ...response].map((entry) => [entry.id, entry])).values()].sort((a, b) => a.id - b.id));
        setMessageError('');
      } catch {
        if (active) setMessageError('Không tải được tin nhắn. Vui lòng thử lại.');
      } finally {
        if (active && firstLoad) setLoadingMessages(false);
        firstLoad = false;
        if (active) pollingId = window.setTimeout(loadMessages, 5_000);
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

  const selectedAsDonor = selected?.item?.donorId === currentUserId;
  const reportTargetUserId = selected?.item
    ? selected.item.donorId === currentUserId
      ? selected.request.receiverId
      : selected.item.donorId
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
          selectCandidate.item?.donorId,
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
    try {
      const request = await requestApi.confirmHandover(
        selected.request.id,
        currentUserId,
        selectedAsDonor ? {} : { asDescribed: asDescribed === 'yes', notAsDescribedReason: asDescribed === 'no' ? notAsDescribedReason.trim() : undefined },
      );
      updateSelectedRequest(request);
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
      sending
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
        `Khiếu nại #${response.id} đã được gửi. Hạn giải trình: ${new Date(response.responseDueAt).toLocaleString('vi-VN')}.`,
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
          conversation.item?.title ?? `Bài đăng #${conversation.request.itemId}`,
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
                const { request, item } = conversation;
                const participantName = requestParticipantName(conversation, currentUserId);
                const selectable = canSelectRequest(conversation, conversations, currentUserId);
                const openDetails = () => {
                  setSelectedRequestId(request.id);
                  setMobileChat(true);
                  setMobileDetailsOpen(false);
                  setReportOpen(false);
                  setReportNotice('');
                  setDisputeNotice('');
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
                        <strong className="block truncate text-sm">{item?.title ?? `Bài đăng #${request.itemId}`}</strong>
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
                <h2 className="truncate text-sm font-bold">{selected.item?.title ?? `Bài đăng #${selected.request.itemId}`}</h2>
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
                    <p className="text-[11px] font-semibold uppercase tracking-wide text-text-muted">Yêu cầu #{selected.request.id}</p>
                    <h3 className="mt-1 truncate text-sm font-bold">{selected.item?.title ?? `Bài đăng #${selected.request.itemId}`}</h3>
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
                    <strong className="text-sm">Yêu cầu #{selected.request.id}</strong>
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
                      ? `Đề nghị nhận món “${selected.item?.title ?? `#${selected.request.itemId}`}”.`
                      : `Đề nghị trao đổi với món “${selected.item?.title ?? `#${selected.request.itemId}`}”.`}
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
                  <p className="mt-3 rounded-md bg-amber-50 p-3 text-xs text-warning">
                    Không tải được chi tiết món đề xuất #{selected.request.offeredItemId ?? ''}.
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
                                alt={`Minh chứng #${entry.mediaId}`}
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
                            <span><strong className="block">{entry.itemCard.title}</strong><StatusBadge status={entry.itemCard.status} /></span>
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
          contextLabel={`Báo cáo người dùng trong yêu cầu #${selected.request.id}`}
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
            <p className="mt-1 text-sm text-text-muted">Yêu cầu #{selected.request.id} · {selected.item?.title ?? 'Bài đăng'}</p>
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
            <p className="mt-1 text-sm text-text-muted">Khiếu nại #{Number.isSafeInteger(responseDisputeId) ? responseDisputeId : ''}</p>
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
              Yêu cầu #{conversation.request.id} · {conversation.request.type === 'SWAP' ? 'Trao đổi' : 'Nhận món đồ'}
            </p>
          </div>
        </div>
        <div className="mt-4 space-y-2 text-sm leading-6 text-text-secondary">
          <p>
            Bài đăng: <strong className="text-text-primary">{conversation.item?.title ?? `#${conversation.request.itemId}`}</strong>
          </p>
          <p>Gửi lúc: {new Date(conversation.request.createdAt).toLocaleString('vi-VN')}</p>
          {conversation.request.type === 'SWAP' ? (
            <p>
              Món đề xuất: <strong className="text-text-primary">{conversation.offeredItem?.title ?? `#${conversation.request.offeredItemId ?? ''}`}</strong>
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

function MockMessages() {
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
                  <ItemImage
                    src={item.images[0]}
                    alt={item.title}
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
                      <ItemImage src={source} alt="Minh chứng" className="size-full object-cover" />
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
                  <ItemImage src={source} alt="Ảnh bằng chứng" className="size-full object-cover" />
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
    avatarInitials: '',
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
      <ItemImage src={item.images[0]} alt={item.title} className="size-12 shrink-0 rounded-md object-cover" />
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
