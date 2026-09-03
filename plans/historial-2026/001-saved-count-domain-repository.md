# Plan 001: Add SavedCount domain model and JSON persistence repository

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything
> in the "STOP conditions" section occurs, stop and report — do not improvise.
>
> **Drift check (run first)**: `git diff --stat 210ca98..HEAD -- app/src/main/java/com/moneycounter/domain app/src/main/java/com/moneycounter/repository`
> If any in-scope file changed since this plan was written, compare the "Current state"
> excerpts against the live code before proceeding; on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none
- **Category**: feature
- **Planned at**: commit `210ca98`, 2026-09-03

## Why this matters

The app counts money toward a target. Today the result disappears when the user clears
or edits quantities. This is the foundation of the "historial" feature: a durable record
of a completed count (the target, the denominations that had quantities, and each
subtotal) persisted as JSON — mirroring the existing `Denomination` persistence pattern.
Later plans build the UI on top of this model and repository.

## Current state

The repo already persists denominations to JSON via this pattern; replicate it exactly.

- `app/src/main/java/com/moneycounter/domain/Denomination.kt` — `data class Denomination(id: String, value: Long)` with an `init { require(...) }` block.
- `app/src/main/java/com/moneycounter/repository/DenominationRepository.kt` — interface `load(): List<Denomination>` / `save(List<Denomination>)`.
- `app/src/main/java/com/moneycounter/repository/JsonDenominationRepository.kt` — implementation using `org.json`, writes to `File(context.filesDir, "denominations.json")` with atomic temp-file rename and silent catch.
- `app/src/main/java/com/moneycounter/domain/Money.kt` — `object Money { const val SCALE = 2; val ZERO = ...; fun of(String): BigDecimal; fun fromLong(Long): BigDecimal }`.
- `app/src/main/java/com/moneycounter/domain/CounterResult.kt` — `data class CounterResult(countedTotal, remaining, excess, status: CounterStatus)`.
- `app/build.gradle.kts` — `org.json` is available via Android framework (no extra dependency needed). Tests use JUnit 4 under `app/src/test/`.

Recon facts (verified):
- Build: `./gradlew assembleDebug --console=plain`
- Compile: `./gradlew compileDebugKotlin --console=plain`
- Tests: `./gradlew test --console=plain`
- Project root: `/Volumes/ExtSSD/mac-storage/projects/money-counter-sdd`
- All source under `app/src/main/java/com/moneycounter/`; tests under `app/src/test/java/com/moneycounter/`.

## Commands you will need

| Purpose   | Command                           | Expected on success |
|-----------|-----------------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`  | BUILD SUCCESSFUL; tests pass |
| Build     | `./gradlew assembleDebug --console=plain`      | BUILD SUCCESSFUL |

## Scope

**In scope** (only these files — create the new ones):
- Create `app/src/main/java/com/moneycounter/domain/SavedCount.cs` → actually `.kt` (new)
- Create `app/src/main/java/com/moneycounter/domain/SavedCountItem.cs` → actually `.kt` (new)
- Create `app/src/main/java/com/moneycounter/repository/SavedCountRepository.cs` → actually `.kt` (new)
- Create `app/src/main/java/com/moneycounter/repository/JsonSavedCountRepository.cs` → actually `.kt` (new)
- Create `app/src/test/java/com/moneycounter/domain/SavedCountSerializationTest.cs` → actually `.kt` (new)

**Out of scope** (do NOT touch):
- Any existing file under `app/src/main/java/` — do not modify them. Only add new files.
- The ViewModel, screens, or UI. That is Plan 002/003.
- No changes to `build.gradle.kts` (org.json is framework-provided).

## Git workflow

- This repo is NOT using git worktrees for this run; the previous plan edited the tree
  in place under `app/src/main/java/`. Make your changes in place in the current working
  tree. Do NOT commit unless the operator explicitly asks.

## Steps

### Step 1: Create `SavedCountItem.kt`

File: `app/src/main/java/com/moneycounter/domain/SavedCountItem.kt`

A data class representing one denomination line in a saved count. Match the `Denomination`
style (data class + `init` validation):

```kotlin
package com.moneycounter.domain

data class SavedCountItem(
    val denominationValue: Long,
    val quantity: Long,
    val subtotal: BigDecimal
) {
    init {
        require(denominationValue > 0) { "Denomination value must be positive" }
        require(quantity >= 0) { "Quantity must be non-negative" }
    }
}
```

Imports: `java.math.BigDecimal`.

### Step 2: Create `SavedCount.kt`

File: `app/src/main/java/com/moneycounter/domain/SavedCount.kt`

A top-level saved record. Fields:
- `id: String` — unique id (UUID string).
- `savedAt: Long` — epoch millis of when it was saved (used as the date).
- `targetAmount: BigDecimal` — the target the count reached.
- `items: List<SavedCountItem>` — only denominations that had quantity > 0.

Provide a helper `total` computed from items if useful, but the UI reads `targetAmount`
as the total shown per the spec. Keep it a plain data class with validation:

```kotlin
package com.moneycounter.domain

import java.math.BigDecimal

data class SavedCount(
    val id: String,
    val savedAt: Long,
    val targetAmount: BigDecimal,
    val items: List<SavedCountItem>
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(targetAmount.signum() > 0) { "targetAmount must be positive" }
    }
}
```

### Step 3: Create `SavedCountRepository.kt` (interface)

File: `app/src/main/java/com/moneycounter/repository/SavedCountRepository.kt`

Mirror the `DenominationRepository` style:

```kotlin
package com.moneycounter.repository

import com.moneycounter.domain.SavedCount

interface SavedCountRepository {
    fun load(): List<SavedCount>
    fun saveAll(history: List<SavedCount>)
}
```

### Step 4: Create `JsonSavedCountRepository.kt` (implementation)

File: `app/src/main/java/com/moneycounter/repository/JsonSavedCountRepository.kt`

Mirror `JsonDenominationRepository` (atomic temp-file write + rename, silent catch, version
field). File name: `"count_history.json` → `count_history.json`.

JSON format (version 1):

```json
{
  "version": 1,
  "history": [
    {
      "id": "<uuid>",
      "savedAt": 1750000000000,
      "targetAmount": "5000.00",
      "items": [
        { "denominationValue": 500, "quantity": 1, "subtotal": "500.00" }
      ]
    }
  ]
}
```

Key behaviors:
- `load()`: if file missing -> empty list `emptyList()` (NO defaults — history starts empty).
  If blank -> empty list. Parse with full error tolerance: skip malformed entries, skip items
  with `denominationValue <= 0`, keep only `quantity > 0` items on load (a saved item with
  zero quantity is meaningless). If a `SavedCount` ends up with no items, still keep it (the
  target alone is meaningful). Return list sorted by `savedAt` descending (newest first).
- `saveAll(history)`: pretty-print with indent 2, write to temp file then rename.
- All `BigDecimal` fields round-trip as strings via `toPlainString()` on save and
  `BigDecimal(string)` on load, scaled to `Money.SCALE`.

Use `java.util.UUID.randomUUID().toString()` for generating ids (in the ViewModel, not the
repo — but if you add a helper factory here for tests, keep it in the repo file's companion
or object).

### Step 5: Write a unit test

File: `app/src/test/java/com/moneycounter/domain/SavedCountRepositoryTest.kt`

Model the test after `app/src/test/java/com/moneycounter/domain/QuantityParserTest.kt`
(JUnit 4, plain asserts, no Android Context — this is JVM-only).

Problem: `JsonSavedCountRepository` takes a `Context`. To test pure serialization without
Android, extract the **serialization/deserialization** into an internal object
`SavedCountJson` (in the same file or a small companion object) that has:
- `fun toJson(history: List<SavedCount>): String`
- `fun fromJson(json: String): List<SavedCount>`

The repository delegates to it. Test `SavedCountJson` directly:
- Round-trip: build a `SavedCount`, `toJson`, `fromJson`, assert equality of fields.
- Skips zero-quantity items on `fromJson`.
- Empty input returns empty list.
- Malformed entries are skipped.

**Verify**: `./gradlew test --console=plain` → BUILD SUCCESSFUL, new test passes.

## Test plan

- `SavedCountRepositoryTest.kt` — round-trip, zero-quantity filter, empty handling, malformed tolerance.
- Pattern: `QuantityParserTest.kt`.

## Done criteria

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0; new `SavedCountRepositoryTest` passes
- [ ] `find app/src -name "SavedCount*Repository*" -o -name "SavedCount*"` shows the new files
- [ ] No files outside the in-scope list are modified (`git status` shows only additions)
- [ ] `plans/README.md` status row (see Plan notes) updated

## STOP conditions

Stop and report back (do not improvise) if:

- The code at the locations in "Current state" doesn't match the excerpts.
- A step's verification fails twice after a reasonable fix attempt.
- You discover the assumption "org.json is available without a dependency" is false.

## Maintenance notes

- The JSON format is versioned (`version: 1`). Future migrations bump the version and handle
  old versions in `fromJson`.
- `targetAmount` doubles as the "Monto total" shown in the UI per the spec. If future UX
  wants the computed sum instead, the repository round-trips both; the UI decides.
