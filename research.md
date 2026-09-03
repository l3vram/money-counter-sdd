# Money Counter — Research / Technical Decisions

## 1. SDD Reference

This feature follows the Specification-Driven Development approach used by GitHub Spec Kit:

```text
Specification
     ↓
Implementation Plan
     ↓
Tasks
     ↓
Implementation
     ↓
Tests / Validation
```

The important principle is that the specification is the source of truth and implementation should serve it.

Reference repository:

https://github.com/github/spec-kit

Reference document:

https://github.com/github/spec-kit/blob/main/spec-driven.md

## 2. Why Not a Database?

The only persistent user configuration in the MVP is a small ordered list of denominations.

Required operations are:

- load all
- replace all
- add
- edit
- delete
- reorder

There is no need for:

- queries
- relations
- indexes
- migrations beyond simple JSON schema versioning
- concurrent database transactions

A JSON file in app-private storage is therefore sufficient.

## 3. Why Not SharedPreferences?

SharedPreferences can persist small values, but the denomination model is naturally structured.

JSON provides:

- clear structure
- easy serialization
- easy debugging
- straightforward schema versioning
- easy future migration

If the Android project already uses DataStore, a JSON string inside Preferences DataStore would also be acceptable. The architectural requirement is that persistence remain lightweight and isolated.

## 4. Why Not Room?

Room is unnecessary for this MVP because there are no relational data requirements.

Room can be introduced later if the application gains:

- counting history
- multiple sessions
- reports
- searchable records
- audit history

Those are explicitly outside MVP.

## 5. Why Long Instead of Double?

Money calculations should not use floating-point values.

Using:

```text
Double
```

can produce binary floating-point representation errors.

Using:

```text
Long
```

for whole monetary units makes the arithmetic deterministic.

## 6. Why Configurable Denominations?

The original UI concept assumes a fixed denomination list, but the actual target environment is Cuba.

Therefore the application must treat denominations as user configuration.

This avoids rebuilding the app whenever:

- a denomination changes
- the user wants a different set
- the user wants to count a different currency
- a specific business uses a subset of denominations

The MVP still starts with a default list.

## 7. Why Block Denomination Changes During Active Count?

Changing:

```text
$5000 × 10
```

to:

```text
$250 × 10
```

while the user is halfway through a count can silently change the counted total.

For a small MVP, the safest UX is:

```text
Active count
     ↓
denomination edit/delete disabled
     ↓
clear count
     ↓
configure denominations
```

This is preferable to hidden data mutation.

## 8. Why No Count Persistence?

Persisting the active count introduces additional product questions:

- Should unfinished counts be restored?
- When is a count considered complete?
- Should target persist?
- What happens after force-close?
- Should multiple sessions exist?

Those questions are not necessary for the core use case.

Therefore only denomination configuration persists in MVP.

## 9. UI Strategy

The UI should prioritize speed over decorative complexity.

The user should be able to:

```text
set target
   ↓
tap + / enter quantities
   ↓
see total
   ↓
see remaining
```

without navigating away from the main screen.

## 10. Future Extensions

Potential future features, intentionally excluded from MVP:

```text
count history
save sessions
export PDF/CSV
multiple currencies
coin/bill categories
custom currency symbol
sound/vibration feedback
dark mode customization
backup/restore configuration
cloud sync
```

These should only be added through a new specification/change rather than being implemented speculatively.
