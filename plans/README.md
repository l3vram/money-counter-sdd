# El Luiso — Status Maestro de Planes

> Unificado el 2026-09-10 contra `main @ 89d2081`. Todos los planes históricos de los runs
> anteriores fueron verificados contra el código real, consolidados aquí y eliminados (`git rm`).
> Build: `./gradlew compileDebugKotlin` + `./gradlew test` → **BUILD SUCCESSFUL, 225 tests, 0 failures**.
> La vista histórica de cada plan vive en `git log`, no hay documentación duplicada.

## 1. Completado (todo merged a `main`)

### Core del contador
| Plan | Qué | Estado |
|------|-----|--------|
| 001-denomination-step-sync | Fix +/− sincroniza el campo editado con la cantidad real; `key={it.id}` en la lista | ✅ DONE |

### Historial 2026 (guardar + consultar + exportar)
| Plan | Qué | Estado |
|------|-----|--------|
| 001 | `SavedCount` + `JsonSavedCountRepository` (JSON v1→v3) | ✅ DONE |
| 002 | `saveCount()` + botón GUARDAR EN HISTORIAL | ✅ DONE |
| 003 | Lista/detalle/PDF — **evolucionó**: la lista es hoy `ReportsScreen` (movimientos) y el detalle es `MovementDetailScreen`; `HistoryScreen`/`HistoryDetailScreen` eliminados como dead code | ✅ DONE (reemplazado) |

### El Luiso Redesign (design system + reskin 8 planes)
| Plan | Qué | Estado | Notas |
|------|-----|--------|-------|
| 001 | Design tokens + `Theme.kt` | ✅ DONE | |
| 002 | Component kit (LuisoButton/Card/TopBar/Notice/TextField/SectionHeader/StatCard) | ✅ DONE | `LuisoEmptyState`/`LuisoCircle` no existen — reemplazados por `LuisoNotice` |
| 003 | Rename app a El Luiso + monograma | ✅ DONE | |
| 004 | Counter reskin | ✅ DONE | |
| 005 | Stock reskin | ✅ DONE | |
| 006 | Reports reskin | ✅ DONE | |
| 007 | Settings reskin | ✅ DONE | |
| 008 | Copy + empty states + verificación | ✅ DONE | |

### Stock / Inventory (5 planes)
| Plan | Qué | Estado |
|------|-----|--------|
| 001 | `Product.stock` + products.json v2 | ✅ DONE |
| 002 | Tab Stock + bottom nav + PRODUCTOS fuera de Ajustes | ✅ DONE |
| 003 | Main screen muestra stock + warning sobre-stock | ✅ DONE |
| 004 | Deducción de stock al guardar venta | ✅ DONE |
| 005 | Reporte de existencias + PDF/CSV | ✅ DONE |

### Reports & Multi-currency (7 planes)
| Plan | Qué | Estado | Notas |
|------|-----|--------|-------|
| 006 | `currencyId` en Product + SavedCount, JSON v3 | ✅ DONE | superado por `Product.prices` map (v4) |
| 007 | Currency en UI: dialog stock + filtro counter + quitar History icon | ✅ DONE | |
| 008 | Reportes 3ra pestaña + grouping + filtro | ✅ DONE | la selección/GENERAR RESUMEN fue retirada por Phase 2b (reemplazada por Cierres) |
| 009 | Reporte unificado merge + PDF/CSV | ✅ DONE | `UnifiedReportScreen` eliminado en 013; helpers sobreviven pero hoy son dead code |
| 010 | Redesign lista (sort Asc/Desc, day totals) + fix USD | ✅ DONE | |
| 011 | Compact nav 64-72dp + fontScale cap 1.2 + lista minimal | ✅ DONE | delete (ELIMINAR) fue retirado junto al modo selección |
| 012 | Fix selection action bar | ✅ OBSOLETO | el modo selección ya no existe |

### Product Multi-currency prices (5 planes)
| Plan | Qué | Estado |
|------|-----|--------|
| 001 | `Product.prices: Map<currencyId, ProductPrice>` + JSON v4 + auto-merge | ✅ DONE |
| 002 | `productsWithPrice` + counter badge multi-moneda | ✅ DONE |
| 003 | Dialog stock por-moneda `ProductPrice` | ✅ DONE |
| 004 | Selector de moneda en reporte de existencias | ✅ DONE |
| 005 | Currency visible en reports | ✅ DONE |

### Accounting Ops — Fase 2 (+ Phase 2b, 13 planes)
| Plan | Qué | Estado |
|------|-----|--------|
| 001 | User profile view + avatar top-bar + logout + CTA productos→stock | ✅ DONE |
| 002 | Glossario terminológico + `TermInfo` ℹ️ | ✅ DONE |
| 003 | Baja por merma (`InventoryWriteoff` + valorizada) | ✅ DONE |
| 004 | Venta a crédito / fiado (`Receivable`) | ✅ DONE |
| 005 | Cobro (`Payment`, settle deuda) | ✅ DONE |
| 008 | Movement journal + repo + migración desde stores legados | ✅ DONE |
| 009 | Fix fiado (siempre visible, sin COMPLETED) + rutear todo al journal | ✅ DONE |
| 010 | Historial de movimientos (badges por tipo) | ✅ DONE |
| 011 | Gastos + Alta/Entrada stock-in | ✅ DONE |
| 012 | Cierres ("Cerrar el día" + manual, netCash, closingId, PDF/CSV) | ✅ DONE |
| 013 | Cleanup journal-only (borra UnifiedReportScreen + HistoryDetailScreen, −1305 líneas) | ✅ DONE |

### Phase 3 — Fundación multi-tenant (2 slices)
| Plan | Qué | Estado |
|------|-----|--------|
| slice-01 | Dominio `Role` (SELLER/OWNER/SUPERUSER + permission matrix) + `Organization`/`Branch`/`Member` | ✅ DONE @ `0a4455e` |
| slice-02 | `FirestorePaths` + `FirestoreMappers` + `firestore.rules` multi-tenant (emulator 27/27) | ✅ DONE @ `0a4455e` |

### Iteración funcional 2026-09-10 (merged `89d2081`)
| Qué | Estado |
|-----|--------|
| Alta de stock (+) para vendedor: `AddStockDialog` + `addStock()` en VM, registra ALTA en journal | ✅ DONE |
| Gastos multi-moneda: selector de moneda en `GastosScreen` + `recordExpense(currencyId)` | ✅ DONE |
| Nueva pestaña **Cierres** en bottom nav (4 tabs) + botón CIERRES retirado de Historial | ✅ DONE |
| Copy: "Contador de dinero" → "El Luiso" en Counter + Login | ✅ DONE |

## 2. Pendiente

### Diseños no ejecutables (requieren planning pass)
| Doc | Qué | Estado |
|-----|-----|--------|
| `006-roles-shared-firestore-DESIGN.md` | Roles + shared Firestore + web admin. Solo el sub-plan 1 (domain + rules) está hecho | DESIGN → planificar |
| `007-multi-branch-scaling-DESIGN.md` | Multi-business/branch a escala | DESIGN → deferido |

### Deuda técnica / mejoras
| Item | Qué | Detalle |
|------|-----|---------|
| Dead code `uniteCounts`/`exportUnited` | `ReportAggregation` + overloads PDF/CSV ya no son llamados por ninguna pantalla | conectar o eliminar |
| `LuisoButton` 40dp vs 48dp | Touch target bajo el mínimo a11y del design kit | subir a 48dp |
| Permission matrix sin conectar | `Role.canDecreaseStock()`/`canManageAccounts()`/`canViewAllSellersDashboard()` no se usan fuera de `Role.kt` | conectar al UI cuando llegue Roles |
| Firestore repos | `FirestorePaths`/`FirestoreMappers` existen pero no hay repositorios reales (todo es `Json*`) | parte de fase 3, sub-plan 2 |

## 3. Riesgos / decisiones retiradas (registro)

- **Modo selección + GENERAR RESUMEN + ELIMINAR** (reports-currency 008/009/012): retirado en Phase 2b; el reporte unificado se reemplazó por **Cierres**. Si el owner quiere re-restaurarlo sobre el modelo `Movement`, es un work item nuevo.
- **Migrar todo JSON a Firestore**: rechazado — viola offline-first; solo entidades compartidas (stock, catálogo) van al cloud, en Phase 3.
- **Superuser CRUD dentro del APK**: rechazado por el owner — se usa un web admin serverless separado.
- **Nota de crédito/débito**: deferido (devoluciones/ajustes post-venta).
- **Cobro parcial (fiado en cuotas)**: deferido — COBRO salda completo.