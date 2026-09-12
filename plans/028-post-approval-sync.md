# Plan 028: Post-approval sync — poblar config local desde orgs/branches cloud

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving on. If any "STOP
> conditions" occurs, stop and report — do not improvise.
>
> **Drift check (run first)**: `git diff --stat 2c3665a..HEAD -- app/src/main/java`
> and compare against the "Current state" excerpts; on mismatch, STOP.

## Status

- **Priority**: P0
- **Effort**: M
- **Risk**: MED (touches org/branch config seeding — plan 019 area).
- **Depends on**: plan 026 + plan 027 (orgs/branches records exist in the cloud once approved); base
  branch after merging both.
- **Category**: integration / onboarding
- **Planned at**: commit `2c3665a`, branch `feature/multi-tenant`, 2026-09-12

## Why this matters

When the web admin approves a signup it creates `orgs` + `branches` rows in Appwrite. The Android
app is offline-first and keeps its org/branch configuration in local JSON (plan 019 bootstrap). The
approved member must end up with the **same org/branch context** in their device so multi-tenant
scoping (plans 019/020) and the visible "negocio / sucursal" work. This plan seeds that local config
from the cloud once the member is approved.

## Current state

- `members` table: read-only for clients; the app polls `observeMember(uid)` (10s) — a row appears
  after approval (`AppwriteMembershipRepository`).
- Org/branch local JSON config lives behind the repository introduced in plan 019
  (bootstrap org/branch). `Membership`/`Member` domain model already carries `orgId`, `role`,
  `branchIds`.
- `orgs` table (cloud): id, name, whatsappNumber, status, createdAt. `branches` table (cloud):
  id, orgId, name, status, createdAt. Both have `read("users")` so any logged user can read them.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL |
| Tests | `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL, 393 + N new, 0 failures |
| APK | `./gradlew assembleDebug` | BUILD SUCCESSFUL |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/appwrite/AppwriteCloudOrgRepository.kt` (create): read org by id
  and branches by orgId from Appwrite tables.
- `app/src/main/java/com/moneycounter/ui/AuthViewModel.kt` (or a dedicated sync helper): on member
  observed + approved, seed the local org/branch config if the orgId isn't present locally yet.
- Local JSON org/branch repository (plan 019 surface, exact name verified at execution time):
  add an idempotent `seedFromCloud(org, branches)` that creates/merges org + its branches without
  overwriting existing local data or breaking the multi-tenant scoping.
- header/profile: show approved org name + selected branch when available (already partially handled;
  verify textually).
- tests: mapping org/branch rows → domain, idempotent re-seed, member-without-org no-op.

**Out of scope**: full cloud sync/concurrency (FASE 13), org suspension handling (future), writing
back to the cloud.

## Steps

### Step 1: Cloud read repository

- `AppwriteCloudOrgRepository`:
  - `suspend fun getOrg(orgId: String): OrgInfo?` (id, name, whatsappNumber, status)
  - `suspend fun getBranches(orgId: String): List<BranchInfo>` (id, name, orgId, status)
  - Map only `ACTIVE` status rows into the local model (skip suspended) but keep the org entry if active.
- Mirror the existing appwrite table-read pattern (same SDK usage as the access/membership repos).

**Verify**: compile.

### Step 2: Seed on approval

- When `observeMember` reports a member (approved path already wires `access=APPROVED`), if
  `member.orgId` is non-blank and the local JSON org config doesn't yet contain that orgId, call
  `seedFromCloud(org, branches)`. Failures are logged, never fatal (offline-tolerant): if cloud read
  fails, stay unseeded and retry on the next poll.
- Guard: never auto-create or overwrite a different local org's branch list; only add the missing org.

**Verify**: compile.

### Step 3: UI surface

- Where the app shows the org/business name or branch list (header / profile), ensure it reflects the
  seeded config after approval (verify current display logic; fix only if it shows a stale/default
  org when a cloud-seeded one exists).

**Verify**: compile + assembleDebug.

### Step 4: Tests

- Repository mapping (row → OrgInfo/BranchInfo) incl. status filtering.
- Seeder: idempotency (second run no-op), member without orgId no-op, existing org not overwritten,
  cloud error ⇒ local untouched.

**Verify**: `./gradlew testDebugUnitTest` → 393 + ~10 new, green. Then `assembleDebug`.

## Done criteria

ALL must hold:

- [ ] `./gradlew testDebugUnitTest` green
- [ ] `./gradlew compileDebugKotlin assembleDebug` green
- [ ] Approved member with orgId/branchIds gets local org+branches seeded (idempotent, offline-tolerant)
- [ ] No files outside scope modified

## STOP conditions

Stop and report if:

- The plan-019 local org/branch repository surface differs from what this plan references (names/
  shapes) — stop and ask for the actual API.
- Seeding requires writes to tables the client cannot write (orgs/branches are read-only for users) —
  that is by design; only `read` is used here, so if the code needs a write, STOP.

## Maintenance notes

- This is one-way (cloud → local) and explicit MVP of FASE 13 sync.
- After this + plan 026 + 027, the E2E flow is: signup → pending → web admin approve (org+branches
  created) → login → forced password change → seeding → counter with correct org/branch context.