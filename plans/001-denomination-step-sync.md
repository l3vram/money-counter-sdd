# Plan 001: Fix +/− so it writes the quantity into the field and stays in sync

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **This repo is NOT a git repository** — there are no branches, no worktrees.
> Edit the source files in place under `app/src/main/java/...`. Do not attempt
> `git` commands; they will fail. The "git workflow" section of the template
> does not apply here.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: bug
- **Planned at**: 2026-09-03 (no git SHA available — not a git repo)

## Why this matters

When the user taps the `+` or `−` button on a denomination row, the quantity
in the ViewModel changes and recomposes the screen, but if the quantity text
field is currently in "editing" state (`isEditing == true`), the field keeps
showing a stale `editText` value instead of the updated, real quantity. The
display desyncs from the actual count: the visible number no longer matches
the subtotal/total. This is the exact problem the operator reported. Also, the
row state is keyed by composition slot rather than by denomination id, so row
state can bleed between rows when the list reorders or filters. This plan
makes the field always reflect the real quantity and stabilizes per-row state.

## Current state

Relevant file: `app/src/main/java/com/moneycounter/ui/components/DenominationRow.kt` (entire file is 148 lines).

The row keeps two local states:

```kotlin
// DenominationRow.kt:45-46
var isEditing by remember { mutableStateOf(false) }
var editText by remember { mutableStateOf("") }
```

The text field value is derived conditionally:

```kotlin
// DenominationRow.kt:82
value = if (isEditing) editText else quantity.toString()
```

On focus-loss / Done it commits `editText` to the ViewModel and clears
editing:

```kotlin
// DenominationRow.kt:91-97
.onFocusChanged { focusState ->
    if (!focusState.isFocused && isEditing) {
        val parsed = editText.toLongOrNull() ?: 0L
        onQuantityChanged(parsed.coerceAtLeast(0))
        isEditing = false
    }
}
```

The increment/decrement handlers do **not** reset `isEditing`:

```kotlin
// DenominationRow.kt:73-79 and 115-121
IconButton(onClick = onDecrement) { ... }
IconButton(onClick = onIncrement) { ... }
```

Because `onIncrement`/`onDecrement` never touch `isEditing`/`editText`, if the
user has the field in editing state and taps `+`/`−`, the recomposed field
still branches to `editText` (stale). The commit-back / clear does not happen,
so the display is stale until the field loses focus.

In `MoneyCounterScreen.kt`, the list renders rows without a stable key:

```kotlin
// MoneyCounterScreen.kt:124
items(uiState.denominations) { denomination -> ... }
```

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile check | `./gradlew compileDebugKotlin --console=plain` | exit 0, BUILD SUCCESSFUL |
| Unit tests | `./gradlew test --console=plain` | BUILD SUCCESSFUL, tests pass |
| Release build | `./gradlew assembleRelease --console=plain` | exit 0 (minify disabled, no signing; fine for compile proof) |

Run these from the project root (`/Volumes/ExtSSD/mac-storage/projects/money-counter-sdd`).

## Scope

**In scope** (the only files you should modify):
- `app/src/main/java/com/moneycounter/ui/components/DenominationRow.kt`
- `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt`

**Out of scope** (do NOT touch, even though they look related):
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` — the `incrementQuantity` / `decrementQuantity` logic is already correct (floor at 0). Do not change it.
- `DenominationManagementScreen.kt`
- Any domain/calculator/repository code.
- The Quantity field's direct-edit behavior beyond what is required here — keep blank→0 normalization on focus-loss that already exists.

## Steps

### Step 1: Reset editing state on increment/decrement in `DenominationRow.kt`

In `app/src/main/java/com/moneycounter/ui/components/DenominationRow.kt`, make
the increment and decrement buttons clear the editing state before/alongside
their action, so the field falls back to `quantity.toString()` and stays in
sync.

Wrap the two `IconButton(onClick = ...)` calls so that before invoking
`onIncrement` / `onDecrement`, they set `isEditing = false` and `editText = ""`.
Concretely:

```kotlin
IconButton(onClick = {
    isEditing = false
    editText = ""
    onIncrement()
}) { ... }

IconButton(onClick = {
    isEditing = false
    editText = ""
    onDecrement()
}) { ... }
```

Place these modifiers **before** the existing `onIncrement()`/`onDecrement()`
calls inside their respective `onClick` lambdas (`DenominationRow.kt:73` and
`:115`). Keep everything else in the button block (icon, tint) unchanged.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → BUILD SUCCESSFUL.

### Step 2: Add a stable key to the denomination list in `MoneyCounterScreen.kt`

In `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt:124`,
add a stable per-row key so the row's `remember` state is tied to the
denomination id instead of the composition slot:

```kotlin
items(uiState.denominations, key = { it.id }) { denomination -> ... }
```

This prevents editing state from bleeding across rows if the list reorders or
changes. No import changes are required (`items` is already imported from
`androidx.compose.foundation.lazy.items`).

**Verify**: `./gradlew compileDebugKotlin --console=plain` → BUILD SUCCESSFUL.

### Step 3: Full verification

- `./gradlew test --console=plain` → BUILD SUCCESSFUL, all existing tests pass.
- `./gradlew assembleRelease --console=plain` → BUILD SUCCESSFUL.

The operator's reported behavior is a UI-behavior fix (no new business rule),
so the existing domain test suite (`MoneyCounterCalculatorTest`) remains
correct and green. Do not add tests for this change; there is no UI test
harness wired for Compose in this repo, and the calculator math is untouched.

**Verify**: both commands above exit 0.

## Test plan

No new automated tests. The change is confined to Compose UI state-handling
that has no testable pure function. Verification is the compile + unit-test +
release-build gates in Step 3. (Existing `MoneyCounterCalculatorTest` covers
the quantity math at the domain layer and is unaffected.)

## Done criteria

ALL must hold:

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` reports BUILD SUCCESSFUL
- [ ] `./gradlew assembleRelease --console=plain` exits 0
- [ ] `isEditing = false; editText = ""` reset present in both increment and decrement click handlers in `DenominationRow.kt`
- [ ] `items(uiState.denominations, key = { it.id })` in `MoneyCounterScreen.kt`
- [ ] No files outside the in-scope list are modified
- [ ] `plans/README.md` status row marked DONE

## STOP conditions

Stop and report back (do not improvise) if:

- The code at the locations in "Current state" does not match the excerpts
  (the codebase has drifted since this plan was written).
- A verification command fails twice after a reasonable fix attempt.
- The fix appears to require touching an out-of-scope file.
- You discover the assumption "the increment/decrement state bug is in
  `DenominationRow.kt`" is false.

## Maintenance notes

- The `+`/`−` handlers now force the field to display the real quantity on
  every tap. If a future feature adds quantity stepping that should preserve
  an in-progress typed value, revisit this.
- If a UI test harness (Compose `ui-test-junit4`) is added later, a regression
  test for "tap + after typing keeps field in sync" belongs in a Compose UI
  test, not the domain suite.
