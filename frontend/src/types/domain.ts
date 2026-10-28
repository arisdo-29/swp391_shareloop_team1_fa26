export type Role = 'guest' | 'user' | 'admin';
export type UserStatus = 'active' | 'suspended' | 'locked' | 'pending_verification';
export type ItemType = 'gift' | 'trade';
export type ItemCondition =
  | 'NEW'
  | 'LIKE_NEW'
  | 'GOOD'
  | 'DEFECTIVE'
  | 'new'
  | 'good'
  | 'used';
export type ItemStatus =
  | 'pending'
  | 'PENDING_REVIEW'
  | 'approved'
  | 'APPROVED'
  | 'rejected'
  | 'REJECTED'
  | 'RESERVED'
  | 'TRADED'
  | 'EXPIRED'
  | 'VIOLATION'
  | 'IN_TRANSACTION'
  | 'COMPLETED'
  | 'expired'
  | 'removed';
export type ItemRequestStatus =
  | 'PENDING'
  | 'RESERVED'
  | 'AWAITING_LOGISTICS'
  | 'LOGISTICS_CONFIRMED'
  | 'AWAITING_HANDOVER'
  | 'COMPLETED'
  | 'DISPUTED'
  | 'REJECTED'
  | 'CANCELLED';
export type TransactionStatus =
  | 'NEGOTIATING'
  | 'SCHEDULE_PROPOSED'
  | 'SCHEDULE_CONFIRMED'
  | 'CREDIT_HELD'
  | 'WAITING_HANDOVER'
  | 'SENDER_CONFIRMED'
  | 'RECEIVER_CONFIRMED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'DISPUTED';
export type CreditHistoryType =
  | 'TOPUP'
  | 'TRANSACTION_FEE'
  | 'SPEND'
  | 'RECEIVE_FEE'
  | 'HOLD'
  | 'REFUND'
  | 'AI_SPEND'
  | 'RELEASE_HOLD'
  | 'ADMIN_ADJUSTMENT';
export type CreditHistoryStatus = 'completed' | 'holding' | 'refunded' | 'pending';

export interface User {
  id: string;
  username: string;
  password: string;
  name: string;
  email: string;
  phone: string;
  district: string;
  avatarInitials: string;
  avatarUrl?: string;
  totalCredit: number;
  availableCredit: number;
  holdCredit: number;
  rewardPoints: number;
  reputationStars: number;
  rank: string;
  status: UserStatus;
  role: 'user' | 'admin';
  joinedAt: string;
  totalTx: number;
  reputationRestoreUsed?: boolean;
}

export interface TransactionEvidence {
  id: string;
  userId: string;
  files: string[];
  createdAt: string;
}

export interface Item {
  id: string;
  ownerId: string;
  title: string;
  description: string;
  type: ItemType;
  category: string;
  categoryId?: number;
  condition: ItemCondition;
  district: string;
  images: string[];
  tradeFor?: string;
  brand?: string;
  defectNote?: string;
  defectImages?: string[];
  operatingStatus?: 'NORMAL' | 'PARTIAL' | 'NOT_WORKING';
  status: ItemStatus;
  rejectionReason?: string;
  moderationReason?: string;
  postApprovalEditCount?: number;
  postedAt: string;
  expiresAt?: string;
  boostedUntil?: string;
  donorName?: string;
  donorTrustStars?: number;
  donorRank?: string;
  donorAsDescribedRate?: number;
}

export interface Transaction {
  id: string;
  itemId: string;
  offeredItemId?: string;
  sourceItemId?: string;
  selectedRequestId?: string;
  requesterId: string;
  ownerId: string;
  type: ItemType;
  feeCredit?: number;
  status: TransactionStatus;
  handoverId?: string;
  creditHeldBy: string[];
  creditHeld?: boolean;
  feeCharged?: boolean;
  feeCaptured?: boolean;
  ownerScheduleConfirmed?: boolean;
  requesterScheduleConfirmed?: boolean;
  completedByOwner?: boolean;
  completedByRequester?: boolean;
  senderConfirmed: boolean;
  receiverConfirmed: boolean;
  ownerEvidence?: string[];
  requesterEvidence?: string[];
  senderEvidence?: string[];
  receiverEvidence?: string[];
  evidenceRecords?: TransactionEvidence[];
  reminderDay0At?: string;
  reminderDay3At?: string;
  autoConfirmAt?: string;
  silentCancelAt?: string;
  completedAt?: string;
  complaintDeadline?: string;
  cancelledAt?: string;
  disputeId?: string;
  createdAt: string;
}

export interface ItemRequest {
  id: string;
  type: ItemType;
  itemId: string;
  itemTitle?: string;
  itemImage?: string;
  donorName?: string;
  offeredItemId?: string;
  offeredItemTitle?: string;
  requesterId: string;
  requesterName?: string;
  requesterTrustStars?: number;
  requesterRank?: string;
  requesterAsDescribedRate?: number;
  status: ItemRequestStatus;
  message: string;
  createdAt: string;
}

export interface CreditHistory {
  id: string;
  userId: string;
  transactionId: string;
  type: CreditHistoryType;
  amount: number;
  balance: number;
  description: string;
  status: CreditHistoryStatus;
  relatedTransactionId?: string;
  ref?: string;
  note: string;
  createdAt: string;
}
