/** Shapes of the backend's responses (see the OpenAPI document). Money is always a decimal string. */

export type GroupRole = 'PRESIDENT' | 'TREASURER' | 'SECRETARY' | 'AUDITOR' | 'MEMBER';
export type MemberStatus = 'INVITED' | 'ACTIVE' | 'SUSPENDED' | 'LEFT' | 'REMOVED';
export type Locale = 'en' | 'rw';

export interface Profile {
  id: string;
  phone: string;
  fullName: string;
  locale: Locale;
  email: string | null;
  platformRole: 'USER' | 'PLATFORM_ADMIN';
}

export interface MyGroup {
  groupId: string;
  name: string;
  memberId: string;
  memberNumber: string;
  role: GroupRole;
}

export interface GroupView {
  groupId: string;
  name: string;
  registrationNumber: string | null;
  phone: string | null;
  email: string | null;
  province: string | null;
  district: string | null;
  sector: string | null;
  cell: string | null;
  village: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
  createdAt: string;
  myRole: GroupRole;
  myMemberId: string;
  activeMembers: number;
}

export interface MemberView {
  memberId: string;
  memberNumber: string;
  fullName: string | null;
  phone: string | null;
  role: GroupRole;
  status: MemberStatus;
  joinedAt: string | null;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface InvitationView {
  invitationId: string;
  phone: string;
  role: GroupRole;
  expiresAt: string;
  createdAt: string;
}

export interface GroupSettings {
  defaultLocale: Locale;
  interestRecognition: 'WHEN_PAID' | 'WHEN_DUE';
  withdrawalsAllowed: boolean;
  withdrawalNoticeDays: number;
  /** RWF as a decimal string, e.g. "5000.00" */
  exitFee: string;
}

export interface SettingsView {
  settings: GroupSettings;
  schemaVersion: number;
  version: number;
  updatedAt: string;
  pendingChange: { changeId: string; proposed: GroupSettings; proposedBy: string | null; proposedAt: string } | null;
}

export interface TransferView {
  transferId: string;
  role: GroupRole;
  fromMemberId: string;
  toMemberId: string;
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED';
  createdAt: string;
}

/** Spec 5.5, as the UI needs it: hide what the server would refuse anyway. The server stays the authority. */
export const canManageMembers = (role: GroupRole) => role === 'PRESIDENT' || role === 'SECRETARY';
export const canEditSettings = (role: GroupRole) => role === 'PRESIDENT' || role === 'TREASURER' || role === 'SECRETARY';
export const canViewMembers = (role: GroupRole) => role !== 'MEMBER';
export const isOffice = (role: GroupRole) => role === 'PRESIDENT' || role === 'TREASURER' || role === 'SECRETARY';

// --- Phase 2: savings ---------------------------------------------------------------------

export type BucketType = 'SAVINGS' | 'SOCIAL_FUND' | 'SHARES';
export type Frequency = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'PER_MEETING' | 'ADHOC';
export type CycleType = 'FIXED_TERM' | 'ROLLING';
export type PaymentMethod = 'CASH' | 'MOMO_MANUAL' | 'BANK';

export interface BucketTerms {
  mandatory: boolean;
  /** RWF decimal string */
  minimumContribution: string;
  contributionFrequency: Frequency;
  withdrawable: boolean;
  endDate: string | null;
  latePenaltyRule: { type: 'FLAT' | 'PERCENT'; value: string; graceDays: number } | null;
}

export interface BucketView {
  bucketId: string;
  name: string;
  description: string | null;
  type: BucketType;
  cycleType: CycleType;
  startDate: string;
  terms: BucketTerms;
  status: 'ACTIVE' | 'CLOSED';
  version: number;
  pendingChange: { changeId: string; proposed: BucketTerms; proposedBy: string | null; proposedAt: string } | null;
}

export interface Balances {
  memberId: string;
  buckets: { bucketId: string; name: string; type: BucketType; balance: string }[];
  total: string;
}

export interface TransactionView {
  transactionId: string;
  journalId: string;
  bucketId: string;
  bucketName: string;
  type: 'CONTRIBUTION' | 'WITHDRAWAL' | 'SHARE_OUT';
  amount: string;
  method: PaymentMethod | 'MOMO_API';
  externalRef: string | null;
  businessDate: string;
  reversed: boolean;
}

export interface StatementEntry {
  journalId: string;
  businessDate: string;
  type: string;
  description: string | null;
  externalRef: string | null;
  direction: 'DEBIT' | 'CREDIT';
  amount: string;
  balanceAfter: string;
}

export interface Statement {
  memberId: string;
  memberNumber: string;
  fullName: string | null;
  from: string;
  to: string;
  buckets: { bucketId: string; name: string; opening: string; entries: StatementEntry[]; closing: string }[];
}

export interface ObligationView {
  obligationId: string;
  bucketId: string;
  memberId: string;
  periodStart: string;
  dueDate: string;
  amountDue: string;
  amountPaid: string;
  status: 'OPEN' | 'PARTIAL' | 'PAID' | 'OVERDUE' | 'WAIVED';
}

export interface ContributionView {
  transactionId: string;
  journalId: string;
  memberId: string;
  bucketId: string;
  amount: string;
  method: PaymentMethod;
  externalRef: string | null;
  businessDate: string;
  recordedAt: string;
  reversed: boolean;
  memberBalance: string;
}

export interface ReversalView {
  requestId: string;
  journalId: string;
  journalType: string;
  reason: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  requestedBy: string;
  decidedBy: string | null;
  decisionReason: string | null;
  reversalJournalId: string | null;
  createdAt: string;
  decidedAt: string | null;
}

/** Spec 5.5 as the savings screens need it (the server stays the authority). */
export const canRecordContributions = (role: GroupRole) => role === 'TREASURER';
export const canApproveMoney = (role: GroupRole) => role === 'PRESIDENT' || role === 'TREASURER';
export const canSeeEveryonesSavings = (role: GroupRole) => role !== 'MEMBER';
