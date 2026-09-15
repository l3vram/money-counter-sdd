/**
 * Stages a list of plain-data operations in one Appwrite transaction and commits it
 * (plan 035).
 *
 * It knows nothing about approvals — that lives in `approvalPlan.js`. Any future action that
 * writes more than one row reuses this as-is, which is the reason it is a separate file.
 *
 * Verified against `node-appwrite@25.2.0`: `createTransaction`, `updateTransaction`
 * ({commit}/{rollback}) and a `transactionId` parameter on the row methods.
 */

/**
 * Seconds before an unfinished transaction expires on the server. An approval is a handful of
 * row writes inside one Function execution, whose own timeout is 30 s, so 60 leaves room for a
 * slow write without leaving abandoned transactions around for long if the runtime is killed
 * mid-flight.
 */
const TRANSACTION_TTL_SECONDS = 60;

/**
 * @param {object} tablesDB   the SDK service
 * @param {string} databaseId
 * @param {Array<{action: string, tableId: string, rowId: string, data?: object, permissions?: string[]}>} operations
 * @param {(msg: string) => void} [error]  logger, for the rollback path
 */
async function runOperations(tablesDB, databaseId, operations, error) {
  if (!Array.isArray(operations) || operations.length === 0) return;

  const transaction = await tablesDB.createTransaction({ ttl: TRANSACTION_TTL_SECONDS });
  const transactionId = transaction.$id;

  try {
    for (const op of operations) {
      await stage(tablesDB, databaseId, transactionId, op);
    }
    await tablesDB.updateTransaction({ transactionId, commit: true });
  } catch (e) {
    // The rollback gets its own try/catch on purpose: if it fails too, the caller must still
    // see the ORIGINAL error. Masking it with the rollback's would hide the real cause, which
    // is exactly the failure mode that made the duplicated-organizations bug hard to read.
    try {
      await tablesDB.updateTransaction({ transactionId, rollback: true });
    } catch (rollbackError) {
      if (error) {
        error(
          `No se pudo revertir la transaccion ${transactionId}: ${rollbackError && rollbackError.message}`
        );
      }
    }
    throw e;
  }
}

function stage(tablesDB, databaseId, transactionId, op) {
  const common = { databaseId, tableId: op.tableId, rowId: op.rowId, transactionId };
  // `permissions` and `data` are only sent when present: passing `permissions: undefined` to an
  // update would be read as "no permissions" and wipe the row's grants.
  const payload = { ...common };
  if (op.data !== undefined) payload.data = op.data;
  if (op.permissions !== undefined) payload.permissions = op.permissions;

  switch (op.action) {
    case 'create':
      return tablesDB.createRow(payload);
    case 'update':
      return tablesDB.updateRow(payload);
    case 'upsert':
      return tablesDB.upsertRow(payload);
    case 'delete':
      return tablesDB.deleteRow(common);
    default:
      throw new Error(`Operacion desconocida: ${op.action}`);
  }
}

module.exports = { runOperations, TRANSACTION_TTL_SECONDS };
