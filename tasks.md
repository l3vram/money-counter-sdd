# Money Counter — Implementation Tasks

## Task Execution Rules

- Follow `spec.md` as the source of truth.
- Follow `plan.md` for technical direction.
- Do not add features outside the MVP.
- Mark tasks complete only after implementation and relevant tests pass.
- Prefer small, independently verifiable changes.
- If an ambiguity is discovered, update the specification before coding around it.

---

## Phase 1 — Project Foundation

### T001 — Inspect existing Android project
- [ ] Identify current Android/Kotlin/Compose versions.
- [ ] Reuse existing project conventions where applicable.
- [ ] Confirm minimum/target SDK.
- [ ] Confirm current test setup.

### T002 — Establish minimal application structure
- [ ] Create/confirm package structure.
- [ ] Configure Compose and Material 3.
- [ ] Confirm debug and release builds work.
- [ ] Avoid introducing unnecessary architecture.

---

## Phase 2 — Domain

### T003 — Create Denomination model
- [ ] Create stable denomination ID.
- [ ] Create positive `Long` denomination value.
- [ ] Support configured ordering.
- [ ] Add model-level validation where appropriate.

### T004 — Create CounterStatus
- [ ] Implement `EMPTY`.
- [ ] Implement `COUNTING`.
- [ ] Implement `COMPLETED`.
- [ ] Implement `OVER`.

### T005 — Implement counter calculation
- [ ] Implement subtotal calculation.
- [ ] Implement counted total.
- [ ] Implement remaining calculation.
- [ ] Implement excess calculation.
- [ ] Implement status derivation.
- [ ] Prevent negative quantities.
- [ ] Handle arithmetic overflow safely.

### T006 — Unit test counter calculation
- [ ] Empty state.
- [ ] One denomination.
- [ ] Multiple denominations.
- [ ] Zero quantity.
- [ ] Target greater than total.
- [ ] Target equal to total.
- [ ] Target lower than total.
- [ ] Large values.
- [ ] Invalid quantity.
- [ ] Overflow behavior.

---

## Phase 3 — Denomination Persistence

### T007 — Define denomination JSON schema
- [ ] Add schema version.
- [ ] Add denomination IDs.
- [ ] Add denomination values.
- [ ] Add ordering through array order or explicit position.
- [ ] Document default configuration.

Expected shape:

```json
{
  "version": 1,
  "denominations": [
    { "id": "d1", "value": 5000 },
    { "id": "d2", "value": 2000 }
  ]
}
```

### T008 — Implement denomination repository
- [ ] Implement `load`.
- [ ] Implement `save`.
- [ ] Use app-private storage.
- [ ] Keep file I/O out of UI and calculation logic.
- [ ] Handle missing file.
- [ ] Handle malformed JSON.
- [ ] Handle invalid data.
- [ ] Add safe write behavior where practical.

### T009 — Add default denomination provider
- [ ] Keep defaults in one dedicated location.
- [ ] Make defaults easy for developers to change.
- [ ] Do not hard-code denomination values into Compose UI.

### T010 — Test persistence
- [ ] Save then load.
- [ ] Missing file uses defaults.
- [ ] Invalid JSON falls back safely.
- [ ] Duplicate denominations are rejected/normalized according to implementation.
- [ ] Invalid values are rejected.
- [ ] Version field is handled.

---

## Phase 4 — ViewModel / State

### T011 — Implement MoneyCounterViewModel
- [ ] Expose immutable UI state.
- [ ] Load denomination configuration.
- [ ] Initialize quantities for configured denominations.
- [ ] Update target.
- [ ] Update denomination quantity.
- [ ] Add denomination.
- [ ] Edit denomination.
- [ ] Delete denomination.
- [ ] Reorder denomination.
- [ ] Clear active count.

### T012 — ViewModel tests
- [ ] Target update.
- [ ] Quantity update.
- [ ] Derived total.
- [ ] Derived remaining.
- [ ] Completed state.
- [ ] Over state.
- [ ] Clear count.
- [ ] Add denomination.
- [ ] Duplicate denomination rejection.
- [ ] Edit/delete blocked while count is active if that UX rule is implemented.

---

## Phase 5 — Main Counter UI

### T013 — Create main screen
- [ ] Top app bar.
- [ ] Target section.
- [ ] Counted total section.
- [ ] Remaining/excess section.
- [ ] Progress indicator where appropriate.
- [ ] Denomination list.
- [ ] Clear-all action.

### T014 — Implement target input
- [ ] Numeric keyboard.
- [ ] Display current target.
- [ ] Clear target action.
- [ ] Reject invalid/negative values.
- [ ] Update ViewModel immediately.

### T015 — Implement denomination row
- [ ] Display denomination.
- [ ] Display quantity.
- [ ] Display subtotal.
- [ ] Decrement button.
- [ ] Increment button.
- [ ] Direct quantity editing.
- [ ] Prevent negative quantity.
- [ ] Immediate calculation.

### T016 — Implement summary states
- [ ] Counting state.
- [ ] Completed state.
- [ ] Over state.
- [ ] Empty state.
- [ ] Make remaining/excess visually obvious.

### T017 — Implement clear-all
- [ ] Show confirmation when data exists.
- [ ] Reset target.
- [ ] Reset all quantities.
- [ ] Preserve denomination configuration.
- [ ] Ensure no accidental denomination deletion.

---

## Phase 6 — Denomination Management

### T018 — Create denomination management screen
- [ ] List configured denominations.
- [ ] Add action.
- [ ] Edit action.
- [ ] Delete action.
- [ ] Reorder action.
- [ ] Back navigation.

### T019 — Add denomination flow
- [ ] Numeric input.
- [ ] Require positive value.
- [ ] Reject duplicate value.
- [ ] Save immediately after success.
- [ ] Return to list.

### T020 — Edit denomination flow
- [ ] Numeric input.
- [ ] Require positive value.
- [ ] Reject duplicates.
- [ ] Prevent operation when count is active.
- [ ] Save after success.

### T021 — Delete denomination flow
- [ ] Confirmation dialog.
- [ ] Prevent operation when count is active.
- [ ] Remove denomination.
- [ ] Save configuration.

### T022 — Reorder denominations
- [ ] Implement simple move-up/move-down controls OR drag-and-drop if already supported cleanly.
- [ ] Persist order.
- [ ] Verify main counter uses configured order.

### T023 — Persistence integration
- [ ] Verify add/edit/delete/reorder survive process/app restart.
- [ ] Verify current count does not need to persist for MVP.

---

## Phase 7 — UI Tests

### T024 — Main screen tests
- [ ] Enter target.
- [ ] Increment denomination.
- [ ] Directly edit quantity.
- [ ] Verify subtotal.
- [ ] Verify total.
- [ ] Verify remaining.
- [ ] Verify completed state.
- [ ] Verify excess state.

### T025 — Denomination management tests
- [ ] Open settings.
- [ ] Add denomination.
- [ ] Reject duplicate.
- [ ] Edit denomination.
- [ ] Delete denomination.
- [ ] Reorder denomination.

### T026 — Clear flow test
- [ ] Create non-empty count.
- [ ] Trigger clear.
- [ ] Verify confirmation.
- [ ] Confirm.
- [ ] Verify target reset.
- [ ] Verify quantities reset.
- [ ] Verify denominations remain.

---

## Phase 8 — UX / Accessibility

### T027 — Touch interaction review
- [ ] Verify increment/decrement buttons are comfortable to tap.
- [ ] Verify numeric fields are easy to focus.
- [ ] Verify keyboard does not obscure important content.

### T028 — Accessibility review
- [ ] Content descriptions for icon-only actions.
- [ ] Meaningful labels for fields.
- [ ] Adequate contrast.
- [ ] State changes are understandable without relying exclusively on color.
- [ ] Screen-reader labels are meaningful.

### T029 — Spanish UI review
- [ ] Review all visible strings.
- [ ] Use consistent terminology.
- [ ] Avoid technical wording.
- [ ] Verify number formatting.

---

## Phase 9 — Reliability

### T030 — Corrupt configuration recovery
- [ ] Simulate malformed JSON.
- [ ] Launch application.
- [ ] Verify app does not crash.
- [ ] Verify defaults are loaded.
- [ ] Verify valid configuration can be saved again.

### T031 — Large-value robustness
- [ ] Test large denomination values.
- [ ] Test large quantities.
- [ ] Verify overflow protection.
- [ ] Verify no negative/wrapped totals.

### T032 — Offline validation
- [ ] Disable network.
- [ ] Launch app.
- [ ] Count money.
- [ ] Configure denominations.
- [ ] Verify all MVP functionality works.

---

## Phase 10 — Final Validation

### T033 — Full test suite
- [ ] Unit tests pass.
- [ ] Persistence tests pass.
- [ ] UI tests pass.

### T034 — Manual acceptance test
- [ ] Follow every acceptance criterion in `spec.md`.
- [ ] Test target below total.
- [ ] Test target equal total.
- [ ] Test target above total.
- [ ] Test denomination configuration.
- [ ] Test clear-all.
- [ ] Test restart persistence.

### T035 — Release build
- [ ] Build release APK.
- [ ] Verify installation.
- [ ] Verify launch.
- [ ] Verify main flow.
- [ ] Verify no debug-only dependency is required.

### T036 — MVP sign-off
- [ ] No unresolved `[NEEDS CLARIFICATION]` items.
- [ ] No speculative features implemented.
- [ ] Specification and implementation behavior match.
- [ ] All required tasks complete.
