# Plan 035: Approving a signup becomes one transaction, and the Function gets a test harness

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat d2f01c3..HEAD -- webadmin/function/src/index.js`
> If it changed since this plan was written, compare the "Current state" excerpts
> against the live code before proceeding; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2 (the damage is recoverable by hand, but it corrupts data and blocks the owner)
- **Effort**: M
- **Risk**: MED — it rewrites the most security-sensitive code in the project, which today has
  **zero tests**. That is also the reason the plan starts by giving it tests.
- **Depends on**: nothing. `webadmin/function` is disjoint from the Android app.
- **Category**: correctness + tech-debt
- **Planned at**: commit `d2f01c3`, 2026-09-14

## Why this matters

`approveSignup` writes five or more rows in sequence and **is not a transaction**. On
2026-09-14 the owner hit exactly that: the `users` row did not exist, the update 404'd, and the
approval died **after** creating the organization and its branches. The signup stayed PENDING,
so every retry minted a fresh `orgId` — **three "Las Pepas" organizations came out of three
attempts**, each with its own branch, none reachable by the user because the read grants are
handed out in the step that never ran.

That was patched twice: an `upsertRow` so the missing `users` row stops being fatal, and an
idempotence check so a retry resumes the existing organization. Both are the right patches and
both leave the underlying property untouched: **a failure halfway still leaves the database in
a state nobody designed.**

Appwrite has real transactions. This plan uses them, and on the way gives the Function the
tests it never had.

## The architecture decision, and why

Today the action interleaves validation, id generation, reads and writes in one linear
function. Three things fall out of that: it cannot be tested without a live Appwrite, the
atomicity has to be re-argued at every call site, and each new action repeats the pattern.

**The shape this plan builds instead — decide, then write:**

```
approvalPlan(input)  →  { ok: false, error } | { ok: true, operations: [...] }      (pure)
runTransaction(tablesDB, operations)                                                (thin)
```

1. **A pure planner** (`src/approvalPlan.js`), with **no `node-appwrite` import**. It takes
   the signup row, the caller's parameters and the current state it needs (existing membership,
   the organization, its branches), and returns either a refusal or a list of operations to
   perform. Every approval rule — OWNER creates an org, others must name an existing one,
   branches must belong to that org, an already-processed signup is refused — lives here and is
   unit-testable with no network.

2. **A thin transactional executor** (`src/transaction.js`): opens a transaction, stages the
   operations, commits, and rolls back on any failure. It knows nothing about approvals, so the
   next action that needs atomicity reuses it as-is.

Why this is the low-coupling answer: the planner depends on nothing, the executor depends only
on the SDK, and `index.js` becomes the wiring between them. A change to an approval rule touches
a pure function with tests. A change in how atomicity is achieved touches one file. Neither
forces the other, and neither touches the panel.

**What NOT to do**: do not make the planner "smart" about Appwrite (no SDK types, no ID
generation inside it unless injected), and do not let the executor know what an approval is.
The moment either happens, the coupling is back and the tests need a live server again.

## Current state

`webadmin/function/src/index.js`, `approveSignup(tablesDB, params, error, log)`:

1. `getRowOrNull(signups, signupId)` — refuses if absent or already processed.
2. `getRowOrNull(members, signupId)` → if it already has an `orgId`, **resume** it
   (the idempotence patch from 2026-09-14).
3. OWNER: `ID.unique()` for the org, `createRow(orgs)`, then one `createRow(branches)` per name.
4. Otherwise: require `orgId` + `branchIds`, verify the org exists and every branch belongs to
   it (`listAll(branches, Query.equal('orgId', [orgId]))`).
5. `upsertRow(members, signupId, …)` with `read("user:<uid>")`.
6. `upsertRow(users, signupId, {access: 'APPROVED', …})` with read+update grants.
7. `updateRow(signups, signupId, {status: 'APPROVED', approvedAt})`.
8. `grantTenantRead(...)` — merges `read("user:<uid>")` into the org row and each branch row.

Steps 3 through 8 are the ones that must become one unit.

### The API this plan uses (verified in `node-appwrite@25.2.0`)

| Call | Purpose |
|---|---|
| `tablesDB.createTransaction({ ttl })` | opens one; returns `$id` |
| `createRow`/`updateRow`/`upsertRow`/`deleteRow`/`getRow`/`listRows` with `transactionId` | stages a write, or reads uncommitted state inside it |
| `tablesDB.createOperations({ transactionId, operations })` | stages many at once |
| `tablesDB.updateTransaction({ transactionId, commit: true })` | commits |
| `tablesDB.updateTransaction({ transactionId, rollback: true })` | discards |

Twelve methods in the SDK's `tables-db.d.ts` accept `transactionId?: string`. **Confirm that
each method this plan stages is one of them before relying on it** — see STOP conditions.

### The boundary that transactions do NOT cover

**Only TablesDB rows are transactional. Appwrite Auth is not.** `users.updatePassword`,
`users.create` and friends cannot join a row transaction. This matters for `resetPassword`,
which changes a password (Auth) and then flags `signups` (a row): those two can never be atomic,
which is exactly why that action was patched to treat the flag as best-effort. **Do not try to
make `resetPassword` transactional** — document the boundary instead.

For `approveSignup` every write is a row, so the whole action fits in one transaction.

## Scope

**In scope**
- `webadmin/function/src/approvalPlan.js` — the pure planner, plus its tests.
- `webadmin/function/src/transaction.js` — the executor, plus its tests (with a fake tablesDB).
- `webadmin/function/src/index.js` — `approveSignup` rewired to planner + executor.
- `webadmin/function/package.json` — a `test` script using **`node:test`**, built into the
  Node 18 runtime, so this adds **zero dependencies**.
- A note in `webadmin/README.md` on how to run the tests.

**Out of scope** (do NOT touch)
- `resetPassword`, and any attempt to make Auth operations transactional — see the boundary
  above.
- The other actions (`reject`, `listOrgs`, `listBranches`, `listUsers`, `getSettings`,
  `setSettings`). They are single-write or read-only. A later plan may move them onto the
  executor, but not this one.
- The SUPERUSER guard and `getCaller`. Not this plan's subject; changing them here would mix an
  authorization change into a correctness change.
- Anything under `app/` — the Android app is untouched.
- The panel (`webadmin/app`). The response shape of `approve` must not change.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Syntax | `npm --prefix webadmin/function run check` | exit 0 |
| Tests | `npm --prefix webadmin/function test` | exit 0, 0 failures |

There is no baseline: **the Function has no tests today**. That is the starting condition, not
an omission to preserve.

## Git workflow

- Branch: `plan/035` off `main`.
- One commit per step, in Spanish, suffixed `(plan 035, paso N)`.
- Do NOT deploy. Deploying is a separate, owner-gated step — and note that a push to `main`
  touching `webadmin/function/**` now **deploys by itself** through the Git integration, so
  merging this plan is the deploy.

## Steps

### Step 0: A test harness for the Function

Add to `webadmin/function/package.json`:

```json
"scripts": {
  "check": "node --check src/index.js",
  "test": "node --test src/"
}
```

Add one trivial passing test to prove the harness runs, then delete it in the next step once
real tests exist. `node:test` ships with Node 18, so nothing is installed.

**Verify**: `npm test` runs and reports a passing test.

### Step 1: The pure planner

`src/approvalPlan.js`, exporting `approvalPlan(input)` where `input` is plain data:

```js
// { signupId, signup, params, existingMember, org, orgBranches, now, newId }
// newId: () => string — injected, so the planner stays pure and tests get stable ids
```

Returns `{ ok: false, error: { statusCode, message } }` or `{ ok: true, orgId, branchIds,
operations: [...] }`, where each operation is plain data:

```js
{ action: 'create' | 'update' | 'upsert', tableId, rowId, data?, permissions? }
```

Move every rule out of `index.js` unchanged — do not redesign them:
- absent signup → 404; already processed → 400 (with the current wording).
- OWNER → create the org (`newId()`), create one branch per name, defaulting to
  `['Sucursal principal']` when the list is empty, and `'Negocio sin nombre'` for a blank name.
- OWNER with an `existingMember.orgId` → resume it, creating nothing.
- other roles → require `orgId` and a non-empty `branchIds`; the org must exist; every branch id
  must belong to it, with the same error messages.
- always → the `members` upsert with its read grant, the `users` upsert with read+update, the
  `signups` status update, and the read grants merged into the org and branch rows.

The grant merge must stay idempotent: reuse the existing `withUserRead` logic by moving it into
this file and importing it from `index.js`, so there is one implementation.

**Verify**: tests covering every branch above, plus the regression that started this plan — a
signup whose `users` row does not exist still produces a complete plan.

### Step 2: The transactional executor

`src/transaction.js`, exporting `runOperations(tablesDB, databaseId, operations)`:

- `createTransaction({ ttl })` — pick a TTL comfortably above the slowest plausible approval
  and state the number in a comment.
- stage each operation, mapping `action` to the SDK call and passing `transactionId`.
- `updateTransaction({ transactionId, commit: true })`.
- on **any** throw: `updateTransaction({ transactionId, rollback: true })` inside its own
  try/catch (a failed rollback must not mask the original error), then rethrow the original.

Tests use a fake `tablesDB` object recording calls — no network. Cover: every operation staged
with the transaction id, commit called once on success, rollback called on a staged failure,
the original error rethrown rather than the rollback's, and no commit after a rollback.

**Verify**: `npm test` green.

### Step 3: Rewire `approveSignup`

`index.js` becomes: read the signup, the existing member and (for non-OWNER) the org and its
branches → call `approvalPlan` → on refusal throw the existing `httpError` → otherwise
`runOperations` → return the same `{ signupId, orgId, branchIds }` the panel already expects.

Delete the now-duplicated inline logic. `grantTenantRead` disappears as a separate step: its
grants are operations in the plan.

**Verify**: `npm run check` and `npm test` green. Re-read the diff and confirm the panel's
response shape is byte-identical.

### Step 4: Document the boundary

In `webadmin/README.md`: how to run the tests, that approval is now atomic, and that **Auth
operations cannot join a row transaction** — with `resetPassword` named as the example, so the
next person does not try.

## Test plan

| Case | Expectation |
|---|---|
| Absent signup | refusal, 404, current wording |
| Signup already APPROVED/REJECTED | refusal, 400 |
| OWNER, no existing member | creates org + one branch per name, members, users, signups, grants |
| OWNER, blank business name | `'Negocio sin nombre'` |
| OWNER, empty branch list | one `'Sucursal principal'` |
| OWNER with an existing member's orgId | **resumes**, creates no second organization |
| SELLER/ADMIN without orgId | refusal, 400 |
| SELLER/ADMIN with empty branchIds | refusal, 400 |
| SELLER/ADMIN, org not found | refusal, 400 |
| SELLER/ADMIN, a branch of another org | refusal naming the offending ids |
| `users` row absent | plan still complete — the regression that motivated this plan |
| Grant merge over existing permissions | preserved, no duplicates, idempotent |
| Executor: success | every op staged with the transaction id, one commit, no rollback |
| Executor: a staged op throws | rollback called, no commit, **original** error rethrown |
| Executor: rollback itself throws | original error still surfaces |

## Done criteria

1. `npm run check` and `npm test` exit 0 with 0 failures.
2. `approveSignup` performs its row writes in exactly one transaction, committed once.
3. A failure at any staged write leaves **no** partial rows — no orphan organization.
4. The `approve` response shape is unchanged; the panel needs no edit.
5. `approvalPlan.js` imports nothing from `node-appwrite`.
6. `grep -c "grantTenantRead" src/index.js` returns 0 — its grants are plan operations.
7. `webadmin/README.md` documents the test command and the Auth boundary.
8. `plans/README.md` status row updated.

## STOP conditions

- The drift check shows `index.js` changed since this plan was written.
- Any row method this plan stages does **not** accept `transactionId` in
  `node-appwrite@25.2.0`. Report which one; the fallback is to keep that single write outside
  the transaction and document why, not to abandon atomicity for the rest.
- Transactions turn out to be unavailable on this project's plan (tier-0). Report it — the
  patched idempotence already in place is the fallback, and the plan becomes a no-op rather
  than a half-measure.
- An approval rule cannot be moved without changing behavior. Report it; this plan preserves
  rules exactly, it does not redesign them.
- Making this work would require touching the SUPERUSER guard. Out of scope.

## Maintenance notes

- Every new multi-row action goes through `runOperations`. That is the whole point of splitting
  it out.
- The planner is where approval policy lives. If a rule needs changing, it changes there, with
  a test, and nothing else moves.
- Auth and rows cannot be atomic together. When an action mixes them, do the essential part
  first and make the accessory part best-effort with a log — the lesson from `resetPassword`,
  written down in §9.5 of `docs/ESTADO-Y-PASOS.md`.
