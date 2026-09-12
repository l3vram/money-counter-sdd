# Plan 025: Registration flow (rol solicitado + datos de negocio + pass temporal + WhatsApp)

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving on. If any "STOP
> conditions" occurs, stop and report — do not improvise.
>
> **Drift check (run first)**: `git diff --stat 2c3665a..HEAD -- app/src/main/java`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against live code; on mismatch, STOP.

## Status

- **Priority**: P0
- **Effort**: M
- **Risk**: MED — adds a new public account-creation path (Appwrite Cloud).
- **Depends on**: plan 019 (org/branch JSON config) + existing AppwriteAuthRepository.
- **Category**: feature / onboarding
- **Planned at**: commit `2c3665a`, branch `feature/multi-tenant`, 2026-09-12

## Why this matters

Today anyone can create an account implicitly on first sign-in (`AppwriteAuthRepository.ensureSession`
auto-registers with whatever password was typed). The user wants an explicit **registration**
flow: choose a role (DUEÑO/ADMIN/SELLER), DUEÑO captures business name + branches, the app
generates a **temporary password** (shown once + included in a pre-loaded WhatsApp message to
the superuser), and stores a **signup request** (`signups` table, row id = Appwrite uid). After
registration the user is `PENDING` until the web admin approves (plan 027).

## Current state

- `app/src/main/java/com/moneycounter/appwrite/AppwriteAuthRepository.kt` — `signInWithEmail`
  auto-creates the account on first sign-in with the typed password (`ensureSession`, `account.create`,
  `createEmailPasswordSession`).
- `app/src/main/java/com/moneycounter/ui/AuthViewModel.kt` — `signInWithEmail`, `signOut`,
  `observeMember`; exposes `uiState: AppAccessState` (SignedOut / Pending / Approved…).
- `app/src/main/java/com/moneycounter/ui/screens/LoginScreen.kt` — email + password fields, login button.
- `app/src/main/java/com/moneycounter/util/WhatsAppLauncher.kt` — `buildWhatsAppUrl(number, message)`
  and `openWhatsApp(context, number, message)`; defaults come from `com.moneycounter.config.ContactConfig`
  (`WHATSAPP_NUMBER`, `ACCESS_REQUEST_MESSAGE`).
- `app/src/main/java/com/moneycounter/ui/screens/AccessRequiredScreen.kt` — shown while PENDING;
  button "Solicitar acceso por WhatsApp".
- Existing Appwrite tables (DB `main`, project `6aa332f40001072d0747`): `users` (email, displayName, access),
  `members` (orgId, role, branchIds), and NEW `signups` (id=uid, email, role enum
  OWNER/ADMIN/SELLER, businessName, branches varchar[]/array, mustChangePassword bool default true,
  status enum PENDING/APPROVED/REJECTED default PENDING, createdAt bigint, approvedAt bigint) with
  table permission `create("users")`, and `settings` (single row id="app", column superuserWhatsapp varchar,
  permission read("users"))`.
- The app already reads/writes Appwrite DB tables from repositories under
  `app/src/main/java/com/moneycounter/appwrite/` (e.g. `AppwriteAccessRepository`,
  `AppwriteMembershipRepository`) — mirror their SDK usage pattern for the new `signups`/`settings` tables.

Repo conventions:
- Pure logic → top-level functions/companions, JUnit4 tests (`org.junit.Test`, `org.junit.Assert.*`).
- UI strings hardcoded Spanish (consistent with existing screens).
- Navigation: string `currentScreen` in `MainActivity` (no nav library).
- Commit style: `feat(onboarding): registro con rol + pass temporal + WhatsApp (plan 025)`.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL |
| Tests | `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL, 375 + N new tests, 0 failures |
| APK | `./gradlew assembleDebug` | BUILD SUCCESSFUL |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/ui/screens/LoginScreen.kt` (entry to registration)
- `app/src/main/java/com/moneycounter/ui/screens/SignUpScreen.kt` (create)
- `app/src/main/java/com/moneycounter/ui/AuthViewModel.kt`
- `app/src/main/java/com/moneycounter/appwrite/AppwriteSignupRepository.kt` (create)
- `app/src/main/java/com/moneycounter/signup/SignupRequest.kt` (domain model, create) and
  `PasswordGenerator.kt` + `SignupWhatsAppMessage.kt` (pure helpers, create — under `domain/` or `signup/`)
- `app/src/main/java/com/moneycounter/MainActivity.kt` (routing to sign-up success screen)
- `app/src/test/java/com/moneycounter/signup/PasswordGeneratorTest.kt`,
  `SignupWhatsAppMessageTest.kt`, `SignupRepositoryTest.kt` (create)

**Out of scope**: forced password change (plan 026), web admin (plan 027), post-approval sync (plan 028).
Do NOT modify `AppwriteAuthRepository.ensureSession` semantics beyond what Step 4 requires.

## Steps

### Step 1: Domain helpers

- `PasswordGenerator.generate(): String` — crypto-secure random password, length 12, charset
  a-z A-Z 0-9 (skip ambiguous chars 0/O/1/l/I optional). Must satisfy Appwrite minimum of 8 chars.
- `SignupWhatsAppMessage.build(email, role, businessName, branches, tempPassword): String` —
  Spanish message, e.g.:
  `Hola, quiero acceso a El Luiso. Rol solicitado: {role}. Correo: {email}. Negocio: {businessName}. Sucursales: {branches.joinToString(", ")}. Contraseña temporal: {tempPassword}`.
  For ADMIN/SELLER the business/sucursales part is omitted.

**Verify**: compile.

### Step 2: `SignupRequest` + `AppwriteSignupRepository`

- `data class SignupRequest(uid, email, role, businessName?, branches: List<String>, mustChangePassword: Boolean = true, status = "PENDING", createdAtMs: Long)`.
- `AppwriteSignupRepository`:
  - `suspend fun submit(request: SignupRequest)` — creates the `signups` row with `rowId = uid`
    (mirror the SDK table write pattern already used for users/members).
  - `suspend fun settingsSuperuserWhatsapp(): String?` — reads `settings`/`app`, returns `superuserWhatsapp`.
  - Wrap errors consistently with `mapAuthError` / project's error mapping.

**Verify**: compile.

### Step 3: `AuthViewModel` — sign-up action

- `fun signUp(email: String, role: Role, businessName: String?, branches: List<String>)`:
  1. Validate email non-blank and password-relevant invariants (no password input — it's generated).
  2. `val tempPass = PasswordGenerator.generate()`
  3. Create the account + session + write the signup via existing repo + `submit`.
     Reuse the same account-creation semantics as `AppwriteAuthRepository.ensureSession`
     (create account with `tempPass`, then `createEmailPasswordSession`). Expose the result
     to `uiState` as `AppAccessState.SignUpPending(tempPass)` (new state) so the success screen shows the password once.
  4. On failure → `_uiState.signUpError` message (Spanish).
- Preserve existing public API used by tests.

**Verify**: compile.

### Step 4: Navigation + screens

- `SignUpScreen`: fields Correo; role selector (3 segmented buttons: DUEÑO / ADMIN / SELLER);
  when DUEÑO selected → business name field + dynamic list of branch name fields (add/remove);
  submit "Crear cuenta" → calls `signUp`. Route from `LoginScreen` via a `TextButton("Crear cuenta")`.
- After successful sign-up show `SignUpSuccessScreen`: displays the **temporary password** with a
  warning "Cámbiala en tu primer inicio de sesión" + button "Solicitar acceso por WhatsApp"
  that calls `openWhatsApp(context, superuserNumber, message)` where message =
  `SignupWhatsAppMessage.build(... tempPass)` and number = `settingsSuperuserWhatsapp()`
  (fallback to `ContactConfig.WHATSAPP_NUMBER` when empty). When WhatsApp number unknown
  (empty), still show the password and an informational note instead of the WhatsApp button.
- Wire routes in `MainActivity` (extend the `currentScreen` pattern; a new screen id `SIGNUP` and
  `SIGNUP_SUCCESS`). End of success flow → back to login (user signs in with email + temp pass).

**Verify**: compile + assembleDebug.

### Step 5: Tests

- `PasswordGeneratorTest`: length ≥ 8, charset only, randomized distinct within limits, no exceptions.
- `SignupWhatsAppMessageTest`: message contains role/email/pass; DUEÑO includes business+sucursales; ADMIN/SELLER omit them.
- `SignupRepositoryTest`: request mapping to/from the row payload (mock the SDK layer like existing repo tests).

**Verify**: `./gradlew testDebugUnitTest` → 375 + ~10 new tests, green. Then `assembleDebug`.

## Test plan

Structural pattern: follow existing repository/VM unit tests in the repo. Cover: password rules,
message composition, repository payload, VM wiring happy-path (no network: inject fakes).

## Done criteria

ALL must hold:

- [ ] `./gradlew testDebugUnitTest` green (375 + new tests)
- [ ] `./gradlew compileDebugKotlin assembleDebug` green
- [ ] Registration creates account + `signups/{uid}` row with `mustChangePassword=true`, `status=PENDING`
- [ ] Success screen shows the generated temp password once; WhatsApp button present when the
      superuser number is known and message includes the password
- [ ] No files outside scope modified (`git status`)

## STOP conditions

Stop and report if:

- The app's existing Appwrite table SDK usage differs materially from the pattern described
  (no prior example of creating a row in a new table on the client).
- Changing `AuthViewModel`/`MainActivity` navigation requires restructuring beyond the
  `currentScreen` pattern.
- The auto-registration semantics in `AppwriteAuthRepository` are entangled in a way that the
  sign-up path cannot reuse without breaking existing sign-in.

## Maintenance notes

- The temp password is intentionally plaintext-shown once and sent to the superuser as part of the
  WhatsApp request — scheme chosen by the owner. Plan 026 forces the change at first login.
- `signups.status` is advisory; `users.access` remains the source of truth for gating (plan 027 sets
  `APPROVED` onChange).