# Plan 032: Make the operational repository interfaces `suspend` so a network implementation is possible

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**:
> `git diff --stat 7286ff1..HEAD -- app/src/main/java/com/moneycounter/repository/ app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED (wide surface, but zero intended behavior change; the full suite is the gate)
- **Depends on**: `plans/030-role-fail-closed.md` (land 030 first so the permission path is settled before this touches the ViewModel)
- **Category**: tech-debt
- **Planned at**: commit `cfd8809`, 2026-09-12
- **Reconciled at**: commit `7286ff1`, 2026-09-15. Drift checked and resolved by the
  orchestrator before dispatch:
  - The four in-scope repositories and their `Json*` implementations are **untouched** since
    the plan was written. Only `MoneyCounterViewModel.kt` moved (+33/−13), from plans 030, 033
    and 034.
  - Every structural claim still holds: `viewModelScope` is used at exactly 15 sites, all
    reads and writes of the four repositories sit inside coroutine blocks, and
    `resolveTenantContext()` is still the one synchronous exception, called from `init` and
    from `setSellerContext`.
  - **Gap found and fixed**: the call-site list below cited five writes; there are **six**.
    `loadStock()` performs a conditional `stockRepository.saveAll(backfilled)` inside the same
    `launch` as its read. An executor working from the old list would have missed it. Line
    numbers were replaced with function names, which do not drift.

## Owner decisions (2026-09-14)

1. **Backwards compatibility is a non-issue here, and deliberately so.** This plan adds the
   `suspend` modifier and nothing else: the `*Json` objects that read and write the files are
   explicitly out of scope, so the file names, the format and the version numbers
   (`Movement` v2, `Closing` v3, `Product` v5) are untouched. An APK with this plan reads
   exactly what the previous one wrote. There is no migration, so there is nothing a migration
   could break. The versioned-migration machinery (`fromJson` reading `optInt("version", 1)`)
   stays available for the plan that does need it — F2 step 9, when `Product.stock` is retired.

2. **The real risk is timing, and the owner chose the loading state.** Today `init` loads
   synchronously, so the first frame already has data. With `load()` suspending, `init` must
   launch a coroutine and the first frame can be briefly empty. The owner's call: **show a
   loading state** rather than seeding the initial state from a second source. Reason: less
   code, less coupling, and when F2 makes stock server-authoritative the "loading" will be real
   anyway — so this builds the shape that is needed later instead of a stopgap.

   Note for the executor: this is the class of bug that produced the `sellerUid` NPE — `init`
   does more than it looks like. Anything `init` reads transitively must be declared above it
   (see §9.12 of `docs/ESTADO-Y-PASOS.md`).

## Why this matters

Every operational repository is local-JSON-only and synchronous:

```kotlin
interface StockRepository {
    fun load(): List<StockItem>
    fun saveAll(items: List<StockItem>)
}
```

A network-backed implementation cannot satisfy this shape — a remote call inside
a non-`suspend` function would block the caller (these run on the main thread via
`MoneyCounterViewModel`). The owner's decision is that **stock becomes
server-authoritative** (`advisor-plans/008-shared-inventory-DESIGN.md`), so every
one of these interfaces will gain an Appwrite implementation.

This plan makes that possible and **nothing else**. It is a pure, mechanical
refactor with no intended behavior change: same JSON files, same data, same
results. Doing it now — before any cloud code — is the difference between writing
the Appwrite layer once and writing it twice. It is deliberately boring.

Granular, intent-named operations (master plan §26: `increaseStock`,
`adjustStock`, rather than a generic `saveAll`) are **not** in this plan. Those
get designed per repository against the actual server operation when its cloud
implementation lands; designing them in the abstract now would be guesswork.

## Current state

### The interfaces (all in `app/src/main/java/com/moneycounter/repository/`)

```kotlin
interface StockRepository    { fun load(): List<StockItem>;  fun saveAll(items: List<StockItem>) }
interface MovementRepository { fun load(): List<Movement>;   fun saveAll(movements: List<Movement>) }
interface ClosingRepository  { /* load / saveAll, same shape */ }
interface ProductRepository  { /* load / save,    same shape */ }
```

`TenantRepository`, `CurrencyRepository`, `DenominationRepository`,
`UnitRepository`, `SavedCountRepository`, `ReceivableRepository`,
`PaymentRepository` and `WriteoffRepository` also exist — see Scope for which are
included.

### The call sites — all of them are already inside coroutines

In `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`. Identified by
**enclosing function**, not line number, because line numbers drift with every plan:

| Function | Repository calls inside its `viewModelScope.launch` |
|---|---|
| `loadProducts()` | `productRepository.load()` |
| `loadStock()` | `stockRepository.load()` **and** a conditional `saveAll(backfilled)` |
| `loadMovements()` | `movementRepository.load()` |
| `loadClosings()` | `closingRepository.load()` |
| `persistStock()` | `stockRepository.saveAll(items)` |
| `persistMovements()` | `movementRepository.saveAll(movements)` |
| `persistClosings()` | `closingRepository.saveAll(closings)` |
| `persistProducts()` | `productRepository.save(products)` |

Every one is already wrapped, so adding `suspend` is a no-op at the call site. **Confirm each
one in the live file before changing it** — `grep -n` for the repository name rather than
trusting this table, which is a map, not the territory.

Note `loadStock()` specifically: its write is *conditional* (`if (backfilled != items)`) and
shares the coroutine with its read. It is easy to miss when scanning for `viewModelScope.launch
{ …saveAll` one-liners, which is exactly what happened to the first version of this list.

**The one genuine exception** is `resolveTenantContext()`, which is **not** in a coroutine and
is called from `init { }` and from `setSellerContext(...)`:

```kotlin
private fun resolveTenantContext() {
    val (org, branch) = tenantRepository.ensureDefaultBootstrap(sellerUid)
    val allBranches = tenantRepository.loadBranches().ifEmpty { listOf(branch) }
    ...
    loadStock()
}
```

This is why `TenantRepository` is **out of scope**: converting it would force `init` and
`setSellerContext` to become asynchronous. That is not hypothetical — plan 033's device
testing produced a crash (`sellerUid` read as JVM-null) precisely because `init` does more than
it looks like. See §9.12 of `docs/ESTADO-Y-PASOS.md` before touching anything `init` reads.

### Conventions

- Repositories live in `app/src/main/java/com/moneycounter/repository/`; each
  interface sits beside its `Json*` implementation.
- The pure serialization object (`StockJson`, `MovementJson`, …) stays
  **untouched and non-`suspend`** — it does no I/O and its tests must not change.
- `MoneyCounterViewModel` already imports `kotlinx.coroutines.launch` and uses
  `viewModelScope` at 15 sites.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew :app:compileDebugKotlin` | exit 0 |
| Unit tests | `./gradlew :app:testDebugUnitTest` | exit 0, same count as baseline, 0 failures |
| Build APK | `./gradlew :app:assembleDebug` | exit 0 |

**Baseline: 507 tests on `main` @ `fcfe380`** (the plan was written when it was 424). This plan
must not change the count: it adds no behavior and no tests. A different number at Step 0 is a
STOP condition.

## Scope

**In scope** (add `suspend`, update implementations and call sites):
- `repository/StockRepository.kt` + `JsonStockRepository.kt`
- `repository/MovementRepository.kt` + `JsonMovementRepository.kt`
- `repository/ClosingRepository.kt` + `JsonClosingRepository.kt`
- `repository/ProductRepository.kt` + `JsonProductRepository.kt`
- `viewmodel/MoneyCounterViewModel.kt` (call sites only)
- Any test that constructs or calls these four repositories

**Out of scope** (do NOT touch):
- `TenantRepository` / `JsonTenantRepository` — see "Current state"; converting it
  forces `init` to become async. Explicitly deferred.
- `CurrencyRepository`, `DenominationRepository`, `UnitRepository`,
  `SavedCountRepository`, `ReceivableRepository`, `PaymentRepository`,
  `WriteoffRepository` — not part of the server-stock path; converting them adds
  risk without unblocking anything. A later plan may follow the same recipe.
- **Every pure `*Json` object** (`StockJson`, `MovementJson`, `ClosingJson`,
  `ProductJson`, …) — they perform no I/O. Do not make them `suspend`; their
  tests must not change.
- Any change to method **names**, parameters, return types, or semantics. Only
  the `suspend` modifier is added.
- Introducing granular/intent-named operations — explicitly a later plan.
- Any Appwrite or network code. None is written here.

## Git workflow

- Branch: `plan/032` off `feature/multi-tenant` (with 030 already landed).
- One commit: `refactor(repos): interfaces suspend para habilitar impl cloud (plan 032)`.
- Do NOT push or open a PR.

## Steps

### Step 0: Record the baseline

Run `./gradlew :app:testDebugUnitTest` and **write down the exact test count and
result**. If it is not green before you start, STOP (see STOP conditions).

### Step 1: Convert `StockRepository` and its implementation

Add `suspend` to both interface methods and to the `JsonStockRepository`
overrides. Change nothing else — same file paths, same JSON, same logic.

```kotlin
interface StockRepository {
    suspend fun load(): List<StockItem>
    suspend fun saveAll(items: List<StockItem>)
}
```

**Verify**: `./gradlew :app:compileDebugKotlin` → fails only with "suspend
function should be called only from a coroutine" errors at the call sites you
have not yet fixed. That is expected at this point; those are your worklist.

### Step 2: Fix the `StockRepository` call sites

In `MoneyCounterViewModel`, wrap any call not already inside a coroutine in
`viewModelScope.launch { }`, matching the existing style at line 312. Do **not**
use `runBlocking` anywhere.

`loadStock()` is called from `resolveTenantContext()` (line 205) and from
`selectBranch()` (line 214) — both synchronous. Make `loadStock()` itself launch
its own coroutine internally so its callers stay synchronous:

```kotlin
private fun loadStock() {
    viewModelScope.launch {
        val items = stockRepository.load()
        ...
    }
}
```

This keeps `resolveTenantContext()` and `init` synchronous, which is required —
see the out-of-scope note on `TenantRepository`.

**Verify**: `./gradlew :app:compileDebugKotlin` → exit 0.
Then `./gradlew :app:testDebugUnitTest` → exit 0, baseline count, 0 failures.

### Step 3: Repeat for `MovementRepository`

Same recipe. Call sites: line 962 (read, inside a coroutine — confirm) and
line 1015 (write, already wrapped).

**Verify**: `./gradlew :app:compileDebugKotlin` → exit 0, then
`./gradlew :app:testDebugUnitTest` → exit 0, baseline count.

### Step 4: Repeat for `ClosingRepository`

Call sites: lines 1020 and 1028.

**Verify**: same two commands, both green.

### Step 5: Repeat for `ProductRepository`

Call sites: lines 271 and 1171.

**Verify**: same two commands, both green.

### Step 6: Confirm the app still builds and nothing leaked

**Verify**:
- `./gradlew :app:assembleDebug` → exit 0
- `grep -rn "runBlocking" app/src/main/` → **no matches** (a `runBlocking` here
  would reintroduce the main-thread block this plan exists to remove)

## Test plan

**No new tests, and the test count must not change.** This refactor has no
observable behavior, so the existing 424-test suite is exactly the right gate:
if behavior changed, something is wrong.

Existing tests that call the four converted repositories must be updated
minimally — wrap the call in `kotlinx.coroutines.test.runTest { }` (or
`runBlocking` **in test code only**, if `runTest` is not already a dependency;
check what the current tests use before adding one). Change only what the
compiler requires. Do not rewrite assertions or restructure test files.

**Verification**: `./gradlew :app:testDebugUnitTest` → exit 0, **same count as
the Step 0 baseline**, 0 failures.

## Done criteria

Machine-checkable. ALL must hold:

- [ ] `./gradlew :app:compileDebugKotlin` exits 0
- [ ] `./gradlew :app:testDebugUnitTest` exits 0 with **the same test count as the
      Step 0 baseline** and 0 failures
- [ ] `./gradlew :app:assembleDebug` exits 0
- [ ] `grep -rn "runBlocking" app/src/main/` returns no matches
- [ ] `grep -n "suspend fun" app/src/main/java/com/moneycounter/repository/StockRepository.kt
      app/src/main/java/com/moneycounter/repository/MovementRepository.kt
      app/src/main/java/com/moneycounter/repository/ClosingRepository.kt
      app/src/main/java/com/moneycounter/repository/ProductRepository.kt`
      shows every method on all four interfaces as `suspend`
- [ ] `git diff --stat` shows **no changes** to any `*Json.kt` pure object, to
      `TenantRepository.kt`, or to `JsonTenantRepository.kt`
- [ ] No files outside the in-scope list are modified
- [ ] `plans/README.md` status row for 032 updated

## STOP conditions

Stop and report back (do not improvise) if:

- The Step 0 baseline suite is **not green before you start** — report the
  pre-existing failures; do not fix them here and do not proceed.
- The test count changes, or any test fails after a conversion. A behavior change
  means the refactor was not mechanical — report which test and why rather than
  adjusting the test to pass.
- You find yourself needing `runBlocking` in `app/src/main/` to make something
  compile. That means a call site cannot be made asynchronous without a design
  decision — report the call site instead.
- Making a call site asynchronous would change startup ordering in `init { }` or
  in `setSellerContext(...)` (the plan-030 role path). Report it.
- A code excerpt in "Current state" does not match the live code.
- You conclude an out-of-scope repository must also be converted. Report why; do
  not expand the scope.

## Follow-up found in review (2026-09-15) — `suspend` alone does not leave the main thread

The diff is compliant and behaviour-identical, which is exactly what was asked. But it is worth
writing down what it does **not** do, because the word "suspend" invites a wrong assumption.

`JsonStockRepository.load()` and its siblings still perform **blocking file I/O with no
dispatcher switch**, and `viewModelScope` runs on `Dispatchers.Main.immediate`. So the read
still blocks the main thread — exactly as it did before, since it was already a blocking call
inside a `launch` on Main. Verified after the change: no `withContext` and no `Dispatchers`
reference anywhere in the four implementations.

**Not a regression, and not a review failure**: this plan promised the modifier and nothing
else, and the executor was right not to add more. But anyone reading "the repositories are
suspend now" may assume the I/O moved off the main thread. It did not.

The natural completion is `withContext(Dispatchers.IO)` inside each `Json*Repository`, which is
a behaviour change (it removes main-thread blocking) and therefore deserves its own plan with
its own verification. It is also what makes the owner's loading-state decision matter: with the
I/O off Main there is a real gap to show a spinner for.

## Maintenance notes

- **This plan is deliberately behavior-free.** A reviewer should check exactly
  two things: that only the `suspend` modifier was added (no renames, no
  signature or semantic changes), and that the test count is unchanged.
- Next in the sequence (`advisor-plans/008-shared-inventory-DESIGN.md` §6): the
  Appwrite schema and the `applyMovement` Function — server-side, independent of
  this refactor.
- `TenantRepository` still forces synchronous startup. When tenant data moves to
  the cloud, `init { }` and `setSellerContext(...)` must become asynchronous
  together, and that interacts with plan 030's role wiring — plan it as one unit.
- When `StockRepository`'s Appwrite implementation lands, `saveAll` should
  **disappear from the client entirely**: under the decided architecture the
  client never writes a stock quantity. Do not build a granular client-side stock
  write API in the meantime.
