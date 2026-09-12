# Plan 026: Forced password change on first login

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving on. If any "STOP
> conditions" occurs, stop and report — do not improvise.
>
> **Drift check (run first)**: `git diff --stat 2c3665a..HEAD -- app/src/main/java`
> and compare the "Current state" excerpts against live code; on mismatch, STOP.

## Status

- **Priority**: P0
- **Effort**: S
- **Risk**: LOW-MED — new gate before the counter; must not lock out approved users on odd states.
- **Depends on**: plan 025 (signup writes `mustChangePassword=true`); base branch after merging plan 025.
- **Category**: feature / onboarding security
- **Planned at**: commit `2c3665a`, branch `feature/multi-tenant`, 2026-09-12

## Why this matters

The web admin (plan 027) also resets passwords to a temporary one. Until the user changes it, the
temp password is known to the superuser. The app must **force the change on first login** and clear
the flag. This keeps the owner's requirement: the master never knows the user's final password.

## Current state

- `signups` table row (id = uid) has column `mustChangePassword` (bool, default true).
- `AppwriteAuthRepository`: `account.updatePassword` is available in the Appwrite client SDK
  (signed-in session) — the repository must expose it.
- `AuthViewModel.uiState: AppAccessState` (SignedOut / Pending / Approved…) — add a
  `PasswordChangeRequired` branch so `AuthenticationGate`/`MainActivity` can route to the change screen.
- `MainActivity` uses the `currentScreen` string pattern; `AuthenticationGate.kt` decides which root
  screen shows based on app access state.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL |
| Tests | `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL, 385 + N new, 0 failures |
| APK | `./gradlew assembleDebug` | BUILD SUCCESSFUL |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/appwrite/AppwriteAuthRepository.kt` (add `changePassword`)
- `app/src/main/java/com/moneycounter/appwrite/AppwriteSignupRepository.kt` (add `setMustChangePassword(uid, false)` + read `mustChangePassword`)
- `app/src/main/java/com/moneycounter/auth/AuthRepository.kt` (interface additions)
- `app/src/main/java/com/moneycounter/ui/AuthViewModel.kt`
- `app/src/main/java/com/moneycounter/access/AppAccessState.kt` (new `PasswordChangeRequired` branch)
- `app/src/main/java/com/moneycounter/ui/screens/ChangePasswordScreen.kt` (create)
- `app/src/main/java/com/moneycounter/ui/AuthenticationGate.kt` + `MainActivity.kt` (route the gate)
- tests: `AuthRepository`/VM-level unit tests

**Out of scope**: password "recovery" self-service (admin-mediated, plan 027), editing signups by users.

## Steps

### Step 1: Repository changePassword

- `AuthRepository.changePassword(newPassword: String): Result<Unit>` → `account.updatePassword`.
- `AppwriteSignupRepository.setMustChangePassword(uid: String, flag: Boolean)` → update the
  `signups/{uid}` row `mustChangePassword` column; `readMustChangePassword(uid): Boolean`.
  Mirror the appwrite table update pattern already used elsewhere.

**Verify**: compile.

### Step 2: VM gate

- On successful sign-in + approved access, read `mustChangePassword` from the signup row. If true,
  set `uiState = PasswordChangeRequired` (instead of proceeding to the counter). Keep the existing
  approved path when the flag is false/non-existent.
- `fun changePassword(current: String, new: String, confirm: String)` — validate new == confirm and
  ≠ current (Spanish inline errors); call repository; on success call
  `setMustChangePassword(uid, false)` and transition to the normal Approved state.
- Handle SDK error mapping consistently (`mapAuthError`).

**Verify**: compile.

### Step 3: Screen + gate routing

- `ChangePasswordScreen`: "Cambiar contraseña" (required on first login), fields Contraseña actual /
  Nueva / Confirmar, submit. Reuse the visual style of `LoginScreen`.
- `AuthenticationGate`/`MainActivity`: when state is `PasswordChangeRequired`, show
  `ChangePasswordScreen` instead of the counter, and only allow exiting via a successful change.
  Approved users with flag cleared never see this screen again.

**Verify**: compile + assembleDebug.

### Step 4: Tests

- Repository: changePassword success/failure mapping (fake SDK).
- VM: approved + flag true ⇒ PasswordChangeRequired; successful change ⇒ Approved and flag-cleared
  repo call; validation mismatches (mismatch confirm, new == current) rejected without repo calls.

**Verify**: `./gradlew testDebugUnitTest` → 385 + ~8 new tests green. Then `assembleDebug`.

## Done criteria

ALL must hold:

- [ ] `./gradlew testDebugUnitTest` green
- [ ] `./gradlew compileDebugKotlin assembleDebug` green
- [ ] Users with `mustChangePassword=true` are forced to the change screen; successful change clears the flag
- [ ] No files outside scope modified

## STOP conditions

Stop and report if:

- The existing access-state machine (`AppAccessState`, `AuthenticationGate`) can't host a new
  branch without a redesign.
- `account.updatePassword` isn't available on the SDK version used (25.2.0) — confirm via docs/IDE.
- Clearing the flag requires permissions the client doesn't have on `signups` (it only needs update
  of its own row; if the table permission blocks client updates, STOP and report).

## Maintenance notes

- After plan 027 lands, the web admin's reset-password action must set `mustChangePassword=true`
  on the target signup row so the next login forces the change again.
- Keep the flag clear even if the user changes the password via any future path to avoid loops.