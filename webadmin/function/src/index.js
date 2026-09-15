const sdk = require('node-appwrite');
const { approvalPlan } = require('./approvalPlan');
const { runOperations } = require('./transaction');

const DATABASE_ID = 'main';

const TABLE_USERS = 'users';
const TABLE_MEMBERS = 'members';
const TABLE_SIGNUPS = 'signups';
const TABLE_ORGS = 'orgs';
const TABLE_BRANCHES = 'branches';
const TABLE_SETTINGS = 'settings';

const SETTINGS_APP_ROW = 'app';

const MAX_ROWS = 1000;

function parseBody(req) {
  if (!req.bodyRaw) return {};
  try {
    const parsed = JSON.parse(req.bodyRaw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch (e) {
    return {};
  }
}

function httpError(statusCode, message) {
  const err = new Error(message);
  err.statusCode = statusCode;
  return err;
}

async function getRowOrNull(tablesDB, tableId, rowId) {
  if (!rowId) return null;
  try {
    return await tablesDB.getRow({ databaseId: DATABASE_ID, tableId, rowId });
  } catch (e) {
    return null;
  }
}

async function memberRole(tablesDB, userId, error) {
  if (!userId) return null;
  try {
    const row = await tablesDB.getRow({ databaseId: DATABASE_ID, tableId: TABLE_MEMBERS, rowId: userId });
    return row && row.role ? row.role : null;
  } catch (e) {
    // Swallowing this silently once cost hours: a missing membership and a broken client
    // both end up as `null`, and `null` is answered with a flat 403. Say which one it was.
    if (error) {
      const code = e && e.code ? e.code : 'sin codigo';
      error(`No se pudo leer members/${userId} (code=${code}): ${e && e.message}`);
    }
    return null;
  }
}

async function getCaller(req, tablesDB, users, error) {
  const userId = (req.headers && req.headers['x-appwrite-user-id']) || process.env.APPWRITE_FUNCTION_USER_ID;
  if (!userId) return null;

  let email = null;
  let name = null;
  try {
    const profile = await users.get({ userId });
    if (profile) {
      email = profile.email || null;
      name = profile.name || null;
    }
  } catch (e) {
    email = null;
    name = null;
  }

  const role = await memberRole(tablesDB, userId, error);
  return { userId, email, name, role };
}

async function listAll(tablesDB, tableId, queries) {
  const result = await tablesDB.listRows({
    databaseId: DATABASE_ID,
    tableId,
    queries: [...queries, sdk.Query.limit(MAX_ROWS)],
  });
  return { rows: result.rows || [], total: result.total || result.rows.length };
}

/**
 * Aprobación en UNA transacción (plan 035).
 *
 * La decisión vive en `approvalPlan` -pura y con tests- y la atomicidad en `runOperations`.
 * Acá sólo queda el cableado: leer lo que el planificador necesita, pedirle el plan, y
 * ejecutarlo. Un cambio de regla toca la función pura; un cambio de cómo se logra la
 * atomicidad toca el ejecutor. Ninguno arrastra al otro.
 *
 * Antes esto escribía cinco o más filas en secuencia y sin transacción: un fallo a mitad
 * dejaba la organización creada, el signup en PENDING, y cada reintento minteaba una
 * organización nueva.
 */
async function approveSignup(tablesDB, params, error, log) {
  const { signupId } = params;

  const signup = await getRowOrNull(tablesDB, TABLE_SIGNUPS, signupId);
  const existingMember = await getRowOrNull(tablesDB, TABLE_MEMBERS, signupId);

  // La organización y sus sucursales sólo hacen falta para los roles que no son DUEÑO: el
  // DUEÑO las crea. Evita dos lecturas en el camino más común.
  let org = null;
  let orgBranches = [];
  const necesitaOrgExistente = signup && signup.role !== 'OWNER' && params.orgId;
  if (necesitaOrgExistente) {
    org = await getRowOrNull(tablesDB, TABLE_ORGS, params.orgId);
    if (org) {
      const listado = await listAll(tablesDB, TABLE_BRANCHES, [
        sdk.Query.equal('orgId', [params.orgId]),
      ]);
      orgBranches = listado.rows;
    }
  }

  const plan = approvalPlan({
    signupId,
    signup,
    params,
    existingMember,
    org,
    orgBranches,
    now: Date.now(),
    newId: () => sdk.ID.unique(),
    readGrant: (uid) => sdk.Permission.read(sdk.Role.user(uid)),
    updateGrant: (uid) => sdk.Permission.update(sdk.Role.user(uid)),
  });

  if (!plan.ok) throw httpError(plan.error.statusCode, plan.error.message);

  if (plan.resumed && log) {
    log(`Reanudando aprobacion de ${signupId}: ya tenia la organizacion ${plan.orgId}`);
  }

  await runOperations(tablesDB, DATABASE_ID, plan.operations, error);

  return { signupId, orgId: plan.orgId, branchIds: plan.branchIds };
}

// Every list action answers `{ rows, total }` — the shape the panel's `ListResult<T>`
// expects. Returning `{ users }` / `{ signups }` instead left `result.rows` undefined in the
// panel, which then crashed on `.length`. TypeScript could not catch it: the payload crosses
// the wire as `unknown` and is cast.
//
// Three bulk reads, not one per user. The role comes from `members`, which is the
// authoritative assignment; `signups.role` is only what the person ASKED for at
// registration, its enum cannot even express SUPERUSER, and a seeded account (the superuser
// itself) has no signup row at all — so reading the role from there showed no role for the
// one account that has the highest one.
async function listUsers(tablesDB) {
  const [users, signups, members] = await Promise.all([
    listAll(tablesDB, TABLE_USERS, []),
    listAll(tablesDB, TABLE_SIGNUPS, []),
    listAll(tablesDB, TABLE_MEMBERS, []),
  ]);

  const byId = (result) => {
    const map = new Map();
    for (const row of result.rows) map.set(row.$id, row);
    return map;
  };
  const signupById = byId(signups);
  const memberById = byId(members);

  const rows = users.rows.map((row) => {
    const signup = signupById.get(row.$id) || null;
    const member = memberById.get(row.$id) || null;
    return {
      id: row.$id,
      email: row.email || '',
      displayName: row.displayName || '',
      access: row.access || '',
      createdAt: row.createdAt != null ? row.createdAt : null,
      // The assigned role, with the requested one as a fallback for someone approved before
      // their membership row existed.
      role: (member && member.role) || (signup && signup.role) || null,
      orgId: (member && member.orgId) || null,
      branchIds: (member && member.branchIds) || [],
      mustChangePassword: Boolean(signup && signup.mustChangePassword),
      signupStatus: (signup && signup.status) || null,
    };
  });

  return { rows, total: users.total };
}

module.exports = async ({ req, res, log, error }) => {
  // The dynamic API key reaches a function TWO different ways, and only one of them
  // works at execution time: the env var `APPWRITE_FUNCTION_API_KEY` exists during the
  // BUILD, while during EXECUTION the key arrives in the `x-appwrite-key` header. Reading
  // only the env var leaves the client with no credential, so every read fails, and
  // `memberRole` turns that into `null` — a permanent 403 for everyone.
  const dynamicKey =
    (req.headers && req.headers['x-appwrite-key']) || process.env.APPWRITE_FUNCTION_API_KEY || '';
  if (!dynamicKey) {
    error('No dynamic API key: neither the x-appwrite-key header nor APPWRITE_FUNCTION_API_KEY');
  }

  const client = new sdk.Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_ENDPOINT || 'https://fra.cloud.appwrite.io/v1')
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID || '6aa332f40001072d0747')
    .setKey(dynamicKey);

  const tablesDB = new sdk.TablesDB(client);
  const users = new sdk.Users(client);

  const params = parseBody(req);
  const action = params.action;

  const caller = await getCaller(req, tablesDB, users, error);
  if (!caller || caller.role !== 'SUPERUSER') {
    return res.json({ ok: false, error: 'Acceso denegado: se requiere sesión de superusuario' }, 403);
  }

  try {
    let data;
    switch (action) {
      case 'whoami':
        data = { userId: caller.userId, email: caller.email, name: caller.name, role: caller.role };
        break;

      case 'listSignups': {
        const status = params.status || 'PENDING';
        const result = await listAll(tablesDB, TABLE_SIGNUPS, [
          sdk.Query.equal('status', [status]),
          sdk.Query.orderDesc('createdAt'),
        ]);
        data = { rows: result.rows, total: result.total };
        break;
      }

      case 'approve':
        data = await approveSignup(tablesDB, params, error, log);
        break;

      case 'reject': {
        const { signupId } = params;
        const signup = await getRowOrNull(tablesDB, TABLE_SIGNUPS, signupId);
        if (!signup) throw httpError(404, 'Solicitud no encontrada (signupId=' + signupId + ')');
        await tablesDB.updateRow({
          databaseId: DATABASE_ID,
          tableId: TABLE_SIGNUPS,
          rowId: signupId,
          data: { status: 'REJECTED' },
        });
        data = { signupId, status: 'REJECTED' };
        break;
      }

      case 'listOrgs':
        data = await listAll(tablesDB, TABLE_ORGS, [sdk.Query.orderAsc('name')]);
        break;

      case 'listBranches':
        data = await listAll(tablesDB, TABLE_BRANCHES, [sdk.Query.orderAsc('name')]);
        break;

      case 'listUsers':
        data = await listUsers(tablesDB);
        break;

      case 'resetPassword': {
        const { userId, password } = params;
        if (!userId) throw httpError(400, 'Se requiere userId');
        if (typeof password !== 'string' || password.length < 8) {
          throw httpError(400, 'La contraseña temporal debe tener al menos 8 caracteres');
        }
        await users.updatePassword({ userId, password });

        // The forced-change flag lives in `signups`, and a seeded account -- the superuser --
        // has no row there. The password change above already happened and IS the point of
        // this action, so a missing flag row must not be reported as a failure: doing so made
        // the panel answer "Row with the requested ID could not be found" for a reset that had
        // actually succeeded, which is the worst kind of wrong answer.
        let mustChangePasswordFlagged = false;
        try {
          await tablesDB.updateRow({
            databaseId: DATABASE_ID,
            tableId: TABLE_SIGNUPS,
            rowId: userId,
            data: { mustChangePassword: true },
          });
          mustChangePasswordFlagged = true;
        } catch (e) {
          // Only a genuinely missing row is tolerated; anything else is a real failure.
          if (!e || e.code !== 404) throw e;
          log(`Sin fila en signups para ${userId}: contrasena cambiada sin marcar cambio forzado`);
        }

        data = { userId, reset: true, mustChangePasswordFlagged };
        break;
      }

      case 'getSettings': {
        const row = await getRowOrNull(tablesDB, TABLE_SETTINGS, SETTINGS_APP_ROW);
        data = { superuserWhatsapp: row && row.superuserWhatsapp ? String(row.superuserWhatsapp) : '' };
        break;
      }

      case 'setSettings': {
        const whatsapp = params.superuserWhatsapp == null ? '' : String(params.superuserWhatsapp);
        await tablesDB.upsertRow({
          databaseId: DATABASE_ID,
          tableId: TABLE_SETTINGS,
          rowId: SETTINGS_APP_ROW,
          data: { superuserWhatsapp: whatsapp, updatedAt: Date.now() },
        });
        data = { superuserWhatsapp: whatsapp };
        break;
      }

      default:
        throw httpError(400, 'Acción desconocida: ' + String(action));
    }

    return res.json({ ok: true, data }, 200);
  } catch (e) {
    const statusCode = e && e.statusCode ? e.statusCode : 500;
    const message = e && e.message ? e.message : 'Error interno del servidor';
    if (statusCode >= 500) {
      error('webadmin function error in action "' + String(action) + '": ' + message);
    } else {
      log('webadmin action "' + String(action) + '" rejected: ' + message);
    }
    return res.json({ ok: false, error: message }, statusCode);
  }
};