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
