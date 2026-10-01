import { configureStore, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import {
  AI_ASSISTANT_SEARCH_FEE,
  AI_SWAP_MATCHING_FEE,
  TOPUP_MIN_VND,
  CREDIT_TO_VND,
  assertWalletInvariant,
  POST_LISTING_FEE,
  RECEIVE_ITEM_FEE,
  releaseFee,
  spendAvailableCredit,
  spendHeldFee,
  txFee,
} from '../utils/credit';
import { loadPersistedState, resetPersistedState, savePersistedState } from '../utils/storage';
import type {
  AppStateData,
  ComplaintStatus,
  Handover,
  Item,
  KeywordAction,
  RankRule,
  ReputationPointRule,
  Transaction,
} from '../types/domain';
import { canTransition, transitionTransaction } from '../utils/transaction';
import { applyReputationPointEvent, rankFor } from '../utils/reputation';
import { inspectContactMessage, contactSafetyLayer1 } from '../utils/contactSafety';
import { isPasswordHash } from '../utils/passwordSecurity';

const initialState: AppStateData = loadPersistedState();
let idSequence = 0;
const uniqueId = (prefix: string) => `${prefix}_${Date.now()}_${++idSequence}`;
const activeActor = (state: AppStateData, actorId: string) =>
  state.currentUserId === actorId &&
  state.users.some((user) => user.id === actorId && user.status === 'active');
const activeAdmin = (state: AppStateData, adminId: string) =>
  activeActor(state, adminId) &&
  state.users.some((user) => user.id === adminId && user.role === 'admin');
const participant = (state: AppStateData, tx: Transaction, actorId: string) =>
  activeActor(state, actorId) && (tx.ownerId === actorId || tx.requesterId === actorId);
const recalculateUserRanks = (state: AppStateData) => {
  state.users.forEach((user) => {
    if (user.role !== 'admin') user.rank = rankFor(user.rewardPoints, state.ranks);
  });
};
const itemApproved = (status: Item['status']) => status === 'approved' || status === 'APPROVED';
const itemPendingReview = (status: Item['status']) => status === 'pending' || status === 'PENDING_REVIEW';
const itemExpired = (item: Item, now = new Date()) =>
  itemApproved(item.status) && new Date(item.expiresAt).getTime() < now.getTime();
const itemAvailableForRequest = (item: Item, now = new Date()) =>
  itemApproved(item.status) && new Date(item.expiresAt).getTime() >= now.getTime();
const activeTransactionForItem = (state: AppStateData, itemId: string) =>
  state.transactions.some(
    (tx) =>
      (tx.itemId === itemId || tx.offeredItemId === itemId || tx.sourceItemId === itemId) &&
      !['COMPLETED', 'CANCELLED', 'DISPUTED'].includes(tx.status),
  );
const reopenableItem = (item: Item) =>
  new Date(item.expiresAt).getTime() >= Date.now() &&
  item.status !== 'expired' &&
  item.status !== 'removed' &&
  item.status !== 'VIOLATION';
const reputationBlocked = (state: AppStateData, userId: string) =>
  state.users.find((user) => user.id === userId)?.reputationStars === 0;

const addSystemMessage = (state: AppStateData, transactionId: string, text: string) => {
  const conv = state.conversations.find((entry) => entry.transactionId === transactionId);
  if (!conv) return;
  state.messages.push({
    id: uniqueId('msg'),
    convId: conv.id,
    sender: 'system',
    type: 'system',
    text,
    time: 'Bay gio',
  });
  conv.lastMessage = text;
  conv.lastMessageAt = new Date().toISOString();
};

const openTransactionConversation = (
  state: AppStateData,
  tx: Transaction,
  lastMessage: string,
) => {
  let conv = state.conversations.find((entry) => entry.transactionId === tx.id);
  if (conv) return conv;
  conv = {
    id: uniqueId('conv'),
    transactionId: tx.id,
    participantIds: [tx.requesterId, tx.ownerId],
    itemId: tx.itemId,
    lastMessage,
    lastMessageAt: new Date().toISOString(),
    unreadCount: 0,
  };
  state.conversations.unshift(conv);
  state.messages.push({
    id: uniqueId('msg'),
    convId: conv.id,
    sender: 'system',
    type: 'system',
    text: lastMessage,
    time: 'Bây giờ',
  });
  return conv;
};

const mondayKey = (date = new Date()) => {
  const copy = new Date(date);
  const day = copy.getDay() || 7;
  copy.setDate(copy.getDate() - day + 1);
  return copy.toISOString().slice(0, 10);
};

const dataSlice = createSlice({
  name: 'data',
  initialState,
  reducers: {
    login(state, action: PayloadAction<{ username: string; passwordHash: string }>) {
      const user = state.users.find(
        (item) =>
          item.username === action.payload.username &&
          item.password === action.payload.passwordHash &&
          item.status !== 'locked',
      );
      state.currentUserId = user?.status === 'active' ? user.id : null;
    },
    demoLogin(state, action: PayloadAction<{ username: 'user' | 'admin'; role: 'user' | 'admin' }>) {
      const user = state.users.find(
        (entry) =>
          entry.username === action.payload.username &&
          entry.role === action.payload.role &&
          entry.status === 'active',
      );
      state.currentUserId = user?.id ?? null;
    },
    logout(state) {
      state.currentUserId = null;
    },
    register(
      state,
      action: PayloadAction<{
        name: string;
        username: string;
        email: string;
        passwordHash: string;
        district: string;
        phone: string;
      }>,
    ) {
      const id = `user_${Date.now()}`;
      state.users.push({
        id,
        username: action.payload.username,
        password: action.payload.passwordHash,
        name: action.payload.name,
        email: action.payload.email,
        phone: action.payload.phone,
        district: action.payload.district,
        avatarInitials: action.payload.name
          .split(' ')
          .map((p) => p[0])
          .slice(-2)
          .join('')
          .toUpperCase(),
        totalCredit: 2000,
        availableCredit: 2000,
        holdCredit: 0,
        rewardPoints: 0,
        reputationStars: 5,
        rank: 'Thành viên mới',
        status: 'active',
        role: 'user',
        joinedAt: new Date().toISOString(),
        totalTx: 0,
      });
      state.currentUserId = id;
    },
    startRegistration(
      state,
      action: PayloadAction<{
        registration: {
          name: string;
          username: string;
          email: string;
          phone: string;
          district: string;
          passwordHash: string;
        };
        verificationId: string;
        otpHash: string;
        expiresAt: string;
        resendAvailableAt: string;
      }>,
    ) {
      if (!isPasswordHash(action.payload.registration.passwordHash) || !isPasswordHash(action.payload.otpHash)) return;
      const username = action.payload.registration.username.trim();
      const email = action.payload.registration.email.trim().toLowerCase();
      const duplicate = state.users.some(
        (user) =>
          user.username.toLowerCase() === username.toLowerCase() ||
          user.email.toLowerCase() === email,
      );
      if (duplicate) return;
      state.emailVerification = {
        id: action.payload.verificationId,
        registration: {
          ...action.payload.registration,
          username,
          email,
          name: action.payload.registration.name.trim(),
          phone: action.payload.registration.phone.trim(),
        },
        otpHash: action.payload.otpHash,
        expiresAt: action.payload.expiresAt,
        resendAvailableAt: action.payload.resendAvailableAt,
        attempts: 0,
      };
    },
    resendRegistrationOtp(
      state,
      action: PayloadAction<{ verificationId: string; otpHash: string; expiresAt: string; resendAvailableAt: string }>,
    ) {
      const verification = state.emailVerification;
      if (!verification || verification.id !== action.payload.verificationId || Date.now() < new Date(verification.resendAvailableAt).getTime()) return;
      if (!isPasswordHash(action.payload.otpHash)) return;
      verification.otpHash = action.payload.otpHash;
      verification.expiresAt = action.payload.expiresAt;
      verification.resendAvailableAt = action.payload.resendAvailableAt;
      verification.attempts = 0;
    },
    verifyRegistrationOtp(state, action: PayloadAction<{ verificationId: string; otpHash: string }>) {
      const verification = state.emailVerification;
      if (!verification || verification.id !== action.payload.verificationId || Date.now() > new Date(verification.expiresAt).getTime() || verification.attempts >= 5) return;
      if (verification.otpHash !== action.payload.otpHash) {
        verification.attempts += 1;
        return;
      }
      const duplicate = state.users.some(
        (user) =>
          user.username.toLowerCase() === verification.registration.username.toLowerCase() ||
          user.email.toLowerCase() === verification.registration.email.toLowerCase(),
      );
      if (duplicate) return;
      const id = `user_${Date.now()}`;
      state.users.push({
        id,
        username: verification.registration.username,
        password: verification.registration.passwordHash,
        name: verification.registration.name,
        email: verification.registration.email,
        phone: verification.registration.phone,
        district: verification.registration.district,
        avatarInitials: verification.registration.name
          .split(' ')
          .map((p) => p[0])
          .slice(-2)
          .join('')
          .toUpperCase(),
        totalCredit: 2000,
        availableCredit: 2000,
        holdCredit: 0,
        rewardPoints: 0,
        reputationStars: 5,
        rank: 'Thành viên mới',
        status: 'active',
        role: 'user',
        joinedAt: new Date().toISOString(),
        totalTx: 0,
      });
      state.emailVerification = undefined;
      state.currentUserId = null;
    },
    changePassword(
      state,
      action: PayloadAction<{ userId: string; currentPasswordHash: string; newPasswordHash: string }>,
    ) {
      const user = state.users.find((entry) => entry.id === action.payload.userId);
      if (
        !user ||
        state.currentUserId !== user.id ||
        !isPasswordHash(action.payload.currentPasswordHash) ||
        !isPasswordHash(action.payload.newPasswordHash) ||
        user.password !== action.payload.currentPasswordHash ||
        user.password === action.payload.newPasswordHash
      ) return;
      user.password = action.payload.newPasswordHash;
    },
    requestPasswordReset(
      state,
      action: PayloadAction<{ email: string; otpHash: string; resetId: string; resetToken: string; expiresAt: string; resendAvailableAt: string }>,
    ) {
      state.passwordReset = undefined;
      const user = state.users.find((entry) => entry.email.toLowerCase() === action.payload.email.trim().toLowerCase());
      if (!isPasswordHash(action.payload.otpHash)) return;
      state.passwordReset = {
        id: action.payload.resetId,
        email: action.payload.email.trim().toLowerCase(),
        userId: user?.id,
        otpHash: action.payload.otpHash,
        expiresAt: action.payload.expiresAt,
        resendAvailableAt: action.payload.resendAvailableAt,
        attempts: 0,
        verified: false,
        resetToken: action.payload.resetToken,
      };
    },
    resendPasswordOtp(
      state,
      action: PayloadAction<{ resetId: string; otpHash: string; expiresAt: string; resendAvailableAt: string }>,
    ) {
      const reset = state.passwordReset;
      if (!reset || reset.id !== action.payload.resetId || reset.verified || Date.now() < new Date(reset.resendAvailableAt).getTime()) return;
      if (!isPasswordHash(action.payload.otpHash)) return;
      reset.otpHash = action.payload.otpHash;
      reset.expiresAt = action.payload.expiresAt;
      reset.resendAvailableAt = action.payload.resendAvailableAt;
      reset.attempts = 0;
    },
    verifyPasswordOtp(state, action: PayloadAction<{ resetId: string; otpHash: string }>) {
      const reset = state.passwordReset;
      if (!reset || reset.id !== action.payload.resetId || reset.verified || Date.now() > new Date(reset.expiresAt).getTime() || reset.attempts >= 5) return;
      if (reset.otpHash !== action.payload.otpHash) {
        reset.attempts += 1;
        return;
      }
      reset.otpHash = '';
      reset.verified = true;
    },
    resetPassword(
      state,
      action: PayloadAction<{ resetId: string; resetToken: string; newPasswordHash: string }>,
    ) {
      const reset = state.passwordReset;
      const user = state.users.find((entry) => entry.id === reset?.userId);
      if (!reset || !user || reset.id !== action.payload.resetId || reset.resetToken !== action.payload.resetToken || !reset.verified || !isPasswordHash(action.payload.newPasswordHash)) return;
      user.password = action.payload.newPasswordHash;
      state.passwordReset = undefined;
      state.currentUserId = null;
    },
    addItem(state, action: PayloadAction<Omit<Item, 'id' | 'postedAt' | 'expiresAt' | 'status'>>) {
      if (reputationBlocked(state, action.payload.ownerId)) return;
      const owner = state.users.find((entry) => entry.id === action.payload.ownerId);
      if (!owner || (action.payload.type === 'trade' && owner.availableCredit < POST_LISTING_FEE)) return;
      const now = new Date();
      const exp = new Date(now);
      exp.setDate(exp.getDate() + 30);
      const combined = `${action.payload.title} ${action.payload.description} ${action.payload.tradeFor ?? ''}`;
      const fixedRuleViolation = contactSafetyLayer1(combined).blocked ||
        state.keywords.some((keyword) =>
          keyword.action === 'block' && combined.toLocaleLowerCase().includes(keyword.keyword.toLocaleLowerCase()),
        ) ||
        action.payload.title.trim().length < 3 ||
        action.payload.description.trim().length < 10 ||
        !action.payload.images.length;
      const itemId = `item_${Date.now()}`;
      if (action.payload.type === 'trade') {
        const paid = spendAvailableCredit(state, {
          userId: owner.id,
          amount: POST_LISTING_FEE,
          type: 'SPEND',
          ref: `post:${itemId}:fee`,
          description: `Phí đăng bài - ${action.payload.title.trim()}`,
        });
        if (!paid) return;
      }
      state.items.unshift({
        ...action.payload,
        id: itemId,
        status: fixedRuleViolation ? 'VIOLATION' : 'PENDING_REVIEW',
        moderationReason: fixedRuleViolation
          ? 'Bài đăng không vượt qua kiểm tra tự động. Vui lòng rà soát thông tin liên hệ, nội dung và hình ảnh.'
          : undefined,
        postedAt: now.toISOString(),
        expiresAt: exp.toISOString(),
        postApprovalEditCount: 0,
      });
    },
    updateItem(
      state,
      action: PayloadAction<{
        itemId: string;
        ownerId: string;
        changes: Pick<
          Item,
          'title' | 'description' | 'category' | 'condition' | 'district' | 'tradeFor'
        >;
      }>,
    ) {
      const item = state.items.find(
        (entry) => entry.id === action.payload.itemId && entry.ownerId === action.payload.ownerId,
      );
      if (!item) return;
      if (itemApproved(item.status)) {
        const count = item.postApprovalEditCount ?? 0;
        if (count >= 1) return;
        item.postApprovalEditCount = count + 1;
      } else if (!itemPendingReview(item.status) && item.status !== 'rejected' && item.status !== 'REJECTED' && item.status !== 'VIOLATION') {
        return;
      }
      Object.assign(item, action.payload.changes);
      item.status = 'PENDING_REVIEW';
      item.rejectionReason = undefined;
      item.moderationReason = undefined;
    },
    removeItem(state, action: PayloadAction<{ itemId: string; ownerId: string }>) {
      const item = state.items.find(
        (entry) => entry.id === action.payload.itemId && entry.ownerId === action.payload.ownerId,
      );
      if (item) item.status = 'removed';
    },
    renewItem(state, action: PayloadAction<{ itemId: string; ownerId: string }>) {
      const item = state.items.find(
        (entry) => entry.id === action.payload.itemId && entry.ownerId === action.payload.ownerId,
      );
      if (!item) return;
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 30);
      item.expiresAt = expiresAt.toISOString();
      item.status = 'PENDING_REVIEW';
    },
    updateProfile(
      state,
      action: PayloadAction<{
        userId: string;
        name: string;
        email: string;
        phone: string;
        district: string;
        avatarUrl?: string;
      }>,
    ) {
      const user = state.users.find((entry) => entry.id === action.payload.userId);
      if (!user) return;
      user.name = action.payload.name;
      user.email = action.payload.email;
      user.phone = action.payload.phone;
      user.district = action.payload.district;
      user.avatarInitials = action.payload.name
        .split(' ')
        .map((part) => part[0])
        .slice(-2)
        .join('')
        .toUpperCase();
      if (action.payload.avatarUrl) user.avatarUrl = action.payload.avatarUrl;
    },
    updateItemStatus(
      state,
      action: PayloadAction<{
        itemId: string;
        status: Item['status'];
        adminId: string;
        rejectionReason?: string;
      }>,
    ) {
      const item = state.items.find((entry) => entry.id === action.payload.itemId);
      if (!item || !activeAdmin(state, action.payload.adminId)) return;
      const rejectionReason = action.payload.rejectionReason?.trim();
      if ((action.payload.status === 'rejected' || action.payload.status === 'REJECTED') && !rejectionReason) return;
      const nextStatus = action.payload.status === 'approved' ? 'APPROVED' :
        action.payload.status === 'rejected' ? 'REJECTED' : action.payload.status;
      const previousStatus = item.status;
      // Admin moderation only changes listing status. Any listing fee is charged
      // when addItem creates the pending trade listing.
      item.status = nextStatus;
      if (nextStatus === 'APPROVED') {
        const expiresAt = new Date();
        expiresAt.setDate(expiresAt.getDate() + 30);
        item.expiresAt = expiresAt.toISOString();
      }
      item.rejectionReason = nextStatus === 'REJECTED' ? rejectionReason : undefined;
      item.moderationReason = rejectionReason;
      state.auditLogs.unshift({
        id: `log_${Date.now()}`,
        adminId: action.payload.adminId,
        action: nextStatus === 'APPROVED' ? 'POST_APPROVED' : nextStatus === 'REJECTED' ? 'POST_REJECTED' : 'POST_STATUS_UPDATED',
        targetType: 'item',
        targetId: item.id,
        detail: `Cập nhật trạng thái bài đăng: ${item.title}`,
        previousValue: previousStatus,
        newValue: nextStatus,
        reason: rejectionReason,
        createdAt: new Date().toISOString(),
      });
      if (
        (action.payload.status === 'rejected' || action.payload.status === 'REJECTED') &&
        item.rejectionReason &&
        /vi phạm|bị cấm|cam|vi pham/i.test(item.rejectionReason)
      ) {
        applyReputationPointEvent(state, {
          userId: item.ownerId,
          key: 'content_violation',
          ref: `${item.id}:content_violation`,
        });
      }
    },
    createTransaction(
      state,
      action: PayloadAction<{
        itemId: string;
        requesterId: string;
        offeredItemId?: string;
        sourceItemId?: string;
      }>,
    ) {
      state.items.forEach((entry) => {
        if (itemExpired(entry)) entry.status = 'expired';
      });
      const item = state.items.find((entry) => entry.id === action.payload.itemId);
      const offeredItemId = action.payload.offeredItemId ?? action.payload.sourceItemId;
      const offeredItem = offeredItemId
        ? state.items.find((entry) => entry.id === offeredItemId)
        : undefined;
      if (
        !item ||
        item.ownerId === action.payload.requesterId ||
        !itemApproved(item.status) ||
        reputationBlocked(state, action.payload.requesterId) ||
        (offeredItem &&
          (offeredItem.ownerId !== action.payload.requesterId ||
            offeredItem.type !== 'trade' ||
            !itemApproved(offeredItem.status) ||
            item.type !== 'trade')) ||
        !activeActor(state, action.payload.requesterId)
      )
        return;
      const tx: Transaction = {
        id: uniqueId('tx'),
        itemId: item.id,
        offeredItemId: offeredItem?.id,
        sourceItemId: offeredItem?.id,
        requesterId: action.payload.requesterId,
        ownerId: item.ownerId,
        type: item.type,
        feeCredit: txFee(state),
        feeCharged: false,
        status: 'NEGOTIATING',
        creditHeldBy: [],
        creditHeld: false,
        feeCaptured: false,
        ownerScheduleConfirmed: false,
        requesterScheduleConfirmed: false,
        completedByOwner: false,
        completedByRequester: false,
        senderConfirmed: false,
        receiverConfirmed: false,
        ownerEvidence: [],
        requesterEvidence: [],
        senderEvidence: [],
        receiverEvidence: [],
        createdAt: new Date().toISOString(),
      };
      state.transactions.unshift(tx);
    },
    createItemRequest(
      state,
      action: PayloadAction<{
        itemId: string;
        requesterId: string;
        message: string;
        offeredItemId?: string;
      }>,
    ) {
      state.items.forEach((entry) => {
        if (itemExpired(entry)) entry.status = 'expired';
      });
      const item = state.items.find((entry) => entry.id === action.payload.itemId);
      const requester = state.users.find((entry) => entry.id === action.payload.requesterId);
      if (
        !item ||
        !requester ||
        item.ownerId === requester.id ||
        !itemAvailableForRequest(item) ||
        activeTransactionForItem(state, item.id) ||
        reputationBlocked(state, requester.id) ||
        !activeActor(state, requester.id)
      )
        return;
      const duplicate = state.itemRequests.some(
        (request) =>
          request.itemId === item.id &&
          request.requesterId === requester.id &&
          (request.status === 'PENDING' || request.status === 'ACCEPTED'),
      );
      if (duplicate) return;
      const offeredItem = action.payload.offeredItemId
        ? state.items.find((entry) => entry.id === action.payload.offeredItemId)
        : undefined;
      if (item.type === 'trade') {
        if (
          !offeredItem ||
          offeredItem.ownerId !== requester.id ||
          !itemAvailableForRequest(offeredItem) ||
          activeTransactionForItem(state, offeredItem.id)
        )
          return;
      }
      state.itemRequests.unshift({
        id: uniqueId('req'),
        type: item.type,
        itemId: item.id,
        offeredItemId: item.type === 'trade' ? offeredItem?.id : undefined,
        requesterId: requester.id,
        status: 'PENDING',
        message: action.payload.message.trim(),
        createdAt: new Date().toISOString(),
      });
    },
    acceptItemRequest(state, action: PayloadAction<{ requestId: string; ownerId: string }>) {
      const request = state.itemRequests.find((entry) => entry.id === action.payload.requestId);
      const item = state.items.find((entry) => entry.id === request?.itemId);
      if (
        !request ||
        !item ||
        item.ownerId !== action.payload.ownerId ||
        request.status !== 'PENDING' ||
        !activeActor(state, action.payload.ownerId) ||
        activeTransactionForItem(state, item.id)
      )
        return;
      const offeredItem = request.offeredItemId
        ? state.items.find((entry) => entry.id === request.offeredItemId)
        : undefined;
      if (
        request.type === 'trade' &&
        (!offeredItem ||
          offeredItem.ownerId !== request.requesterId ||
          !itemAvailableForRequest(offeredItem) ||
          activeTransactionForItem(state, offeredItem.id))
      )
        return;
      const transactionId = uniqueId('tx');
      if (request.type === 'gift') {
        const paid = spendAvailableCredit(state, {
          userId: request.requesterId,
          amount: RECEIVE_ITEM_FEE,
          type: 'RECEIVE_FEE',
          ref: `${transactionId}:receive_fee`,
          description: `PhĂ­ nháº­n Ä‘á»“ - ${item.title}`,
        });
        if (!paid) return;
      }
      request.status = 'ACCEPTED';
      state.itemRequests.forEach((entry) => {
        if (entry.itemId === item.id && entry.id !== request.id && entry.status === 'PENDING') {
          entry.status = 'NOT_SELECTED';
        }
      });
      item.status = 'IN_TRANSACTION';
      const tx: Transaction = {
        id: transactionId,
        itemId: item.id,
        offeredItemId: request.offeredItemId,
        sourceItemId: request.offeredItemId,
        selectedRequestId: request.id,
        requesterId: request.requesterId,
        ownerId: item.ownerId,
        type: item.type,
        feeCredit: txFee(state),
        feeCharged: request.type === 'gift',
        status: 'NEGOTIATING',
        creditHeldBy: [],
        creditHeld: false,
        feeCaptured: false,
        ownerScheduleConfirmed: false,
        requesterScheduleConfirmed: false,
        completedByOwner: false,
        completedByRequester: false,
        senderConfirmed: false,
        receiverConfirmed: false,
        ownerEvidence: [],
        requesterEvidence: [],
        senderEvidence: [],
        receiverEvidence: [],
        createdAt: new Date().toISOString(),
      };
      state.transactions.unshift(tx);
      openTransactionConversation(
        state,
        tx,
        'Chủ bài đăng đã chọn yêu cầu này. Hai bên có thể nhắn tin trong ShareLoop.',
      );
    },
    refreshExpiredItems(state) {
      state.items.forEach((item) => {
        if (itemExpired(item)) item.status = 'expired';
      });
    },
    selectTransaction(state, action: PayloadAction<{ transactionId: string; ownerId: string }>) {
      const tx = state.transactions.find((entry) => entry.id === action.payload.transactionId);
      if (!tx || tx.ownerId !== action.payload.ownerId || !activeActor(state, action.payload.ownerId))
        return;
      openTransactionConversation(
        state,
        tx,
        'Chủ bài đăng đã chọn bạn để giao dịch. Hai bên có thể nhắn tin trong ShareLoop.',
      );
    },
    proposeHandover(state, action: PayloadAction<Omit<Handover, 'id' | 'status'>>) {
      const tx = state.transactions.find((entry) => entry.id === action.payload.transactionId);
      if (
        !tx ||
        !participant(state, tx, action.payload.proposedBy) ||
        !canTransition(tx, 'SCHEDULE_PROPOSED')
      )
        return;
      const id = uniqueId('ho');
      state.handovers.push({ ...action.payload, id, status: 'proposed' });
      tx.handoverId = id;
      tx.ownerScheduleConfirmed = tx.ownerId === action.payload.proposedBy;
      tx.requesterScheduleConfirmed = tx.requesterId === action.payload.proposedBy;
      transitionTransaction(tx, 'SCHEDULE_PROPOSED');
      const conv =
        state.conversations.find((entry) => entry.transactionId === tx.id) ??
        (action.payload.proposedBy === tx.ownerId
          ? openTransactionConversation(
              state,
              tx,
              'Chủ bài đăng đã chọn bạn để giao dịch. Hai bên có thể nhắn tin trong ShareLoop.',
            )
          : undefined);
      if (conv)
        state.messages.push({
          id: uniqueId('msg'),
          convId: conv.id,
          sender: action.payload.proposedBy,
          type: 'handover_card',
          handoverId: id,
          text: 'Đề xuất lịch giao nhận',
          time: 'Bây giờ',
        });
    },
    acceptHandover(state, action: PayloadAction<{ handoverId: string; userId: string }>) {
      const ho = state.handovers.find((entry) => entry.id === action.payload.handoverId);
      const tx = state.transactions.find((entry) => entry.id === ho?.transactionId);
      if (
        !ho ||
        !tx ||
        !participant(state, tx, action.payload.userId) ||
        ho.status !== 'proposed' ||
        ho.id !== tx.handoverId ||
        ho.proposedBy === action.payload.userId ||
        !canTransition(tx, 'SCHEDULE_CONFIRMED')
      )
        return;
      ho.status = 'confirmed';
      ho.agreedBy = action.payload.userId;
      const scheduledAt = new Date(`${ho.date}T${ho.time || '00:00'}`);
      transitionTransaction(tx, 'SCHEDULE_CONFIRMED');
      tx.ownerScheduleConfirmed = true;
      tx.requesterScheduleConfirmed = true;
      tx.creditHeldBy = [];
      tx.creditHeld = false;
      tx.reminderDay0At = scheduledAt.toISOString();
      const reminderDay3 = new Date(scheduledAt);
      reminderDay3.setDate(reminderDay3.getDate() + 3);
      tx.reminderDay3At = reminderDay3.toISOString();
      const autoConfirm = new Date(scheduledAt);
      autoConfirm.setDate(autoConfirm.getDate() + 7);
      tx.autoConfirmAt = autoConfirm.toISOString();
      const silentCancel = new Date(scheduledAt);
      silentCancel.setDate(silentCancel.getDate() + 20);
      tx.silentCancelAt = silentCancel.toISOString();
      transitionTransaction(tx, 'WAITING_HANDOVER');
      applyReputationPointEvent(state, {
        userId: tx.ownerId,
        key: 'handover_confirmed_on_time',
        ref: `${tx.id}:${tx.ownerId}:handover_confirmed_on_time`,
      });
      applyReputationPointEvent(state, {
        userId: tx.requesterId,
        key: 'handover_confirmed_on_time',
        ref: `${tx.id}:${tx.requesterId}:handover_confirmed_on_time`,
      });
      addSystemMessage(state, tx.id, 'Lịch đã được xác nhận. Thông tin liên hệ đã mở khóa.');
    },
    confirmTransactionSide(
      state,
      action: PayloadAction<{ transactionId: string; userId: string; evidence?: string[] }>,
    ) {
      const tx = state.transactions.find((entry) => entry.id === action.payload.transactionId);
      if (
        !tx ||
        !participant(state, tx, action.payload.userId) ||
        !['WAITING_HANDOVER', 'SENDER_CONFIRMED', 'RECEIVER_CONFIRMED'].includes(tx.status)
      )
        return;
      if (action.payload.userId === tx.ownerId) {
        if (tx.completedByOwner) return;
        if (!canTransition(tx, tx.completedByRequester ? 'COMPLETED' : 'SENDER_CONFIRMED')) return;
        tx.completedByOwner = true;
        tx.senderConfirmed = true;
        tx.ownerEvidence = action.payload.evidence ?? [];
        tx.senderEvidence = tx.ownerEvidence;
        if (tx.ownerEvidence.length) {
          tx.evidenceRecords = [
            ...(tx.evidenceRecords ?? []),
            {
              id: uniqueId('evi'),
              userId: action.payload.userId,
              files: tx.ownerEvidence,
              createdAt: new Date().toISOString(),
            },
          ];
        }
        if (tx.completedByRequester) {
          if (spendHeldFee(state, tx.id)) {
            const item = state.items.find((entry) => entry.id === tx.itemId);
            if (item) item.status = 'COMPLETED';
          } else {
            tx.completedByOwner = false;
            tx.senderConfirmed = false;
            tx.ownerEvidence = [];
            tx.senderEvidence = [];
          }
        } else transitionTransaction(tx, 'SENDER_CONFIRMED');
      } else {
        if (tx.completedByRequester) return;
        if (!canTransition(tx, tx.completedByOwner ? 'COMPLETED' : 'RECEIVER_CONFIRMED')) return;
        tx.completedByRequester = true;
        tx.receiverConfirmed = true;
        tx.requesterEvidence = action.payload.evidence ?? [];
        tx.receiverEvidence = tx.requesterEvidence;
        if (tx.requesterEvidence.length) {
          tx.evidenceRecords = [
            ...(tx.evidenceRecords ?? []),
            {
              id: uniqueId('evi'),
              userId: action.payload.userId,
              files: tx.requesterEvidence,
              createdAt: new Date().toISOString(),
            },
          ];
        }
        if (tx.completedByOwner) {
          if (spendHeldFee(state, tx.id)) {
            const item = state.items.find((entry) => entry.id === tx.itemId);
            if (item) item.status = 'COMPLETED';
          } else {
            tx.completedByRequester = false;
            tx.receiverConfirmed = false;
            tx.requesterEvidence = [];
            tx.receiverEvidence = [];
          }
        } else transitionTransaction(tx, 'RECEIVER_CONFIRMED');
      }
    },
    addKeyword(
      state,
      action: PayloadAction<{ adminId: string; keyword: string; action: KeywordAction }>,
    ) {
      if (!activeAdmin(state, action.payload.adminId)) return;
      const keyword = action.payload.keyword.trim();
      if (!keyword || state.keywords.some((entry) => entry.keyword.toLowerCase() === keyword.toLowerCase()))
        return;
      const id = uniqueId('kw');
      state.keywords.unshift({ id, keyword, detections: 0, action: action.payload.action });
      state.auditLogs.unshift({
        id: uniqueId('log'),
        adminId: action.payload.adminId,
        action: 'add_keyword',
        targetType: 'keyword',
        targetId: id,
        detail: `Thêm từ khóa cấm: ${keyword}`,
        createdAt: new Date().toISOString(),
      });
    },
    updateKeyword(
      state,
      action: PayloadAction<{ adminId: string; id: string; keyword: string; action: KeywordAction }>,
    ) {
      if (!activeAdmin(state, action.payload.adminId)) return;
      const entry = state.keywords.find((item) => item.id === action.payload.id);
      const keyword = action.payload.keyword.trim();
      if (
        !entry ||
        !keyword ||
        state.keywords.some(
          (item) => item.id !== entry.id && item.keyword.toLowerCase() === keyword.toLowerCase(),
        )
      )
        return;
      entry.keyword = keyword;
      entry.action = action.payload.action;
    },
    deleteKeyword(state, action: PayloadAction<{ adminId: string; id: string }>) {
      if (!activeAdmin(state, action.payload.adminId)) return;
      state.keywords = state.keywords.filter((entry) => entry.id !== action.payload.id);
    },
    addDistrict(state, action: PayloadAction<{ adminId: string; name: string }>) {
      if (!activeAdmin(state, action.payload.adminId)) return;
      const name = action.payload.name.trim();
      if (!name || state.districts.some((entry) => entry.name.toLowerCase() === name.toLowerCase()))
        return;
      state.districts.push({ id: uniqueId('dist'), name, status: 'active' });
    },
    updateDistrict(state, action: PayloadAction<{ adminId: string; id: string; name: string }>) {
      if (!activeAdmin(state, action.payload.adminId)) return;
      const entry = state.districts.find((item) => item.id === action.payload.id);
      const name = action.payload.name.trim();
      if (
        !entry ||
        !name ||
        state.districts.some(
          (item) => item.id !== entry.id && item.name.toLowerCase() === name.toLowerCase(),
        )
      )
        return;
      const oldName = entry.name;
      entry.name = name;
      state.users.forEach((user) => {
        if (user.district === oldName) user.district = name;
      });
      state.items.forEach((item) => {
        if (item.district === oldName) item.district = name;
      });
      state.handovers.forEach((handover) => {
        if (handover.district === oldName) handover.district = name;
      });
    },
    deleteDistrict(state, action: PayloadAction<{ adminId: string; id: string }>) {
      if (!activeAdmin(state, action.payload.adminId)) return;
      state.districts = state.districts.filter((entry) => entry.id !== action.payload.id);
    },
    createComplaint(
      state,
      action: PayloadAction<{
        transactionId: string;
        reporterId: string;
        reason: string;
        content: string;
        evidence: string[];
      }>,
    ) {
      const tx = state.transactions.find((entry) => entry.id === action.payload.transactionId);
      if (!tx || !participant(state, tx, action.payload.reporterId)) return;
      const now = Date.now();
      if (
        tx.status !== 'COMPLETED' ||
        !tx.complaintDeadline ||
        now > new Date(tx.complaintDeadline).getTime() ||
        state.complaints.some(
          (entry) =>
            entry.transactionId === tx.id &&
            entry.reporterId === action.payload.reporterId &&
            !['resolved', 'rejected'].includes(entry.status),
        )
      )
        return;
      const reason = action.payload.reason.trim();
      const content = action.payload.content.trim();
      if (!reason || !content) return;
      const reportedUserId = tx.ownerId === action.payload.reporterId ? tx.requesterId : tx.ownerId;
      const responseDueAt = new Date();
      responseDueAt.setDate(responseDueAt.getDate() + 3);
      state.complaints.unshift({
        id: uniqueId('cmp'),
        reporterId: action.payload.reporterId,
        reportedUserId,
        transactionId: tx.id,
        reason,
        content,
        evidence: action.payload.evidence,
        createdAt: new Date().toISOString(),
        status: 'received',
        responseDueAt: responseDueAt.toISOString(),
      });
    },
    respondToComplaint(
      state,
      action: PayloadAction<{
        complaintId: string;
        respondentId: string;
        response: string;
        evidence?: string[];
      }>,
    ) {
      const complaint = state.complaints.find((entry) => entry.id === action.payload.complaintId);
      if (
        !complaint ||
        complaint.reportedUserId !== action.payload.respondentId ||
        !activeActor(state, action.payload.respondentId) ||
        !action.payload.response.trim() ||
        ['resolved', 'rejected', 'violation_confirmed'].includes(complaint.status)
      )
        return;
      complaint.response = action.payload.response.trim();
      complaint.responseEvidence = action.payload.evidence ?? [];
      complaint.responseSubmittedAt = new Date().toISOString();
    },
    updateComplaintStatus(
      state,
      action: PayloadAction<{
        adminId: string;
        complaintId: string;
        status: ComplaintStatus;
        adminNote?: string;
        resolution?: string;
      }>,
    ) {
      if (!activeAdmin(state, action.payload.adminId)) return;
      const complaint = state.complaints.find((entry) => entry.id === action.payload.complaintId);
      if (!complaint) return;
      const allowed =
        (complaint.status === 'received' && action.payload.status === 'processing') ||
        (complaint.status === 'processing' &&
          (action.payload.status === 'violation_confirmed' || action.payload.status === 'rejected')) ||
        complaint.status === action.payload.status;
      if (!allowed) return;
      complaint.status = action.payload.status;
      complaint.adminNote = action.payload.adminNote ?? complaint.adminNote;
      complaint.resolution = action.payload.resolution ?? complaint.resolution;
      if (action.payload.status === 'violation_confirmed') {
        complaint.resolvedAt = new Date().toISOString();
        applyReputationPointEvent(state, {
          userId: complaint.reportedUserId,
          key: 'valid_complaint',
          ref: `${complaint.id}:valid_complaint`,
        });
      } else if (action.payload.status === 'rejected') {
        complaint.resolvedAt = new Date().toISOString();
      }
    },
    adminAdjustReputationStars(
      state,
      action: PayloadAction<{
        adminId: string;
        userId: string;
        change: number;
        reason: string;
      }>,
    ) {
      const target = state.users.find((entry) => entry.id === action.payload.userId);
      if (
        !target ||
        target.role === 'admin' ||
        !activeAdmin(state, action.payload.adminId) ||
        !Number.isFinite(action.payload.change) ||
        action.payload.change === 0 ||
        !action.payload.reason.trim()
      )
        return;
      const previous = target.reputationStars;
      target.reputationStars = Math.max(0, Math.min(5, Number((target.reputationStars + action.payload.change).toFixed(1))));
      state.auditLogs.unshift({
        id: uniqueId('log'),
        adminId: action.payload.adminId,
        action: 'adjust_reputation_stars',
        targetType: 'user',
        targetId: target.id,
        detail: action.payload.reason.trim(),
        previousValue: String(previous),
        newValue: String(target.reputationStars),
        createdAt: new Date().toISOString(),
      });
    },
    restoreOneReputationStar(
      state,
      action: PayloadAction<{ adminId: string; userId: string; reason: string }>,
    ) {
      const target = state.users.find((entry) => entry.id === action.payload.userId);
      if (
        !target ||
        target.role === 'admin' ||
        !activeAdmin(state, action.payload.adminId) ||
        target.reputationStars > 0 ||
        target.reputationRestoreUsed ||
        !action.payload.reason.trim()
      )
        return;
      target.reputationStars = 1;
      target.reputationRestoreUsed = true;
      state.auditLogs.unshift({
        id: uniqueId('log'),
        adminId: action.payload.adminId,
        action: 'restore_one_reputation_star',
        targetType: 'user',
        targetId: target.id,
        detail: action.payload.reason.trim(),
        previousValue: '0',
        newValue: '1',
        createdAt: new Date().toISOString(),
      });
    },
    updateRankRule(
      state,
      action: PayloadAction<{
        adminId: string;
        id: string;
        name: string;
        minPoints: number;
        maxPoints?: number;
        benefits?: string;
        status?: RankRule['status'];
      }>,
    ) {
      if (!activeAdmin(state, action.payload.adminId)) return;
      const rank = state.ranks.find((entry) => entry.id === action.payload.id);
      if (!rank || !action.payload.name.trim() || action.payload.minPoints < 0) return;
      const nextRanks = state.ranks.map((entry) =>
        entry.id === rank.id
          ? {
              ...entry,
              name: action.payload.name.trim(),
              minPoints: action.payload.minPoints,
              maxPoints: action.payload.maxPoints,
              benefits: action.payload.benefits?.trim(),
              status: action.payload.status ?? 'active',
            }
          : entry,
      );
      const valid = nextRanks.every((entry, index) =>
        nextRanks.every((other, otherIndex) => {
          if (index === otherIndex) return true;
          const aMax = entry.maxPoints ?? Number.MAX_SAFE_INTEGER;
          const bMax = other.maxPoints ?? Number.MAX_SAFE_INTEGER;
          return aMax < other.minPoints || bMax < entry.minPoints;
        }),
      );
      if (!valid) return;
      Object.assign(rank, {
        name: action.payload.name.trim(),
        minPoints: action.payload.minPoints,
        maxPoints: action.payload.maxPoints,
        benefits: action.payload.benefits?.trim(),
        status: action.payload.status ?? 'active',
      });
      recalculateUserRanks(state);
    },
    addRankRule(
      state,
      action: PayloadAction<{
        adminId: string;
        name: string;
        minPoints: number;
        maxPoints?: number;
        benefits?: string;
        status?: RankRule['status'];
      }>,
    ) {
      if (!activeAdmin(state, action.payload.adminId)) return;
      const name = action.payload.name.trim();
      if (!name || action.payload.minPoints < 0) return;
      const candidate: RankRule = {
        id: uniqueId('rank'),
        name,
        minPoints: action.payload.minPoints,
        maxPoints: action.payload.maxPoints,
        benefits: action.payload.benefits?.trim(),
        status: action.payload.status ?? 'active',
      };
      const nextRanks = [...state.ranks, candidate];
      const valid = nextRanks.every((entry, index) =>
        nextRanks.every((other, otherIndex) => {
          if (index === otherIndex) return true;
          const aMax = entry.maxPoints ?? Number.MAX_SAFE_INTEGER;
          const bMax = other.maxPoints ?? Number.MAX_SAFE_INTEGER;
          return aMax < other.minPoints || bMax < entry.minPoints;
        }),
      );
      if (!valid) return;
      state.ranks.push(candidate);
      recalculateUserRanks(state);
    },
    addPointRule(
      state,
      action: PayloadAction<{
        adminId: string;
        behavior: string;
        type: ReputationPointRule['type'];
        points: number;
        status: ReputationPointRule['status'];
        description: string;
      }>,
    ) {
      if (!activeAdmin(state, action.payload.adminId)) return;
      const behavior = action.payload.behavior.trim();
      if (!behavior || action.payload.points < 0) return;
      const id = uniqueId('rule');
      state.pointRules.push({
        id,
        key: `custom_${Date.now()}` as ReputationPointRule['key'],
        behavior,
        type: action.payload.type,
        points: action.payload.points,
        status: action.payload.status,
        description: action.payload.description.trim(),
      });
      state.auditLogs.unshift({
        id: uniqueId('log'),
        adminId: action.payload.adminId,
        action: 'add_point_rule',
        targetType: 'point_rule',
        targetId: id,
        detail: `Thêm quy tắc điểm: ${behavior}`,
        createdAt: new Date().toISOString(),
      });
    },
    updatePointRule(
      state,
      action: PayloadAction<{
        adminId: string;
        id: string;
        behavior: string;
        type: ReputationPointRule['type'];
        points: number;
        status: ReputationPointRule['status'];
        description: string;
      }>,
    ) {
      if (!activeAdmin(state, action.payload.adminId)) return;
      const rule = state.pointRules.find((entry) => entry.id === action.payload.id);
      const behavior = action.payload.behavior.trim();
      if (!rule || !behavior || action.payload.points < 0) return;
      rule.behavior = behavior;
      rule.type = action.payload.type;
      rule.points = action.payload.points;
      rule.status = action.payload.status;
      rule.description = action.payload.description.trim();
      state.auditLogs.unshift({
        id: uniqueId('log'),
        adminId: action.payload.adminId,
        action: 'update_point_rule',
        targetType: 'point_rule',
        targetId: rule.id,
        detail: `Cập nhật quy tắc điểm: ${behavior}`,
        createdAt: new Date().toISOString(),
      });
    },
    cancelTransaction(state, action: PayloadAction<{ transactionId: string; actorId: string }>) {
      const tx = state.transactions.find((entry) => entry.id === action.payload.transactionId);
      if (!tx || !participant(state, tx, action.payload.actorId)) return;
      if (releaseFee(state, action.payload.transactionId)) {
        const item = state.items.find((entry) => entry.id === tx.itemId);
        if (item && reopenableItem(item)) item.status = 'APPROVED';
        applyReputationPointEvent(state, {
          userId: action.payload.actorId,
          key: 'unreasoned_cancel',
          ref: `${tx.id}:${action.payload.actorId}:unreasoned_cancel`,
        });
      }
    },
    sendMessage(state, action: PayloadAction<{ convId: string; sender: string; text: string }>) {
      const conv = state.conversations.find((entry) => entry.id === action.payload.convId);
      const tx = state.transactions.find((entry) => entry.id === conv?.transactionId);
      if (
        !conv ||
        !tx ||
        !participant(state, tx, action.payload.sender) ||
        !conv.participantIds.includes(action.payload.sender) ||
        !action.payload.text.trim()
      )
        return;
      if (inspectContactMessage(action.payload.text).blocked) return;
      state.messages.push({
        id: uniqueId('msg'),
        convId: action.payload.convId,
        sender: action.payload.sender,
        type: 'chat',
        text: action.payload.text,
        time: 'Bây giờ',
      });
      conv.lastMessage = action.payload.text;
      conv.lastMessageAt = new Date().toISOString();
    },
    topupCredit(
      state,
      action: PayloadAction<{ userId: string; vnd: number; id?: string; code?: string }>,
    ) {
      const user = state.users.find((entry) => entry.id === action.payload.userId);
      if (!user || !activeActor(state, action.payload.userId)) return;
      if (
        !Number.isSafeInteger(action.payload.vnd) ||
        action.payload.vnd < TOPUP_MIN_VND ||
        action.payload.vnd % 1000 !== 0
      )
        return;
      const id = action.payload.id ?? uniqueId('top');
      const code = action.payload.code ?? uniqueId('SLTOPUP');
      if (
        !id.trim() ||
        !code.trim() ||
        state.topups.some((topup) => topup.id === id || topup.code === code) ||
        state.creditHistory.some((entry) => entry.ref === id)
      )
        return;
      state.topups.unshift({
        id,
        code,
        userId: user.id,
        amount: action.payload.vnd / CREDIT_TO_VND,
        vnd: action.payload.vnd,
        method: 'QR Banking',
        status: 'pending',
        createdAt: new Date().toISOString(),
      });
    },
    confirmTopup(state, action: PayloadAction<{ topupId: string; adminId: string }>) {
      const topup = state.topups.find((entry) => entry.id === action.payload.topupId);
      const user = state.users.find((entry) => entry.id === topup?.userId);
      if (
        !topup ||
        !user ||
        !activeAdmin(state, action.payload.adminId) ||
        topup.status !== 'pending' ||
        !Number.isSafeInteger(topup.vnd) ||
        topup.vnd <= 0 ||
        topup.vnd < TOPUP_MIN_VND ||
        topup.vnd % 1000 !== 0 ||
        topup.amount !== topup.vnd / CREDIT_TO_VND ||
        !Number.isSafeInteger(user.totalCredit + topup.amount) ||
        !Number.isSafeInteger(user.availableCredit + topup.amount) ||
        state.creditHistory.some((entry) => entry.ref === topup.id)
      )
        return;
      topup.status = 'completed';
      topup.confirmedAt = new Date().toISOString();
      user.totalCredit += topup.amount;
      user.availableCredit += topup.amount;
      state.creditHistory.unshift({
        id: uniqueId('ch'),
        userId: user.id,
        transactionId: topup.id,
        type: 'TOPUP',
        amount: topup.amount,
        balance: user.availableCredit,
        description: 'Nạp Credit qua QR',
        status: 'completed',
        ref: topup.id,
        note: 'Nạp Credit qua QR',
        createdAt: new Date().toISOString(),
      });
      state.auditLogs.unshift({
        id: uniqueId('log'),
        adminId: action.payload.adminId,
        action: 'confirm_topup',
        targetType: 'user',
        targetId: user.id,
        detail: `Xác nhận nạp ${topup.amount} Credit`,
        createdAt: new Date().toISOString(),
      });
    },
    useAiFeature(
      state,
      action: PayloadAction<{ userId: string; feature: 'SWAP_MATCHING' | 'ASSISTANT_SEARCH' }>,
    ) {
      if (!activeActor(state, action.payload.userId)) return;
      const periodKey =
        action.payload.feature === 'SWAP_MATCHING'
          ? `week:${mondayKey()}`
          : `day:${new Date().toISOString().slice(0, 10)}`;
      let counter = state.aiUsage.find(
        (entry) =>
          entry.userId === action.payload.userId &&
          entry.feature === action.payload.feature &&
          entry.periodKey === periodKey,
      );
      if (!counter) {
        counter = {
          id: uniqueId('aiu'),
          userId: action.payload.userId,
          feature: action.payload.feature,
          periodKey,
          count: 0,
        };
        state.aiUsage.push(counter);
      }
      const fee =
        action.payload.feature === 'SWAP_MATCHING'
          ? AI_SWAP_MATCHING_FEE
          : AI_ASSISTANT_SEARCH_FEE;
      const paid = spendAvailableCredit(state, {
        userId: action.payload.userId,
        amount: fee,
        type: 'AI_SPEND',
        ref: `ai:${action.payload.feature}:${action.payload.userId}:${Date.now()}`,
        description:
          action.payload.feature === 'SWAP_MATCHING'
            ? 'Phí AI gợi ý ghép đôi Swap'
            : 'Phí AI trợ lý tìm đồ',
      });
      if (!paid) return;
      counter.count += 1;
    },
    adminAdjustCredit(
      state,
      action: PayloadAction<{
        adminId: string;
        userId: string;
        amount: number;
        note: string;
        ref: string;
      }>,
    ) {
      const user = state.users.find((entry) => entry.id === action.payload.userId);
      if (
        !user ||
        !activeAdmin(state, action.payload.adminId) ||
        !Number.isSafeInteger(action.payload.amount) ||
        action.payload.amount === 0 ||
        !Number.isSafeInteger(user.availableCredit + action.payload.amount) ||
        user.availableCredit + action.payload.amount < 0 ||
        !Number.isSafeInteger(user.totalCredit + action.payload.amount) ||
        !action.payload.ref.trim() ||
        state.creditHistory.some((entry) => entry.ref === action.payload.ref) ||
        state.topups.some((topup) => topup.id === action.payload.ref)
      )
        return;
      user.totalCredit += action.payload.amount;
      user.availableCredit += action.payload.amount;
      state.creditHistory.unshift({
        id: uniqueId('ch'),
        userId: user.id,
        transactionId: action.payload.ref,
        type: 'ADMIN_ADJUSTMENT',
        amount: action.payload.amount,
        balance: user.availableCredit,
        description: action.payload.note,
        status: 'completed',
        ref: action.payload.ref,
        note: action.payload.note,
        createdAt: new Date().toISOString(),
      });
      state.auditLogs.unshift({
        id: uniqueId('log'),
        adminId: action.payload.adminId,
        action: 'adjust_credit',
        targetType: 'user',
        targetId: user.id,
        detail: action.payload.note,
        createdAt: new Date().toISOString(),
      });
    },
    updateFeeSetting(state, action: PayloadAction<{ adminId: string; fee: number }>) {
      if (
        !activeAdmin(state, action.payload.adminId) ||
        !Number.isSafeInteger(action.payload.fee) ||
        action.payload.fee < 0
      )
        return;
      const setting = state.settings.find((entry) => entry.key === 'tx_fee_credit');
      if (setting) {
        setting.value = action.payload.fee;
        setting.updatedAt = new Date().toISOString();
        setting.updatedBy = action.payload.adminId;
      }
      state.auditLogs.unshift({
        id: `log_${Date.now()}`,
        adminId: action.payload.adminId,
        action: 'change_fee_setting',
        targetType: 'setting',
        targetId: 'tx_fee_credit',
        detail: `Cap nhat cau hinh Credit giao dich: ${action.payload.fee} Credit`,
        createdAt: new Date().toISOString(),
      });
    },
    lockUser(state, action: PayloadAction<{ adminId: string; userId: string; locked: boolean; reason?: string }>) {
      const user = state.users.find((entry) => entry.id === action.payload.userId);
      if (!user || user.role === 'admin' || !activeAdmin(state, action.payload.adminId)) return;
      const previousStatus = user.status;
      const reason = action.payload.reason?.trim();
      user.status = action.payload.locked ? 'locked' : 'active';
      state.auditLogs.unshift({
        id: `log_${Date.now()}`,
        adminId: action.payload.adminId,
        action: action.payload.locked ? 'ACCOUNT_LOCKED' : 'ACCOUNT_UNLOCKED',
        targetType: 'user',
        targetId: user.id,
        detail: `${action.payload.locked ? 'Khóa' : 'Mở khóa'} ${user.name}${reason ? `: ${reason}` : ''}`,
        previousValue: previousStatus,
        newValue: user.status,
        reason,
        createdAt: new Date().toISOString(),
      });
    },
    resetDemoData(state) {
      if (
        !state.users.some(
          (user) =>
            user.id === state.currentUserId && user.role === 'admin' && user.status === 'active',
        )
      )
        return;
      return resetPersistedState();
    },
  },
});

export const actions = dataSlice.actions;
export const dataReducer = dataSlice.reducer;
export const store = configureStore({ reducer: { data: dataSlice.reducer } });
store.subscribe(() => {
  assertWalletInvariant(store.getState().data);
  savePersistedState(store.getState().data);
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

export const selectData = (state: RootState) => state.data;
export const selectCurrentUser = (state: RootState) =>
  state.data.users.find((user) => user.id === state.data.currentUserId) ?? null;

