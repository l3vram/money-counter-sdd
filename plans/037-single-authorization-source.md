# Plan 037: `createClosing` authorizes from the permission service, not the UI state

> **Executor instructions**: this plan is two lines of product code. Per the skill's
> `token-budget.md` §Batching it is executed by the orchestrator directly rather than
> dispatched — the spawn overhead exceeds the work. The **review** is where the value is, and
> it runs at full rigour because the risk router classes authorization changes as HIGH.
>
> **Drift check**: `git diff --stat acf81f1..HEAD -- app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`

## Status

- **Priority**: P1
- **Effort**: XS
- **Risk**: HIGH by category (authorization), XS by surface
- **Depends on**: plan 036 (merged at `acf81f1`), which closed the exploitable window this plan
  removes the cause of
- **Category**: security
- **Planned at**: commit `acf81f1`, 2026-09-15
- **Found by**: the plan 036 review gauntlet

## Why this matters

There are **two sources of truth for authorization** in `MoneyCounterViewModel`:
`permissionService` for every mutation, and the **UI state** for closings.

```kotlin
fun createClosing(movementIds: List<String>): String? {
    val state = _uiState.value
    val selection = resolveClosingSelection(
        canCreateBranchClosing = state.canCreateBranchClosing,   // ← UI state
        canCreateSellerClosing = state.canCreateSellerClosing,
        ...
```

Plan 036 made this safe by flipping the UI-state defaults to `false`, so the window where an
unresolved session could create a branch closing is closed. **This plan removes the cause
rather than the symptom**: the UI state is a projection *for rendering*, refreshed by
`refreshPermissions()`. Authorizing from it means any future change to when or how that
projection is refreshed silently becomes an authorization change.

Verified before writing this plan: these are the **only two** reads of a `can*` flag from the
state in the whole ViewModel (`grep -n "state\.can[A-Z]\|_uiState\.value\.can[A-Z]"` → lines
1086 and 1087). So one edit removes the entire second source.

## The change

```kotlin
-        canCreateBranchClosing = state.canCreateBranchClosing,
-        canCreateSellerClosing = state.canCreateSellerClosing,
+        canCreateBranchClosing = permissionService.canCreateBranchClosing(),
+        canCreateSellerClosing = permissionService.canCreateSellerClosing(),
```

`resolveClosingSelection` stays exactly as it is — a pure function in the companion object
taking booleans, already covered by 16 assertions in `ClosingComputeTest`. **Do not touch it**:
its purity is why the closing rules are testable at all, and the parameters are the right seam.

## Scope

**In scope**: two argument expressions in `createClosing`, plus a comment saying why the
service and not the state.

**Out of scope**: `resolveClosingSelection`, `refreshPermissions()`, `PermissionService`, the
UI-state flags (they stay — the UI needs them to render), and every other action.

## On testability — stated plainly

This wiring change is **not unit-testable in this codebase**. `MoneyCounterViewModel` takes an
`Application`, so no test can construct it and assert which source the booleans came from —
the same limitation recorded in §9.12 of `docs/ESTADO-Y-PASOS.md` for the `init` ordering bug.

What is already covered: `resolveClosingSelection`'s rules, thoroughly. What is not: the
wiring. The guards are the diff review and the comment in the code. **Do not invent a seam to
make this testable** — an injected permission provider to test two argument expressions is
coverage theatre, and it adds the coupling this plan exists to reduce.

## Commands

| Purpose | Command | Expected |
|---|---|---|
| Compile | `./gradlew :app:compileDebugKotlin` | exit 0 |
| Tests | `./gradlew :app:testDebugUnitTest` | exit 0, **509**, 0 failures — unchanged |
| APK | `./gradlew :app:assembleDebug` | exit 0 |

Baseline: **509 tests on `main` @ `acf81f1`**. This plan adds no test and must not change the
count; a different number means something else moved.

## Done criteria

1. The three commands exit 0, 509 tests, 0 failures.
2. `grep -n "state\.can[A-Z]\|_uiState\.value\.can[A-Z]"` on the ViewModel returns **nothing**.
3. `resolveClosingSelection` is byte-identical.
4. The permission behaviour for a resolved session is unchanged: `refreshPermissions()` copies
   the same service into the state, so service and state agree once the role is known. The
   change only matters before that, and there the service is the correct answer.

## STOP conditions

- The baseline is not 509 tests.
- `permissionService` is not in scope at the `createClosing` call site (it is a private field of
  the ViewModel; if that changed, report it).
- Any other read of a `can*` flag for a decision appears. Report it rather than fixing it here.

## Maintenance notes

- Authorization is consulted in **one** place: `permissionService`. The UI-state flags exist to
  render, and rendering is all they may be used for.
- The rule this came from: a projection refreshed by a lifecycle callback must never be the
  authority for a decision. See §9.11quater of `docs/ESTADO-Y-PASOS.md`.
