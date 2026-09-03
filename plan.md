# Money Counter — Implementation Plan

## 1. Technical Direction

### Platform

- Android
- Kotlin
- Jetpack Compose
- Material 3

### Architecture

Use a deliberately small architecture:

```text
Compose UI
    │
    ▼
ViewModel
    │
    ▼
Domain calculation/state
    │
    ├── Denomination configuration repository
    │
    └── Local JSON persistence
```

Do not introduce a backend, Room, Retrofit, dependency injection framework, or multi-module architecture for the MVP unless the existing project already requires one.

## 2. Core Design Principle

The specification is the source of truth.

Implementation should follow the requirements in `spec.md` rather than adding speculative functionality.

If an implementation decision conflicts with the specification, stop and resolve the conflict instead of silently changing behavior.

## 3. Domain Model

### Denomination

Conceptual model:

```text
Denomination
- id: stable identifier
- value: Long
- position: Int
```

A stable identifier is useful because the value itself may be edited.

The persisted denomination configuration should resemble:

```json
{
  "version": 1,
  "denominations": [
    { "id": "d1", "value": 5000 },
    { "id": "d2", "value": 2000 },
    { "id": "d3", "value": 1000 }
  ]
}
```

The exact serialization strategy can use Kotlin serialization or another lightweight JSON serializer already supported by the project.

### Counter state

Conceptual state:

```text
CounterState
- targetAmount: Long?
- quantities: Map<DenominationId, Long>
- denominations: List<Denomination>
- countedTotal: Long
- remaining: Long
- excess: Long
- status: CounterStatus
```

The calculated fields should preferably be derived rather than independently mutable.

### CounterStatus

```text
EMPTY
COUNTING
COMPLETED
OVER
```

## 4. Money Representation

Do NOT use `Double` or `Float` for monetary calculations.

Use `Long` representing the smallest relevant whole monetary unit for the MVP.

Example:

```text
5000 × 12 = 60000
```

This avoids floating-point precision errors.

If decimal denominations are required in a future version, revisit the money representation explicitly rather than silently changing the MVP model.

## 5. Calculation Rules

Create a small pure domain component, for example:

```text
MoneyCounterCalculator
```

It should accept:

```text
target
denominations
quantities
```

and return a derived result.

Pseudo-behavior:

```text
total = 0

for each denomination:
    quantity = quantities[id] ?: 0
    total += denomination.value * quantity

if target is null or target == 0:
    status depends on total:
        total == 0 -> EMPTY
        total > 0  -> COUNTING

else if total < target:
    remaining = target - total
    status = COUNTING

else if total == target:
    status = COMPLETED

else:
    excess = total - target
    status = OVER
```

The implementation must also consider overflow protection.

If multiplication or addition would overflow `Long`, the operation must be rejected or handled safely rather than wrapping around.

For a normal cash-counting application this should be an extremely rare case, but the domain layer should not contain silent arithmetic overflow.

## 6. Persistence

### Decision

Use a local JSON file stored in application-private storage.

Rationale:

- denomination configuration is tiny
- no relational queries are required
- no database is justified
- easy to inspect/debug
- survives app restarts
- works offline
- keeps MVP simple

Suggested structure:

```text
app private storage
└── denominations.json
```

Do not modify a JSON resource inside `res/raw` or `assets` at runtime. APK resources are packaged and are not the correct persistence mechanism.

### Repository

Use a small interface:

```text
DenominationRepository
- load()
- save(denominations)
```

A concrete implementation:

```text
JsonDenominationRepository
```

The repository should be the only component responsible for file I/O.

### Startup behavior

```text
Application start
      │
      ▼
Load denominations.json
      │
      ├── valid → use it
      │
      ├── missing → use defaults and persist them
      │
      └── invalid → use defaults and safely recover
```

The UI should not know how the JSON file works.

### Atomicity

Prefer writing the configuration safely so a partial write does not leave the app with corrupt configuration.

A simple temp-file-then-replace strategy is sufficient if practical.

## 7. Configuration Rules

### Add

- value must be > 0
- value must be unique
- save immediately after successful addition

### Edit

- value must be > 0
- value must remain unique
- save immediately after successful edit
- block editing while current count is non-empty for MVP

### Delete

- require confirmation
- block deletion while current count is non-empty for MVP
- save after successful deletion

### Reorder

- preserve all denomination IDs and values
- save after successful reorder

## 8. Default Configuration

Keep default denominations in one dedicated configuration source.

Do not scatter default values throughout Compose code.

Example:

```text
DefaultDenominations
    ├── 5000
    ├── 2000
    ├── 1000
    ├── 500
    ├── 200
    ├── 100
    ├── 50
    └── 20
```

These are starting defaults only. The application must allow the user to replace/customize them.

Because the target market is Cuba, confirm the desired initial denomination set before release if exact current Cuban denominations are important. The architecture must not depend on a fixed list.

## 9. ViewModel

Suggested responsibility:

```text
MoneyCounterViewModel
```

Responsibilities:

- expose immutable UI state
- update target
- update denomination quantity
- add denomination
- edit denomination
- delete denomination
- reorder denomination
- clear current count
- invoke repository for denomination persistence
- calculate derived counter result

The ViewModel should not contain Compose-specific rendering code.

## 10. UI Components

Suggested Compose structure:

```text
MoneyCounterApp
│
├── MoneyCounterScreen
│   ├── TopAppBar
│   ├── TargetAmountCard
│   ├── CounterSummaryCard
│   ├── DenominationList
│   │   └── DenominationRow
│   └── ClearAllButton
│
└── DenominationManagementScreen
    ├── TopAppBar
    ├── DenominationList
    ├── AddDenominationDialog
    └── DeleteConfirmationDialog
```

Avoid creating dozens of tiny components without a reason.

## 11. Main Screen UX

Recommended hierarchy:

```text
┌──────────────────────────────────────┐
│ Contador de dinero              ⚙    │
├──────────────────────────────────────┤
│ OBJETIVO                             │
│ $ 150.000                            │
│                                      │
│ CONTADO                              │
│ $ 126.500                            │
│                                      │
│ ━━━━━━━━━━━━━━━━━━━━━━━━ 84%         │
│                                      │
│ FALTAN                               │
│ $ 23.500                             │
├──────────────────────────────────────┤
│ DENOMINACIONES                       │
│                                      │
│ $5.000   [ − ] 10 [ + ]    $50.000  │
│ $2.000   [ − ]  8 [ + ]    $16.000  │
│ ...                                  │
├──────────────────────────────────────┤
│          [ BORRAR TODO ]             │
└──────────────────────────────────────┘
```

The summary should remain easy to reach. If the denomination list becomes long, consider a compact/sticky summary rather than forcing the user to scroll back to the top.

## 12. Quantity Interaction

Support both:

```text
[ − ] 10 [ + ]
```

and direct editing.

Rules:

- decrement below zero is not allowed
- increment/decrement updates immediately
- direct entry accepts non-negative integers
- blank field may temporarily represent zero during editing
- invalid final values are normalized/rejected safely

Do not require a save button for every denomination quantity.

## 13. Target Input

The target amount should be a numeric field.

Recommended UX:

```text
MONTO OBJETIVO

$ [ 150000 ]     🗑
```

Formatting can be applied for display while keeping the internal value numeric.

Do not make the user type currency symbols.

## 14. Clear Behavior

If count is empty:

```text
clear
  ↓
reset state
```

If count has data:

```text
Borrar todo
    ↓
confirmation
    ↓
reset target + quantities
    ↓
keep denomination configuration
```

Never delete denominations through the clear-count action.

## 15. Navigation

MVP can use two destinations:

```text
Counter
   │
   └── Settings / Denominations
             │
             └── Add/Edit/Delete/Reorder
```

No complex navigation architecture is necessary.

## 16. Error Handling

User-facing errors should be concise.

Examples:

```text
"El valor debe ser mayor que cero."

"Esta denominación ya existe."

"No puedes modificar las denominaciones mientras haya un conteo activo."

"No se pudo guardar la configuración. Intenta nuevamente."
```

For corrupted local JSON:

- do not show a fatal error
- fall back to defaults
- attempt to save a valid configuration
- optionally log diagnostic information in debug builds

## 17. Testing Strategy

### Unit tests

Prioritize the pure calculation/domain layer.

Required scenarios:

```text
empty state
single denomination
multiple denominations
zero quantities
target below total
target equal total
target above total
large values
invalid/negative quantity
overflow protection
```

### Persistence tests

Test:

```text
save → load
missing file → defaults
invalid JSON → defaults
duplicate values → invalid configuration handling
version handling
```

### UI tests

Cover the most important user flows:

```text
enter target
change quantity
see total
see remaining
reach target
exceed target
open denominations
add denomination
delete denomination
clear count
```

## 18. Dependencies

Keep dependencies minimal.

Use:

- Android SDK
- Kotlin
- Jetpack Compose
- Material 3
- AndroidX lifecycle/ViewModel as appropriate
- lightweight JSON serialization if needed

Do not add libraries merely for convenience when platform APIs are sufficient.

## 19. Security / Privacy

No account or network access is required.

Denomination configuration is local application data.

Do not collect personal or financial information.

## 20. Complexity Gate

Before implementation, verify:

```text
[ ] No backend
[ ] No database
[ ] No authentication
[ ] No unnecessary abstraction layers
[ ] No multi-module architecture unless already required
[ ] No speculative features
[ ] Calculation logic is pure/testable
[ ] Persistence is isolated
[ ] UI is state-driven
```

Any deviation must be explicitly justified.

## 21. Implementation Sequence

1. Establish Android project/build configuration.
2. Implement domain models.
3. Implement pure calculation logic.
4. Add unit tests for calculation logic.
5. Implement denomination repository and JSON persistence.
6. Add persistence tests.
7. Implement ViewModel/state.
8. Implement main screen.
9. Implement denomination management screen.
10. Add UI tests for critical flows.
11. Polish accessibility, keyboard behavior, spacing, and touch targets.
12. Run full test suite and release build.
