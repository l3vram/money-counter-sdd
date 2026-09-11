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

### Iteración 2026-09-11 (rama `feature/cobro-con-productos`)
| Qué | Estado |
|-----|--------|
| Cobro con detalle: al cobrar una deuda se muestran los PRODUCTOS y cantidades en la vista del contador (`FiadoProductsCard`, solo lectura) | ✅ DONE 242 tests verde |
| Botón cobrar: solo se ve cuando existen deudas abiertas por cobrar (se oculta si no hay fiados pendientes) | ✅ DONE |
| Fix teclado: `windowSoftInputMode` `adjustPan` → `adjustResize` (elimina hueco vacío entre campo y teclado al escribir cantidades/denominaciones) | ✅ DONE |
| Fix espacio residual teclado: ocultar bottom nav mientras el IME está visible → el contenido llega hasta el borde del teclado | ✅ DONE |

## 2. Pendiente

### Diseños no ejecutables (requieren planning pass)
| Doc | Qué | Estado |
|-----|-----|--------|
| `006-roles-shared-firestore-DESIGN.md` | Roles + shared Firestore + web admin. Solo el sub-plan 1 (domain + rules) está hecho | DESIGN → planificar |
| `007-multi-branch-scaling-DESIGN.md` | Multi-business/branch a escala | DESIGN → deferido |

### Deuda técnica / mejoras
| Item | Qué | Detalle |
|------|-----|---------|
| Avatar `LuisoAvatar` (Components.kt) | Con `photoUrl` de Google pinta la foto pero le superpone la inicial (ver `Components.kt`) | si hay foto mostrar SOLO la foto; si no hay, la inicial |
| Login Google en Cuba | Google Sign-In no funcionaba en Cuba (embargo: endpoints identidad bloqueados) | **RESUELTO por Appwrite email+password** (fase migración) |
| `LuisoButton` 40dp vs 48dp | Touch target bajo el mínimo a11y del design kit | subir a 48dp |
| Permission matrix sin conectar | `Role.canDecreaseStock()`/`canManageAccounts()`/`canViewAllSellersDashboard()` no se usan fuera de `Role.kt` | conectar al UI cuando llegue Roles |
| ELIMINAR en lote del Historial | El modo selección volvió con GENERAR RESUMEN pero sin batch-delete | opcional: `deleteMovements(ids)` + persistir journal |
| Señales +/- en reportes y cierres | Gasto/Merma deben verse como salida (`-`) y Venta/Cobro/Alta/Entrada como entrada (`+`) para identificarlas de un vistazo | aplicar a Historial + Cierres (y resumen) |
| Stock: solo OWNER borra | Un seller nunca puede borrar/eliminar nada del stock; solo el owner | gating en StockScreen (relacionado a `canEditStock`) |
| Cantidades en el Historial | En el listado de movimientos mostrar la cantidad junto al producto, p. ej. `Arroz 20 Lb` (hoy la fila solo muestra el nombre del primer producto) | `MovementRow` en ReportsScreen |
| Firestore repos | `FirestorePaths`/`FirestoreMappers` existen pero no hay repositorios reales (todo es `Json*`) | parte de fase 3, sub-plan 2 |

### Migración Firebase → Appwrite.io (en curso, rama `feature/appwrite`)
| Paso | Qué | Estado |
|------|-----|--------|
| SDK | `io.appwrite:sdk-for-android:25.2.0` (27.2.0 exige compileSdk 37/AGP 9.1 → se usó 25.2.0 + compileSdk 36) | ✅ DONE |
| Cliente | `Appwrite.init` (endpoint `https://fra.cloud.appwrite.io/v1`, project `6aa332f40001072d0747`) + `AppwriteHealth.ping()` (botón "Verificar conexión" en login) | ✅ DONE — verificado en emulador: "Conectado a Appwrite en 741 ms" |
| Auth | `AppwriteAuthRepository` email+password con auto-registro (`createEmailPasswordSession` → `user_not_found` → `account.create` → retry) | ✅ DONE |
| Access | `AppwriteAccessRepository` → tabla `users` (row id = uid; access PENDING/APPROVED/BLOCKED + perfil) | ✅ DONE |
| Membership | `AppwriteMembershipRepository` → tabla `members` (row id = uid; orgId/role/branchIds) | ✅ DONE |
| Cola de tablas | … | ⏳ PENDIENTE → Crear en consola Appwrite: **Database id `main`** con **tabla `users`** y **tabla `members`** (los createRow de la app crean los documentos; el admin pone `access=APPROVED`/`PENDING` y crea `members/{uid}` con `role`/`orgId`) |
| Plataforma Android | … | ⏳ PENDIENTE → Console > Settings > Add Platform > Android: package `com.moneycounter` + SHA-256 del `~/.android/debug.keystore` (debugCanonical) |
| Borrar legado | `FirebaseAuthRepository`/`FirestoreAccessRepository`/`FirestoreMembershipRepository` borrados; google-services plugin y deps Firebase fuera de Gradle | ✅ DONE |
| Verificación local | 242 tests verde + `assembleDebug` OK + emulador (login + ping) | ✅ DONE |

## 3. Riesgos / decisiones retiradas (registro)

- **Modo selección + GENERAR RESUMEN** (reports-currency 008/009/012): re-restaurado sobre el modelo `Movement` en `feature/reportes-seleccionables` (`uniteMovements` + modo selección en Historial + `UnifiedReportScreen` + export PDF/CSV). ELIMINAR en lote sigue pendiente.
- **Migrar Firebase → Appwrite.io** (rama `feature/appwrite`): auth identity (login/access/membership) → Appwrite Cloud fra (`email+password`, PENDING→APPROVED igual al modelo de `users/{uid}`/`members/{uid}`)). Los datos operativos (journal JSON local) siguen offline-first.
- **Migrar todo JSON a Firestore**: rechazado — viola offline-first; solo entidades compartidas (stock, catálogo) van al cloud, en Phase 3.
- **Superuser CRUD dentro del APK**: rechazado por el owner — se usa un web admin serverless separado.
- **Nota de crédito/débito**: deferido (devoluciones/ajustes post-venta).
- **Cobro parcial (fiado en cuotas)**: deferido — **fiado por partes** planificado en `014-cobro-fiado-por-partes.md` (dirección preferida: Variante 3 — cerrar la deuda y reabrir una nueva con lo pendiente, pudiendo modificar cantidades/eliminar productos). Hoy: COBRO salda completo y los productos de la deuda se muestran en solo lectura.