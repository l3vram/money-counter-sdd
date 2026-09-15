/**
 * Approval rules, as a pure function (plan 035).
 *
 * **This file must never import `node-appwrite`.** That is the whole point: the rules become
 * testable with no network and no installed dependencies, which is how the Function finally
 * got tests at all. Anything that needs the SDK — id generation, permission strings, the
 * writes themselves — is either injected or left to the caller.
 *
 * The output is a *plan*: a list of plain-data operations for the executor to stage in one
 * transaction. Deciding and writing are separated so a rule change touches this file and an
 * atomicity change touches `transaction.js`, and neither drags the other.
 */

const TABLE_USERS = 'users';
const TABLE_MEMBERS = 'members';
const TABLE_SIGNUPS = 'signups';
const TABLE_ORGS = 'orgs';
const TABLE_BRANCHES = 'branches';

/**
 * Adds a read grant for one user to a row's existing permissions, without disturbing them.
 * Several members share one organization, so this merges and never overwrites — and it is
 * idempotent, because approving twice would otherwise pile up duplicates.
 *
 * Takes the grant as a string so the planner stays free of the SDK.
 */
function withGrant(existing, grant) {
  const current = Array.isArray(existing) ? existing : [];
  return current.includes(grant) ? current : current.concat([grant]);
}

function refuse(statusCode, message) {
  return { ok: false, error: { statusCode, message } };
}

/**
 * @param {object} input
 * @param {string} input.signupId
 * @param {object|null} input.signup           the `signups` row
 * @param {object} input.params                what the panel sent (orgId, branchIds)
 * @param {object|null} input.existingMember   the `members` row, if any
 * @param {object|null} input.org              the `orgs` row, for non-OWNER roles
 * @param {Array<object>} input.orgBranches    that organization's branches, for non-OWNER
 * @param {number} input.now
 * @param {() => string} input.newId           injected id generator (sdk.ID.unique in prod)
 * @param {(uid: string) => string} input.readGrant    e.g. `read("user:abc")`
 * @param {(uid: string) => string} input.updateGrant  e.g. `update("user:abc")`
 * @returns {{ok: false, error: {statusCode: number, message: string}}
 *          |{ok: true, orgId: string, branchIds: string[], resumed: boolean, operations: object[]}}
 */
function approvalPlan(input) {
  const {
    signupId,
    signup,
    params = {},
    existingMember = null,
    org = null,
    orgBranches = [],
    now,
    newId,
    readGrant,
    updateGrant,
  } = input;

  if (!signup) {
    return refuse(404, 'Solicitud no encontrada (signupId=' + signupId + ')');
  }
  if (signup.status && signup.status !== 'PENDING') {
    return refuse(400, 'La solicitud ya fue procesada (estado actual: ' + signup.status + ')');
  }

  const operations = [];
  let orgId = params.orgId;
  let branchIds = params.branchIds;
  let resumed = false;

  // Idempotence for the OWNER path. A failure halfway used to leave the signup PENDING with
  // the organization already created, and each retry minted a fresh `orgId` — three
  // organizations came out of three attempts. With the whole thing in one transaction this
  // should no longer happen, but the check stays: the rows written before plan 035 are still
  // out there, and resuming is the correct answer for them.
  const alreadyAssignedOrg = existingMember && existingMember.orgId;

  if (signup.role === 'OWNER' && alreadyAssignedOrg) {
    orgId = existingMember.orgId;
    branchIds = Array.isArray(existingMember.branchIds) ? existingMember.branchIds : [];
    resumed = true;
  } else if (signup.role === 'OWNER') {
    orgId = newId();
    const orgName =
      (signup.businessName && String(signup.businessName).trim()) || 'Negocio sin nombre';
    operations.push({
      action: 'create',
      tableId: TABLE_ORGS,
      rowId: orgId,
      data: { name: orgName, whatsappNumber: '', status: 'ACTIVE', createdAt: now },
      permissions: [readGrant(signupId)],
    });
    const names =
      Array.isArray(signup.branches) && signup.branches.length
        ? signup.branches
        : ['Sucursal principal'];
    const created = [];
    for (const name of names) {
      const branchId = newId();
      operations.push({
        action: 'create',
        tableId: TABLE_BRANCHES,
        rowId: branchId,
        data: { orgId, name: String(name), status: 'ACTIVE', createdAt: now },
        permissions: [readGrant(signupId)],
      });
      created.push(branchId);
    }
    branchIds = created;
  } else {
    if (!orgId) {
      return refuse(400, 'Se requiere orgId para aprobar una solicitud de rol ' + signup.role);
    }
    if (!Array.isArray(branchIds) || branchIds.length === 0) {
      return refuse(
        400,
        'Se requiere al menos una branchId para aprobar una solicitud de rol ' + signup.role
      );
    }
    if (!org) {
      return refuse(400, 'Organización no encontrada: ' + orgId);
    }
    const validIds = new Set(orgBranches.map((r) => r.$id));
    const unknown = branchIds.filter((b) => !validIds.has(b));
    if (unknown.length) {
      return refuse(
        400,
        'Las branchIds no pertenecen a la organización ' + orgId + ': ' + unknown.join(', ')
      );
    }
    // The organization and its branches already exist, so the member only needs to be let in
    // to read them. Merging preserves the grants of whoever was already assigned there.
    operations.push({
      action: 'update',
      tableId: TABLE_ORGS,
      rowId: orgId,
      permissions: withGrant(org.$permissions, readGrant(signupId)),
    });
    for (const branchId of branchIds) {
      const branch = orgBranches.find((r) => r.$id === branchId);
      operations.push({
        action: 'update',
        tableId: TABLE_BRANCHES,
        rowId: branchId,
        permissions: withGrant(branch && branch.$permissions, readGrant(signupId)),
      });
    }
  }

  // `members` has rowSecurity enabled and no table-level read, so the row must carry its own
  // read grant: the Android app polls `members/{uid}` as the signed-in user. Without it the
  // read 404s, and a 404 is how the app spells "no membership".
  operations.push({
    action: 'upsert',
    tableId: TABLE_MEMBERS,
    rowId: signupId,
    data: { orgId, role: signup.role, branchIds },
    permissions: [readGrant(signupId)],
  });

  // Upsert, not update: the signup flow writes the `signups` row, while the `users` row is
  // only created when the app next boots with a session (`ensureUserDocument`). Someone who
  // registers and closes the app has no `users` row yet, and an update would 404 — which made
  // approving them impossible from the panel. The signup row carries the email.
  operations.push({
    action: 'upsert',
    tableId: TABLE_USERS,
    rowId: signupId,
    data: {
      email: signup.email || '',
      access: 'APPROVED',
      updatedAt: now,
      createdAt: signup.createdAt != null ? signup.createdAt : now,
    },
    permissions: [readGrant(signupId), updateGrant(signupId)],
  });

  operations.push({
    action: 'update',
    tableId: TABLE_SIGNUPS,
    rowId: signupId,
    data: { status: 'APPROVED', approvedAt: now },
  });

  return { ok: true, orgId, branchIds, resumed, operations };
}

module.exports = { approvalPlan, withGrant };
