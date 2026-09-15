# 008 — Server-authoritative stock (F2): architecture

> **Status**: DESIGN — decided 2026-09-12 by the owner. Supersedes the earlier
> draft of this file. Written against `feature/multi-tenant @ cfd8809`.
> Master-plan references: §19–§26 (stock model), §41–§51 (authorization, offline,
> sync, idempotency, concurrency), FASE 4 and FASE 13.

## 1. The owner's decision

> *"Necesitamos que el stock sea desde el servidor así no hay problemas de
> concurrencia por sucursal, eso es lo más importante. Lo que se queda cacheado
> son los movimientos, que son por usuario y van subiendo más diferido […] los
> movimientos también deberían estar actualizados en el server pero para el
> OWNER, porque ya los demás usuarios lo tienen en el teléfono."*

Two different consistency models, on purpose:

| | Consistency | Why |
|---|---|---|
| **Stock** | Server-authoritative, strong, per branch | It is the contended resource — several sellers hit the same row |
| **Movements / Closings** | Local-first, eventually consistent, deferred push | Per user, nobody else contends them; the server copy exists for the OWNER |

## 2. The central idea

**The movement is the unit of truth and the carrier of the stock delta. The
server applies it atomically and idempotently. No client ever writes a stock
quantity.**

This single rule delivers both goals — no concurrency loss, no data loss —
because it removes client-side read-modify-write entirely.

The existing code already supports it:

```kotlin
// app/src/main/java/com/moneycounter/domain/Movement.kt:49-53
fun MovementType.affectsStockSign(): Int = when (this) {
    MovementType.ALTA, MovementType.ENTRADA -> 1
    MovementType.MERMA, MovementType.VENTA, MovementType.VENTA_FIADO -> -1
    MovementType.GASTO, MovementType.COBRO -> 0
}
```

A movement carries `id` (a `UUID.randomUUID()`, collision-safe across devices),
`organizationId`, `branchId`, `sellerUid`, `type` and `products[]` with a
`quantity` per line. So the stock delta is **derivable from the movement alone**:
`delta(line) = line.quantity × type.affectsStockSign()`.

And `MoneyCounterViewModel.recordMovement(m)` (line 971) is the **single funnel**
every movement already passes through. That is the one seam the whole client
integration hangs off.

## 3. Server design (Appwrite)

### Tables

- **`stock`** — row id `{branchId}_{productId}` (deterministic: no lookup, no
  duplicate rows possible). Columns: `orgId`, `branchId`, `productId`,
  `quantity`, `updatedAt`.
- **`movements`** — **row id = the client's `Movement.id` (the idempotency key)**.
  All movement fields, plus `stockApplied: Boolean`.
- **`closings`** — row id = the client's closing id. No stock interaction.

`quantity` must be stored so that atomic server-side deltas are possible. The
domain uses `BigDecimal` with `Money.SCALE`; store an integer in minor units
(quantity × 10^SCALE) and convert at the edges, so the delta is an integer
operation. Do not store a decimal string — it cannot be incremented atomically.

### Function `applyMovement` — the only path that writes stock

Every mutation goes through it. Steps, server-side:

1. **Authorize** — this is the real security boundary (§41–§42). Resolve the
   caller's uid → `members/{uid}` → role + `branchIds`. Reject unless the role
   permits this `MovementType` **and** `branchId ∈ member.branchIds` **and**
   `orgId == member.orgId`. The role→movement matrix must mirror `Role.kt`; keep
   the two in sync deliberately (see §7).
2. **Idempotent insert** — create `movements/{movement.id}` with
   `stockApplied = false`. If the row already exists: read it; if
   `stockApplied == true`, return success with current stock and **do nothing
   else**. This is what makes retries safe.
3. **Apply the deltas atomically** — for each product line, apply
   `quantity × affectsStockSign(type)` to `stock/{branchId}_{productId}` using a
   server-side atomic increment/decrement, never a read-then-write.
4. **Mark** `stockApplied = true`.
5. **Return** the authoritative stock rows that changed.

**Why this is crash-safe**: a failure between 2 and 3 leaves the movement stored
with `stockApplied = false`; the client's retry re-enters, sees the row is not
applied, and completes step 3. Nothing is lost, and nothing is applied twice.

**Residual risk, stated honestly**: a crash *in the middle* of step 3 on a
multi-product movement could apply some lines and not others; the retry would
re-apply the ones that succeeded. Mitigation: apply all lines within one Function
execution (most movements touch 1–3 products), and add the reconciler below.

### Reconciler (the safety net)

A scheduled Function recomputing, per branch,
`quantity = Σ deltas of all movements where stockApplied = true`
and correcting drift. Movements remain the source of truth (§38: *"Los rollups
son una optimización. No reemplazan los movimientos como fuente de verdad"*);
the `stock` table is a fast authoritative projection, and the reconciler is what
makes that claim true over time.

## 4. Client design

### Stock — read-only

The client **never** computes an authoritative stock value. `stock.json` becomes
a cache of server values plus optimistic pending deltas, for display only. The
pure functions in `StockItem.kt` (`increaseStock`, `decreaseStock`,
`adjustStock`) survive as the **optimistic projection** used while a movement is
queued — not as a persistence path.

### Movements — local-first with an outbox

`recordMovement(m)` becomes:

```
persist locally  →  enqueue in outbox  →  call applyMovement
    on success:  store returned authoritative stock, mark movement synced
    on failure:  stay queued; retry on reconnect (idempotent by id)
```

Offline, the sale completes locally and the UI shows the optimistic stock with a
pending indicator. On reconnect the outbox flushes in order and the authoritative
stock replaces the optimistic value.

**Overselling while offline stays possible.** That is already this app's
documented behavior — `StockItem.kt` states quantity is *"warn-and-allow: over-
selling may push it negative"*. This design does not make it worse, and the
server becomes the place where a stricter rule could later be enforced. Flagging
it so it is a chosen tradeoff, not a surprise.

### Movements — pull only where it is needed

Sellers already hold their own movements locally, so they **push only**. Pulling
the branch/organization journal is an OWNER/ADMIN feature (dashboard, branch
history), paginated per §51. This roughly halves the sync surface.

### Closings

Same pattern as movements — local-first, idempotent push by id — but with no
stock interaction, so no Function is required beyond authorization.

## 5. The structural prerequisite

```kotlin
interface StockRepository {
    fun load(): List<StockItem>          // not suspend, whole-collection
    fun saveAll(items: List<StockItem>)  // rewrites everything
}
```

Not `suspend` (a network call would block the ViewModel) and whole-collection
(`saveAll` cannot be atomic and destroys concurrent edits by construction).
`MovementRepository`, `ProductRepository` and `ClosingRepository` share the shape.

Master plan §26 called this out in advance: *"Evitar APIs demasiado genéricas. No
crear `updateStock(...)` como única API pública"*, prescribing intent-named
operations.

**This refactor must land before any cloud code.** It is mechanical, wide, and
fully guarded by the existing 424 tests — and doing it after the Appwrite work
would mean rewriting that work.

## 6. Development order — most structural first

| # | Step | Why it is placed here |
|---|---|---|
| 1 | **Permission fix** (`plans/030`) | A client with wrongly-open permissions turns, under shared stock, from a local problem into branch-wide corruption |
| 2 | **Repository interfaces → `suspend` + granular** | Pure refactor, no behavior change. Everything cloud depends on it; doing it later means redoing the cloud work |
| 3 | **Appwrite schema + `applyMovement` Function** (authorization + idempotency) | Server-side, independent of the client; testable on its own. The real security boundary |
| 4 | **Client: stock becomes server-read** | Local stock demoted to cache; `Product.stock` still untouched |
| 5 | **Client: outbox + `recordMovement` funnel** | The offline path and the retry semantics |
| 6 | **Closings push** | Same pattern as 5, simpler |
| 7 | **OWNER pull + pagination** (§46 indexes, §51) | The only consumer that needs other users' movements |
| 8 | **Branch selector scoped to `member.branchIds`** | `selectBranch()` exists but no composable calls it, and it validates only the org — not that the member is assigned to the branch |
| 9 | **Retire `Product.stock`** (§56) | Only once every consumer reads the projection |

Steps 2, 3 and 5 are the large ones. Steps 1 and 2 are the "do not drag
architecture debt" foundation the owner asked to start from.

## 7. Facts verified, and what still must be checked

Verified in the repo at `cfd8809`:
- Appwrite Android SDK pinned at `io.appwrite:sdk-for-android:25.2.0`
  (`app/build.gradle.kts:98`); `minSdk = 26`, `targetSdk = 34`.
- Movement ids are `UUID.randomUUID()` at 9 call sites in `MoneyCounterViewModel`.
- `recordMovement` (line 971) is the single write funnel for movements.
- `affectsStockSign()` already encodes the per-type stock direction.
- All operational repositories are `Json*` (local); only identity/access/tenant
  master data is on Appwrite.

**Verified against the live project on 2026-09-15** (all of §7's open items are now closed):

| Question | Answer, verified empirically |
|---|---|
| Is there an atomic increment/decrement? | **Yes.** `incrementRowColumn` / `decrementRowColumn` in `node-appwrite@25.2.0`, and the same operations exist in the MCP catalog |
| Do they accept a transaction? | **Yes** — both take `transactionId`, so a stock delta and the movement insert commit together |
| Are `min` / `max` real bounds? | **Yes, on the RESULT.** Decrementing `1789407949223` by 3 with `min: 1789407949221` was refused by the server: `400 column_limit_exceeded — "updatedAt" has reached the minimum value of…`, and the row was left untouched |
| Behaviour on a missing row? | **Fails, does not create**: `404 column_not_found`. The Function must ensure the stock row exists (idempotent upsert of quantity 0) before incrementing — in the same transaction |
| Is `quantity` compatible? | `StockItem.quantity` is `BigDecimal` with `Money.SCALE = 2`. The increment API takes a *number*, so the server column must be a **bigint of centiunits** (quantity × 100) to keep deltas exact. Floats would drift |

**What this changes in the design**: §7 warned that without an atomic primitive, step 3 would
need another serialization mechanism. That contingency is off the table. Better still, `min: 0`
would give server-enforced oversell protection as a single parameter — see decision 1 below for
why it is deliberately **not** used.

## 8. Owner decisions (2026-09-15) — the open questions, closed

### 1. An offline sale that oversells on arrival: **allow it, stock goes negative**

The sale already happened and the money is in the drawer. Refusing it creates a worse problem
than a negative number: someone has to decide what happens to the cash. A negative quantity is
an honest signal that something needs reconciling — a shortage, an unrecorded write-off, a bad
count.

**Implementation**: sales do **not** pass `min`. The `min: 0` protection verified above is
available and deliberately unused for sales. It may still be right for operations that are not
a completed fact (a manual stock correction, say), which is a separate decision when that
arrives.

### 2. An OWNER device pulls **the last 30 days**, older history on demand

A phone with an unbounded journal gets slow. 30 days covers the operation and the month's
closings; older pages are fetched when asked for. This sets the pagination and index work in
step 7.

### 3. **The cloud starts empty** — no stock migration, no history migration

Neither existing stock nor existing movements are uploaded. Inventory is loaded through normal
stock entries (`altas`).

This was the owner's call over the advisor's recommendation to seed stock, and on reflection it
is the better one: an `alta` is a **movement**, so it flows through `recordMovement` →
`applyMovement` like everything else. The stock therefore arrives through the single write
funnel, leaves an audit trail, and needs **no migration code at all** — removing a whole plan
whose main risk was inventing history. The cost is one manual load, once.

**Consequence to hold in view**: until that load happens, shared stock reads zero while the
shop has merchandise. So the rollout order is: deploy, load inventory via altas, then trust the
shared numbers. Local `Product.stock` remains the source for the app until step 9 retires it,
which is what makes that window survivable.

## 9. What this document is not

No code was written or modified to produce it. Line references point at
`feature/multi-tenant @ cfd8809`.
