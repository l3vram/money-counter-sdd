# Plan 018: PermissionService + rol ADMIN + SELLER read-only (defensa en profundidad)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise.
>
> **Drift check (run first)**: `git diff --stat 1069419..HEAD -- app/src/main/java/com/moneycounter/domain app/src/main/java/com/moneycounter/viewmodel app/src/main/java/com/moneycounter/ui app/src/test`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against live code; on mismatch, STOP.

## Status

- **Priority**: P0
- **Effort**: M
- **Risk**: MED — changes visible SELLER behavior (product decision, confirmed: per master plan SELLER is strictly read-only on inventory; loses Alta `+` and Baja por merma).
- **Depends on**: plans/017-branch-baseline.md
- **Category**: security
- **Planned at**: commit `1069419`, 2026-09-11

## Why this matters

The master plan (FASE 1) requires four distinct roles (OWNER/ADMIN/SELLER/SUPERUSER)
behind one `PermissionService`, plus an `AppContext`. Today `Role` has 3 roles
(no ADMIN), permissions are a few standalone functions mostly unused, and SELLER
can still register merma and add stock — both explicitly forbidden by the master
plan. This slice adds ADMIN, centralizes every permission, and pushes enforcement
into the ViewModel (defense in depth: UI hiding alone is not security — the VM
must refuse the mutation regardless of what the client shows). Closes, in the
same pass, catalog management (`Ajustes`) leaking to SELLER.

That stance documents the core rule: the ViewModel becomes the security boundary.

## Current state

Excerpts (verify before editing):

`app/src/main/java/com/moneycounter/domain/Role.kt` — enum has SELLER/OWNER/SUPERUSER and scattered functions, e.g.:
```kotlin
enum class Role { SELLER, OWNER, SUPERUSER; ... }
fun Role.canAddStock(): Boolean = when (this) {
    Role.SELLER, Role.OWNER -> true      // must become OWNER/ADMIN only
    Role.SUPERUSER -> false }
fun Role.canRegisterWriteoff(): Boolean = when (this) {
    Role.SELLER, Role.OWNER -> true      // must become OWNER/ADMIN only
    Role.SUPERUSER -> false }
```

`app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`:
- Lines 90–96 hold author identity only: `private var sellerUid: String = ""`, `sellerName`, and `fun setSeller(uid, name)`.
- Stock-mutating public functions are NOT permission-gated today: `addStock(...)` (line 461), `registerWriteoff(...)` (line 557), `recordExpense(...)` (line 594), `addProduct(...)` (line 368), `editProduct(...)` (line 405), `deleteProduct(...)` (line 445), plus catalog mutators `addUnit`/`editUnit`/`deleteUnit`/`addCurrency`/`editCurrency`/`deleteCurrency`/`addDenomination`/`editDenomination`/`deleteDenomination`/`moveDenominationUp/Down`.

`app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt`:
- Line 71: `member: Member? = null`; line 74 `val canRegisterWriteoff = member?.role.mayRegisterWriteoff()`; line 75 `val canEditStock = member?.role.mayEditStock()`; line 126 `canDelete = canEditStock`; line 165 `if (canRegisterWriteoff) { … merma … }`; ProductRow receives `onAddStock` (line 121–123 defined, param line 523, added only when a row gate allows).

`app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt` — "Nuevo gasto" button line 207 (`onClick = onNavigateToGasto`), param line 72.

`app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` — Settings IconButton line 108 (`onClick = onNavigateToSettings`), Profile line 115.

`app/src/main/java/com/moneycounter/MainActivity.kt` — line 92–94: `LaunchedEffect(profile?.uid) { viewModel.setSeller(profile?.uid, profile?.displayName) }`. `member` is available in scope (line 76 param).

Repo conventions (match them):
- Pure logic → top-level functions or companion objects; unit-tested with JUnit4 (`org.junit.Test`, `org.junit.Assert.*`), `testImplementation("org.json:json:20240303")`.
- `Money.SCALE = 2`, `Money.ZERO`.
- UI gate pattern already used for StockScreen: `member?.role.mayX()`. This plan MOVES gating to `MoneyCounterUiState` booleans (single source of truth) because ReportsScreen/MoneyCounterScreen have no `member` param.
- Commit style (Spanish conventional): e.g. `feat(perms): SELLER read-only inventario + rol ADMIN (plan 018)`.

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL |
| Tests     | `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL, 246 + N new tests, 0 failures |
| APK       | `./gradlew assembleDebug` | BUILD SUCCESSFUL |

## Scope

**In scope**:
- `app/src/main/java/com/moneycounter/domain/Role.kt`
- `app/src/main/java/com/moneycounter/domain/PermissionService.kt` (create)
- `app/src/main/java/com/moneycounter/domain/AppContext.kt` (create)
- `app/src/main/java/com/moneycounter/viewmodel/MoneyCounterViewModel.kt`
- `app/src/main/java/com/moneycounter/MainActivity.kt`
- `app/src/main/java/com/moneycounter/ui/screens/StockScreen.kt`
- `app/src/main/java/com/moneycounter/ui/screens/ReportsScreen.kt`
- `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt`
- `app/src/test/java/com/moneycounter/domain/RoleTest.kt`
- `app/src/test/java/com/moneycounter/domain/PermissionServiceTest.kt` (create)
- `app/src/test/java/com/moneycounter/domain/AppContextTest.kt` (create)

**Out of scope** (do NOT touch even though related):
- `domain/Movement.kt`, `domain/Closing.kt`, `domain/Product.kt`, repositories, JSON schemas (plans 020/021/023).
- Anything about org/branch selection UI.
- SUPERUSER operational changes beyond what the matrix says (SUPERUSER stays blocked from ops).

## Steps

### Step 1: Add ADMIN role and finalize the permission matrix in `Role.kt`

Add `ADMIN` to the enum. Replace the flat functions with the definitive matrix.
**Exact matrix** (this is the contract; tests assert it verbatim):

| Permission | OWNER | ADMIN | SELLER | SUPERUSER |
|---|---|---|---|---|
| canSell (register sale) | yes | yes | yes | no |
| canRegisterCreditSaleAndCollect | yes | yes | yes | no |
| canRegisterExpense (GASTO) | yes | yes | no | no |
| canAddStock (ALTA) | yes | yes | no | no |
| canEditStock (ajuste directo / editar cantidad) | yes | yes | no | no |
| canRegisterWriteoff (MERMA) | yes | yes | no | no |
| canCreateProduct / canEditProduct / canDeleteProduct | yes | yes | no | no |
| canViewInventory | yes | yes | yes | no |
| canViewHistory (own) | yes | yes | yes | no |
| canViewBranchHistory | yes | yes | no | no |
| canViewOrganizationHistory | yes | no | no | no |
| canCreateSellerClosing | yes | yes | yes | no |
| canCreateBranchClosing | yes | yes | no | no |
| canViewReports | yes | yes | no | no |
| canManageCatalog (denominaciones, monedas, unidades) | yes | yes | no | no |
| canViewAllSellersDashboard | yes | no | no | no |
| canManageAccounts (SUPERUSER admin) | no | no | no | yes |

Keep the existing null-safe `mayX()` helpers (`Role?.mayRegisterWriteoff()` etc.) but repoint them to the new matrix. Add `mayAddStock()`, `mayRegisterExpense()`, `mayCreateSellerClosing()`, `mayCreateBranchClosing()`, `mayManageCatalog()`, `mayViewBranchHistory()`. Keep the documented semantic: `null` role ⇒ single-user full privileges (so legacy installs keep working during migration). `mayViewOwnerDashboard()` stays `false` for `null`.

**Verify**: `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL.

### Step 2: Create `PermissionService.kt`

```kotlin
interface PermissionService {
    fun canSell(): Boolean
    fun canRegisterCreditSaleAndCollect(): Boolean
    fun canRegisterExpense(): Boolean
    fun canAddStock(): Boolean
    fun canEditStock(): Boolean
    fun canRegisterWriteoff(): Boolean
    fun canCreateProduct(): Boolean
    fun canEditProduct(): Boolean
    fun canDeleteProduct(): Boolean
    fun canViewInventory(): Boolean
    fun canViewBranchHistory(): Boolean
    fun canViewOrganizationHistory(): Boolean
    fun canCreateSellerClosing(): Boolean
    fun canCreateBranchClosing(): Boolean
    fun canViewReports(): Boolean
    fun canManageCatalog(): Boolean
    fun canViewAllSellersDashboard(): Boolean
    fun canManageAccounts(): Boolean
}
```
- `class RolePermissionService(role: Role?) : PermissionService` — delegates to the `Role.kt` functions ("null ⇒ full ops").
- `object DefaultPermissionService : PermissionService` — every method returns `true` except `canViewAllSellersDashboard()` and `canManageAccounts()` returning `false`.
- `fun PermissionService.forRole(role: Role?): PermissionService` top-level helper mapping null → DefaultPermissionService.

This is the single source of truth used by both the VM (security) and the UI (UX).

**Verify**: compile.

### Step 3: Create `AppContext.kt`

```kotlin
data class AppContext(
    val uid: String,
    val organizationId: String? = null,
    val branchId: String? = null,
    val role: Role? = null
)
```
Add `fun AppContext.isSeller(): Boolean = role == Role.SELLER`. Add `require(uid.isNotBlank())`.

**Verify**: compile.

### Step 4: Wire permission service + catalog gating into `MoneyCounterViewModel`

- Add field `private val permissionService: PermissionService = DefaultPermissionService` (initial value keeps single-user behavior until a role is known).
- Add to `MoneyCounterUiState` (defaults `true` for single-user compatibility):
  `canAddStock, canRegisterWriteoff, canEditStock, canCreateProduct, canEditProduct, canDeleteProduct, canRegisterExpense, canViewReports, canCreateSellerClosing, canCreateBranchClosing, canManageCatalog` (all `Boolean = true`), plus `canViewAllSellersDashboard = false`.
- Change `setSeller` to keep its signature (tests call it) but add a sibling:
  `fun setSellerContext(uid: String?, name: String?, role: Role?)` setting uid/name/role, updating `permissionService = forRole(role)`, and pushing the new booleans into `_uiState` via a `refreshPermissions()` private helper.
- Gate every mutation at the top with an early `return false` when the permission is missing:
  `addProduct`→canCreateProduct, `editProduct`→canEditProduct, `deleteProduct`→canDeleteProduct, `addStock`→canAddStock, `registerWriteoff`→canRegisterWriteoff, `recordExpense`→canRegisterExpense, and catalog mutators (`addUnit/editUnit/deleteUnit`, `addCurrency/editCurrency/deleteCurrency`, `addDenomination/editDenomination/deleteDenomination`, `moveDenominationUp/Down`) →canManageCatalog.
- Do NOT gate `saveCount`, `registerCreditSale`, `recordCollection` (SELLER keeps them).
- `AppContext` is the session context; keep a `private var context = AppContext("")` updated by `setSellerContext`, exposed via `val appContext: AppContext` getter (consumed in plan 019).

**Verify**: compile.

### Step 5: Update `MainActivity.kt`

Change the `LaunchedEffect` (line 92) to also pass the role:
`viewModel.setSellerContext(profile?.uid, profile?.displayName, member?.role)`.
Keep using `viewModel` normally. (Member is already a param — line 76.)

**Verify**: compile.

### Step 6: UI gating — single source of truth = uiState booleans

- `StockScreen.kt`: replace `member?.role.mayRegisterWriteoff()`/`mayEditStock()` (lines 74–75) with `viewModel.uiState` values; add `canAddStock` gate — the ProductRow `onAddStock` (lines 121–123 / 523) must not be wired for `!canAddStock` (render the row without the add button). Keep `canDelete = canEditStock` (line 126) and merma `if (canRegisterWriteoff)` (line 165). You may keep the unused `member` param (used for other purposes) but gating no longer depends on it.
- `ReportsScreen.kt`: wrap the "Nuevo gasto" button (line 207) so it is shown only when `uiState.canRegisterExpense`.
- `MoneyCounterScreen.kt`: hide the Settings IconButton (line 108) when `!uiState.canManageCatalog` (keep Profile at line 115 always).

**Verify**: compile.

### Step 7: Tests

- Extend `RoleTest.kt`: update SELLER expectations (canAddStock false, canRegisterWriteoff false, canRegisterExpense false); add a full ADMIN matrix block; assert `Role.values()` still round-trips through storage; keep null-role single-user tests.
- Create `PermissionServiceTest.kt`: for each of the four roles assert the exact matrix from Step 1 (parametrize per role or write one test per role); assert `DefaultPermissionService` returns the documented defaults.
- Create `AppContextTest.kt`: construction, blank-uid rejection (`require` throws), `isSeller`.

**Verify**: `./gradlew testDebugUnitTest` → BUILD SUCCESSFUL, 246 + ~12 new tests, green.

## Test plan

New/changed files as in Step 7. Structural pattern: follow existing `RoleTest.kt`. Cover: per-role matrix, null-role fallback, storage round-trip, AppContext invariants.

## Done criteria

ALL must hold:

- [ ] `./gradlew testDebugUnitTest` green (246 + new tests)
- [ ] `./gradlew compileDebugKotlin assembleDebug` green
- [ ] `grep -rn "canRegisterCreditSaleAndCollect\|canAddStock\|canRegisterWriteoff" app/src/main/java/com/moneycounter/domain/Role.kt` shows the new matrix, and SELLER rows forbid stock mutations
- [ ] Every VM mutation listed in Step 4 returns `false` without its permission (verified by code read plus RoleTest/PermissionServiceTest)
- [ ] No files outside scope modified (`git status`)

## STOP conditions

Stop and report if:

- `StockScreen`/`ReportsScreen`/`MoneyCounterScreen` structure differs materially from the line references above.
- A test asserts behavior the owner did not authorize (e.g. SELLER able to merma — the master plan forbids it).
- Gating `recordExpense` for SELLER requires touching `GastosScreen` reachability in a way that breaks navigation.

## Maintenance notes

- This is a **behavioral change**: after this lands, SELLER loses Alta/Merma/GASTO/`Ajustes`. Confirm with the owner before release.
- `RecordExpense` gating is new — verify no seller flow depends on it.
- The `AppContext` added here is the seed for plan 019 (org/branch context) — do not re-create a second context model there.