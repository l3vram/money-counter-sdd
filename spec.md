# Money Counter — Feature Specification

## 1. Overview

Build a small Android application for counting physical cash by denomination.

The primary use case is Cuba, where the user must be able to define and maintain their own currency denominations. The application must therefore NOT hard-code a fixed denomination list as the only source of truth.

The app compares the amount being counted against a target amount and continuously shows:

- target amount
- counted amount
- remaining amount when the count is below target
- excess amount when the count is above target
- completion state when the count exactly reaches target

The app is intended for fast, repetitive, practical use and must work fully offline.

## 2. Goals

### Primary goals

1. Make cash counting fast and low-friction.
2. Allow users to configure denominations.
3. Persist denomination configuration between app launches.
4. Allow the user to enter a target amount.
5. Allow quantities to be entered for every denomination.
6. Recalculate totals immediately after every change.
7. Make the remaining/excess amount obvious.
8. Provide a safe, obvious "clear all" action.
9. Keep the MVP simple and maintainable.
10. Cover the calculation/business rules with automated tests.

### Non-goals for MVP

- User accounts
- Cloud synchronization
- Backend
- Authentication
- Multi-user support
- Banking integration
- OCR
- Camera/scanning
- Automatic bill recognition
- Historical counting sessions
- Export/import of sessions
- Advertising
- Analytics
- Complex database
- Multi-currency accounting
- Exchange-rate conversion

## 3. User Stories

### US-001 — Set target amount

As a user, I want to enter the total amount I expect to count so that the application can tell me how much remains.

### US-002 — Count money by denomination

As a user, I want to enter how many units of each denomination I have so that the application calculates the subtotal and total automatically.

### US-003 — See the remaining amount

As a user, I want to immediately see how much money is missing from the target while I count.

### US-004 — Know when the target is reached

As a user, I want a clear visual indication when the counted amount exactly matches the target.

### US-005 — Detect excess

As a user, I want to know when I have counted more than the target and by how much.

### US-006 — Configure denominations

As a user, I want to add, edit, delete, and reorder denominations because the application is intended for Cuba and the denomination set must be configurable.

### US-007 — Persist denominations

As a user, I want my configured denominations to survive app restarts without needing a server or database.

### US-008 — Quickly clear a count

As a user, I want to clear the current target and denomination quantities so I can start a new counting operation quickly.

## 4. Functional Requirements

### FR-001 — Target amount

The application SHALL provide an input for a target amount.

The target amount SHALL be a non-negative whole monetary value for the MVP.

The UI SHALL provide a clear action to remove/reset the target amount.

### FR-002 — Denomination quantity

Each denomination SHALL have an associated quantity.

Quantity SHALL be a non-negative integer.

Quantity `0` is valid and is the default state for a new counting session.

Negative quantities SHALL NOT be accepted.

### FR-003 — Subtotal

For every denomination:

`subtotal = denominationValue × quantity`

The subtotal SHALL update immediately after the quantity changes.

### FR-004 — Counted total

The counted total SHALL be:

`countedTotal = Σ(denominationValue × quantity)`

The counted total SHALL update immediately after any denomination quantity changes.

### FR-005 — Difference

If:

`countedTotal < targetAmount`

then:

`remaining = targetAmount - countedTotal`

If:

`countedTotal = targetAmount`

then:

`remaining = 0` and status is `COMPLETED`.

If:

`countedTotal > targetAmount`

then:

`excess = countedTotal - targetAmount` and status is `OVER`.

### FR-006 — Status

The counter SHALL expose one derived status:

- `EMPTY`: no target and no money counted
- `COUNTING`: a target and/or counted amount exists but the target has not been reached
- `COMPLETED`: counted total equals target and target is greater than zero
- `OVER`: counted total is greater than target

The implementation may use a more precise internal state model if needed, but the UI behavior must remain equivalent.

### FR-007 — Add denomination

The denomination management screen SHALL allow the user to add a denomination.

A denomination requires:

- positive integer value
- optional display label only if implementation needs one; value alone is sufficient for MVP

Duplicate denomination values SHALL NOT be allowed.

### FR-008 — Edit denomination

The user SHALL be able to change the value of an existing denomination.

If editing would create a duplicate denomination value, the operation SHALL be rejected with an understandable validation message.

Changing a denomination SHALL NOT silently corrupt the current count.

Recommended behavior: denomination editing is a configuration operation and should be disabled/blocked while a non-empty active count exists, or the UI must explicitly warn that changing it affects the current count. Prefer the simpler and safer first option for MVP: require the current count to be cleared before editing/removing denominations.

### FR-009 — Delete denomination

The user SHALL be able to delete a denomination.

Deletion SHALL require confirmation.

The user SHALL not be able to accidentally delete a denomination through a normal tap.

For MVP, deleting/editing denominations SHALL require the active count to be empty.

### FR-010 — Reorder denominations

The denomination screen SHALL allow the user to reorder denominations.

The main counter screen SHALL display denominations in the configured order.

For MVP, ordering may be implemented with simple move-up/move-down controls instead of drag-and-drop if that produces a simpler and more reliable implementation.

### FR-011 — Persist denomination configuration

Denominations SHALL persist between application runs.

The MVP SHALL NOT require SQLite/Room for denomination configuration.

Preferred implementation: a small local JSON configuration stored in app-private persistent storage.

The application SHALL:

1. load the configuration at startup
2. use a built-in default configuration if no valid configuration exists
3. save configuration changes
4. continue working offline

The persisted JSON SHALL be treated as application data, not as a resource file that is edited directly inside the APK.

### FR-012 — Default denominations

The initial/default denomination list SHALL be easy to change by the developer.

The initial list SHOULD include common Cuban denominations but MUST NOT assume that this list can never change.

The exact default values must be defined in the implementation plan/configuration and should be kept separate from UI code.

### FR-013 — Clear all

The main screen SHALL provide a clear "Borrar todo" / "Clear all" action.

When the active count contains data, clearing SHALL require confirmation.

Clearing SHALL reset:

- target amount
- all denomination quantities

Clearing SHALL NOT delete the configured denomination list.

### FR-014 — Denomination configuration persistence vs. count state

For MVP:

- denomination configuration MUST persist between runs
- active counting session state MAY be reset when the application is closed/restarted

Do not add persistence for counting sessions unless explicitly requested later.

### FR-015 — Numeric input

Money and quantity fields SHALL use an appropriate numeric keyboard.

The application SHOULD minimize typing by making quantity controls easy to increment/decrement.

A denomination row SHOULD support both:
- direct quantity entry
- increment/decrement controls

### FR-016 — Immediate calculation

No explicit "calculate" button SHALL be required.

Every valid input change SHALL immediately update the relevant subtotal, counted total, remaining/excess value, and status.

## 5. UX Requirements

The application is optimized for a user who may be counting physical cash while holding bills/coins.

### Main screen concept

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
│ $5.000    [ − ]  10  [ + ] $50.000  │
│ $2.000    [ − ]   8  [ + ] $16.000  │
│ $1.000    [ − ]  20  [ + ] $20.000  │
│ $500      [ − ]   5  [ + ] $ 2.500  │
│ $200      [ − ]   5  [ + ] $ 1.000  │
│ ...                                  │
├──────────────────────────────────────┤
│                                      │
│          [ 🗑 BORRAR TODO ]           │
└──────────────────────────────────────┘
```

The exact visual implementation is left to the plan, but the hierarchy is important:

1. target
2. counted total
3. remaining/excess
4. denomination rows
5. clear action

### Completed state

```text
┌──────────────────────────────────────┐
│ OBJETIVO        $150.000              │
│ CONTADO         $150.000       ✓      │
│                                      │
│ ━━━━━━━━━━━━━━━━━━━━━━━━ 100%        │
│                                      │
│          ✓ MONTO COMPLETADO          │
└──────────────────────────────────────┘
```

### Over state

```text
┌──────────────────────────────────────┐
│ OBJETIVO        $150.000              │
│ CONTADO         $152.000              │
│                                      │
│ ━━━━━━━━━━━━━━━━━━━━━━━━ 101%        │
│                                      │
│ EXCEDENTE                            │
│ +$2.000                              │
└──────────────────────────────────────┘
```

### Denomination management screen

```text
┌──────────────────────────────────────┐
│ ← Denominaciones                 +    │
├──────────────────────────────────────┤
│                                      │
│  ☰  $5.000                    ⋮      │
│  ☰  $2.000                    ⋮      │
│  ☰  $1.000                    ⋮      │
│  ☰  $500                      ⋮      │
│  ☰  $200                      ⋮      │
│  ☰  $100                      ⋮      │
│                                      │
│       [ + AGREGAR DENOMINACIÓN ]     │
└──────────────────────────────────────┘
```

Add/edit dialog:

```text
┌──────────────────────────────────────┐
│ Nueva denominación                   │
│                                      │
│ Valor                                │
│ ┌──────────────────────────────────┐ │
│ │ 250                              │ │
│ └──────────────────────────────────┘ │
│                                      │
│        CANCELAR       GUARDAR        │
└──────────────────────────────────────┘
```

## 6. Acceptance Criteria

### AC-001 — Target

Given the user enters `150000` as target,
when the target is accepted,
then the main screen shows `150000` as the target.

### AC-002 — Subtotal

Given denomination `5000`,
when quantity is `10`,
then subtotal is `50000`.

### AC-003 — Total

Given:
- `5000 × 10`
- `2000 × 5`

then counted total is `60000`.

### AC-004 — Remaining

Given target `100000` and counted total `75000`,
then the UI shows `25000` remaining.

### AC-005 — Completed

Given target `100000` and counted total `100000`,
then the UI shows completed state and zero remaining.

### AC-006 — Excess

Given target `100000` and counted total `105000`,
then the UI shows `5000` excess.

### AC-007 — Zero quantity

Given a denomination with quantity `0`,
then its subtotal is `0`.

### AC-008 — Negative quantity

Given the user attempts to enter `-1`,
then the application rejects the value and does not produce a negative subtotal.

### AC-009 — Add denomination

Given the user enters denomination `250`,
when saving,
then `250` appears in the configured denomination list and remains after app restart.

### AC-010 — Duplicate denomination

Given denomination `500` already exists,
when the user attempts to add another `500`,
then the application rejects it.

### AC-011 — Delete denomination

Given an existing denomination,
when the user chooses delete and confirms,
then the denomination is removed from the configuration.

### AC-012 — Clear count

Given target and denomination quantities contain values,
when the user confirms "Borrar todo",
then target and all quantities become zero/empty while denominations remain configured.

### AC-013 — Configuration persistence

Given a user adds denomination `250`,
when the application is closed and opened again,
then denomination `250` remains available.

### AC-014 — Offline

Given the device has no network connection,
when the user opens and uses the application,
then all MVP functionality continues to work.

## 7. Edge Cases

- Target is zero.
- Target is empty.
- All quantities are zero.
- A denomination value is extremely large.
- Quantity is extremely large.
- User enters leading zeros.
- User enters blank quantity.
- User enters invalid characters.
- User tries to save duplicate denominations.
- User tries to edit/delete denominations while a count is active.
- Persisted JSON is missing.
- Persisted JSON is malformed.
- Persisted JSON contains invalid/negative/duplicate values.
- Device configuration is restored from an older version.

Invalid persisted configuration SHALL fall back safely to a valid default configuration rather than crashing the application.

## 8. Localization

The MVP UI SHOULD be in Spanish.

Currency formatting SHALL be locale-aware where practical, but calculations MUST remain based on integer monetary units.

Do not encode currency symbols into the calculation model.

## 9. Quality Attributes

### Usability

- Primary counting flow should require minimal navigation.
- Main totals must be visually prominent.
- Quantity entry should be fast.
- Buttons should have touch targets appropriate for mobile use.
- Scrolling must not make the current total difficult to find.

### Reliability

- Calculation logic must be deterministic and unit-tested.
- Invalid user input must not crash the app.
- Invalid persisted configuration must not crash the app.

### Maintainability

- Calculation/domain logic must be separated from Compose UI.
- Denomination persistence must be isolated from calculation logic.
- Avoid unnecessary abstractions and frameworks.

### Offline-first

No network permission or backend is required for MVP.

## 10. Definition of Done

The feature is complete when:

- target entry works
- denomination quantities work
- subtotals work
- total works
- remaining works
- completed state works
- excess state works
- add/edit/delete/reorder denomination works
- denomination configuration survives restart
- clear-all works safely
- malformed persisted configuration is handled
- core domain tests pass
- main UI scenarios are manually validated
- app builds successfully in release configuration
