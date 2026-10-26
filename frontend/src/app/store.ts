import { configureStore, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { Transaction, User } from '../types/domain';

// Store chỉ giữ phiên đăng nhập: currentUserId và người đang đăng nhập (users chỉ có tối đa 1 phần tử).
// Dữ liệu nghiệp vụ lấy qua services/*Api.ts, không lưu và không khôi phục từ trình duyệt.
interface SessionState {
  currentUserId: string | null;
  users: User[];
  // Tạm giữ cho ProductDetail.tsx tới khi PR #332 merge, sau đó xoá.
  transactions: Transaction[];
}

const initialState: SessionState = {
  currentUserId: null,
  users: [],
  transactions: [],
};

const dataSlice = createSlice({
  name: 'data',
  initialState,
  reducers: {
    // Đăng nhập thật qua API: chỉ giữ đúng người đang đăng nhập để Guards và các màn tìm thấy currentUser.
    loginFromApi(state, action: PayloadAction<User>) {
      state.users = [action.payload];
      state.currentUserId = action.payload.id;
    },
    logout(state) {
      state.currentUserId = null;
      state.users = [];
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
  },
});

export const actions = dataSlice.actions;
export const dataReducer = dataSlice.reducer;
export const store = configureStore({ reducer: { data: dataSlice.reducer } });

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

// Tạm giữ cho ProductDetail.tsx tới khi PR #332 merge, sau đó xoá.
export const selectData = (state: RootState) => state.data;
export const selectCurrentUser = (state: RootState) =>
  state.data.users.find((user) => user.id === state.data.currentUserId) ?? null;
