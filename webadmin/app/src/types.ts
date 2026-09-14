export type Role = 'OWNER' | 'ADMIN' | 'SELLER';
/**
 * A role as assigned in `members`. Kept apart from [Role] on purpose: the signup form can
 * only request the three business roles, while the SUPERUSER is seeded by hand and never
 * requested — its own `signups.role` enum cannot even express it.
 */
export type MemberRole = Role | 'SUPERUSER';
export type SignupStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type OrgStatus = 'ACTIVE' | 'SUSPENDED' | 'DELETED';
export type BranchStatus = 'ACTIVE' | 'SUSPENDED';

export interface UserInfo {
  userId: string;
  email: string | null;
  name: string | null;
  role: string | null;
}

export interface SignupRow {
  $id: string;
  email: string;
  role: Role;
  businessName?: string | null;
  branches?: string[] | null;
  mustChangePassword: boolean;
  status: SignupStatus;
  createdAt?: number | null;
  approvedAt?: number | null;
}

export interface OrgRow {
  $id: string;
  name: string;
  whatsappNumber?: string | null;
  status: OrgStatus;
  createdAt?: number | null;
}

export interface BranchRow {
  $id: string;
  orgId: string;
  name: string;
  status: BranchStatus;
  createdAt?: number | null;
}

export interface UserRow {
  id: string;
  email: string;
  displayName: string;
  access: string;
  createdAt?: number | null;
  role: MemberRole | null;
  orgId?: string | null;
  branchIds?: string[];
  mustChangePassword: boolean;
  signupStatus: SignupStatus | null;
}

export interface ListResult<T> {
  rows: T[];
  total: number;
}

export const ROLE_LABEL: Record<MemberRole, string> = {
  OWNER: 'Dueño',
  ADMIN: 'Administrador',
  SELLER: 'Vendedor',
  SUPERUSER: 'Superusuario',
};

export function formatDate(value: number | null | undefined): string {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' });
}

export function errMsg(error: unknown): string {
  if (error instanceof Error && error.message) return error.message;
  return String(error);
}

export function isAccessDenied(error: unknown): boolean {
  return errMsg(error).toLowerCase().includes('acceso denegado');
}