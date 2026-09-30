export type Role = 'guest' | 'user' | 'admin';
export type UserStatus = 'active' | 'suspended' | 'locked' | 'pending_verification';
export type ItemType = 'gift' | 'trade';
export type ItemCondition = 'new' | 'good' | 'used';
export type ItemStatus =
  | 'pending'
  | 'PENDING_REVIEW'
  | 'approved'
  | 'APPROVED'
  | 'rejected'
  | 'REJECTED'
  | 'VIOLATION'
  | 'IN_TRANSACTION'
  | 'COMPLETED'
  | 'expired'
  | 'removed';
export type ItemRequestStatus = 'PENDING' | 'ACCEPTED' | 'NOT_SELECTED' | 'CANCELLED';
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
export type KeywordAction = 'flag' | 'block';
export type ComplaintStatus = 'received' | 'processing' | 'resolved' | 'rejected' | 'violation_confirmed';

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
  condition: ItemCondition;
  district: string;
  images: string[];
  tradeFor?: string;
  status: ItemStatus;
  rejectionReason?: string;
  moderationReason?: string;
  postApprovalEditCount?: number;
  postedAt: string;
  expiresAt: string;
}

export interface Handover {
  id: string;
  transactionId: string;
  date: string;
  time: string;
  district: string;
  address: string;
  method: 'Gặp trực tiếp' | 'Giao hàng';
  note?: string;
  proposedBy: string;
  agreedBy?: string;
  status: 'proposed' | 'confirmed';
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
  offeredItemId?: string;
  requesterId: string;
  status: ItemRequestStatus;
  message: string;
  createdAt: string;
}

export interface Conversation {
  id: string;
  transactionId: string;
  participantIds: string[];
  itemId: string;
  lastMessage: string;
  lastMessageAt: string;
  unreadCount: number;
}

export interface Message {
  id: string;
  convId: string;
  sender: string | 'system';
  type: 'chat' | 'system' | 'handover_card';
  text?: string;
  handoverId?: string;
  time: string;
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

export interface Topup {
  id: string;
  code: string;
  userId: string;
  amount: number;
  vnd: number;
  method: string;
  status: 'pending' | 'confirming' | 'completed' | 'failed';
  createdAt: string;
  confirmedAt?: string;
}

export interface Dispute {
  id: string;
  transactionId: string;
  reporterId: string;
  reason: string;
  status: 'open' | 'reviewing' | 'resolved' | 'closed';
  resolution?: string;
  adminNote?: string;
  createdAt: string;
}

export interface ForbiddenKeyword {
  id: string;
  keyword: string;
  detections: number;
  action: KeywordAction;
}

export interface SupportedDistrict {
  id: string;
  name: string;
  status: 'active' | 'inactive';
}

export interface Complaint {
  id: string;
  reporterId: string;
  reportedUserId: string;
  transactionId: string;
  reason: string;
  content: string;
  evidence: string[];
  createdAt: string;
  status: ComplaintStatus;
  adminNote?: string;
  resolution?: string;
  response?: string;
  responseEvidence?: string[];
  responseDueAt?: string;
  responseSubmittedAt?: string;
  resolvedAt?: string;
}

export interface AiUsageCounter {
  id: string;
  userId: string;
  feature: 'SWAP_MATCHING' | 'ASSISTANT_SEARCH';
  periodKey: string;
  count: number;
}

export interface RankRule {
  id: string;
  name: string;
  minPoints: number;
  maxPoints?: number;
  benefits?: string;
  status?: 'active' | 'inactive';
}

export type ReputationPointRuleKey =
  | 'transaction_completed'
  | 'handover_confirmed_on_time'
  | 'valid_complaint'
  | 'unreasoned_cancel'
  | 'content_violation'
  | `custom_${string}`;

export interface ReputationPointRule {
  id: string;
  key: ReputationPointRuleKey;
  behavior: string;
  type: 'plus' | 'minus';
  points: number;
  status: 'active' | 'paused';
  description: string;
}

export interface ReputationPointHistory {
  id: string;
  userId: string;
  ruleId: string;
  event: string;
  change: number;
  pointsAfter: number;
  ref?: string;
  createdAt: string;
}

export interface AdminAuditLog {
  id: string;
  adminId: string;
  action: string;
  targetType: 'user' | 'item' | 'transaction' | 'dispute' | 'setting' | 'keyword' | 'district' | 'complaint' | 'rank' | 'point_rule';
  targetId: string;
  detail: string;
  previousValue?: string;
  newValue?: string;
  reason?: string;
  createdAt: string;
}

export interface SystemSetting {
  key: string;
  value: string | number;
  label: string;
  updatedAt: string;
  updatedBy: string;
}

export interface AppStateData {
  currentUserId: string | null;
  systemRevenue: number;
  users: User[];
  items: Item[];
  itemRequests: ItemRequest[];
  transactions: Transaction[];
  handovers: Handover[];
  conversations: Conversation[];
  messages: Message[];
  creditHistory: CreditHistory[];
  topups: Topup[];
  disputes: Dispute[];
  keywords: ForbiddenKeyword[];
  districts: SupportedDistrict[];
  complaints: Complaint[];
  aiUsage: AiUsageCounter[];
  ranks: RankRule[];
  pointRules: ReputationPointRule[];
  pointHistory: ReputationPointHistory[];
  auditLogs: AdminAuditLog[];
  settings: SystemSetting[];
  passwordReset?: PasswordResetState;
  emailVerification?: EmailVerificationState;
}

export interface PasswordResetState {
  id: string;
  email: string;
  userId?: string;
  otpHash: string;
  expiresAt: string;
  resendAvailableAt: string;
  attempts: number;
  verified: boolean;
  resetToken?: string;
}

export interface PendingRegistration {
  name: string;
  username: string;
  email: string;
  phone: string;
  district: string;
  passwordHash: string;
}

export interface EmailVerificationState {
  id: string;
  registration: PendingRegistration;
  otpHash: string;
  expiresAt: string;
  resendAvailableAt: string;
  attempts: number;
}
