# El Luiso — Status Maestro de Planes

> **Si retomás el trabajo, leé primero `docs/ESTADO-Y-PASOS.md`**: qué estamos haciendo,
> las decisiones tomadas, los cambios ya aplicados en Appwrite, el orden de los pasos y una
> sección de trampas donde cada entrada costó un diagnóstico equivocado.
>
> ⚠️ **Riesgo activo**: producción corre código del webadmin que sólo existe en `plan/033`,
> no en `main`. Desplegar desde `main` revertiría los arreglos. Ver §2 de ese documento.

> **Rama activa**: `feature/multi-tenant` — commit `cfd8809`.
> Rama desviada de `main @ 1069419`. Regresión verde: **424 tests, 0 fallos** (2026-09-12).
>
> Fecha: 2026-09-12 | Runs: `2026-09-11-multi-tenant-foundation` (017-024) · onboarding (025-029)

---

## Multi-Tenant Foundation (plans 017–024)

### Execution order & status

| # | Plan | Título | Effort | Deps | Status |
|---|------|--------|--------|------|--------|
| 017 | `017-branch-baseline` | Baseline verde en feature/multi-tenant | S | — | ✅ DONE |
| 018 | `018-permission-service` | PermissionService + rol ADMIN + SELLER read-only (defensa en profundidad) | M | 017 | ✅ DONE (4d9edac) |
| 019 | `019-tenant-context-bootstrap` | Contexto de tenant — org/branch bootstrap local + resolución de contexto | M | 018 | ✅ DONE (428502b) |
| 020 | `020-ledger-scoping` | Scoping de ledger — Movement/Closing con orgId/branchId + filtros historial por rol | L | 019 | ✅ DONE (1d646e2) |
| 021 | `021-stock-model` | Modelo StockItem + StockRepository + backfill (compat con Product.stock) | L | 020 | ✅ DONE (340f07e) |
| 022 | `022-stock-dual-write` | Escrituras de stock duales — venta/alta/entrada/merma/ajuste sincronizan Product.stock + StockItem | M | 021 | ✅ DONE (plan/022 @ dceba36, 339 tests) |
| 023 | `023-closing-scope` | Cierres por rol — ClosingScope SELLER/BRANCH + creación gateada | M | 022 | ✅ DONE (4b13d17) |
| 024 | `024-isolation-tests` | Aislamiento de tenants — tests cross-tenant/cross-branch/escalada | M | 023 | ✅ DONE (2c3665a) |

**Waves** (sequential — heavy overlap en MoneyCounterViewModel.kt + Screens):
```
W1: [017]
W2: [018]
W3: [019]
W4: [020]
W5: [021]
W6: [022]
W7: [023]
W8: [024]
```

**Alcance definido (decisión 2026-09-11):** Fundación de tenants (FASE 1–4/6/7 del plan maestro). 
**Deferido:** Owner Dashboard (FASE 8–9), sync manager real (FASE 13), performance/pagination (FASE 14).

## Onboarding de cuentas + Web Admin (plans 025–029)

Base: `feature/multi-tenant @ 2c3665a` (375 tests). GATE A aprobado 2026-09-12.
Decisiones del dueño: rol elegido en el registro · DUEÑO captura negocio+sucursales · pass temporal generada (visible + en el WhatsApp al superuser) · Web admin React+TS+Vite en Appwrite Sites + Function (SUPERUSER-only), alineado con `el_luiso_role_plans/SUPERUSER-PLAN.md`.

| # | Plan | Título | Deps | Status |
|---|------|--------|------|--------|
| 025 | `025-signup-flow` | Registro con rol + datos de negocio + pass temporal + signups + WhatsApp | — | ✅ DONE (f83d0dc) |
| 026 | `026-forced-password-change` | Cambio forzado de contraseña en el primer login | 025 | ✅ DONE (4f27e36) |
| 027 | `027-web-admin-sites` | Web admin React+TS+Vite en Appwrite Sites + Function SUPERUSER | — (paralelo 026) | ✅ DONE (a41e8ca, merge 9dce7d3) |
| 028 | `028-post-approval-sync` | Sync post-aprobación — poblar config local org/branches cloud | 026 + 027 | ✅ DONE (cfd8809) |
| 029 | `029-qa-regression-docs` | QA E2E + regresión + docs + README | 028 | ✅ DONE (2026-09-12; verificado abajo) |

**Infra Appwrite LIVE (2026-09-12):** tablas `signups` (id=uid, role, businessName, branches[], mustChangePassword, status), `orgs` (name, whatsappNumber, status), `branches` (orgId, name, status), `settings` (row `app`, superuserWhatsapp), `users` (access PENDING/APPROVED), `members` (orgId, role, branchIds). Function `admin` (node-18, deployment `6aa4c515687c4d9d7361` ready, smoke whoami→403 OK). Site `admin-web`: **https://6aa4cb0a8f6a4c30a83f.appwrite.network** (deployment `6aa4cb0a3ae416106f82`; endpoint/proyecto/function-id horneados). Plataforma Web `web-admin-site` con el hostname del site. Pendiente del dueño: fila `members/{uid}` con role SUPERUSER (tabla vacía) + `settings.superuserWhatsapp`.

**Verificación 029 (2026-09-12):** `testDebugUnitTest` 424 tests / 0 fallos · `compileDebugKotlin`+`assembleDebug` OK (APK 18.6 MB) · caja intacta (025-028 no tocan Money/Movement/MoneyCounterViewModel) · web admin HTTP 200 + guard function 403 · script de release: `docs/onboarding-e2e.md`.

---

## Permisos offline + preparación de inventario compartido (plans 030–031)

Base: `feature/multi-tenant @ cfd8809` (424 tests). Auditoría 2026-09-12. **GATE A pendiente.**

Hallazgo que origina 030: el rol de sesión se resuelve por red y *cualquier* fallo — incluida la falta de conectividad — degrada a "sin membresía", que en `DefaultPermissionService` significa **todos los permisos**. Siendo la app offline-first, un SELLER sin señal opera con poderes de OWNER. Evidencia: `AppwriteMembershipRepository.kt:32-36` (catch que emite `null`), `MainActivity.kt:91-94` (`LaunchedEffect` keyeado sólo por uid, nunca re-lee el rol), `PermissionService.kt:33-52` (Default concede todo). Los 424 tests pasan porque nadie cubre este cableado.

| # | Plan | Título | Priority | Effort | Deps | Status |
|---|------|--------|----------|--------|------|--------|
| 030 | `030-role-fail-closed` | Rol cacheado localmente + no degradar por error de red + re-cableado | P1 | M | — | ✅ DONE en `plan/030` (`ff98b3f`, 444 tests) — **Gate B pendiente** |
| 033 | `033-no-membership-no-access` | Sin membresía no se entra (fail-closed) + cerrar tablas de tenant | **P0** | M | 030 | 🔄 pasos 1–6 DONE en `plan/033` (460 tests) — **paso 7 (dispositivo) pendiente del dueño** |
| 039 | `039-apply-movement-function` | La Function `applyMovement`: autoriza, es idempotente y aplica el delta de stock en una transacción | P1 | L | 038 (sólo el contrato) | TODO |
| 038 | `038-movement-carries-product-id` | Cada línea de movimiento lleva su `productId` — sin eso el diario no puede derivar stock | P1 | M | — | TODO |
| 037 | `037-single-authorization-source` | `createClosing` autoriza por el `permissionService`, no por el UiState | P1 | XS | 036 | ✅ DONE en `plan/037` (509 tests, sin cambio) |
| 036 | `036-initial-state-honesty` | El I/O fuera del hilo principal + estado de carga + los permisos por defecto fallan cerrado | P1 | S/M | 032 | ✅ DONE en `plan/036` (509 tests) — **Gate B pendiente**. La revisión encontró que `createClosing` autoriza leyendo el UiState: la ventana era escalada real. Follow-up anotado |
| 035 | `035-atomic-approval` | Aprobar un registro en **una** transacción + primer harness de tests de la Function | P2 | M | — | ✅ DONE en `plan/035` (25 tests de la Function, los primeros) |
| 034 | `034-offline-session` | Sesión offline que sobrevive + un 401 que expulsa + indicador de "sin conexión" | P1 | M | 033 | ✅ DONE en `plan/034` (507 tests) — dueño verificó en dispositivo el 14/09: arranque sin red OK, cartel OK; la expulsión inmediata por 401 se aceptó como limitación (ver §9.11bis del handoff) |
| 032 | `032-suspend-repositories` | Interfaces de repositorio `suspend` (habilita impl cloud) | P1 | M | 030 | ✅ DONE en `plan/032` (507 tests, sin cambio de conteo) — **Gate B pendiente** |
| 031 | `031-repo-cleanup` | Rescatar prosa del glosario y podar worktrees/ramas obsoletos | P3 | S | — | TODO |

**Waves**:
```
W1: [030]        seguridad — bloquea todo lo demás
W2: [033]        seguridad — cierra el fail-open permanente; la mitad de base YA está aplicada
W3: [034]        offline: arrancar sin red (hoy la app no abre sin conexión)
W4: [032, 031]   032 refactor estructural · 031 limpieza (archivos disjuntos)
```

**Decisión del dueño (2026-09-12):** fail-closed mediante **rol cacheado localmente** — offline se usa el último rol conocido en vez de conceder todo; las instalaciones legacy sin rol cacheado conservan el comportamiento actual (`DefaultPermissionService` permisivo). No se endurece `DefaultPermissionService` en este plan.

**Decisiones del dueño (2026-09-14) → plan 034:** la sesión offline **no expira** — sin red el usuario sólo ve lo que ya tenía, nada se refresca y sus operaciones quedan pendientes, así que no hay nada que ganar bloqueándolo. Lo que sí: **un 401 lo expulsa** (sesión revocada, cuenta borrada, acceso quitado), y eso es lo que hace aceptable lo anterior, porque la revocación surte efecto en cuanto el dispositivo vuelve a ver la red. Y el estado desconectado tiene que verse **siempre**, en todas las pantallas. Además se subió la duración de sesión del proyecto al máximo de Appwrite (1 año; no existe "para siempre").

**Decisión del dueño (2026-09-12, revierte lo anterior) → plan 033:** no hay instalaciones legacy que proteger, así que `DefaultPermissionService` **sí** se endurece: sin membresía usable no se entra a la app — **ni como SELLER** — hasta que el SUPERUSER asigne negocio, sucursal y rol. Además el SUPERUSER sigue siendo una fila `members` (los labels de Auth fueron considerados y rechazados); Luis usa otra cuenta para probar roles de negocio.

**Cambios en Appwrite ya aplicados (2026-09-12, fuera de plan):** `members/{uid de Luis}` con `role SUPERUSER` + `orgId "platform"`; tabla `members` pasada a `$permissions: []` + `rowSecurity: true` (antes `read("users")`, o sea que cualquier usuario autenticado leía el rol y el orgId de cualquier otro). `webadmin/function/src/index.js` ajustado para escribir la fila con `read("user:<uid>")` — **commiteado, sin desplegar**. Hasta desplegarlo, aprobar un registro crea una fila que la app no puede leer. `users`, `orgs` y `branches` siguen con `read("users")`: los cierra el plan 033.

### Diseño en paralelo (no ejecutable)

`advisor-plans/008-shared-inventory-DESIGN.md` — arquitectura de F2, **decidida por el dueño el 2026-09-12**: stock server-authoritative (es el recurso en contención); movimientos y cierres local-first con subida diferida (son por usuario; la copia en server existe para el OWNER).

**Idea central:** el movimiento es la unidad de verdad y el portador del delta de stock; el servidor lo aplica atómica e idempotentemente, y **ningún cliente escribe jamás una cantidad de stock**. Encaja con el código existente: `MovementType.affectsStockSign()` ya codifica la dirección por tipo, los ids son `UUID.randomUUID()` (clave de idempotencia lista) y `recordMovement()` (`MoneyCounterViewModel.kt:971`) es el embudo único de escritura.

**Mecanismo:** Function `applyMovement` = autorizar (rol + `branchIds`, la frontera de seguridad real, §42) → insert idempotente en `movements/{movement.id}` con `stockApplied=false` → deltas atómicos sobre `stock/{branchId}_{productId}` → marcar `stockApplied=true`. Una caída entre pasos deja el movimiento sin aplicar y el reintento lo completa: no se pierde ni se aplica dos veces. Reconciliador programado como red de seguridad.

Orden de desarrollo (§6 del diseño): 1 permisos · 2 repos `suspend` · 3 schema+Function · 4 stock server-read · 5 outbox · 6 cierres · 7 pull OWNER+paginación · 8 selector de sucursal · 9 retirar `Product.stock`. Los pasos 1 y 2 son los planes 030 y 032. Quedan 3 preguntas abiertas (§8) y 2 verificaciones técnicas pendientes (§7).

---

## Historial completado (merged a `main`)

### Core del contador
| Plan | Qué | Estado |
|------|-----|--------|
| 001-denomination-step-sync | Fix +/− sincroniza el campo editado con la cantidad real; `key={it.id}` en la lista | ✅ DONE |

### Historial 2026 (guardar + consultar + exportar)
| Plan | Qué | Estado |
|------|-----|--------|
| 001 | `SavedCount` + `JsonSavedCountRepository` (JSON v1→v3) | ✅ DONE |
| 002 | `saveCount()` + botón GUARDAR EN HISTORIAL | ✅ DONE |
| 003 | Lista/detalle/PDF — `ReportsScreen`/`MovementDetailScreen` (screens legacy eliminadas como dead code) | ✅ DONE |

### El Luiso Redesign (design system + reskin 8 planes)
| Plan | Qué | Estado |
|------|-----|--------|
| 001–008 | Design tokens, component kit, rename app, counter/stock/reports/settings reskin, copy | ✅ DONE |

### Stock / Inventory (5 planes)
| Plan | Qué | Estado |
|------|-----|--------|
| 001–005 | Product.stock, stock tab, warning sobre-stock, deducción, reporte existencias | ✅ DONE |

### Reports & Multi-currency (7 planes)
| Plan | Qué | Estado |
|------|-----|--------|
| 006–012 | Multi-currency, reports, refactor compact nav, selection fixes | ✅ DONE |

### Product Multi-currency prices (5 planes)
| Plan | Qué | Estado |
|------|-----|--------|
| 001–005 | prices map, counter badge, dialog stock, selector currency | ✅ DONE |

### Accounting Ops — Fase 2 (13 planes)
| Plan | Qué | Estado |
|------|-----|--------|
| 001–013 | Profile, glossario, merma, fiado, cobro, movement journal, historial, gastos/altas, cierres, cleanup journal | ✅ DONE |

### Phase 3 — Fundación multi-tenant (2 slices)
| Plan | Qué | Estado |
|------|-----|--------|
| slice-01/02 | Dominio Role/Org/Branch/Member + FirestorePaths/mappers/rules multi-tenant | ✅ DONE |

### Iteración 2026-09-10
| Qué | Estado |
|-----|--------|
| Alta de stock para vendedor, gastos multi-moneda, tab Cierres, copy El Luiso | ✅ DONE |

### Iteración 2026-09-11 (feature/cobro-con-productos)
| Qué | Estado |
|-----|--------|
| Cobro con productos, fix teclado | ✅ DONE 242 tests |

### Iteración 2026-09-11 (feature/historial-productos-signos)
| Qué | Estado |
|-----|--------|
| COBRO lleva productos, historial con concepto+productos, señales +/−, TOTAL sign-aware | ✅ DONE 246 tests |

### Iteración 2026-09-11 (feature/pendientes-menores)
| Qué | Estado |
|-----|--------|
| Stock solo OWNER borra, avatar photoUrl, LuisoButton 48dp | ✅ DONE 246 tests |

---

## Pendiente (no formateado como plan aún)

| Doc | Qué | Estado |
|-----|-----|--------|
| `006-roles-shared-firestore-DESIGN.md` | Roles + shared Firestore + web admin. Solo domain+rules hecho | DESIGN → planificar |
| `007-multi-branch-scaling-DESIGN.md` | Multi-business/branch a escala | DESIGN → deferido |

## Decisiones registradas / riesgos

- **Modo selección + GENERAR RESUMEN** — ELIMINADO definitivamente.
- **Migrar Firebase → Appwrite.io** — hecha (auth/access/membership; datos operativos siguen offline-first).
- **Migrar JSON completo a Firestore** — rechazado (violates offline-first).
- **Superuser CRUD dentro del APK** — rechazado; se usa web admin separado.
- **Cobro parcial (fiado por partes)** — NO se hace (decisión dueño 2026-09-11).
- **SELLER alta+merma — COMPORTAMIENTO CAMBIA en plan 018**: SELLER pierde Alta y Baja por Merma (ahora OWNER/ADMIN). Confirmado por el dueño el 2026-09-11.
