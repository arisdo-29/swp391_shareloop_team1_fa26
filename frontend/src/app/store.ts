import { configureStore, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import {
  assertWalletInvariant,
  RECEIVE_ITEM_FEE,
  spendAvailableCredit,
  txFee,
} from '../utils/credit';
import { loadPersistedState } from '../utils/storage';
import type { AppStateData, Item, Transaction, User } from '../types/domain';

// Store chỉ giữ phiên đăng nhập (currentUser) và vài nhánh cũ còn được trang gọi.
// Dữ liệu nghiệp vụ lấy qua services/*Api.ts, không lưu và không khôi phục từ trình duyệt.
const initialState: AppStateData = loadPersistedState();
let idSequence = 0;
const uniqueId = (prefix: string) => `${prefix}_${Date.now()}_${++idSequence}`;
const activeActor = (state: AppStateData, actorId: string) =>
  state.currentUserId === actorId &&
  state.users.some((user) => user.id === actorId && user.status === 'active');
const itemApproved = (status: Item['status']) => status === 'approved' || status === 'APPROVED';
const itemExpired = (item: Item, now = new Date()) =>
  itemApproved(item.status) && Boolean(item.expiresAt) && new Date(item.expiresAt!).getTime() < now.getTime();
const itemAvailableForRequest = (item: Item, now = new Date()) =>
  itemApproved(item.status) && (!item.expiresAt || new Date(item.expiresAt).getTime() >= now.getTime());
const activeTransactionForItem = (state: AppStateData, itemId: string) =>
  state.transactions.some(
    (tx) =>
      (tx.itemId === itemId || tx.offeredItemId === itemId || tx.sourceItemId === itemId) &&
      !['COMPLETED', 'CANCELLED', 'DISPUTED'].includes(tx.status),
  );

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

const dataSlice = createSlice({
  name: 'data',
  initialState,
  reducers: {
    // Đăng nhập thật qua API: thêm (hoặc cập nhật) user vào danh sách và đặt làm người đang đăng nhập,
    // để Guards và các màn còn dùng mock vẫn tìm thấy currentUser.
    loginFromApi(state, action: PayloadAction<User>) {
      const index = state.users.findIndex((user) => user.id === action.payload.id);
      if (index >= 0) {
        state.users[index] = { ...state.users[index], ...action.payload };
      } else {
        state.users.push(action.payload);
      }
      state.currentUserId = action.payload.id;
    },
    logout(state) {
      const userId = state.currentUserId;
      state.currentUserId = null;
      if (userId?.startsWith('api_')) {
        state.users = state.users.filter((user) => user.id !== userId);
      }
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
      request.status = 'RESERVED';
      state.itemRequests.forEach((entry) => {
        if (entry.itemId === item.id && entry.id !== request.id && entry.status === 'PENDING') {
          entry.status = 'REJECTED';
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
  },
});

export const actions = dataSlice.actions;
export const dataReducer = dataSlice.reducer;
export const store = configureStore({ reducer: { data: dataSlice.reducer } });
store.subscribe(() => {
  assertWalletInvariant(store.getState().data);
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

export const selectData = (state: RootState) => state.data;
export const selectCurrentUser = (state: RootState) =>
  state.data.users.find((user) => user.id === state.data.currentUserId) ?? null;

