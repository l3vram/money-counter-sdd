# Plan 031: Rescue the glossary prose, then prune stale worktrees and branches

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat cfd8809..HEAD -- GLOSSARY.md`
> If `GLOSSARY.md` changed since this plan was written, compare it against the
> "Current state" excerpt before proceeding; on a mismatch, STOP.

## Status

- **Priority**: P3
- **Effort**: S
- **Risk**: LOW (documentation + git housekeeping; no source code changes)
- **Depends on**: none (but run it *after* 030 lands, so the working tree is quiet)
- **Category**: tech-debt
- **Planned at**: commit `cfd8809`, 2026-09-12

## Why this matters

The repo carries 12 git worktrees under `/private/var/folders/.../T/` (the macOS
temp directory, which the OS may purge) and ~22 stale branches from completed
agent runs. All of the `plan/018`–`plan/028` branches are fully contained in
`feature/multi-tenant`, and every worktree is clean, so pruning them loses
nothing and makes `git branch` readable again.

One exception: `worktree-agent-a121e29e83a373461` holds 2 commits that are *not*
contained. Its code (the `TermInfo` composable) was integrated by other means and
is present in the current branch, but its `GLOSSARY.md` is richer — 28 lines vs
17 — with prose the current version dropped. Rescue that prose first, then prune.

## Current state

`GLOSSARY.md` today (17 lines) opens with a one-line intro and closes with a
single-line note:

```markdown
# Glosario de terminología contable

Términos correctos de contabilidad usados en la app, con su nombre viejo (heredado) como referencia.

| Término correcto | Nombre viejo en la app | Qué es |
...
Nota: *"Nota de crédito"* = devolución/descuento posterior; *"Nota de débito"* = cargo extra posterior. Ambas quedan para una fase futura (devoluciones).
```

The **markdown table between them is identical in both versions** and must not be
touched. Only the intro paragraph and the closing note differ.

Recover the richer version with:

```
git show worktree-agent-a121e29e83a373461:GLOSSARY.md
```

Worktree and branch inventory (verified 2026-09-12):
- 12 worktrees: the repo root plus `mc-wt-018` … `mc-wt-029` under
  `/private/var/folders/bz/6r30lrbn7_3fj5wz16jlbv1c0000gn/T/opencode/`. All clean.
- `plan/018` … `plan/028` — all 11 verified contained in `feature/multi-tenant`.
- 11 `worktree-agent-*` branches — all contained except
  `worktree-agent-a121e29e83a373461` (2 commits: `93cd782`, `acb8ee9`).

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| List worktrees | `git worktree list` | 13 rows before, 1 after |
| Containment check | `git merge-base --is-ancestor <branch> feature/multi-tenant` | exit 0 = contained |
| Build (final sanity) | `./gradlew :app:assembleDebug` | exit 0 |

Run from the repo root, on branch `feature/multi-tenant`.

## Scope

**In scope**:
- `GLOSSARY.md` (modify — intro paragraph and closing note only)
- git worktree and branch removal (no file content)

**Out of scope** (do NOT touch):
- The markdown table inside `GLOSSARY.md` — identical in both versions.
- Any `.kt` file. `TermInfo` is already integrated; do not re-apply the agent
  branch's code commits.
- `feature/multi-tenant`, `main`, and any `feature/*` branch — only `plan/*` and
  `worktree-agent-*` branches are pruned.
- The `origin/*` remote branches.

## Git workflow

- Work directly on `feature/multi-tenant`.
- One commit for the glossary: `docs(glossary): recuperar prosa ampliada (plan 031)`.
- Branch/worktree pruning produces no commit.
- Do NOT push.

## Steps

### Step 1: Restore the richer glossary prose

Replace the one-line intro with the fuller version, and the one-line closing note
with the sectioned version, both taken verbatim from
`git show worktree-agent-a121e29e83a373461:GLOSSARY.md`. Intro becomes:

```markdown
El dueño de la app no es contador y quiere que la app use los términos
contables correctos, manteniendo los nombres viejos/familiares como
referencia. Esta tabla es la fuente autoritativa: la app la expone en
pantalla mediante botones ℹ️ (ver `TermInfo` en
`app/src/main/java/com/moneycounter/ui/components/Components.kt`).
```

Closing note becomes:

```markdown
## Nota sobre notas de crédito/débito

- **Nota de crédito**: devolución o descuento posterior a una venta ya
  registrada.
- **Nota de débito**: cargo extra posterior a una venta ya registrada.

Ambas quedan para una fase futura (devoluciones); no están implementadas
todavía.
```

Keep the table between them exactly as it is.

**Verify**: `git diff --stat GLOSSARY.md` → only `GLOSSARY.md` changed; `wc -l GLOSSARY.md` → 28.
Confirm the `TermInfo` path in the intro is real: `grep -n "fun TermInfo" app/src/main/java/com/moneycounter/ui/components/Components.kt` → one match. If it is not, fix the path in the prose to the real one rather than committing a broken reference.

Commit it.

### Step 2: Tag the unmerged agent branch before pruning anything

So the 2 loose commits stay recoverable by name rather than only via reflog:

```
git tag archive/termInfo-glossary worktree-agent-a121e29e83a373461
```

**Verify**: `git rev-parse archive/termInfo-glossary` → resolves to `93cd782`.

### Step 3: Remove the 12 temporary worktrees

```
git worktree remove <path>        # for each mc-wt-* path
git worktree prune
```

Use `git worktree list` to enumerate the paths. If a path no longer exists on
disk (the OS purged it), `git worktree prune` alone clears the stale entry.

**Verify**: `git worktree list` → exactly one row, the repo root.

### Step 4: Delete the contained branches

Only after step 3 (a branch checked out in a worktree cannot be deleted).

Delete `plan/018` … `plan/028` and every `worktree-agent-*` branch. Use
`git branch -d` (lowercase `-d`), which **refuses to delete anything not fully
merged** — that safety check is the point. For the one unmerged branch
`worktree-agent-a121e29e83a373461`, `-d` will correctly refuse; delete it with
`-D` **only because step 2 tagged it**.

**Verify**: `git branch | grep -cE "plan/0|worktree-agent"` → `0`, and
`git rev-parse archive/termInfo-glossary` still resolves.

### Step 5: Confirm nothing broke

**Verify**: `./gradlew :app:assembleDebug` → exit 0.

## Test plan

No new tests — this plan changes documentation and git metadata only. The
existing suite is the regression gate: `./gradlew :app:testDebugUnitTest` → exit
0 with the same test count as before this plan (no tests added or removed).

## Done criteria

- [ ] `wc -l GLOSSARY.md` → 28, and the term table is byte-identical to before
      (`git diff cfd8809..HEAD -- GLOSSARY.md` shows changes only outside the table)
- [ ] `git rev-parse archive/termInfo-glossary` resolves to `93cd782`
- [ ] `git worktree list` → exactly one row
- [ ] `git branch | grep -cE "plan/0|worktree-agent"` → `0`
- [ ] `git branch` still lists `main` and `feature/multi-tenant`
- [ ] `./gradlew :app:testDebugUnitTest` exits 0 with an unchanged test count
- [ ] `./gradlew :app:assembleDebug` exits 0
- [ ] `plans/README.md` status row for 031 updated

## STOP conditions

Stop and report back (do not improvise) if:

- `git merge-base --is-ancestor <branch> feature/multi-tenant` fails for any
  branch other than `worktree-agent-a121e29e83a373461` — that means a branch holds
  unmerged work this plan did not account for. **Delete nothing** and report which.
- `git branch -d` refuses to delete a branch other than
  `worktree-agent-a121e29e83a373461`. Do not reach for `-D`; report instead.
- A worktree turns out to have uncommitted changes (`git -C <path> status --porcelain`
  is non-empty, ignoring `build/` and `.gradle`). Report it; do not remove it.
- `GLOSSARY.md` does not match the "Current state" excerpt.

## Maintenance notes

- Future fleet runs will create new worktrees under the same temp path. Consider
  pointing them at a location inside the repo (or under `~`) so a purge cannot
  take uncommitted agent work with it.
- The `archive/termInfo-glossary` tag can be deleted once someone confirms the
  glossary prose is the only thing of value in those 2 commits.
- Not addressed here: the planning artifacts (`PLAN.md`, `docs/`,
  `el_luiso_role_plans/`, `plans/017-024`, `029`) remain untracked by the owner's
  decision on 2026-09-12. They exist only on this disk, with no backup.
