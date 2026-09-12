import { Account, Client, ExecutionMethod, Functions } from 'appwrite';
import type {
  BranchRow,
  ListResult,
  OrgRow,
  SignupRow,
  SignupStatus,
  UserInfo,
  UserRow,
} from './types';

export const ENDPOINT: string =
  import.meta.env.VITE_APPWRITE_ENDPOINT ?? 'https://fra.cloud.appwrite.io/v1';
export const PROJECT_ID: string =
  import.meta.env.VITE_APPWRITE_PROJECT_ID ?? '6aa332f40001072d0747';
export const FUNCTION_ID: string = import.meta.env.VITE_ADMIN_FUNCTION_ID ?? '';

const client = new Client().setEndpoint(ENDPOINT).setProject(PROJECT_ID);
const account = new Account(client);

// A client of its own for function calls: `setJWT` is per-client, and the session client
// must not start sending a JWT on every `account` call.
const functionClient = new Client().setEndpoint(ENDPOINT).setProject(PROJECT_ID);
const functions = new Functions(functionClient);

/**
 * "Load failed" (Safari) y "Failed to fetch" (Chrome) son el mismo TypeError: la peticion
 * no llego a completarse y el navegador no dice cual era. Etiquetar cada paso convierte un
 * mensaje inutil en un diagnostico, que es justo lo que faltaba al depurar este panel.
 */
function describeFailure(step: string, err: unknown): Error {
  const raw = err instanceof Error ? err.message : String(err);
  const anyErr = err as { code?: number; type?: string };
  const detalle = [
    anyErr && anyErr.code ? `code=${anyErr.code}` : null,
    anyErr && anyErr.type ? `type=${anyErr.type}` : null,
  ]
    .filter(Boolean)
    .join(' ');
  const esRed = /load failed|failed to fetch|networkerror/i.test(raw);
  const pista = esRed
    ? ' — la peticion no llego a completarse: revisa la conexion, un bloqueador de contenido, o que este dominio este registrado como plataforma Web del proyecto'
    : '';
  return new Error(`${step}: ${raw}${detalle ? ` (${detalle})` : ''}${pista}`);
}

let jwtPromise: Promise<string> | null = null;

function getJwt(): Promise<string> {
  if (!jwtPromise) {
    jwtPromise = account
      .createJWT()
      .then((result) => result.jwt)
      .catch((error) => {
        jwtPromise = null;
        throw describeFailure('No se pudo crear el token de sesion (createJWT)', error);
      });
  }
  return jwtPromise;
}

function parseResponseBody(body: string | undefined): { ok: boolean; data?: unknown; error?: string } {
  if (!body) return { ok: false, error: 'Respuesta vacía de la función' };
  try {
    const parsed = JSON.parse(body);
    if (parsed && typeof parsed === 'object') {
      return parsed as { ok: boolean; data?: unknown; error?: string };
    }
    return { ok: false, error: String(parsed) };
  } catch (e) {
    return { ok: false, error: body };
  }
}

async function callFunction<T>(action: string, params: Record<string, unknown> = {}): Promise<T> {
  if (!FUNCTION_ID) {
    throw new Error('Configuración inválida: falta VITE_ADMIN_FUNCTION_ID');
  }

  // Go through the SDK, never a hand-rolled fetch. Two bugs came out of doing it by hand:
  // the missing `X-Appwrite-Project` header (which Appwrite reports as a bogus CORS/origin
  // error), and the execution payload shape — the action JSON must be wrapped as the
  // `body` field of the create-execution request, not sent as the request itself, or the
  // function receives an empty body and answers "Acción desconocida: undefined".
  const jwt = await getJwt();
  functionClient.setJWT(jwt);

  let execution;
  try {
    execution = await functions.createExecution({
      functionId: FUNCTION_ID,
      body: JSON.stringify({ action, ...params }),
      async: false,
      xpath: '/',
      method: ExecutionMethod.POST,
    });
  } catch (err) {
    throw describeFailure(`No se pudo ejecutar la funcion (accion "${action}")`, err);
  }

  const status = execution.responseStatusCode;
  const payload = parseResponseBody(execution.responseBody);
  if (status < 200 || status >= 300) {
    throw new Error(payload.error ?? `HTTP ${status}`);
  }
  if (!payload.ok) {
    throw new Error(payload.error ?? 'Error desconocido de la función');
  }
  return payload.data as T;
}

export function clearAuth(): void {
  jwtPromise = null;
}

export async function login(email: string, password: string): Promise<UserInfo> {
  clearAuth();
  // Appwrite refuses to create a session while one is active ("Creation of a session is
  // prohibited when a session is active"). A previous attempt that authenticated but then
  // failed `whoami` — the 403 before the SUPERUSER row existed — leaves exactly that
  // dangling session, locking the user out of the login form. Drop it first.
  try {
    await account.deleteSession('current');
  } catch (e) {
    // No session to drop: that is the normal path.
  }
  try {
    await account.createEmailPasswordSession(email, password);
  } catch (err) {
    throw describeFailure('No se pudo iniciar sesion (createEmailPasswordSession)', err);
  }
  return whoami();
}

export async function logout(): Promise<void> {
  try {
    await account.deleteSession('current');
  } finally {
    clearAuth();
  }
}

export async function whoami(): Promise<UserInfo> {
  return callFunction<UserInfo>('whoami');
}

export async function listSignups(
  status: SignupStatus = 'PENDING',
): Promise<ListResult<SignupRow>> {
  return callFunction<ListResult<SignupRow>>('listSignups', { status });
}

export async function approve(
  signupId: string,
  overrides: { orgId?: string; branchIds?: string[] } = {},
): Promise<{ signupId: string; orgId: string; branchIds: string[] }> {
  return callFunction('approve', { signupId, ...overrides });
}

export async function reject(signupId: string): Promise<{ signupId: string }> {
  return callFunction('reject', { signupId });
}

export async function listOrgs(): Promise<ListResult<OrgRow>> {
  return callFunction<ListResult<OrgRow>>('listOrgs');
}

export async function listBranches(): Promise<ListResult<BranchRow>> {
  return callFunction<ListResult<BranchRow>>('listBranches');
}

export async function listUsers(): Promise<ListResult<UserRow>> {
  return callFunction<ListResult<UserRow>>('listUsers');
}

export async function resetPassword(
  userId: string,
  password: string,
): Promise<{ userId: string }> {
  return callFunction('resetPassword', { userId, password });
}

export async function getSettings(): Promise<{ superuserWhatsapp: string }> {
  return callFunction('getSettings');
}

export async function setSettings(superuserWhatsapp: string): Promise<{ superuserWhatsapp: string }> {
  return callFunction('setSettings', { superuserWhatsapp });
}