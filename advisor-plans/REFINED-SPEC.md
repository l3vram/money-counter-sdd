# Refined Spec — El Luiso / Money Counter: Accounting Terminology, Inventory Operations & Roles

> Source of truth for the planning of this multi-phase effort. Negotiated with the owner on
> 2026-09-09. Written against commit `e32de39` (branch `main`). Executor plans live beside this
> file as `advisor-plans/NNN-*.md`. This document is the "why"; the plans are the "how".

## 0. Product context & principles

El Luiso is an **Android-only, offline-first** app for cash sales in Cuba. Sellers count cash by
denomination against a target (= sum of products sold). Today it is single-user, 100% local
(versioned JSON files in `filesDir`), with Google login + remote access control (PENDING/
APPROVED/BLOCKED) already layered around it via Firebase.

Non-negotiable principles carried into every phase:

- **Offline-first**: the app must be usable with no/poor connectivity; data always available.
- **Minimal infrastructure & data transfer**: no self-hosted backend for now; move as few bytes
  as possible.
- **Fast, uncluttered UI**: sales happen in cash, in the moment; screens must not slow the worker.
- **Do not break what works**: money math (`BigDecimal`, `Money.SCALE=2`), existing JSON formats
  and versions, existing screens/navigation stay intact unless a phase explicitly changes them.
- **Keep the path smooth**: each phase leaves clean extension points for the next.

## 1. Current state (recon, commit `e32de39`)

- **Stack**: Kotlin, Jetpack Compose + Material 3, `compileSdk 34`, `minSdk 26`. Single `:app`
  module. `MainActivity` hosts `MoneyCounterApp` with string-based nav (`currentScreen`:
  `counter`, `stock`, `report`, `settings`, `reports`, `summary`, `detail`, `profile`).
- **Architecture**: `MoneyCounterViewModel` → pure domain → JSON repositories (constructed
  directly in the VM, `MoneyCounterViewModel.kt:59-63`). No DI, no Room, no Retrofit.
- **Domain**: `Product(id, name, unit, stock: BigDecimal, prices: Map<currencyId, ProductPrice>)`;
  `SavedCount` (a completed cash count ≈ a **sale**) with `SavedCountItem` (denominations) and
  `SavedProductItem` (products sold). `Money.SCALE=2`, `Money.ZERO`, `Money.fromLong`.
- **Stock**: lives on `Product.stock`. Deducted in `MoneyCounterViewModel.saveCount()` via the
  static `applyStockDeduction(products, selections)` (`:596`); over-sell allowed (stock can go
  negative, "warn-and-allow").
- **Reports**: `ReportKeys.groupByMonthDay` (month→day grouping); `ReportAggregation.uniteCounts`
  (consolidated report over same-currency counts). Screens: `ReportsScreen`, `UnifiedReportScreen`,
  `HistoryDetailScreen`.
- **Persistence pattern (exemplar)**: `JsonSavedCountRepository` + `SavedCountJson` object —
  versioned root (`{"version":N,"history":[...]}`), atomic write via `.tmp` + `renameTo`, silent
  catch, tolerant `fromJson`. Every new local entity must mirror this pattern.
- **Auth/Access (done)**: `auth/` (Google via Credential Manager), `access/` (`AccessStatus`,
  `AppAccessState`, `FirestoreAccessRepository`, `UserProfileData`), `AuthenticationGate`,
  `LoginScreen`, `AccessRequiredScreen`, `ContactConfig` + `WhatsAppLauncher`, `firestore.rules`,
  and a `sync/` stub (`SyncManager`, `SyncState`).
- **Tests**: JUnit4 unit tests under `app/src/test/`, run with `./gradlew test`. ~108 passing.

## 2. Verdict on the existing "Google Login + Access Control + Cloud Sync Foundation" plan

That plan (root `plan.md — Google Login + Access Control + Cloud Sync Foundation.md`) is **~100%
already implemented**. Nothing material remains from it. Its limitation for the owner's real goal:
its data model is **single-tenant** — each `users/{uid}` privately owns its own subcollections.
The roles vision (shared stock, per-seller reports, owner-sees-all, multi-branch, superuser) is
**multi-tenant** and needs a different model (Phase 3). So the answer to "what's missing" is: not
that plan — the next one (roles + shared data).

## 3. Authoritative accounting glossary (owner asked for correct names)

The app's "conteo" is, in accounting terms, a **sale with a cash count (arqueo de caja)**. Correct
Spanish accounting names, with the app's old/familiar label kept as reference via small ℹ️ info
affordances on screen and a `GLOSSARY.md`:

| App (old) | Correct accounting name | What it really is |
|---|---|---|
| Conteo / `SavedCount` | **Venta** (comprobante) con **arqueo de caja** | A cash sale + physical cash breakdown by denomination |
| Objetivo (target) | **Importe a cobrar / Total de la venta** | Sum of products = amount to collect |
| Contado (denominations) | **Arqueo de caja** (efectivo recibido) | Physical count of cash |
| Faltante / Excedente | **Diferencia de caja** (faltante / sobrante) | Cash-drawer difference |
| Stock | **Inventario / Existencias** | On-hand quantities |
| Baja por merma *(new, item 1)* | **Ajuste de inventario por merma** (baja) | Inventory out **without a sale**, a loss |
| Salida con deuda / fiado *(item 2)* | **Venta a crédito → Cuenta por cobrar (CxC)** | Sale with no cash in; creates a right to collect |
| Liquidación de deuda *(item 3)* | **Cobro / Recibo de cobro** (settles the CxC) | Cash in that clears a receivable |
| Reporte unificado | **Cierre / Resumen consolidado** | Period consolidation |

Clarification the owner explicitly requested (credit/debit notes):

- **Merma is NOT a credit note** — it is an internal **inventory adjustment** (a loss), not a
  customer document.
- **Fiado = venta a crédito** → creates a **cuenta por cobrar**; collecting it = **recibo de cobro**.
- **Nota de crédito** = customer **returns** goods or is granted a **discount** after the sale
  (reduces what they owe / refunds money). **Deferred** to a later phase (returns).
- **Nota de débito** = a later **extra charge** (increases what they owe). **Deferred**.

## 4. Phased roadmap (owner chose: iterate simple → complex)

### Phase 1 — Accounting terminology layer  · plan 002
Glossary doc + correct on-screen labels + small ℹ️ info affordances (reusing the existing
`LuisoNotice` banner style). **No data-model or logic change.** Risk LOW.

### Phase 2 — Local inventory & credit operations  · plans 003, 004, 005
Built on the **current local model** (single user), but modeled as first-class entities +
JSON repositories that map 1:1 to Firestore subcollections later.

- **003 · Baja por merma (inventory write-off)**: reduces `Product.stock`, records a
  `InventoryWriteoff` with a **valued loss** (`quantity × unitPrice`), shown as a separate report
  section and summed at the end of the consolidated report. **No cash movement.**
- **004 · Venta a crédito (fiado → cuenta por cobrar)**: deducts stock like a sale but records a
  `Receivable(debtorName, amount, date, products, status=OPEN)` instead of cash. Appears in reports
  as outstanding debt.
- **005 · Cobro (settle receivable)**: on the main counter screen, list OPEN receivables, settle one
  → **adds that cash** to summaries, marks `SETTLED`. Does **not** touch stock (already deducted at
  sale). Depends on 004.

### Phase 3 — Roles + shared data (the big epic)  · design 006
Multi-tenant model in Firestore: organization → branch → members with roles. **Shared inventory +
shared catalog** move to Firestore (offline cache on, atomic `FieldValue.increment` for stock);
**sales/writeoffs/receivables/payments stay per branch, tagged with sellerUid + sellerName**;
reports per seller; **owner dashboard** aggregates all sellers. The **superuser lives in a separate,
lightweight web admin** (static SPA on Firebase Hosting, hitting the same Firestore with the same
security rules + a superuser custom claim) — CRUD of organizations/branches, approve/block users,
assign roles, set the WhatsApp number. This keeps the Android app lean/offline-first and gives the
admin proper large-screen CRUD; it is **serverless (no self-hosted backend)** but is a **separate
web codebase**. Rules — not the client — are the real authority. Requires its own planning pass —
see design 006. Interim: Firebase Console suffices for approvals until orgs/branches exist.

### Phase 4 — Multi-business / multi-branch at scale  · design 007
Owner + business + multiple branches, per-branch stock/sales, owner sees all branches with
year/month/week/day filters. Firestore data-model scaling and long-term fit. Deferred; see 007.

## 5. Roles & permissions (target, Phase 3)

| Capability | SELLER | OWNER | SUPERUSER |
|---|---|---|---|
| Register sales (counter), see own reports | ✅ | ✅ | — |
| Add / increase stock | ✅ | ✅ | — |
| **Decrease** stock (merma, manual adjust) | ❌ | ✅ | — |
| Register credit sale (fiado) & collect | ✅ | ✅ | — |
| View shared inventory (no decrement) | ✅ | ✅ | — |
| Owner dashboard: all sellers' consolidated + charts + filters | ❌ | ✅ | — |
| Approve/block accounts, assign role/org/branch, set WhatsApp number | ❌ | ❌ | ✅ |

Seller identity = Google `displayName` (already shown on the profile screen). Every per-branch
record (sale, writeoff, receivable, payment) is stamped with `sellerUid` + `sellerName`.

## 6. Data-model additions & their Firestore mapping (forward-compatible)

New local entities in Phase 2, designed to become branch subcollections in Phase 3:

```
InventoryWriteoff { id, at, productId, name, unit, quantity, unitPrice, lossValue, currencyId, reason? }
Receivable        { id, at, debtorName, amount, currencyId, products[], status(OPEN|SETTLED), settledAt? }
Payment           { id, at, receivableId, amount, currencyId }   // the "cobro" record
```

Phase-3 Firestore shape:

```
organizations/{orgId}
  branches/{branchId}
    catalog/{products|currencies|denominations|units}   ← shared config
    stock/{productId}         ← shared, FieldValue.increment
    sales/{id}                ← +sellerUid, sellerName
    writeoffs/{id}            ← +sellerUid (owner-only create)
    receivables/{id}          ← +sellerUid
    payments/{id}             ← +sellerUid
members/{uid}                 ← role, orgId, branchIds
users/{uid}                   ← email, displayName, access, role, orgId  (superuser flag/claim)
```

## 7. Non-goals / explicitly deferred

- Nota de crédito / nota de débito (returns, post-sale discounts, extra charges).
- Self-hosted backend, Cloud Functions, Room, Retrofit, DI frameworks, multi-module.
- Full data migration of all JSON to Firestore before Phase 3.
- Superuser CRUD **inside** the Android app — it lives in a separate web admin instead (Phase 3).

## 8. Definition of done (per phase, high level)

- **Phase 1**: `GLOSSARY.md` exists; screens show correct labels + working ℹ️ info; `./gradlew test`
  green; no logic/JSON change.
- **Phase 2**: merma reduces stock and shows valued loss in reports; fiado creates a receivable
  without adding cash; collection adds cash and settles the receivable; all three appear in the
  consolidated report; new JSON repos versioned & tolerant; unit tests for each; `./gradlew test`
  green; existing tests unchanged.
- **Phase 3 / 4**: defined in their design docs before execution.
```
