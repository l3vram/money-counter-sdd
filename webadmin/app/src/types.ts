export type Role = 'OWNER' | 'ADMIN' | 'SELLER';
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
  role: Role | null;
  mustChangePassword: boolean;
  signupStatus: SignupStatus | null;
}

export interface ListResult<T> {
  rows: T[];
  total: number;
}

export const ROLE_LABEL: Record<Role, string> = {
  OWNER: 'Dueño',
  ADMIN: 'Administrador',
  SELLER: 'Vendedor',
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