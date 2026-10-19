import type { AppStateData } from '../types/domain';

// Dữ liệu nghiệp vụ lấy từ API; không khôi phục dữ liệu mẫu hoặc số dư từ localStorage.
export function loadPersistedState(): AppStateData {
  return {
    currentUserId: null, systemRevenue: 0, users: [], items: [], itemRequests: [],
    transactions: [], handovers: [], conversations: [], messages: [], creditHistory: [],
    topups: [], disputes: [], keywords: [], districts: [], complaints: [], ranks: [],
    pointRules: [], pointHistory: [], auditLogs: [], settings: [],
  };
}

export function resetPersistedState(): AppStateData {
  return loadPersistedState();
}
