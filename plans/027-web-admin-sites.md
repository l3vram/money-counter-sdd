# Plan 027: Web admin (React + TS + Vite) + Appwrite Function, deploys on Appwrite Sites

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving on. If any "STOP
> conditions" occurs, stop and report — do not improvise.
>
> This plan writes the **code** (web admin + function). The Appwrite-side
> **infrastructure** (site, function resource, deployments) is provisioned by the
> orchestrator via the Appwrite MCP (sites_create/functions_create/deployments),
> NOT by this executor.
>
> **Drift check (run first)**: `git status` clean on base; there is no existing
> `webadmin/` directory on the base branch.

## Status

- **Priority**: P0
- **Effort**: L
- **Risk**: HIGH-ish (new web deployment surface) — mitigated by SUPERUSER-only guard in the function.
- **Depends on**: nothing app-side; runs parallel with plan 026.
- **Category**: feature / platform admin (aligns with `el_luiso_role_plans/SUPERUSER-PLAN.md`)
- **Planned at**: commit `2c3665a`, branch `feature/multi-tenant`, 2026-09-12

## Why this matters

Approving signups today is manual (Appwrite console). The owner wants the **superuser** to operate
the platform from a dedicated web admin deployed on **Appwrite Sites**, aligned with SUPERUSER-PLAN:
independent of Android, exclusive to SUPERUSER, structured as Org/User/Branch admin with the real
authorization enforced server-side (the function), never only in the UI.

## Scope

New directory `webadmin/` (both the site under `webadmin/app/` and the function under
`webadmin/function/`). Nothing else in the Android repo is touched.

### Backend — Appwrite Function (Node runtime, entrypoint `src/index.js`)

Expose a small HTTP-ish API via `Appwrite SDK` `Functions` execution (the site POSTs to the function
execution endpoint):

- Helper `isSuperuser(req)`: read `x-appwrite-user-id` header; fetch the `members` row with that id;
  return true only if `role == "SUPERUSER"`. Everything else → 403.
- Endpoints (action from JSON body):
  - `listSignups` (status filter, default PENDING)
  - `approve`: given signupId + optional overrides →
    1. if signup.role == OWNER, create `orgs` row (`name = signup.businessName`, status ACTIVE,
       createdAt) and one `branches` row per `signup.branches` (orgId, name, status ACTIVE);
    2. create `members/{signupId}` row with `orgId`, `role = signup.role`, `branchIds` (OWNER gets
       its own branches; ADMIN/SELLER get admin-chosen orgId + branchIds from the request);
    3. update `users/{signupId}` `access = "APPROVED"`;
    4. update `signups/{signupId}` `status = APPROVED`, `approvedAt`.
  - `reject`: update `signups/{signupId}` status REJECTED, `users/{signupId}` access stays PENDING
    (or set BLOCKED — keep PENDING so approve can still be re-done).
  - `listOrgs` / `listBranches` / `listUsers` (users JOIN view: email, displayName, access, signup role)
  - `resetPassword`: `users.updatePassword(userId, password)` (≥8 chars) + set
    `signups/{userId}` `mustChangePassword = true`
  - `setSettings`: update `settings/app` `superuserWhatsapp`
- Server SDK with the project's auto-generated per-execution API key (function is provisioned with
  the needed scopes by the orchestrator). Never log secrets or whole signups payloads.

### Frontend — React + TypeScript + Vite SPA (`webadmin/app/`)

- **Login**: Appwrite Web SDK `account.createEmailPasswordSession`. After login call the function
  `whoami` (checks SUPERUSER). Non-superuser → "Acceso denegado" screen.
- **Route guard**: all pages require a superuser session (redirect to /login).
- **Pages (single-level, no deep nesting — MVP)**:
  - Dashboard: counts (signups pending, orgs, branches, users) via `listOrgs/listBranches/listUsers`.
  - Signups: list PENDING with email, role solicited, business, branches; **Approve** button
    (ADMIN/SELLER approve requires choosing orgId+branchIds from org/branch lists), **Reject** button.
  - Users: list + **Reset password** (prompt for a ≥8 temp password); shows must-change badge.
  - Organizations / Branches: read-only lists (Org: name/whatsapp/status; Branch: org/name/status).
  - Settings: edit `superuserWhatsapp`.
- Config via `import.meta.env`: `VITE_APPWRITE_ENDPOINT` = `https://fra.cloud.appwrite.io/v1`,
  `VITE_APPWRITE_PROJECT_ID` = `6aa332f40001072d0747`, `VITE_ADMIN_FUNCTION_ID`.
- No embedding of API keys; only Appwrite Web SDK client calls + function executions.
- Styling: plain CSS or Tailwind — keep it minimal; Spanish UI text (consistent with the Android app).

## Commands you will need (run inside `webadmin/app` and `webadmin/function`)

| Purpose | Command | Expected on success |
|---|---|---|
| Install | `npm install` | ok |
| Site build | `npm run build` | static bundle in `dist/` |
| Function | `node --check src/index.js` (or the project's lint) | ok |

## Steps

### Step 1: Scaffold `webadmin/app` (Vite React-TS)

No network-dependent templates: hand-write `package.json` (react, react-dom, vite, @vitejs/plugin-react,
typescript, appwrite). `build` → `vite build` with `outDir: dist`. Put `src/main.tsx`, `App.tsx`,
`api.ts` (function caller: POST `${ENDPOINT}/functions/${FUNCTION_ID}/executions` with the session
JWT in `Authorization` header + JSON body with action/params).

**Verify**: `npm install && npm run build` succeeds.

### Step 2: Function

`webadmin/function/package.json` (node runtime entrypoint `src/index.js`, `main` = `src/index.js`,
dependency `node-appwrite`), `src/index.js` implementing the endpoints above. Gate every endpoint at
the top with `isSuperuser`. Use the auto key via env (the orchestrator sets scopes) —
reference `node-appwrite` `Client` with `setKey(process.env.APPWRITE_FUNCTION_API_KEY)` fallback.
Include a `README` note describing expected env (endpoint, project).

**Verify**: `node --check src/index.js`.

### Step 3: Wire the login + guard + pages

Screens per Scope. Keep components flat and single-file where reasonable (App.tsx + pages/*.tsx).
No router dependency required (one view switch by state); email/pass login; whoami guard.

**Verify**: `npm run build` green.

### Step 4: Commit

Commit under `webadmin/` only: `feat(webadmin): panel React+TS+Vite y Function admin (plan 027)`.

## Done criteria

ALL must hold:

- [ ] `webadmin/app` builds to a static bundle (`npm run build`)
- [ ] `webadmin/function` passes `node --check` and gating logic is SUPERUSER-only at the top of each endpoint
- [ ] No Android source modified (`git status` shows only `webadmin/` additions)
- [ ] Deploy steps (executed by orchestrator via MCP) documented in `webadmin/README.md`:
      site + function provisioning + first approve flow smoke test

## STOP conditions

Stop and report if:

- Appwrite Web SDK (v25.2.0 line) can't open a session in the browser without extra config
  (CORS/domain setup) — note it, but do not stop the code from being written.
- `node-appwrite` version compatibility issues with the target runtime beyond standard pinning.

## Maintenance notes

- This lands the web admin as code; actual provisioning/deployment + smoke test is performed
  by the orchestrator with the Appwrite MCP after this branch is ready.
- Authorization principle (from SUPERUSER-PLAN): the frontend only controls UX; the function is the
  real enforcement point.
- PENDING signups count on the Dashboard uses `listSignups`; pagination is deferred (superuser-scale volume).