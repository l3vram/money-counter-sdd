# Design 007: Multi-business / multi-branch at scale — Phase 4 architecture

> **DESIGN document, not an executor plan.** Deferred until Phase 3 (design 006) lands. It answers
> the owner's item 4.3 and the explicit worry: *"no estoy seguro que Firestore me sirva para esto
> para siempre."*

## Status

- **Priority**: P3 (future)
- **Effort**: XL
- **Risk**: HIGH
- **Depends on**: 006 (roles + shared Firestore + web admin)
- **Category**: migration + direction

## Goal (item 4.3)

An owner can have **more than one branch** (and later more than one business). Stock and sales are
**independent per branch**. From the owner's first screen (dashboard) they can see **everything
across all their branches**, with year/month/week/day filters.

## Does Firestore scale for this? (the owner's question)

**For this domain: yes, comfortably.** The scale here is small — a handful of businesses, a few
branches each, a few sellers per branch, thousands of sale records per branch per year. That is well
within Firestore's serverless model, and it keeps the "no self-hosted backend" principle. The org →
branch → collection layout in design 006 already extends to N branches: an owner dashboard queries
across `organizations/{orgId}/branches/*/sales`.

The real scaling concerns are **read cost and dashboard latency**, not capacity:

- Aggregating a year of sales across many branches by reading every document is wasteful and slow.
  Solution: maintain **rollup documents** (daily/monthly per-branch totals) written alongside each
  sale (client-side increment, or a periodic recompute), and have the dashboard read rollups, not raw
  sales. This keeps reads O(periods) instead of O(sales).
- Keep offline-first intact: rollups are cached like any other document.

## When would Firestore stop being enough?

Only if the product grows well beyond this use case — e.g. cross-branch analytics over millions of
rows, heavy server-side reporting, or multi-tenant billing. If that day comes, the migration path is
**additive, not a rewrite**: introduce a thin read-model/export pipeline (e.g. scheduled export to a
warehouse) while Firestore stays the operational store. Nothing in Phases 1–3 blocks that. Document
this as an escape hatch; **do not build it now**.

## Data-model notes for Phase 4

- Branch is already a first-class node (`organizations/{orgId}/branches/{branchId}`) from design 006 —
  multi-branch needs no schema change, only UI to switch/aggregate branches and per-branch rollups.
- Owner dashboard: add a branch selector + an "all branches" aggregate view over rollups.
- Multi-business (one owner, several orgs) is a later increment: `members/{uid}.orgId` becomes a list,
  or membership moves to `organizations/{orgId}/members/{uid}`.

## Open questions

- Do sellers ever move between branches, and should their historical records follow them?
- Are prices/catalog shared across an owner's branches or independent per branch?
- Rollup granularity (day is usually enough; week/month derived from day).

## Explicitly deferred

Everything here. It exists so Phase 3 keeps the branch node clean and does not paint us into a corner.
No executor work until 006 is delivered and the owner confirms multi-branch is the next priority.
```
