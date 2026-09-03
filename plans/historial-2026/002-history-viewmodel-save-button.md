# Plan 002: Wire history into ViewModel and add Save button on completed count

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything
> in the "STOP conditions" section occurs, stop and report — do not improvise. This plan
> depends on Plan 001; run 001 first or confirm its files exist.
>
> **Drift check (run first)**: `git status --short` — confirm the files from Plan 001
> (`SavedCount.kt`, `SavedCountItem.kt`, `SavedCountRepository.kt`,
> `JsonSavedCountRepository.kt`) exist, and review the current `MoneyCounterViewModel.kt`
> and `MoneyCounterScreen.kt` against the excerpts below before editing.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: plans/historial-2026/001-saved-count-domain-repository.md
- **Category**: feature
- **Planned at**: commit `210ca98`, 2026-09-03

## Why this matters

Plan 001 created the persistence layer. Now the app must (a) let the user save a completed
count, and (b) expose the saved history to the UI. This adds history state + a
`saveCount` operation to the shared ViewModel, and a "Guardar" button that appears in the
summary when a count reaches its target (`CounterStatus.COMPLETED`).

## Current state

Re-readable files (exact current versions — read them before editing, do not trust memory):

- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` — currently holds
  `MoneyCounterUiState(targetAmount, denominations, quantities, result, hasActiveCount)`,
  exposes `uiState: StateFlow<MoneyCounterUiState>`, has `recalculate()`, uses
  `JsonDenominationRepository`. Instantiated once at the `MoneyCounterApp` level in
  `MainActivity.kt`.
- `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` — the counter screen.
  Its `SummarySection` composable (around line 254+) renders `CounterStatus.COMPLETED` as a
  "✓ MONTO COMPLETADO" text block. The screen's `TopAppBar` has a `Settings` action.
- `app/src/main/java/com/moneycounter/domain/CounterStatus.kt` — enum `EMPTY, COUNTING, COMPLETED, OVER`.

The ViewModel's `uiState.result.status` is `CounterStatus.COMPLETED` exactly when
`countedTotal == targetAmount` (see `MoneyCounterCalculator`). That is the gating condition
for the Save button.

## Commands you will need

| Purpose   | Command                           | Expected on success |
|-----------|-----------------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | BUILD SUCCESSFUL |
| Build     | `./gradlew assembleDebug --console=plain`      | BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`  | BUILD SUCCESSFUL; all pass |

## Scope

**In scope** (only these files):
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt` (edit)
- `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` (edit)
- `app/src/main/java/com/moneycounter/repository/JsonSavedCountRepository.kt` (exists from 001 — it already has a repository instance factory you'll reuse)

**Out of scope** (do NOT touch):
- Any new screen for history/detail — that is Plan 003.
- `MainActivity.kt` navigation — that is Plan 003.
- Existing domain calculators and the denominations repository.

## Git workflow

- Edit in place in the current working tree (this run does not use worktrees).
- Do NOT commit unless the operator explicitly asks.

## Steps

### Step 1: Add history state and saveCount to the ViewModel

In `MoneyCounterViewModel.kt`:

1. Add `val historyRepository: SavedCountRepository = JsonSavedCountRepository(application)`.
2. Extend `MoneyCounterUiState` with `val history: List<SavedCount> = emptyList()` and
   `val lastSavedId: String? = null`. `lastSavedId` lets the UI know a count was just saved.
3. Add a `saveCount()` function:

```kotlin
fun saveCount(): String? {
    val state = _uiState.value
    if (state.result.status != CounterStatus.COMPLETED) return null
    if (state.targetAmount == null) return null

    val items = state.denominations
        .mapNotNull { den ->
            val qty = state.quantities[den.id] ?: 0L
            if (qty <= 0) null
            else SavedCountItem(den.value, qty, Money.fromLong(den.value * qty))
        }

    val saved = SavedCount(
        id = UUID.randomUUID().toString(),
        savedAt = System.currentTimeMillis(),
        targetAmount = state.targetAmount,
        items = items
    )

    _uiState.update { st ->
        st.copy(
            history = listOf(saved) + st.history,
            lastSavedId = saved.id
        )
    }
    persistHistory()
    return saved.id
}

fun deleteSavedCount(id: String) {
    _uiState.update { st ->
        st.copy(history = st.history.filterNot { it.id == id })
    }
    persistHistory()
}

private fun persistHistory() {
    val history = _uiState.value.history
    viewModelScope.launch { historyRepository.saveAll(history) }
}
```

4. In `init { loadDenominations() }`, also load history into state:

```kotlin
private fun loadHistory() {
    viewModelScope.launch {
        val history = historyRepository.load()
        _uiState.update { it.copy(history = history) }
    }
}
```
Call `loadHistory()` from `init` (after `loadDenominations()`).

Imports to add: `com.moneycounter.domain.SaveCountItem`, `com.moneycounter.domain.SavedCount`,
`com.moneycounter.repository.SavedCountRepository`, `com.moneycounter.repository.JsonSavedCountRepository`,
`java.util.UUID`.

Note: `MoneyCounterUiState` uses `SavedCount` — ensure the data class import resolves.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → BUILD SUCCESSFUL.

### Step 2: Add "Guardar" button on completed count in the summary

In `MoneyCounterScreen.kt`, inside the `SummarySection` composable's `when (result.status)`
block for `CounterStatus.COMPLETED`, add a `FilledTonalButton` labeled "GUARDAR" that calls a
new `onSave: () -> Unit` callback parameter threaded into `SummarySection`.

Specifically:
1. Add `onSave: () -> Unit` to `SummarySection`'s parameter list.
2. Pass `onSave = { viewModel.saveCount() }` where `SummarySection` is called in
   `MoneyCounterScreen` (around line 107-118).
3. Inside the `CounterStatus.COMPLETED` branch, after the "✓ MONTO COMPLETADO" text, add:

```kotlin
Spacer(modifier = Modifier.height(8.dp))
FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
    Text("GUARDAR EN HISTORIAL")
}
```

Import `Icons.Default.Check` (add to the icons imports; `material-icons-extended` is already
a dependency).

**Verify**: `./gradlew assembleDebug --console=plain` → BUILD SUCCESSFUL.

## Test plan

- No new unit test required here (serialization is covered in Plan 001; this is wiring).
- Verify by build + the existing test suite still green.

## Done criteria

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew assembleDebug --console=plain` exits 0
- [ ] `MoneyCounterViewModel` has `history`, `saveCount()`, `deleteSavedCount()`, `loadHistory()` wired; compiles
- [ ] `MoneyCounterScreen` shows a "GUARDAR EN HISTORIAL" button only under `CounterStatus.COMPLETED`
- [ ] No files outside the in-scope list are modified
- [ ] `plans/README.md` status row updated

## STOP conditions

- The code at the locations in "Current state" doesn't match the excerpts.
- A step's verification fails twice after a reasonable fix attempt.
- You discover `<Context>` isn't accessible where you need it in the ViewModel (it is an
  `AndroidViewModel`, so `application` is available — `JsonSavedCountRepository(application)` works).
