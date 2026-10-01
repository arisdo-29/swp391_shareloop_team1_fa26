import {
  initialData,
  seedComplaints,
  seedCreditHistory,
  seedDistricts,
  seedItems,
  seedKeywords,
  seedPointHistory,
  seedPointRules,
  seedRanks,
} from '../mocks/database';
import type { AppStateData } from '../types/domain';
import { rankFor } from './reputation';

const KEY = 'shareloop:v1:state';

function normalizeCreditHistory(parsed: AppStateData) {
  const existing = (parsed.creditHistory ?? [])
    .filter((entry) => !['TRANSACTION_FEE', 'HOLD', 'RELEASE_HOLD'].includes(entry.type as string))
    .filter((entry) => !/:HOLD$|:SPEND$|:RELEASE$/.test(entry.ref ?? entry.transactionId ?? ''))
    .map((entry) => {
    const entryType = entry.type as string;
    const relatedTransactionId =
      entry.relatedTransactionId ?? entry.ref?.split(':')[0] ?? entry.transactionId?.split(':')[0];
    const type =
      entryType === 'TRANSACTION_FEE'
        ? 'SPEND'
        : entryType === 'AI_FEE'
          ? 'AI_SPEND'
          : entryType === 'RELEASE_HOLD'
          ? 'REFUND'
          : entry.type;
    const transactionId = entry.transactionId ?? entry.ref ?? entry.id;
    const description = entry.description ?? entry.note;
    const status = entry.status ?? (type === 'REFUND' ? 'refunded' : 'completed');
    return {
      ...entry,
      transactionId,
      type,
      description,
      status,
      relatedTransactionId,
      note: entry.note ?? description,
    };
  });
  const existingIds = new Set(existing.map((entry) => entry.id));
  return [...existing, ...seedCreditHistory.filter((entry) => !existingIds.has(entry.id))];
}

function scaleCreditsDown(parsed: AppStateData) {
  parsed.systemRevenue = Math.round((parsed.systemRevenue ?? 0) / 10);
  parsed.users = (parsed.users ?? []).map((user) => ({
    ...user,
    totalCredit: Math.round(user.totalCredit / 10),
    availableCredit: Math.round(user.availableCredit / 10),
    holdCredit: Math.round(user.holdCredit / 10),
  }));
  parsed.creditHistory = (parsed.creditHistory ?? []).map((entry) => ({
    ...entry,
    amount: Math.round(entry.amount / 10),
    balance: Math.round(entry.balance / 10),
  }));
  parsed.topups = (parsed.topups ?? []).map((topup) => ({
    ...topup,
    amount: Math.round(topup.amount / 10),
    vnd: Math.round(topup.vnd / 10),
  }));
  parsed.transactions = (parsed.transactions ?? []).map((tx) => ({
    ...tx,
    feeCredit: tx.feeCredit === undefined ? undefined : Math.round(tx.feeCredit / 10),
  }));
  parsed.settings = (parsed.settings ?? []).map((setting) =>
    setting.key === 'tx_fee_credit' && typeof setting.value === 'number'
      ? { ...setting, value: Math.round(setting.value / 10) }
      : setting,
  );
}

function legacyOfferedItemId(
  tx: AppStateData['transactions'][number],
  requests: AppStateData['itemRequests'],
) {
  if (tx.offeredItemId ?? tx.sourceItemId) return tx.offeredItemId ?? tx.sourceItemId;
  if (tx.type !== 'trade') return undefined;
  const selectedRequest = requests.find((request) => request.id === tx.selectedRequestId);
  if (selectedRequest?.offeredItemId) return selectedRequest.offeredItemId;
  return requests.find(
    (request) =>
      request.type === 'trade' &&
      request.itemId === tx.itemId &&
      request.requesterId === tx.requesterId &&
      request.status === 'ACCEPTED',
  )?.offeredItemId ??
    (tx.itemId === 'item_001' && tx.requesterId === 'user_001'
      ? 'item_007'
      : tx.itemId === 'item_011' && tx.requesterId === 'user_001'
        ? 'item_010'
        : undefined);
}

export function loadPersistedState(): AppStateData {
  if (typeof localStorage === 'undefined') return initialData;
  const raw = localStorage.getItem(KEY);
  if (!raw) return initialData;
  try {
    const parsed = JSON.parse(raw) as AppStateData;
    const scaleVersion = Number(parsed.settings?.find((setting) => setting.key === 'credit_scale_version')?.value ?? 1);
    if (scaleVersion < 2) {
      scaleCreditsDown(parsed);
      parsed.settings = [
        ...(parsed.settings ?? []).filter((setting) => setting.key !== 'credit_scale_version'),
        {
          key: 'credit_scale_version',
          value: 2,
          label: 'Phiên bản quy đổi Credit',
          updatedAt: new Date().toISOString(),
          updatedBy: 'system',
        },
      ];
    }
    parsed.topups = (parsed.topups ?? []).map((topup) => ({
      ...topup,
      code: topup.code ?? `SLTOPUP-${topup.id.slice(-6).toUpperCase()}`,
      status: (topup.status as string) === 'success' ? 'completed' : topup.status,
    }));
    const itemRequests = parsed.itemRequests ?? [];
    parsed.transactions = (parsed.transactions ?? []).map((tx) => {
      const offeredItemId = legacyOfferedItemId(tx, itemRequests);
      return {
        ...tx,
        offeredItemId,
        sourceItemId: tx.sourceItemId ?? offeredItemId,
        creditHeldBy: [],
        creditHeld: false,
        feeCaptured: tx.feeCaptured ?? false,
        feeCharged: false,
        feeCredit: 0,
        ownerScheduleConfirmed: tx.ownerScheduleConfirmed ?? false,
        requesterScheduleConfirmed: tx.requesterScheduleConfirmed ?? false,
        completedByOwner: tx.completedByOwner ?? tx.senderConfirmed ?? false,
        completedByRequester: tx.completedByRequester ?? tx.receiverConfirmed ?? false,
        evidenceRecords: tx.evidenceRecords ?? [],
        status:
          tx.status === 'CREDIT_HELD'
            ? 'WAITING_HANDOVER'
            : tx.status,
      };
    });
    const persistedItemIds = new Set((parsed.items ?? []).map((item) => item.id));
    const now = Date.now();
    parsed.items = [
      ...(parsed.items ?? []),
      ...seedItems.filter((item) => !persistedItemIds.has(item.id)),
    ].map((item) => {
      const approved = item.status === 'approved' || item.status === 'APPROVED';
      const expired = approved && new Date(item.expiresAt).getTime() < now;
      return {
        ...item,
        status: expired ? 'expired' : item.status,
        postApprovalEditCount: item.postApprovalEditCount ?? 0,
      };
    });
    parsed.keywords = parsed.keywords ?? seedKeywords;
    parsed.districts = parsed.districts ?? seedDistricts;
    parsed.itemRequests = parsed.itemRequests ?? [];
    parsed.complaints = (parsed.complaints ?? seedComplaints).map((complaint) => ({
      ...complaint,
      responseEvidence: complaint.responseEvidence ?? [],
    }));
    parsed.systemRevenue = parsed.systemRevenue ?? 0;
    parsed.aiUsage = parsed.aiUsage ?? [];
    parsed.ranks = parsed.ranks ?? seedRanks;
    parsed.pointRules = parsed.pointRules ?? seedPointRules;
    parsed.pointHistory = parsed.pointHistory ?? seedPointHistory;
    parsed.creditHistory = normalizeCreditHistory(parsed);
    parsed.settings = (parsed.settings ?? []).map((setting) =>
      setting.key === 'tx_fee_credit'
        ? { ...setting, value: 0, label: 'Giao dich mien phi' }
        : setting,
    );
    parsed.users = (parsed.users ?? []).map((user) => {
      // Older demo data used the typo "use" for the seeded demo user.
      const normalizedUser = user.id === 'user_001' && user.username === 'use'
        ? { ...user, username: 'user' }
        : user;
      const demoWalletUser = normalizedUser.id === 'user_001' &&
        normalizedUser.totalCredit >= 11996 &&
        normalizedUser.availableCredit >= 11796 &&
        normalizedUser.holdCredit === 200
        ? { ...normalizedUser, totalCredit: 20, availableCredit: 20, holdCredit: 0 }
        : normalizedUser;
      return demoWalletUser.role === 'admin'
        ? demoWalletUser
        : {
            ...demoWalletUser,
            reputationRestoreUsed: demoWalletUser.reputationRestoreUsed ?? false,
            rank: rankFor(demoWalletUser.rewardPoints, parsed.ranks),
          };
    });
    return parsed;
  } catch {
    return initialData;
  }
}

export function savePersistedState(state: AppStateData) {
  localStorage.setItem(KEY, JSON.stringify(state));
}

export function resetPersistedState(): AppStateData {
  localStorage.setItem(KEY, JSON.stringify(initialData));
  return initialData;
}
