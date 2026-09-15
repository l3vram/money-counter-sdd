# Plan 039: `applyMovement` — the server applies a movement and its stock delta, atomically

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. Commit in the worktree per the git workflow below.
> SKIP updating `plans/README.md`; the reviewer maintains the index. Before
> reporting, audit every claim against an actual tool result from this session.
>
> **You write code only.** The Appwrite tables, columns, indexes and permissions
> are **provisioned by the orchestrator** over MCP — see "Provisioned outside
> this plan". Do not attempt to create them; you have no credentials and must
> not add any.
>
> **Drift check (run first)**:
> `git diff --stat 13a5472..HEAD -- webadmin/function/src/`

## Status

- **Priority**: P1 — this is F2 step 3, the core of shared inventory
- **Effort**: L
- **Risk**: HIGH — it is the authorization boundary for every operational write, and it moves stock
- **Depends on**: plan 038 for the payload contract (each product line carries a `productId`).
  The two run **in parallel**: this plan is written against the contract, not against 038's code,
  and they touch disjoint directories
- **Category**: backend + security
- **Planned at**: commit `13a5472`, 2026-09-15

## Why this matters

Today stock lives in each device's JSON and two sellers in one shop cannot share it. The design
(`advisor-plans/008-shared-inventory-DESIGN.md`) settles the architecture: **the movement is
the unit of truth and carries the stock delta; the server applies it; no client ever writes a
stock quantity.**

This plan builds that server side.

## What is already verified — do not re-derive

Verified empirically against the live project on 2026-09-15 (design §7):

| Fact | Consequence for this plan |
|---|---|
| `incrementRowColumn` / `decrementRowColumn` exist and accept `transactionId` | The movement insert and the stock deltas commit in **one** transaction |
| `min` / `max` bound the **result**, server-side (`400 column_limit_exceeded`) | Available, and deliberately unused for sales — see the owner's decision below |
| Increment on a missing row **fails with `404`**, it does not create | The Function must ensure the stock row exists first, **in the same transaction** |
| `StockItem.quantity` is `BigDecimal` with `Money.SCALE = 2` | Server quantities are **bigint centiunits** (quantity × 100). Never floats: deltas would drift |

**Owner decision (design §8.1)**: an offline sale that oversells on arrival is **allowed** and
stock goes negative. The sale already happened and the cash is in the drawer; a negative is an
honest signal to reconcile. **So sales pass no `min`.**

## Reuse the plan 035 pattern — decide, then write

`webadmin/function` already has this shape and 25 tests proving it works:

- `src/approvalPlan.js` — a **pure** planner, no `node-appwrite` import, returning plain-data
  operations.
- `src/transaction.js` — `runOperations(tablesDB, databaseId, operations, error)`: opens a
  transaction, stages, commits, rolls back on any throw and rethrows the **original** error.

**Reuse `transaction.js` as-is.** Do not fork it, do not "improve" it. If it needs a new
operation kind, add that kind to it with its own test — that is the one acceptable change.

Write `src/movementPlan.js` as the second pure planner. It must not import `node-appwrite`:
that is what lets the stock rules be tested with no network and no installed dependencies, and
it is why the Function has tests at all.

## Provisioned outside this plan (orchestrator, over MCP)

Do not create these; assume they exist. Their shape is part of your contract:

**Table `movements`** — row id is the movement's own id (idempotency key):
`orgId`, `branchId`, `sellerUid`, `type`, `at` (bigint), `currencyId`, `amountCents` (bigint),
`linesJson` (string, the product lines as JSON), `appliedAt` (bigint).

**Table `stock`** — row id is `{branchId}_{productId}`:
`orgId`, `branchId`, `productId`, `quantityCents` (bigint), `updatedAt` (bigint).

Both with `rowSecurity: true` and **no table-level write**: only the Function's dynamic key
writes them (§9.11 of `docs/ESTADO-Y-PASOS.md` — a row you cannot read answers 404, so read
grants are handed out per member exactly as `approveSignup` does it).

## The Function's contract

Action `applyMovement`, payload: one movement as plain data, including `id`, `type`, `orgId`,
`branchId`, `sellerUid`, `at`, `currencyId`, `amount`, and `lines[]` where **each line has
`productId` and `quantity`** (plan 038 makes that true client-side).

Behaviour, in order:

1. **Authorize** — this is the real security boundary (design §42), not the client's UI:
   - the caller must be the movement's `sellerUid`, **or** hold a role that may act for others;
   - the caller's membership `orgId` must equal the movement's `orgId`;
   - the movement's `branchId` must be in the caller's `branchIds`;
   - the caller's role must permit this movement type. Mirror `Role.kt`'s matrix; do **not**
     invent a second matrix — encode it once in the planner and test it.
2. **Idempotency** — `movements/{id}` is the key. If the row already exists, the movement was
   already applied: return success **without** re-applying any delta. A retry from a flaky
   network must never move stock twice.
3. **Derive the delta server-side** — from `type` and each line's `quantity`, using the same
   sign rule as `MovementType.affectsStockSign()`: `ALTA`/`ENTRADA` → `+1`,
   `MERMA`/`VENTA`/`VENTA_FIADO` → `-1`, `GASTO`/`COBRO` → `0`. **Never accept a delta from the
   client** — that is the design's central rule.
4. **Ensure the stock row**, then apply the delta, both staged in the transaction. A missing row
   404s on increment, so upsert it at `quantityCents: 0` first; the upsert must be idempotent so
   a concurrent movement does not clobber a quantity.
5. **Commit** the insert and every delta together.

A movement whose type has sign `0`, or whose lines are empty, inserts the movement and moves no
stock. A line with an empty `productId` (pre-v3, see plan 038) is **refused**: it cannot be
applied, and silently skipping it would lose a stock delta.

## Scope

**In scope**
- `webadmin/function/src/movementPlan.js` — the pure planner, plus tests.
- `webadmin/function/src/index.js` — the `applyMovement` action, wiring planner → executor.
- `webadmin/function/src/transaction.js` — only if a new operation kind is genuinely needed,
  with its own test.
- `webadmin/README.md` — document the action and its contract.

**Out of scope** (do NOT touch)
- Creating or altering any Appwrite table, column, index or permission.
- The Android app. Plan 038 owns the client contract; the client call is F2 step 5, a later plan.
- `approveSignup`, `approvalPlan.js`, and every existing action.
- The SUPERUSER guard and `getCaller`. `applyMovement` is called by **operational** users, not
  superusers, so it needs its **own** authorization path — and that is exactly why it must not
  be bolted onto the existing 403 guard. If the current guard would block it, STOP and report:
  changing that guard is a separate, gated decision.

## Commands

| Purpose | Command | Expected |
|---|---|---|
| Syntax | `npm --prefix webadmin/function run check` | exit 0 |
| Tests | `npm --prefix webadmin/function test` | exit 0, 0 failures |

Baseline: **25 tests**. This plan adds many; the count must rise.

## Git workflow

- Branch: `plan/039` (your worktree is on it).
- One commit per step, in Spanish, suffixed `(plan 039, paso N)`.
- Do NOT push, do NOT merge. **A push to `main` touching `webadmin/function/**` deploys by
  itself** — which is why merging is the orchestrator's decision at a human gate.

## Steps

### Step 0: Baseline
`npm --prefix webadmin/function test` → 25 passing. If not, STOP.

### Step 1: The authorization rules, pure and tested
In `movementPlan.js`, encode the checks of contract item 1 as pure functions over plain data
(the caller's membership, the movement). Return a refusal `{ ok: false, error: { statusCode,
message } }` or `{ ok: true, … }`, matching `approvalPlan.js`'s shape so the two read alike.

Test the matrix exhaustively: each role × each movement type, plus the cross-tenant and
cross-branch refusals. These are the tests that matter most in the whole plan.

### Step 2: Idempotency and the delta
Extend the planner: given the movement and whether `movements/{id}` already exists, return
either "already applied, no operations" or the full operation list — the movement insert plus
one ensure-row and one delta per line.

Test: the same movement twice yields operations once; sign per type; empty lines; sign-zero
types; and a line with an empty `productId` refused.

### Step 3: Wire it
Add the `applyMovement` case in `index.js`: read the caller's membership and whether the
movement row exists, call the planner, refuse or `runOperations`. Return a small, stable
payload.

### Step 4: Document
`webadmin/README.md`: the action, its payload, the authorization rules, and the reason the
delta is derived server-side and never accepted from the client.

## Test plan

| Case | Expectation |
|---|---|
| Each role × each movement type | permitted or refused per `Role.kt`'s matrix |
| Caller's org ≠ movement's org | refused |
| Movement's branch ∉ caller's `branchIds` | refused |
| Caller is not the seller and lacks the role to act for others | refused |
| `movements/{id}` already exists | success, **zero** stock operations |
| `ALTA` / `ENTRADA` | stock increases by the line quantity |
| `MERMA` / `VENTA` / `VENTA_FIADO` | stock decreases |
| `GASTO` / `COBRO` | movement inserted, no stock operation |
| Empty `lines` | movement inserted, no stock operation |
| A line with `productId == ""` | refused, movement not applied |
| A client-supplied delta field | ignored; the delta always comes from type × quantity |
| Fractional quantity (1.5) | 150 centiunits, exact |
| Several lines | one ensure-row and one delta each |

## Done criteria

1. `run check` and `test` exit 0, 0 failures, count well above 25.
2. `movementPlan.js` imports nothing from `node-appwrite`.
3. Every stock delta is derived from type × quantity; no code path reads a delta from the payload.
4. Applying the same movement twice moves stock once, proven by test.
5. The insert and all deltas go through `runOperations` in one transaction.
6. No Appwrite table, column or permission was created or altered by this plan.
7. `webadmin/README.md` documents the action.

## STOP conditions

- The baseline is not 25 tests, or the drift check shows `webadmin/function/src/` changed.
- The existing SUPERUSER guard would block an operational caller. Report it — changing that
  guard is a gated decision, not an executor's.
- A stock row cannot be ensured and incremented in the same transaction. Report what the API
  does; the fallback is documented serialization, not dropping atomicity silently.
- Authorization would need a second copy of the role matrix. Report it; one encoding, tested.

## Maintenance notes

- **No client ever writes a stock quantity.** The server derives every delta. If a future
  payload field looks like a delta, it is a bug.
- `movements/{id}` is the idempotency key, and the movement id is already a client-side
  `UUID.randomUUID()` — the design verified that at 9 call sites.
- Authorization belongs to the Function, not the app. The app's gates are UX (plan 037's
  lesson: one source of truth, and for the server that source is here).


---

## Amendment, 2026-09-15 (orchestrator, after the first review)

The executor delivered steps 0-2 and stopped at step 3 on this plan's own STOP condition. The
review of that work changed two things in the plan and settled the STOP.

**1. The ensure-row upsert must OMIT `quantityCents`.** This plan said the upsert "must be
idempotent so a concurrent movement does not clobber a quantity" without saying how. Sending
`quantityCents: 0` IS the clobber: `upsertRow` applies the fields you send, so on an existing
row it resets the quantity and then applies the delta. Verified against the live table — see
trap 9.14 in `docs/ESTADO-Y-PASOS.md` for the three-case table. Omitting the column is safe on
a new row only because the column carries `default: 0`, which is why it was provisioned that
way. A test must assert the operation's `data` has no `quantityCents` key.

**2. Centiunits are converted from the decimal STRING, never through a float.** See trap 9.15.

**3. The STOP is resolved: a two-lane top-level gate, with SUPERUSER as the default lane.**

```js
const OPERATIONAL_ACTIONS = new Set(['applyMovement']);
if (!OPERATIONAL_ACTIONS.has(action) && (!caller || caller.role !== 'SUPERUSER')) return 403;
```

An explicit allowlist, not an `if/else` on the role: an unknown or misspelled action falls
through to the SUPERUSER lane and is refused. Default-deny.

This does not weaken the boundary, it moves it. The top-level gate stops being the only
boundary and becomes the boundary for the **admin** actions; `applyMovement` carries its own
complete authorization inside `movementPlan` (membership row required, org match, branch match,
role/type matrix, sellerUid check), which is what this plan's contract item 1 always demanded
and what design §42 requires. The alternative — a second Function — would duplicate the client,
the dynamic-key handling and the transaction executor to avoid one allowlist.

**Owner: this is the one decision in this plan I took without you.** It is flagged at Gate B.
