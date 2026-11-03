import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../../app/store';
import {
  Button,
  EmptyState,
  Field,
  ItemImage,
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
import { requestApi, toBackendUserId, type RequestResponse } from '../../services/requestApi';
import type { ApiError } from '../../services/http';
import { ReportDialog } from '../../components/ReportDialog';
import {
  canChat,
  canViewChat,
  canSelectPendingRequest,
  markRequestChatSeen,
} from '../../utils/request';
import { chatImageForbiddenMessage } from '../../utils/apiErrorMessages';
import { mediaApi } from '../../services/mediaApi';
import { respondDispute } from '../../services/reportApi';
import {
  CHAT_POLL_INTERVAL_MS,
  mergeChatMessages,
  requestConversationPath,
  transactionProgressPath,
  shouldRefreshConversationDetail,
} from '../../utils/chatMessages';
import { ConversationList } from '../../components/messages/ConversationList';
import { ChatHeader } from '../../components/messages/ChatHeader';
import { ChatMessage } from '../../components/messages/ChatMessage';
import { SelectRequestDialog } from '../../components/messages/SelectRequestDialog';
import { disputeErrorMessage } from '../../utils/disputeErrors';
import { productDetailFromRequestPath } from '../../utils/productDetailNavigation';
import {
  donorIdOf,
  requestParticipantName,
  type ApiConversation,
} from '../../components/messages/conversation';

// Giữ đường import cũ cho các test và nơi đang import kiểu này từ trang Tin nhắn.
export type { ApiConversation };
export { SelectRequestDialog };

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

function ApiMessages({ currentUserId }: { currentUserId: number }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { requestId, disputeId } = useParams();
  const requestedId = requestId ? Number(requestId) : null;
  const responseDisputeId = disputeId ? Number(disputeId) : null;
  const selectRequestParam = new URLSearchParams(location.search).get('selectRequest');
  const selectRequestFromUrl =
    selectRequestParam && /^[1-9]\d*$/.test(selectRequestParam) ? Number(selectRequestParam) : null;
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
  const [reportOpen, setReportOpen] = useState(false);
  const [reportNotice, setReportNotice] = useState('');
  const [pinnedItems, setPinnedItems] = useState<PinnedItemResponse[]>([]);
  const [pinnedItemsLoading, setPinnedItemsLoading] = useState(false);
  const [pinnedItemsError, setPinnedItemsError] = useState('');
  const [pinnedItemsReloadKey, setPinnedItemsReloadKey] = useState(0);
  const [shareItemId, setShareItemId] = useState('');
  const [shareableItems, setShareableItems] = useState<ItemResponse[]>([]);
  const [responseNote, setResponseNote] = useState('');
  const [responseEvidence, setResponseEvidence] = useState<File[]>([]);
  const [responseSubmitting, setResponseSubmitting] = useState(false);
  const [responseError, setResponseError] = useState('');
  const [responseNotice, setResponseNotice] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const chatImageInputRef = useRef<HTMLInputElement>(null);
  const activeRequestId = useRef<number | null>(null);
  const selectInFlight = useRef(false);
  const sendInFlight = useRef(false);
  const requestGroupsLoadedAt = useRef(new Map<number, number>());

  useEffect(() => {
    let active = true;
    setLoadingConversations(true);
    setConversationError('');

    Promise.all([requestApi.mine(currentUserId), itemApi.mine()])
      .then(async ([sentRequests, ownedItems]) => {
        if (!active) return;
        setShareableItems(ownedItems.filter((item) => item.status === 'APPROVED'));
        const receivedGroups = await Promise.all(
          ownedItems.map((item) => requestApi.forItem(item.id, currentUserId)),
        );
        const loadedAt = Date.now();
        ownedItems.forEach((item) => requestGroupsLoadedAt.current.set(item.id, loadedAt));
        const requestById = new Map<number, RequestResponse>();
        [...sentRequests, ...receivedGroups.flat()].forEach((request) => {
          requestById.set(request.id, request);
        });
        if (requestedId !== null) {
          if (!Number.isSafeInteger(requestedId) || requestedId <= 0)
            throw new Error('Invalid request');
          const detail = await requestApi.get(requestedId, currentUserId);
          requestById.set(detail.id, detail);
        }

        const itemById = new Map(ownedItems.map((item) => [item.id, item]));
        const missingItemIds = [
          ...new Set(
            [...requestById.values()]
              .flatMap((request) => [request.itemId, request.offeredItemId])
              .filter((itemId): itemId is number => itemId !== undefined && !itemById.has(itemId)),
          ),
        ];
        const missingItems = await Promise.allSettled(
          missingItemIds.map((id) => itemApi.detail(id)),
        );
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
        setSelectedRequestId(
          (current) =>
            requestedId ??
            (current && nextConversations.some(({ request }) => request.id === current)
              ? current
              : (nextConversations[0]?.request.id ?? null)),
        );
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
    ? ['LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER', 'COMPLETED', 'DISPUTED'].includes(
        selected.request.status,
      )
    : false;
  const selectedItemId = selected?.request.itemId;
  const selectedOfferedItemId =
    selected?.request.type === 'SWAP' ? selected.request.offeredItemId : undefined;
  // Hai món của giao dịch SWAP đã nằm trong phần đầu khung chat; dải ghim chỉ dành cho món khác được gửi kèm.
  const extraPinnedItems = pinnedItems.filter(
    (pinnedItem) =>
      pinnedItem.id !== selectedItemId && pinnedItem.id !== selectedOfferedItemId,
  );
  const selectedItemDonorId = selected ? donorIdOf(selected) : undefined;

  useEffect(() => {
    if (
      selectRequestFromUrl &&
      conversations.some(({ request }) => request.id === selectRequestFromUrl)
    ) {
      setSelectCandidateId(selectRequestFromUrl);
    }
  }, [conversations, selectRequestFromUrl]);

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
          setConversations((current) =>
            current.map((entry) =>
              entry.request.id === detail.id ? { ...entry, request: detail } : entry,
            ),
          );
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

  // Mở một cuộc thì tải lại chi tiết để trạng thái, lịch hẹn và ảnh đại diện trong khung chat là bản mới nhất.
  useEffect(() => {
    if (selectedRequestId === null) return;
    let active = true;
    requestApi
      .get(selectedRequestId, currentUserId)
      .then((detail) => {
        if (!active) return;
        setConversations((current) =>
          current.map((entry) =>
            entry.request.id === detail.id ? { ...entry, request: detail } : entry,
          ),
        );
      })
      .catch(() => {
        // Lỗi thì vẫn dùng bản trong danh sách; lần thăm dò tin nhắn sau sẽ làm mới lại.
      });
    return () => {
      active = false;
    };
  }, [currentUserId, selectedRequestId]);

  useEffect(() => {
    // Chỉ cuộn khung chat; scrollIntoView sẽ cuộn cả trang xuống footer.
    const container = messagesEndRef.current?.parentElement;
    if (container) container.scrollTop = container.scrollHeight;
  }, [messages.length, selectedRequestId]);

  const selectedDonorId = selected ? donorIdOf(selected) : undefined;
  const selectedAsDonor = selectedDonorId === currentUserId;
  // Người nhận trong giao dịch Cho tặng không gửi ảnh được (backend trả CHAT_IMAGE_GIVE_RECEIVER_FORBIDDEN)
  const isGiftReceiver = selected?.request.type === 'GIVE' && !selectedAsDonor;
  const reportTargetUserId = selectedDonorId
    ? selectedDonorId === currentUserId
      ? selected!.request.receiverId
      : selectedDonorId
    : null;
  const updateSelectedRequest = (request: RequestResponse) => {
    setConversations((current) =>
      current.map((entry) => (entry.request.id === request.id ? { ...entry, request } : entry)),
    );
  };

  const confirmRequestSelection = async () => {
    if (
      !selectCandidate ||
      selectInFlight.current ||
      !canSelectRequest(selectCandidate, conversations, currentUserId)
    )
      return;

    selectInFlight.current = true;
    setSelectSubmitting(true);
    setSelectError('');
    try {
      const refreshedRequests = await requestApi.forItem(
        selectCandidate.request.itemId,
        currentUserId,
      );
      setConversations((current) => mergeConversationRequests(current, refreshedRequests));
      const refreshedCandidate = refreshedRequests.find(
        (request) => request.id === selectCandidate.request.id,
      );
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
        setSelectError(
          'Bài đăng đã có người được chọn hoặc yêu cầu này không còn khả dụng. Danh sách đã được cập nhật.',
        );
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
        REQUEST_INVALID_STATE_TRANSITION:
          'Bài đăng đã có người được chọn hoặc yêu cầu này không còn khả dụng. Danh sách đã được cập nhật.',
      };
      setSelectError(
        errorMessages[apiError.code] ||
          'Không thể chọn người này. Vui lòng tải lại danh sách và thử lại.',
      );
      if (apiError.status === 409 || apiError.status === 404) {
        setConversationReloadKey((key) => key + 1);
      }
    } finally {
      selectInFlight.current = false;
      setSelectSubmitting(false);
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
      if (
        !selected?.request.contactRevealedAt &&
        hasAmbiguousContactSignal(content) &&
        (await classifyAmbiguousContact(content))
      ) {
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
      setMessages((current) =>
        current.some((entry) => entry.id === sent.id) ? current : [...current, sent],
      );
      setMessage('');
      setChatImage(null);
      if (chatImageInputRef.current) chatImageInputRef.current.value = '';
      setShareItemId('');
    } catch (error) {
      const apiError = error as ApiError;
      if (activeRequestId.current !== selectedRequestId) return;
      setMessageError(
        chatImageForbiddenMessage(apiError.code) ||
          apiError.message ||
          'Không gửi được tin nhắn. Vui lòng thử lại.',
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

  const submitDisputeResponse = async () => {
    if (
      !responseDisputeId ||
      !Number.isSafeInteger(responseDisputeId) ||
      responseDisputeId <= 0 ||
      !responseNote.trim() ||
      responseSubmitting
    )
      return;
    setResponseSubmitting(true);
    setResponseError('');
    try {
      const evidenceMediaIds = responseEvidence.length
        ? await uploadEvidence(responseEvidence)
        : undefined;
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

  useEffect(() => {
    if (selectedRequestId) {
      markRequestChatSeen(selectedRequestId);
    }
  }, [selectedRequestId]);

  const openConversation = (requestId: number) => {
    setSelectedRequestId(requestId);
    markRequestChatSeen(requestId);
    setMobileChat(true);
    setReportOpen(false);
    setReportNotice('');
    navigate(requestConversationPath(requestId));
  };

  return (
    <div className="wide-shell py-5 sm:py-7">
      <div className="grid gap-[clamp(0.75rem,1.5vw,1.25rem)] lg:h-[calc(100vh-9rem)] lg:min-h-[640px] lg:grid-cols-[minmax(280px,400px)_minmax(0,1fr)]">
        <ConversationList
          className={mobileChat ? 'hidden lg:flex' : 'flex'}
          conversations={conversations}
          currentUserId={currentUserId}
          selectedRequestId={selectedRequestId}
          loading={loadingConversations}
          error={conversationError}
          onRetry={() => setConversationReloadKey((key) => key + 1)}
          onOpen={openConversation}
          canSelect={(conversation) => canSelectRequest(conversation, conversations, currentUserId)}
          selectSubmitting={selectSubmitting}
          onSelectCandidate={(id) => {
            setSelectError('');
            setSelectCandidateId(id);
            setSelectedRequestId(id);
            navigate(`${requestConversationPath(id)}?selectRequest=${id}`, { replace: true });
          }}
        />

        {selected ? (
          <>
            <section
              aria-label={`Cuộc trò chuyện với ${selectedParticipantName}`}
              className={`${mobileChat ? 'flex' : 'hidden lg:flex'} min-h-[640px] min-w-0 flex-col overflow-hidden rounded-lg bg-white ring-1 ring-border/80 lg:min-h-0`}
            >
              <ChatHeader
                conversation={selected}
                pinnedItems={pinnedItems}
                currentUserId={currentUserId}
                canReport={Boolean(reportTargetUserId && reportTargetUserId !== currentUserId)}
                onBack={() => setMobileChat(false)}
                onReport={() => setReportOpen(true)}
              />

              {reportNotice ? (
                <div
                  role="status"
                  className="mx-4 mt-3 rounded-md bg-success-soft px-3 py-2 text-xs text-success"
                >
                  <p>{reportNotice}</p>
                </div>
              ) : null}

              <div className="min-h-0 flex-1 space-y-2.5 overflow-y-auto px-4 py-4">
                {pinnedItemsError ? (
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
                ) : null}
                {!pinnedItemsLoading && extraPinnedItems.length ? (
                  <div className="flex max-w-full gap-2 overflow-x-auto pb-1">
                    {extraPinnedItems.map((pinnedItem) => (
                      <button
                        key={pinnedItem.id}
                        type="button"
                        className="flex min-w-[220px] items-center gap-3 rounded-md p-2 text-left ring-1 ring-border/70 transition hover:bg-surface-low"
                        onClick={() => navigate(`/items/${pinnedItem.id}`)}
                      >
                        <ItemImage
                          src={pinnedItem.imageUrl ?? undefined}
                          alt={pinnedItem.title}
                          className="size-12 shrink-0 rounded-md object-cover"
                        />
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
                      progressPath={transactionProgressPath(selected.request.id)}
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
                  <div
                    role="status"
                    className="rounded-md border border-border bg-surface-low px-3 py-2 text-center text-xs text-text-secondary"
                  >
                    Giao dịch đã bị huỷ. Cuộc trò chuyện đang ở chế độ chỉ xem.
                  </div>
                ) : null}
                {messageError ? (
                  <div
                    role="alert"
                    className="flex items-center justify-between gap-3 rounded-md bg-error-soft px-3 py-2 text-xs text-error"
                  >
                    <p>{messageError}</p>
                    <button
                      type="button"
                      className="shrink-0 font-semibold underline"
                      onClick={() => setMessageReloadKey((key) => key + 1)}
                    >
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
                      placeholder={
                        isCancelled
                          ? 'Giao dịch đã huỷ (chỉ xem)'
                          : chatEnabled
                            ? 'Nhập tin nhắn...'
                            : 'Chưa thể nhắn tin'
                      }
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
                  <Button
                    aria-label="Gửi tin nhắn"
                    disabled={
                      sending || !chatEnabled || (!message.trim() && !shareItemId && !chatImage)
                    }
                  >
                    {sending ? 'Đang gửi' : 'Gửi'}
                  </Button>
                </form>
                {chatEnabled && !requestMediaEnabled && !isGiftReceiver ? (
                  <p className="text-2xs text-text-muted">
                    Gửi ảnh được sau khi hai bên chốt lịch hẹn.
                  </p>
                ) : null}
              </div>
            </section>
          </>
        ) : (
          <section className="hidden place-items-center rounded-lg bg-white ring-1 ring-border/80 lg:grid">
            <EmptyState
              title="Chọn cuộc trò chuyện"
              text="Chọn một yêu cầu ở cột bên trái để xem tin nhắn."
            />
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
            navigate(requestConversationPath(selectCandidate.request.id), { replace: true });
          }}
          onViewItem={(itemId) => {
            navigate(productDetailFromRequestPath(itemId, selectCandidate.request.id, true));
          }}
          onConfirm={() => void confirmRequestSelection()}
        />
      ) : null}
      {reportOpen && selected && reportTargetUserId && reportTargetUserId !== currentUserId ? (
        <ReportDialog
          target={{
            targetType: 'USER',
            targetUserId: reportTargetUserId,
            requestId: selected.request.id,
          }}
          contextLabel="Báo cáo người dùng trong yêu cầu này"
          onClose={() => setReportOpen(false)}
          onSuccess={() => {
            setReportOpen(false);
            setReportNotice('Báo cáo đã được gửi đến quản trị viên để xem xét.');
          }}
        />
      ) : null}
      {responseDisputeId !== null ? (
        <DialogFrame>
          <h2 className="text-xl font-bold">Giải trình khiếu nại</h2>
          <p className="mt-1 text-sm text-text-secondary">
            Bạn có 48 giờ kể từ lúc nhận thông báo để gửi giải trình. Giờ hết hạn ghi trong thông
            báo khiếu nại.
          </p>
          {responseNotice ? (
            <div className="mt-5 space-y-4">
              <div className="rounded-md bg-success-soft p-3 text-sm text-success">
                {responseNotice}
              </div>
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
                <p className="mt-2 text-xs text-text-muted">
                  Đã chọn {responseEvidence.length} ảnh.
                </p>
              ) : null}
              {!Number.isSafeInteger(responseDisputeId) || responseDisputeId <= 0 ? (
                <div className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">
                  Đường dẫn khiếu nại không hợp lệ.
                </div>
              ) : responseError ? (
                <div className="mt-4 rounded-md bg-error-soft p-3 text-xs text-error">
                  {responseError}
                </div>
              ) : null}
              <div className="mt-6 flex justify-end gap-2">
                <Button
                  variant="ghost"
                  disabled={responseSubmitting}
                  onClick={() => navigate('/messages')}
                >
                  Hủy
                </Button>
                <Button
                  disabled={
                    responseSubmitting ||
                    !responseNote.trim() ||
                    !Number.isSafeInteger(responseDisputeId) ||
                    responseDisputeId <= 0
                  }
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
