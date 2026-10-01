import type { AppStateData, CreditHistory } from '../types/domain';
import { canTransition, transitionTransaction } from './transaction';
import { applyReputationPointEvent } from './reputation';

export const CREDIT_TO_VND = 1000;
export const POST_LISTING_FEE = 5;
export const RECEIVE_ITEM_FEE = 5;
export const AI_SWAP_MATCHING_FEE = 500;
export const AI_ASSISTANT_SEARCH_FEE = 100;
export const TOPUP_MIN_VND = 1000;

export function txFee(_data: AppStateData) {
  return 0;
}

export function assertWalletInvariant(data: AppStateData) {
  data.users.forEach((user) => {
    if (
      !Number.isSafeInteger(user.totalCredit) ||
      !Number.isSafeInteger(user.availableCredit) ||
      !Number.isSafeInteger(user.holdCredit) ||
      user.totalCredit < 0 ||
      user.availableCredit < 0 ||
      user.holdCredit < 0 ||
      user.totalCredit !== user.availableCredit + user.holdCredit
    ) {
      throw new Error(`Credit invariant failed for ${user.id}`);
    }
  });
  if (!Number.isSafeInteger(data.systemRevenue) || data.systemRevenue < 0) {
    throw new Error('System revenue invariant failed');
  }
}

function hasHistory(data: AppStateData, ref: string) {
  return data.creditHistory.some((entry) => entry.ref === ref);
}

function ledgerEntry(
  data: AppStateData,
  input: Omit<CreditHistory, 'id' | 'createdAt'> & { id?: string; createdAt?: string },
): CreditHistory {
  return {
    ...input,
    id: input.id ?? `ch_${Date.now()}_${data.creditHistory.length + 1}`,
    createdAt: input.createdAt ?? new Date().toISOString(),
  };
}

function addLedger(data: AppStateData, entry: CreditHistory) {
  if (!entry.ref || !hasHistory(data, entry.ref)) data.creditHistory.unshift(entry);
}

export function releaseFee(data: AppStateData, transactionId: string) {
  const tx = data.transactions.find((item) => item.id === transactionId);
  if (!tx || !canTransition(tx, 'CANCELLED')) return false;
  tx.creditHeldBy = [];
  tx.creditHeld = false;
  tx.feeCaptured = false;
  transitionTransaction(tx, 'CANCELLED');
  tx.cancelledAt = new Date().toISOString();
  return true;
}

export function spendHeldFee(data: AppStateData, transactionId: string) {
  const tx = data.transactions.find((item) => item.id === transactionId);
  if (
    !tx ||
    !canTransition(tx, 'COMPLETED') ||
    !tx.completedByOwner ||
    !tx.completedByRequester
  )
    return false;
  tx.creditHeldBy = [];
  tx.creditHeld = false;
  tx.feeCaptured = false;
  transitionTransaction(tx, 'COMPLETED');
  tx.completedAt = new Date().toISOString();
  [tx.ownerId, tx.requesterId].forEach((userId) => {
    applyReputationPointEvent(data, {
      userId,
      key: 'transaction_completed',
      ref: `${transactionId}:${userId}:transaction_completed`,
      createdAt: tx.completedAt,
    });
  });
  const deadline = new Date(tx.completedAt);
  deadline.setDate(deadline.getDate() + 7);
  tx.complaintDeadline = deadline.toISOString();
  return true;
}

export function spendAvailableCredit(
  data: AppStateData,
  input: {
    userId: string;
    amount: number;
    type: 'AI_SPEND' | 'SPEND' | 'RECEIVE_FEE';
    ref: string;
    description: string;
  },
) {
  const user = data.users.find((entry) => entry.id === input.userId);
  if (
    !user ||
    !Number.isSafeInteger(input.amount) ||
    input.amount <= 0 ||
    user.availableCredit < input.amount ||
    hasHistory(data, input.ref)
  )
    return false;
  user.availableCredit -= input.amount;
  user.totalCredit -= input.amount;
  data.systemRevenue += input.amount;
  addLedger(
    data,
    ledgerEntry(data, {
      userId: user.id,
      transactionId: input.ref,
      type: input.type,
      amount: -input.amount,
      balance: user.availableCredit,
      description: input.description,
      status: 'completed',
      ref: input.ref,
      note: input.description,
    }),
  );
  return true;
}
