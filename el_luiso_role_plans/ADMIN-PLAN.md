# El Luiso — Plan ADMIN
## Gestión completa de Branch dentro de Android

## 1. Objetivo
Implementar la experiencia `ADMIN` dentro de la aplicación Android existente.

El ADMIN administra una o varias branches asignadas dentro de su organization mediante `Membership`.

Responsabilidades:
- Ventas
- Productos
- Inventario
- Historial
- Cierres
- Sellers
- Gestión operativa

## 2. Scope
El ADMIN solo puede acceder a:
`organizationId == membership.organizationId`

y:
`branchId ∈ membership.branchIds`

Nunca puede acceder a una branch no asignada ni a otra organization.

## 3. Dashboard
Debe ser operativo:
- Ventas de hoy
- Stock bajo
- Fiados pendientes
- Últimos movimientos
- Cierre actual

No necesita el nivel de analytics del OWNER.

## 4. Ventas
Puede consultar las ventas de sus branches y registrar operaciones permitidas por el sistema.

Las operaciones deben respetar:
`organizationId`, `branchId`, `sellerUid`.

## 5. Productos
Puede:
- CREATE
- READ
- UPDATE
- DELETE

según las reglas del negocio.

Separación obligatoria:
`Product` = catálogo de organization
`StockItem` = stock por branch

## 6. Inventario
Puede realizar operaciones administrativas:
- Entrada
- Alta
- Merma
- Ajuste

Cada modificación debe generar trazabilidad mediante Movement.

Nunca modificar stock sin registrar la operación correspondiente.

## 7. Regla crítica SELLER vs ADMIN
SELLER:
- Puede leer inventario
- Puede vender
- Una venta puede decrementar stock
- NO puede dar altas
- NO puede editar stock
- NO puede eliminar stock
- NO puede hacer ajustes
- NO puede hacer mermas
- NO puede modificar productos

ADMIN sí puede realizar las operaciones administrativas de inventario.

## 8. Inventario
Vista:
```text
Producto       Stock
Coca Cola      20
Agua           15
Pan             7
```

Filtros:
- Categoría
- Stock bajo
- Sin stock

## 9. Historial
Puede consultar el historial completo de sus branches autorizadas.

Filtros:
- Date
- Seller
- Product
- Movement Type

Tipos:
`ALTA`, `ENTRADA`, `GASTO`, `MERMA`, `VENTA`, `VENTA_FIADO`, `COBRO`

## 10. Sellers
Puede:
- Listar
- Ver
- Crear
- Desactivar
- Reactivar
- Asignar branch

No puede:
- Crear SUPERUSER
- Crear OWNER
- Modificar otra organization

## 11. Membership
Usar:
```text
Membership
├── userUid
├── organizationId
├── role
├── branchIds
├── active
└── createdAt
```

No duplicar permisos en múltiples lugares.

## 12. Cierres
El ADMIN puede cerrar su branch.

Conceptualmente:
`ClosingScope.BRANCH`

Debe incluir:
- organizationId
- branchId
- scope
- totals
- movements snapshot
- stock snapshot
- createdAt
- createdBy

Debe ser reproducible y auditable.

## 13. Concurrencia
Stock debe soportar operaciones concurrentes.

Ejemplo:
```text
Stock = 10
Seller A vende 3
Seller B vende 4
Resultado = 3
```

Utilizar operaciones seguras/atómicas o mecanismo equivalente disponible en Appwrite.

## 14. Idempotencia
Operaciones importantes deben tener `operationId` o identificador único:
- Venta
- Cobro
- Movimiento
- Cierre

Retries por mala conexión/sincronización no deben duplicar operaciones.

## 15. Offline-first
Mantener:
```text
UI
 ↓
Repository
 ↓
Local cache
 ↓
SyncManager
 ↓
Appwrite
```

Definir estrategia explícita para operaciones críticas.

## 16. Navegación
```text
Dashboard
Ventas
Inventario
Productos
Historial
Cierres
Sellers
```

No mostrar:
- Owner global dashboard
- Superuser
- otras organizations

## 17. Permisos

| Acción | ADMIN |
|---|---:|
| Ver ventas | YES |
| Registrar venta | YES |
| Ver inventario | YES |
| Crear producto | YES |
| Editar producto | YES |
| Eliminar producto | YES |
| Entrada stock | YES |
| Alta stock | YES |
| Merma | YES |
| Ajuste | YES |
| Ver historial branch | YES |
| Ver historial seller | YES |
| Crear cierre branch | YES |
| Gestionar sellers | YES |
| Gestionar SUPERUSER | NO |
| Gestionar OWNER | NO |
| Acceder otra organization | NO |
| Acceder branch no asignada | NO |

## 18. Milestones
### M1 — ADMIN routing
Resolver ADMIN, memberships, branchIds y navegación.

### M2 — Branch context
Selected branch, validación de membership y cambio entre branches autorizadas.

### M3 — Products
CRUD, organization catalog y relación con branch.

### M4 — Stock
StockItem, entries, alta, merma, ajuste y audit movement.

### M5 — Sales
Sales, stock decrement, movement y seller attribution.

### M6 — History
Branch history, filters y pagination.

### M7 — Sellers
User management, Membership y branch assignment.

### M8 — Closings
Branch closing, snapshot, totals y audit.

### M9 — Offline
Cache, sync, retry e idempotency.

### M10 — Security/performance
Authorization, cross-branch, cross-tenant, pagination y concurrency tests.

## 19. Definition of Done
- [ ] ADMIN funciona dentro de Android
- [ ] Membership implementado
- [ ] Branch scope obligatorio
- [ ] Product CRUD
- [ ] Stock operacional
- [ ] Movements
- [ ] Sales
- [ ] History
- [ ] Sellers
- [ ] Branch closing
- [ ] Pagination
- [ ] Idempotency
- [ ] Concurrency safety
- [ ] Offline/cache compatible
- [ ] ADMIN no accede a otra branch
- [ ] ADMIN no accede a otra organization
- [ ] ADMIN no puede crear SUPERUSER
- [ ] ADMIN no puede convertirse en OWNER
- [ ] SELLER sigue siendo read-only respecto al inventario
