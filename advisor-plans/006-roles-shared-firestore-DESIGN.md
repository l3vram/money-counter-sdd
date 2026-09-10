# Design 006: Roles + shared data (Firestore) + separate web admin — Phase 3 architecture

> **This is a DESIGN document, not an executor plan.** It defines the target architecture and the
> sub-plans a later planning pass must produce. Do not dispatch an executor against this file. It
> needs its own Recon → Plan pass once Phase 2 (plans 003–005) has landed and the local entities
> exist to migrate.

## Status

- **Priority**: P2 (after Phase 2)
- **Effort**: XL (epic — will decompose into ~6–8 executor plans)
- **Risk**: HIGH (data-model pivot + first real cloud writes)
- **Depends on**: 002, 003, 004, 005 (the local entities become the migration source)
- **Category**: migration + feature

## Goal

Turn the single-user app into a **multi-tenant** one: a business (organization) with one or more
branches, staffed by sellers, overseen by an owner, with accounts governed by a superuser. Concretely
this delivers the owner's item 4:

- **4.1** Shared inventory across all sellers of a branch; reports kept **per seller**.
- **4.2** Owner has everything a seller has **plus** the ability to **decrement** stock (merma,
  manual adjustment) **plus** an owner dashboard: consolidated of all sellers with charts and
  year/month/week/day filters, shown as the owner's **first screen**.
- **4.4** A **superuser** approves new accounts and manages companies/roles — in a **separate web
  admin** (owner's decision, 2026-09-09), not inside the Android app.

## The pivot: single-tenant → multi-tenant

Today `users/{uid}` privately owns everything (see `firestore.rules`). That cannot express "two
sellers share one stock." The model must become organization-centric:

```
organizations/{orgId}                      { name, ownerUid, whatsappNumber, createdAt }
  branches/{branchId}                       { name }
    catalog/products/{productId}            ← shared config (name, unit, prices)
    catalog/currencies/{id}, denominations/{id}, units/{id}   ← shared config
    stock/{productId}                       { qty }            ← SHARED, atomic increments
    sales/{saleId}                          { ...SavedCount, sellerUid, sellerName }
    writeoffs/{id}                          { ...InventoryWriteoff, sellerUid }   (owner-only create)
    receivables/{id}                        { ...Receivable, sellerUid }
    payments/{id}                           { ...Payment, sellerUid }
members/{uid}                               { role: SELLER|OWNER, orgId, branchIds: [..] }
users/{uid}                                 { email, displayName, access, role, orgId }
```

Superuser identity = a **Firebase custom claim** (`superuser: true`) — never a client-writable field.

## What moves to the cloud vs. what stays local/per-seller

| Data | Source of truth | Why |
|---|---|---|
| Inventory (stock qty) | **Firestore** (`branches/{b}/stock/{p}`), atomic `FieldValue.increment` | Must be shared & race-safe across seller devices |
| Catalog (products/currencies/denominations/units) | **Firestore** (shared) | All sellers of a branch see the same catalog |
| Sales / writeoffs / receivables / payments | **Firestore**, tagged `sellerUid` | Per-seller reports + owner aggregation need them centrally, but each is authored by one seller |
| Active count (in-progress) | Local only | Ephemeral; never shared |

**Offline-first is preserved** by Firestore's on-device persistence: reads/writes hit the local cache
and sync when connectivity returns. This is why Firestore was chosen over a self-hosted backend — it
is serverless and offline-native. **Shared stock uses `FieldValue.increment(-qty)`** so two
concurrent sales never clobber each other; the SDK replays queued increments on reconnect.

## Repository abstraction (reuse, don't rewrite)

The `sync/SyncManager` stub and the existing repository **interfaces** are the seam. Each
`XRepository` gets a Firestore-backed implementation for the shared entities; the ViewModel keeps
talking to the interface. The local JSON impls remain as the **offline seed / fallback and the export
format**. This keeps the UI and domain untouched (honors the "don't break" principle).

## Roles & permissions (enforced by Firestore rules, mirrored in UI)

See the matrix in `REFINED-SPEC.md §5`. Key rule invariants:

- A member can read/write only within their `orgId` / `branchIds`.
- **Decrement of stock** (merma create, negative manual adjust) requires `role == OWNER`.
- `access`, `role`, `orgId`, and org/branch documents are writable **only** by a request whose token
  carries `superuser: true`. The Android client can never elevate itself (extends the current
  `firestore.rules` invariant).
- Sellers create sales/receivables/payments stamped with their own `sellerUid` (rule: the record's
  `sellerUid == request.auth.uid`).

## Owner dashboard (item 4.2)

A new first screen for `role == OWNER`: consolidated across all sellers of the branch, with charts
(totals over time) and filters by year/month/week/day. Reuses `ReportKeys`/`ReportAggregation`
grouping, fed by the branch's `sales` collection (no `sellerUid` filter = all sellers; add a per-
seller filter toggle). Sellers keep seeing only their own via a `where sellerUid == me` query.

## Superuser web admin (item 4.4) — separate deliverable

A **static SPA on Firebase Hosting** (serverless), authenticated with the same Google login, gated by
the `superuser: true` custom claim. Responsibilities:

- CRUD organizations & branches; set each org's WhatsApp number (the Android
  `AccessRequiredScreen`/`ContactConfig` reads this remotely instead of a hardcoded constant).
- List pending users; approve/block; assign `role`, `orgId`, `branchIds`.
- Cannot do more than the Firestore rules allow — the claim + rules are the real authority.

It is a **separate codebase in a web stack** (out of the Kotlin repo). Setting the `superuser` custom
claim itself requires a one-time admin action (Firebase Admin SDK / a trusted script) since claims
can't be set from a pure client — document this as a bootstrap step. Interim before the web admin
exists: approvals via **Firebase Console** (already supported).

## Migration strategy

1. Ship the org/branch/member model + rules + read paths behind the existing repository interfaces.
2. On an owner's first cloud login, **seed** their first branch's catalog + stock from their existing
   **local JSON** (one-time import). Local remains the export/backup format.
3. Flip shared entities (catalog, stock) to read from Firestore (cache-first); keep per-seller
   records writing to Firestore with `sellerUid`.
4. Verify offline behavior on a real device with airplane mode toggled mid-sale.

## Sub-plans this epic will decompose into (next planning pass)

1. Firestore data model + security rules (org/branch/member, superuser claim) + rules unit tests.
2. Repository Firestore implementations for shared catalog (behind existing interfaces).
3. Shared stock with atomic increments + offline queue + over-sell handling.
4. Per-seller sales/writeoffs/receivables/payments with `sellerUid` stamping + per-seller report queries.
5. Role gating in the Android UI (owner-only decrement; owner dashboard as first screen).
6. Owner dashboard (charts + year/month/week/day filters).
7. Local→Firestore seeding/migration + offline verification.
8. **Web admin** (separate repo): auth + claim gate, org/branch/user CRUD, WhatsApp-number config.

## Open questions to resolve before execution

- Onboarding: how does a brand-new seller get attached to an org/branch — self-select in-app then
  await superuser assignment, or fully assigned by the superuser from the WhatsApp request?
- WhatsApp number: per-organization (superuser-set) vs. a single global support number?
- Report retention/volume per branch (affects Firestore read cost — see design 007).
- Currency scoping: are currencies/denominations per-branch or per-org?
```
