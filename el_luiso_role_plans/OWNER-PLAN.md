# El Luiso — Plan OWNER
## Dashboard, Analytics y Reportes dentro de Android

## 1. Objetivo
Implementar la experiencia `OWNER` dentro de la aplicación Android existente.

El OWNER accede a su organization y todas sus branches autorizadas. Su experiencia se orienta a:
- Dashboard
- Métricas
- Gráficos
- Reportes
- Comparación de branches
- Inventario consolidado
- Historial consolidado
- Cierres

No crear otra aplicación Android.

## 2. Principio
```text
SELLER → vender
ADMIN  → administrar branch
OWNER  → analizar negocio
```

## 3. Scope
Todas las consultas deben quedar limitadas a:
`organizationId == currentOwner.organizationId`

Nunca debe acceder a otra organization.

## 4. Dashboard
Será la pantalla inicial del OWNER.

Mostrar:
- Ventas del período
- Comparación con período anterior
- Ventas por día
- Ventas por branch
- Total fiado
- Cobrado
- Pendiente
- Stock total
- Productos con bajo stock
- Productos sin movimiento
- Cierres y actividad por branch

## 5. Filtros globales
Período:
- Hoy
- Semana
- Mes
- Año
- Personalizado

Branch:
- Todas
- Branch específica

Opcionalmente:
- Seller
- Currency
- Category

## 6. Analytics
Implementar:
- Ventas por branch
- Participación porcentual
- Ventas por seller
- Cantidad de operaciones
- Ticket promedio
- Top productos
- Rotación
- Productos sin movimiento
- Ventas por categoría cuando exista información suficiente

## 7. Inventario consolidado
El OWNER puede ver el stock por producto y branch:
```text
Producto
  Centro → 20
  Norte  → 15
  Sur    → 7
  Total  → 42
```

La fuente de verdad continúa siendo:
`StockItem(organizationId, branchId, productId, quantity)`

Nunca volver a usar `Product.stock` como fuente global.

## 8. Historial
Puede consultar el historial de toda su organization con filtros:
- Branch
- Seller
- Movement Type
- Date
- Product

Tipos:
`ALTA`, `ENTRADA`, `GASTO`, `MERMA`, `VENTA`, `VENTA_FIADO`, `COBRO`

El OWNER es principalmente consultivo.

## 9. Cierres
Puede visualizar:
- Cierres de sellers
- Cierres de branches

Recomendación:
`OWNER → consulta`
`ADMIN → cierre de branch`
`SELLER → cierre propio`

## 10. Reportes
Sección:
```text
Reportes
├── Ventas
├── Inventario
├── Crédito
├── Branches
├── Sellers
└── Cierres
```

Ventas:
- Total
- Cantidad
- Ticket promedio
- Por día
- Por branch
- Por seller
- Por producto

Crédito:
- Total fiado
- Total cobrado
- Saldo pendiente

## 11. Performance
No descargar todo el historial para calcular dashboards.

Preferir:
```text
UI
 ↓
ViewModel
 ↓
Repository
 ↓
Appwrite query/function
 ↓
aggregated result
```

Introducir rollups solo cuando sean necesarios.

## 12. Arquitectura Android
Mantener:
```text
UI
 ↓
ViewModel
 ↓
Repository
 ↓
Data source
 ↓
Appwrite
```

El Dashboard no consulta Appwrite directamente desde Composables.

## 13. AppContext
Utilizar:
`uid`, `organizationId`, `role`, `selectedBranch`

Recordatorio: AppContext no es autorización. La autorización real debe existir en backend.

## 14. Navegación
```text
Dashboard
Ventas
Inventario
Reportes
Historial
Cierres
```

## 15. Milestones
### M1 — OWNER routing
Resolver OWNER, navegación específica y dashboard base.

### M2 — Dashboard
Sales, credit, inventory, closings.

### M3 — Filters
Date range, branch y seller cuando aplique.

### M4 — Sales analytics
Daily sales, branch/seller comparison y product ranking.

### M5 — Inventory analytics
Consolidated stock, low stock y no movement.

### M6 — Credit reports
Credit, collections, outstanding.

### M7 — History
Organization history, filters y pagination.

### M8 — Closings
Seller/branch closings y comparación.

### M9 — Offline/cache
Dashboard cache, last known metrics y sync state.

### M10 — Performance/security
Pagination, queries optimizadas, cross-tenant y permission tests.

## 16. Definition of Done
- [ ] OWNER dentro de Android
- [ ] Dashboard como Home
- [ ] Ventas
- [ ] Inventario
- [ ] Reportes
- [ ] Historial
- [ ] Cierres
- [ ] Filtros por período
- [ ] Filtros por branch
- [ ] Comparación de branches
- [ ] Métricas de sellers
- [ ] Métricas de productos
- [ ] Crédito
- [ ] Stock consolidado
- [ ] Pagination
- [ ] Cache/offline compatible
- [ ] Cross-tenant isolation
- [ ] OWNER no accede a otra organization
- [ ] OWNER no obtiene permisos ADMIN automáticamente
