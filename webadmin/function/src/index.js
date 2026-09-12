const sdk = require('node-appwrite');

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

async function approveSignup(tablesDB, params) {
  const { signupId } = params;
  const signup = await getRowOrNull(tablesDB, TABLE_SIGNUPS, signupId);
  if (!signup) throw httpError(404, 'Solicitud no encontrada (signupId=' + signupId + ')');
  if (signup.status && signup.status !== 'PENDING') {
    throw httpError(400, 'La solicitud ya fue procesada (estado actual: ' + signup.status + ')');
  }

  const now = Date.now();
  let orgId = params.orgId;
  let branchIds = params.branchIds;
  const createdBranches = [];

  if (signup.role === 'OWNER') {
    orgId = sdk.ID.unique();
    const orgName = (signup.businessName && String(signup.businessName).trim()) || 'Negocio sin nombre';
    await tablesDB.createRow({
      databaseId: DATABASE_ID,
      tableId: TABLE_ORGS,
      rowId: orgId,
      data: { name: orgName, whatsappNumber: '', status: 'ACTIVE', createdAt: now },
    });
    const names = Array.isArray(signup.branches) && signup.branches.length ? signup.branches : ['Sucursal principal'];
    for (const name of names) {
      const branchId = sdk.ID.unique();
      await tablesDB.createRow({
        databaseId: DATABASE_ID,
        tableId: TABLE_BRANCHES,
        rowId: branchId,
        data: { orgId, name: String(name), status: 'ACTIVE', createdAt: now },
      });
      createdBranches.push(branchId);
    }
    branchIds = createdBranches;
  } else {
    if (!orgId) throw httpError(400, 'Se requiere orgId para aprobar una solicitud de rol ' + signup.role);
    if (!Array.isArray(branchIds) || branchIds.length === 0) {
      throw httpError(400, 'Se requiere al menos una branchId para aprobar una solicitud de rol ' + signup.role);
    }
    const org = await getRowOrNull(tablesDB, TABLE_ORGS, orgId);
    if (!org) throw httpError(400, 'Organización no encontrada: ' + orgId);
    const orgBranches = await listAll(tablesDB, TABLE_BRANCHES, [sdk.Query.equal('orgId', [orgId])]);
    const validIds = new Set(orgBranches.rows.map((r) => r.$id));
    const unknown = branchIds.filter((b) => !validIds.has(b));
    if (unknown.length) {
      throw httpError(400, 'Las branchIds no pertenecen a la organización ' + orgId + ': ' + unknown.join(', '));
    }
  }

  // `members` has rowSecurity enabled and no table-level read, so the row must
  // carry its own read grant: the Android app polls `members/{uid}` as the signed-in
  // user. Without this the read 404s, and a 404 is how the app spells "no membership".
  await tablesDB.upsertRow({
    databaseId: DATABASE_ID,
    tableId: TABLE_MEMBERS,
    rowId: signupId,
    data: { orgId, role: signup.role, branchIds },
    permissions: [sdk.Permission.read(sdk.Role.user(signupId))],
  });
  await tablesDB.updateRow({
    databaseId: DATABASE_ID,
    tableId: TABLE_USERS,
    rowId: signupId,
    data: { access: 'APPROVED', updatedAt: now },
  });
  await tablesDB.updateRow({
    databaseId: DATABASE_ID,
    tableId: TABLE_SIGNUPS,
    rowId: signupId,
    data: { status: 'APPROVED', approvedAt: now },
  });

  return { signupId, orgId, branchIds };
}

async function listUsers(tablesDB) {
  const result = await listAll(tablesDB, TABLE_USERS, []);
  const users = [];
  for (const row of result.rows) {
    let role = null;
    let mustChangePassword = false;
    let signupStatus = null;
    const signup = await getRowOrNull(tablesDB, TABLE_SIGNUPS, row.$id);
    if (signup) {
      role = signup.role || null;
      mustChangePassword = Boolean(signup.mustChangePassword);
      signupStatus = signup.status || null;
    }
    users.push({
      id: row.$id,
      email: row.email || '',
      displayName: row.displayName || '',
      access: row.access || '',
      createdAt: row.createdAt != null ? row.createdAt : null,
      role,
      mustChangePassword,
      signupStatus,
    });
  }
  return { users, total: result.total };
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
        data = { signups: result.rows, total: result.total };
        break;
      }

      case 'approve':
        data = await approveSignup(tablesDB, params);
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
        await tablesDB.updateRow({
          databaseId: DATABASE_ID,
          tableId: TABLE_SIGNUPS,
          rowId: userId,
          data: { mustChangePassword: true },
        });
        data = { userId, reset: true };
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