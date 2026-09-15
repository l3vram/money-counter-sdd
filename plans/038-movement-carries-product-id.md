# Plan 038: A movement line carries its `productId`

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. Commit in the worktree per the git workflow below.
> SKIP updating `plans/README.md`; the reviewer maintains the index. Before
> reporting, audit every claim against an actual tool result from this session.
>
> **Drift check (run first)**:
> `git diff --stat 13a5472..HEAD -- app/src/main/java/com/moneycounter/domain/Movement.kt app/src/main/java/com/moneycounter/repository/JsonMovementRepository.kt app/src/main/java/com/moneycounter/domain/MovementMigration.kt`

## Status

- **Priority**: P1 — blocks F2 step 3
- **Effort**: M
- **Risk**: MED — touches the movement journal's persisted shape
- **Depends on**: nothing in code. Disjoint from plan 039, which runs in parallel
- **Category**: data model
- **Planned at**: commit `13a5472`, 2026-09-15
- **Found by**: planning recon for F2 step 3

## Why this matters

The whole F2 premise is that **the movement is the unit of truth and carries the stock delta**
(`advisor-plans/008-shared-inventory-DESIGN.md` §2). That premise does not hold today:

```kotlin
data class MovementProductLine(
    val name: String,        // ← the product is identified by NAME
    val unit: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal
)
```

There is **no `productId`**. Meanwhile stock is keyed by it — `StockItem.productId`, and the
server's stock row will be `{branchId}_{productId}`. So the `applyMovement` Function cannot
tell which stock row a movement touches, and it cannot resolve name → id either: **products are
local**, they do not exist on Appwrite.

Locally this never surfaced because stock is mutated at the same moment the movement is
recorded (the dual write of plan 022). The journal was never the source for stock, so nobody
noticed it could not be.

Keying stock by name is not an option: names change, and stock would silently split in two the
first time a product is renamed.

### A consequence worth recording

This retroactively supports the owner's decision that **the cloud starts empty with no history
migration** (design §8.3). A migration could not have derived stock from the existing journal
anyway — the ids are not in it. The decision was right for a reason that had not been
articulated when it was made.

## Current state

| Piece | Where | Today |
|---|---|---|
| The line | `domain/Movement.kt:13` | `name`, `unit`, `quantity`, `unitPrice`, `subtotal` |
| Stock key | `domain/StockItem.kt` | `productId` (`:101`, `:108`, `:123`, `:137`) |
| Persistence | `repository/JsonMovementRepository.kt` | `VERSION = 2`; `fromJson` accepts version 1 **or** `VERSION` |
| Line decoding | `JsonMovementRepository.kt:177` | builds `MovementProductLine` |
| Legacy migration | `domain/MovementMigration.kt:68`, `:85` | builds lines from older shapes |
| Construction sites | `MoneyCounterViewModel.kt:616`, `:661`, `:719`, `:820`, `:1478` | five; the `Product` is in scope at each |

## The design decision, and why

Add the field **with a default**:

```kotlin
val productId: String = ""
```

The default is what makes this small and safe:
- No construction site breaks at compile time, so the executor changes them deliberately rather
  than being forced to by the compiler — and the ones it misses are visible as an empty id
  rather than as a build error late in the plan.
- Old persisted lines (version 2 and 1) decode with `productId = ""`, which reads as **"unknown,
  pre-v3"**. No migration invents an id it cannot know.
- The `applyMovement` Function (plan 039) treats an empty `productId` as **"this line cannot
  move stock"** and refuses the movement. Correct: a line with no id is not applicable
  server-side, and the old local movements it comes from never go to the cloud anyway.

**Do not** try to backfill ids for old lines by matching on name. It would be a guess, and a
wrong guess silently moves the wrong product's stock.

## Scope

**In scope**
- `domain/Movement.kt` — the new field on `MovementProductLine`.
- `repository/JsonMovementRepository.kt` — serialize and read it; bump `VERSION` to 3, keeping
  versions 1 and 2 readable.
- `domain/MovementMigration.kt` — pass it through where lines are built.
- The five construction sites in `MoneyCounterViewModel.kt` — pass the real product id.
- Tests: round-trip of the new field, a version-2 file decoding to an empty id, and each
  construction site's movement carrying the id.

**Out of scope** (do NOT touch)
- `StockItem` and the local stock logic. It already works by id; nothing changes.
- The dual write of plan 022. Local behaviour is unchanged by this plan.
- `webadmin/` — plan 039 owns the server side and runs in parallel.
- Any attempt to infer ids for existing persisted lines.
- Removing `name` from the line. It is still what the UI shows, and the history must keep
  showing what was sold even if the product is later renamed or deleted.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Compile | `./gradlew :app:compileDebugKotlin` | exit 0 |
| Tests | `./gradlew :app:testDebugUnitTest` | exit 0, 0 failures |
| APK | `./gradlew :app:assembleDebug` | exit 0 |

**Baseline: 509 tests on `main` @ `13a5472`.** This plan adds tests; the count must rise, never
fall. Use `--rerun-tasks` on the final verification so you are not reading a cached green.

## Git workflow

- Branch: `plan/038` (your worktree is on it).
- One commit per step, in Spanish, suffixed `(plan 038, paso N)`.
- Do NOT push, do NOT merge.

## Steps

### Step 0: Baseline
Run the three commands. If the count is not 509, STOP and report.

### Step 1: The field
Add `val productId: String = ""` to `MovementProductLine`, with a KDoc saying what the empty
value means (unknown, pre-v3; not applicable server-side) and why it is not backfilled.

**Verify**: compile green, suite green, count unchanged — nothing reads it yet.

### Step 2: Persistence, version 3
In `JsonMovementRepository`: write `productId` on each line; read it with a default of `""`;
bump `VERSION` to 3. `fromJson` must keep accepting **1, 2 and 3** — check how the current
guard is written (`if (version != 1 && version != VERSION) return emptyList()`) and extend it
so a version-2 file still loads. A user's existing journal **must not** be dropped.

**Verify**: tests for a v3 round trip, and for a v2 document decoding with empty ids. Count
rises.

### Step 3: Legacy migration passes it through
`MovementMigration.kt` builds lines at two places. Where a product id is genuinely available,
pass it; where it is not, leave the default and do not guess.

**Verify**: existing migration tests stay green; add one fixing that migrated lines carry an
empty id rather than a fabricated one.

### Step 4: The five construction sites
In `MoneyCounterViewModel.kt` at lines ~616, ~661, ~719, ~820 and ~1478, pass the real product
id. The `Product` is in scope at each — confirm that per site with `grep -n` rather than
trusting these line numbers, which drift.

Line ~1478 is a helper (`MovementProductLine(name = name, unit = unit, …)`); it will need the id
threaded in from its callers. Follow it up and pass it; if a caller genuinely has no product id
available, STOP and report which — that would mean a movement path that cannot move stock, and
that is a design question, not an executor's call.

**Verify**: a test per site, or one parameterised test, asserting the built movement's line
carries the id. Full suite green with `--rerun-tasks`.

## Test plan

| Case | Expectation |
|---|---|
| v3 round trip | `productId` preserved exactly |
| A version-2 document | loads, every line `productId == ""` — **not** dropped |
| A version-1 document | still loads, as today |
| A line with no `productId` in the JSON | decodes to `""`, never null, never a crash |
| Migrated legacy lines | empty id, nothing fabricated |
| Each construction site | the movement's line carries the real product id |
| `name` still present | unchanged; history keeps showing what was sold |

## Done criteria

1. The three commands exit 0, 0 failures, count higher than 509.
2. `MovementProductLine` has `productId` defaulting to `""`.
3. `VERSION` is 3 and versions 1 and 2 still load, with a test proving each.
4. All five construction sites pass a real id, verified by test.
5. No id is inferred from a name anywhere.
6. `StockItem`, the local stock logic and `webadmin/` are untouched.

## STOP conditions

- Baseline is not 509 tests, or the drift check shows in-scope files changed.
- A construction site has no product id available. Report which; do not invent one.
- Bumping the version would drop an existing journal. That is data loss — stop.
- The change would require touching `StockItem` or the dual write. Out of scope; report.

## Maintenance notes

- `name` and `productId` coexist on purpose: the id is for moving stock, the name is what the
  history shows. Removing either breaks one of the two jobs.
- An empty `productId` means "pre-v3, unknown". Treat it as "cannot move stock", never as a
  reason to guess.
