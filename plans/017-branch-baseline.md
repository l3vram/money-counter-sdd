# Plan 017: Branch multi-tenant + baseline verde (puerta de entrada del run)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` (the orchestrator maintains the index; report "done").
>
> **Drift check (run first)**: `git diff --stat 1069419..HEAD -- app/`
> If anything changed in `app/` since this plan was written, STOP and report.

## Status

- **Priority**: P0
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: dx
- **Planned at**: commit `1069419`, 2026-09-11

## Why this matters

The whole run depends on a clean, green base: the branch `feature/multi-tenant`
must exist and every slice afterwards must be able to prove it did not break
the app by comparing against this baseline (246 tests, BUILD SUCCESSFUL). This
plan formalizes that baseline on the branch and is the gate every other plan
chains from.

## Current state

- Repo root: `/Volumes/ExtSSD/mac-storage/projects/money-counter-sdd` (Android app, Kotlin + Jetpack Compose + Material 3).
- Branch `feature/multi-tenant` created from `main` at `1069419`. Verified already by the orchestrator; if your worktree is on it, confirm.
- Verification commands (recon-verified, run from repo root of your worktree):

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Tests     | `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL, 246 tests, 0 failures |
| Compile   | `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL |
| APK       | `./gradlew assembleDebug` | BUILD SUCCESSFUL, APK produced |

## Scope

**In scope**: nothing to modify. Branch + verification only.

**Out of scope**: any source change. This plan MUST produce a zero-diff worktree.

## Git workflow

- Worktree branch name for this plan: `slice/017`.
- Commit nothing unless you actually changed a file (you should not).
- Do not push.

## Steps

### Step 1: Confirm branch and clean tree

`git branch --show-current` → `feature/multi-tenant` (or the worktree branch created from it).
`git status --porcelain` → only untracked `plans/` files and `PLAN.md …` allowed; no modified tracked files.

### Step 2: Run the full suite

`./gradlew testDebugUnitTest` → `BUILD SUCCESSFUL in …`.
Count tests from the JUnit XML:
`find app/build/test-results/testDebugUnitTest -name '*.xml' -exec grep -ho 'tests="[0-9]*"' {} + | grep -o '[0-9]*' | awk '{s+=$1} END {print s " tests"}'` → `246 tests`.

### Step 3: Compile and package

`./gradlew compileDebugKotlin assembleDebug` → `BUILD SUCCESSFUL`.

## Test plan

No new tests (verification-only plan). The 246 existing tests are the done gate.

## Done criteria

ALL must hold:

- [ ] `./gradlew testDebugUnitTest` → BUILD SUCCESSFUL, 246 tests
- [ ] `./gradlew compileDebugKotlin assembleDebug` → BUILD SUCCESSFUL
- [ ] `git status --porcelain` shows no modified tracked source files

## STOP conditions

Stop and report if:

- The test count differs from 246, or the build fails on a clean branch from `1069419`.
- `git diff --stat 1069419..HEAD -- app/` shows source changes.

## Maintenance notes

- Every later plan's drift check compares against `1069419`. If a plan bumps
  to a newer base SHA, it must state it in its header.