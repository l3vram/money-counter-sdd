# Plan 029: QA E2E + regresión + docs + README (closing gate)

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving on. If any "STOP
> conditions" occurs, stop and report — do not improvise.
>
> **Drift check (run first)**: `git diff --stat 2c3665a..HEAD -- app/src/main` and
> confirm plan 025 + 026 + 027 + 028 are merged into the base branch.

## Status

- **Priority**: P0
- **Effort**: M
- **Risk**: LOW — verification + docs only (code already landed).
- **Depends on**: plans 025, 026, 027, 028 (all merged into `feature/multi-tenant`).
- **Category**: QA / docs
- **Planned at**: commit `2c3665a`, branch `feature/multi-tenant`, 2026-09-12

## Why this matters

Closes the onboarding milestone with a full regression (the REGLA CAJA: montos SIEMPRE correctos —
`testDebugUnitTest` green + netCashTotal/signs review), an E2E smoke script for the new flow, and
updated documentation so the owner/operators know how to run the whole system.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Full tests | `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL, ~403 tests, 0 failures |
| Compile+APK | `./gradlew compileDebugKotlin assembleDebug` | BUILD SUCCESSFUL |
| Web build | `npm install && npm run build` in `webadmin/app` | static bundle ok |

## Steps

### Step 1: Full regression

- Run `testDebugUnitTest` + `compileDebugKotlin` + `assembleDebug`. Record the new test count.
- **Caja review (manual, code-level)**: re-check `netCashTotal()`/`MovementType.cashSign()`/
  `moneySign()` are untouched and correct; confirm spend signs GASTO/MERMA negative and fiado out of caja.
- Confirm role permissions matrix still intact (Role.kt / PermissionService tests green).

**Verify**: all commands green.

### Step 2: E2E smoke checklist (document the manual script for the owner)

Write `docs/onboarding-e2e.md` with the exact steps:
1. Fresh install → register (DUEÑO with business + branches; ADMIN/SELLER without) → note temp password shown.
2. WhatsApp message pre-loaded with role/email/pass → superuser.
3. Web admin (Appwrite Sites URL): login, list signups PENDING, approve DUEÑO (org+branches auto-created),
   approve ADMIN/SELLER picking org+branches; reject a case.
4. App: login with email + temp pass → forced change → counter opens with the correct org/branch context.
5. Reset-password path: admin resets a user → user login forced change again.
6. Role checks in-app (SELLER: no stock ops; ADMIN: stock/file ops present; OWNER: org-wide).

### Step 3: Docs

- Update `plans/README.md`: mark 025/026/027/028/029 with commit SHAs + final test count; move
  "web admin" from deferred to done-for-MVP; note remaining roadmap.
- `webadmin/README.md` already describes provisioning (plan 027) — refine with the LIVE URLs and
  function/site IDs used in the actual deploy.
- Update this repo's root README cheat section if it lists milestones (only if such a section exists).

### Step 4: Final gate

- Produce the summary line for Gate B: commits merged, test totals, deploy URLs, outstanding risks.
- No version bump / no merge to `main` / no release unless the owner explicitly asks.

## Done criteria

ALL must hold:

- [ ] `./gradlew testDebugUnitTest` green (~403 tests, 0 failures)
- [ ] `./gradlew compileDebugKotlin assembleDebug` green
- [ ] `webadmin/app` build green
- [ ] `docs/onboarding-e2e.md` written with reproducible steps
- [ ] `plans/README.md` updated (025-029 DONE + SHAs)
- [ ] Caja review documented (signs/denomination check)

## STOP conditions

Stop and report if:

- Any test regression appears in plans 017-024 territory (tenant isolation, stock dual-write, closing scope) — do not patch; report.
- The onboarding flow cannot be exercised end-to-end because the web admin deploy/functions aren't live — document what's missing and stop.

## Maintenance notes

- The E2E doc is the source of truth for the next release test.
- Outstanding roadmap stays: Owner Dashboard (FASE 8-9), full sync/concurrency (FASE 13), AuditLog,
  org suspension (Android block), delete `Product.stock` duplication.