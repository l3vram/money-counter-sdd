# El Luiso — Multi-Tenant
## Plan maestro de arquitectura e implementación incremental

---

# 1. Objetivo

Transformar El Luiso en una aplicación **multi-tenant, multi-negocio y multi-sucursal**, manteniendo la aplicación actual funcional mientras se realiza la migración.

El modelo objetivo es:

```text
SUPERUSER
    |
    +---------------- Plataforma ----------------+
                                                 |
                                              OWNER
                                                 |
                           +---------------------+---------------------+
                           |                                           |
                       NEGOCIO A                                   NEGOCIO B
                           |
                 +---------+---------+
                 |                   |
             SUCURSAL A          SUCURSAL B
                 |
          +------+------+
          |             |
       ADMIN         SELLER
          |
      +---+---+
      |       |
   SELLER   SELLER
```

La unidad principal de operación es la **Sucursal**.

Los datos operativos siempre deben estar asociados a:

```text
organizationId
branchId
```

y, cuando corresponda:

```text
sellerUid
```

---

# 2. Regla arquitectónica principal

La aplicación debe aplicar el siguiente scope:

```text
Organization
    |
    +--- Branch
            |
            +--- Products / Catalog
            +--- Stock
            +--- Movements
            +--- Sales
            +--- Receivables
            +--- Payments
            +--- Expenses
            +--- Writeoffs
            +--- Closings
```

Nunca asumir:

```text
User -> datos
```

como modelo de aislamiento.

El modelo correcto es:

```text
User
 |
 +--- Membership
          |
          +--- Organization
          |
          +--- Role
          |
          +--- Branches
```

---

# 3. Principio fundamental: implementación incremental

## IMPORTANTE

Este proyecto **NO debe migrarse completamente de una sola vez**.

No realizar:

```text
single-user
    ↓
big rewrite
    ↓
multi-tenant terminado
```

La estrategia obligatoria será:

```text
Estado actual
     |
     v
M1 — Tenant Context
     |
     v
M2 — Shared Inventory
     |
     v
M3 — Branch Operations
     |
     v
M4 — Closings
     |
     v
M5 — Owner Dashboard
     |
     v
M6 — Superuser Admin
     |
     v
M7 — Legacy Migration
     |
     v
M8 — Hardening / Production
```

Cada milestone debe:

1. tener un alcance reducido;
2. ser compilable;
3. tener tests;
4. no romper funcionalidades anteriores;
5. poder revisarse independientemente;
6. dejar el proyecto en un estado funcional.

**No comenzar el milestone siguiente si el anterior no está estable.**

---

# 4. Regla para el agente

Antes de modificar código:

1. inspeccionar los archivos existentes;
2. buscar todos los usos de la entidad que se va a modificar;
3. revisar los repositorios existentes;
4. revisar los ViewModels afectados;
5. revisar navegación;
6. revisar tests existentes;
7. implementar el cambio mínimo;
8. agregar tests;
9. ejecutar tests;
10. compilar APK.

No asumir que la documentación histórica representa exactamente el estado actual del código.

El repositorio ya contiene parte de la arquitectura multi-tenant, por lo que debe **evolucionarse**, no reemplazarse.

---

# 5. Estado actual

El repositorio ya posee conceptos de:

```text
Organization
Branch
Member
Role
Product
Movement
Closing
Currency
Denomination
MeasurementUnit
```

También existe una capa de acceso/membership y una migración hacia Appwrite.

La aplicación posee además lógica de:

```text
Ventas
Ventas fiadas
Cobros
Gastos
Mermas
Inventario
Historial
Cierres
```

Por tanto, el objetivo no es crear una aplicación nueva.

El objetivo es:

```text
actual
  +
tenant isolation
  +
branch isolation
  +
role permissions
  +
cloud persistence
```

---

# 6. Modelo de negocio

## 6.1 Owner

Un OWNER representa al propietario de uno o más negocios.

```text
OWNER
 |
 +--- Business A
 |
 +--- Business B
```

Puede visualizar todos sus negocios.

---

# 7. Organization / Business

En código mantener:

```kotlin
Organization
```

En UI puede mostrarse como:

```text
Negocio
Empresa
```

Modelo:

```kotlin
data class Organization(
    val id: String,
    val name: String,
    val ownerUid: String,
    val createdAt: Long,
    val active: Boolean = true
)
```

---

# 8. Branch / Sucursal

Cada sucursal pertenece a una organización.

```kotlin
data class Branch(
    val id: String,
    val organizationId: String,
    val name: String,
    val address: String?,
    val active: Boolean,
    val createdAt: Long
)
```

Separar:

```text
name
address
```

No concatenar permanentemente:

```text
"Negocio - Dirección"
```

---

# 9. Membership

La relación entre usuario y organización debe representarse mediante Membership.

```kotlin
data class Membership(
    val id: String,
    val userUid: String,
    val organizationId: String,
    val role: Role,
    val branchIds: List<String>,
    val active: Boolean,
    val createdAt: Long
)
```

Esto permite:

```text
Usuario
 |
 +--- Organización A / ADMIN / Sucursal 1
 |
 +--- Organización B / SELLER / Sucursal 3
```

aunque inicialmente la UI solo necesite un negocio.

---

# 10. Roles

Los roles definitivos son:

```text
OWNER
ADMIN
SELLER
SUPERUSER
```

---

# 11. OWNER

El OWNER tiene visibilidad sobre todos sus negocios.

Puede:

```text
VIEW organizations
VIEW branches
VIEW inventory
VIEW sales
VIEW history
VIEW closings
VIEW reports
VIEW statistics
VIEW sellers
VIEW administrators
```

Puede seleccionar:

```text
Todos los negocios
Un negocio
Todas las sucursales
Una sucursal
```

---

# 12. OWNER — alcance

El OWNER debe poder consultar:

```text
Business A
    |
    +--- Branch A
    +--- Branch B
    +--- Branch C
```

y obtener:

```text
ventas consolidadas
stock consolidado
cierres
historial
fiados
cobros
gastos
mermas
```

---

# 13. ADMIN

ADMIN es un rol operativo de sucursal.

Tiene CRUD completo dentro de las sucursales a las que está asignado.

Permisos:

```text
VENTA                    YES
VIEW INVENTORY           YES
ADD STOCK / ALTA         YES
EDIT STOCK               YES
CREATE PRODUCT           YES
EDIT PRODUCT             YES
DELETE PRODUCT           YES
VIEW HISTORY             YES
VIEW ALL SELLERS         YES
CREATE SELLER CLOSING    YES
CREATE BRANCH CLOSING    YES
VIEW CLOSINGS            YES
CREATE EXPENSE           YES
CREATE WRITEOFF          YES
CREATE CREDIT SALE       YES
COLLECT RECEIVABLE       YES
```

ADMIN no puede administrar organizaciones fuera de su scope.

No puede:

```text
crear SUPERUSER
promoverse a OWNER
acceder a otra organización
```

---

# 14. SELLER

## Regla fundamental

El SELLER es un usuario **operativo de ventas**.

No administra inventario.

No puede modificar stock.

No puede crear productos.

No puede eliminar productos.

No puede registrar altas.

No puede registrar entradas.

No puede registrar mermas.

No puede realizar ajustes de inventario.

El inventario para SELLER es estrictamente **read-only**.

---

# 15. Permisos definitivos del SELLER

```text
VENTA                     YES
VENTA FIADA               YES
COBRO                     YES
VIEW INVENTORY            YES

ADD STOCK / ALTA          NO
ENTRADA STOCK             NO
EDIT STOCK                NO
DELETE STOCK              NO
CREATE PRODUCT            NO
EDIT PRODUCT              NO
DELETE PRODUCT            NO
MERMA                     NO
AJUSTE INVENTARIO         NO

VIEW OWN HISTORY          YES
VIEW OTHER SELLERS        NO

CREATE OWN CLOSING        YES
VIEW OWN CLOSINGS         YES
VIEW OTHER CLOSINGS       NO

VIEW BRANCH REPORTS       NO
VIEW OWNER DASHBOARD      NO
MANAGE USERS              NO
MANAGE MEMBERSHIPS        NO
MANAGE BRANCHES           NO
MANAGE ORGANIZATION       NO
```

---

# 16. Matriz de permisos

| Acción | OWNER | ADMIN | SELLER |
|---|---:|---:|---:|
| Ver dashboard | YES | opcional | NO |
| Ver negocio | YES | limitado | NO |
| Ver sucursales | YES | asignadas | asignada |
| Venta | YES | YES | YES |
| Venta fiada | YES | YES | YES |
| Cobro | YES | YES | YES |
| Ver inventario | YES | YES | YES |
| Alta de stock | YES | YES | NO |
| Entrada de stock | YES | YES | NO |
| Ajustar stock | YES | YES | NO |
| Crear producto | YES | YES | NO |
| Editar producto | YES | YES | NO |
| Eliminar producto | YES | YES | NO |
| Merma | YES | YES | NO |
| Historial propio | YES | YES | YES |
| Historial vendedores | YES | YES | NO |
| Cierre propio | YES | YES | YES |
| Cierre sucursal | YES | YES | NO |
| Reportes | YES | YES | NO |
| Usuarios | limitado | NO | NO |
| Branches | YES | NO | NO |
| Memberships | según política | NO | NO |

---

# 17. SUPERUSER

SUPERUSER es un rol de plataforma.

No pertenece a una sucursal.

No debe utilizarse para operaciones normales de caja.

Funciones:

```text
Organizations
Branches
Users
Memberships
Roles
Access
```

Debe administrarse preferentemente desde un panel web.

---

# 18. Separación de responsabilidades

## OWNER

```text
Business management
Analytics
Reports
Global visibility
```

## ADMIN

```text
Branch management
Inventory
Sales
Closings
Operations
```

## SELLER

```text
Sales
Collections
Inventory read
Own history
Own closing
```

## SUPERUSER

```text
Platform administration
```

---

# 19. Inventario

Actualmente `Product` contiene información de stock.

Esto debe evolucionar.

No utilizar:

```kotlin
Product.stock
```

como fuente de verdad para una aplicación multi-sucursal.

Separar:

```text
Product
```

de:

```text
StockItem
```

---

# 20. Product

Producto:

```kotlin
data class Product(
    val id: String,
    val organizationId: String,
    val name: String,
    val unit: String,
    ...
)
```

No contiene el stock de una sucursal.

---

# 21. StockItem

Crear:

```kotlin
data class StockItem(
    val id: String,
    val organizationId: String,
    val branchId: String,
    val productId: String,
    val quantity: BigDecimal,
    val updatedAt: Long
)
```

Conceptualmente:

```text
Producto:
Arroz

Sucursal Centro:
100

Sucursal Norte:
20

Sucursal Sur:
300
```

---

# 22. Catálogo vs inventario

Recomendación:

```text
Product
    |
    +--- organization-level
```

Mientras:

```text
Stock
    |
    +--- branch-level
```

Esto significa que un producto puede existir en todo el negocio aunque no tenga stock en determinada sucursal.

---

# 23. Inventario compartido

Todos los usuarios autorizados de una misma sucursal ven el mismo inventario.

Ejemplo:

```text
Branch A

Stock arroz = 100
```

Seller A:

```text
VIEW = 100
```

Seller B:

```text
VIEW = 100
```

ADMIN:

```text
VIEW = 100
```

Si ADMIN agrega:

```text
+50
```

todos deben terminar viendo:

```text
150
```

---

# 24. SELLER y stock

SELLER únicamente:

```text
READ
```

No permitir:

```text
CREATE stock
UPDATE stock
DELETE stock
```

Esto incluye indirectamente impedir:

```text
ALTA
ENTRADA
MERMA
AJUSTE
```

Una venta sí modifica stock, pero el SELLER no ejecuta un "stock update" administrativo.

La venta es una operación de negocio:

```text
SALE
  |
  +--- Movement
  |
  +--- Stock decrement
```

---

# 25. Venta del SELLER

SELLER puede:

```text
crear venta
```

La aplicación debe realizar:

```text
Venta
 ↓
Movement
 ↓
Stock decrement
```

El SELLER no tiene permiso genérico de:

```text
decreaseStock()
```

Tiene permiso de:

```text
registerSale()
```

Esto es una distinción importante.

---

# 26. Repository API

Evitar APIs demasiado genéricas.

No crear:

```kotlin
updateStock(...)
```

como única API pública.

Preferir:

```kotlin
interface StockRepository {

    fun observeStock(
        organizationId: String,
        branchId: String
    ): Flow<List<StockItem>>

    suspend fun increaseStock(
        organizationId: String,
        branchId: String,
        productId: String,
        quantity: BigDecimal
    )

    suspend fun adjustStock(
        organizationId: String,
        branchId: String,
        productId: String,
        quantity: BigDecimal,
        reason: String
    )
}
```

Y las operaciones de venta deben pasar por:

```kotlin
SaleRepository
```

---

# 27. Movement

Todo movimiento operativo debe tener:

```text
organizationId
branchId
```

y cuando corresponda:

```text
sellerUid
sellerName
```

Ejemplo:

```kotlin
data class Movement(
    val id: String,
    val organizationId: String,
    val branchId: String,
    val sellerUid: String,
    val sellerName: String,
    val at: Long,
    val type: MovementType,
    val currencyId: String,
    ...
)
```

---

# 28. Tipos de movimiento

Mantener:

```text
ALTA
ENTRADA
GASTO
MERMA
VENTA
VENTA_FIADO
COBRO
```

Pero establecer permisos:

```text
ALTA
    OWNER / ADMIN

ENTRADA
    OWNER / ADMIN

GASTO
    OWNER / ADMIN
    SELLER según decisión de negocio

MERMA
    OWNER / ADMIN

VENTA
    OWNER / ADMIN / SELLER

VENTA_FIADO
    OWNER / ADMIN / SELLER

COBRO
    OWNER / ADMIN / SELLER
```

Para la primera implementación:

```text
SELLER GASTO = NO
```

salvo que el negocio lo requiera posteriormente.

---

# 29. Historial

## SELLER

Consulta:

```text
branchId == currentBranch
AND sellerUid == currentUser
```

## ADMIN

Consulta:

```text
branchId == currentBranch
```

## OWNER

Consulta:

```text
organizationId == selectedOrganization
```

con filtro opcional:

```text
branch
seller
date
type
currency
```

---

# 30. Cierres

El cierre pertenece a una sucursal.

Modelo recomendado:

```kotlin
enum class ClosingScope {
    SELLER,
    BRANCH
}
```

```kotlin
data class Closing(
    val id: String,
    val organizationId: String,
    val branchId: String,
    val scope: ClosingScope,
    val sellerUid: String?,
    val sellerName: String?,
    ...
)
```

---

# 31. SELLER closing

SELLER puede crear:

```text
ClosingScope.SELLER
```

Solo incluye:

```text
sus propios movimientos abiertos
```

No puede cerrar la sucursal completa.

---

# 32. ADMIN closing

ADMIN puede crear:

```text
ClosingScope.BRANCH
```

Incluye:

```text
todos los movimientos abiertos de la sucursal
```

incluyendo:

```text
SELLER A
SELLER B
SELLER C
ADMIN
```

según corresponda.

---

# 33. OWNER closing

OWNER puede consultar:

```text
SELLER closings
BRANCH closings
```

de todas sus sucursales.

La creación de un cierre por OWNER debe mantenerse como decisión explícita de producto.

Recomendación:

```text
OWNER = principalmente consulta
ADMIN = responsable operativo del cierre
```

---

# 34. Snapshot de cierre

Mantener el snapshot actual.

El cierre debe conservar:

```text
movements
totalsByType
netCash
stockSnapshot
```

Esto permite reconstruir qué estado tenía la sucursal al momento del cierre.

---

# 35. Dashboard OWNER

El OWNER debe tener una pantalla específica:

```text
OwnerDashboardScreen
```

Métricas:

```text
Ventas
Cobros
Gastos
Mermas
Ventas fiadas
Por cobrar
Stock
```

---

# 36. Filtros dashboard

Debe soportar:

```text
Día
Semana
Mes
Año
Rango personalizado
```

y:

```text
Todas las sucursales
Sucursal específica
```

Opcionalmente:

```text
Vendedor
Moneda
```

---

# 37. Dashboard consolidado

Ejemplo:

```text
Mi Empresa

Ventas
-------------------------
Centro       $100,000
Norte        $ 80,000
Sur          $120,000
-------------------------
TOTAL        $300,000
```

El OWNER debe poder navegar:

```text
Empresa
 ↓
Sucursal
 ↓
Vendedor
```

---

# 38. Rollups

No calcular estadísticas leyendo todo el historial en cada render.

Primera versión:

```text
queries por rango
```

Cuando el volumen lo justifique:

```text
daily rollups
monthly rollups
```

Ejemplo:

```text
branch
  |
  +--- analytics
         |
         +--- 2026-09-10
```

Con:

```text
sales
payments
expenses
writeoffs
creditSales
```

Los rollups son una optimización.

No reemplazan los movimientos como fuente de verdad.

---

# 39. Selección de Organization

El contexto de sesión debe mantener:

```kotlin
data class AppContext(
    val uid: String,
    val organizationId: String?,
    val branchId: String?,
    val role: Role
)
```

El usuario puede seleccionar:

```text
Organization
```

y luego:

```text
Branch
```

Pero estos valores son solamente contexto de UI.

Nunca deben ser considerados autorización.

---

# 40. Authorization

Crear:

```text
PermissionService
```

Ejemplo:

```kotlin
interface PermissionService {

    fun canSell(): Boolean

    fun canViewInventory(): Boolean

    fun canModifyInventory(): Boolean

    fun canCreateProduct(): Boolean

    fun canDeleteProduct(): Boolean

    fun canViewOwnHistory(): Boolean

    fun canViewBranchHistory(): Boolean

    fun canViewOrganizationHistory(): Boolean

    fun canCreateSellerClosing(): Boolean

    fun canCreateBranchClosing(): Boolean
}
```

---

# 41. Regla de seguridad

La UI no es seguridad.

Esto:

```kotlin
if (role == SELLER) {
    hideAddStock()
}
```

es solamente UX.

La seguridad real debe impedir:

```text
SELLER -> backend -> ADD STOCK
```

aunque el usuario manipule el cliente.

---

# 42. Backend authorization

Validar siempre:

```text
currentUser
organization
membership
role
branch
```

antes de cualquier operación.

Ejemplo:

```text
SELLER
organization=A
branch=1

request:
organization=A
branch=2

=> DENIED
```

---

# 43. Cross-tenant isolation

Nunca permitir:

```text
organization A
    ↓
read organization B
```

ni:

```text
branch A
    ↓
read branch B
```

aunque el usuario conozca el ID.

---

# 44. Appwrite

La implementación cloud debe continuar utilizando Appwrite.

No introducir Firebase nuevamente para los datos operativos si Appwrite será el backend elegido.

Mantener la abstracción:

```text
UI
 ↓
Repository
 ↓
Appwrite implementation
```

---

# 45. Persistencia

Entidades principales:

```text
users
organizations
branches
memberships
products
stock
movements
closings
receivables
payments
```

La estructura final puede ser:

```text
organizations/{organizationId}
    branches/{branchId}
```

y datos operativos scoped a:

```text
organizationId
branchId
```

La implementación concreta debe adaptarse a las capacidades reales de Appwrite y sus índices.

---

# 46. Índices

Planificar consultas para:

```text
branchId + at
branchId + sellerUid + at
branchId + type + at
branchId + closingId
organizationId + branchId
organizationId + sellerUid
```

No crear índices especulativos.

Primero definir las queries reales.

---

# 47. Offline-first

No eliminar la funcionalidad local.

Arquitectura:

```text
UI
 ↓
Repository
 ↓
Local cache
 ↓
Sync
 ↓
Appwrite
```

El usuario debe poder:

```text
consultar
vender
```

cuando sea posible sin Internet.

---

# 48. Sync

Crear:

```text
SyncManager
```

Estados:

```text
IDLE
SYNCING
OFFLINE
ERROR
```

Las operaciones pendientes deben conservar:

```text
localId
syncStatus
```

---

# 49. Idempotencia

Una operación enviada dos veces no puede producir dos ventas.

Usar:

```text
operationId
```

o el ID de la operación local como identificador remoto.

Ejemplo:

```text
saleId = ABC123
```

Primer intento:

```text
ABC123 created
```

Segundo intento:

```text
ABC123 already exists
```

No crear una segunda venta.

---

# 50. Concurrencia de stock

Caso:

```text
Stock = 10
```

Seller A:

```text
vende 3
```

Seller B:

```text
vende 4
```

Resultado:

```text
Stock = 3
```

Nunca:

```text
Stock = 7
```

ni:

```text
Stock = 6
```

La operación de stock debe ser atómica o utilizar un mecanismo equivalente.

---

# 51. Paginación

No cargar todo el historial.

Implementar:

```text
limit
cursor
```

Ejemplo:

```text
50 registros
↓
load more
```

Especialmente necesario para OWNER.

---

# 52. UI SELLER

La aplicación para SELLER debe permanecer simple.

Navegación:

```text
Venta
Stock
Historial
Cierre
```

Stock:

```text
READ ONLY
```

No mostrar botones:

```text
+
Alta
Entrada
Editar
Eliminar
Merma
Ajustar
```

---

# 53. UI ADMIN

Navegación:

```text
Venta
Stock
Historial
Cierres
Gastos
Reportes
```

Stock:

```text
Crear producto
Editar producto
Eliminar producto
Alta
Entrada
Ajuste
```

---

# 54. UI OWNER

Pantalla inicial:

```text
Dashboard
```

Accesos:

```text
Dashboard
Negocios
Sucursales
Inventario
Ventas
Historial
Cierres
Reportes
```

---

# 55. Web Admin

Crear posteriormente:

```text
el-luiso-admin
```

Funciones:

```text
Login
Organizations
Branches
Users
Memberships
Roles
Access
```

No mezclar administración de plataforma con la aplicación operativa Android.

---

# 56. Migración de Product

Durante la migración:

```text
Product.stock
```

puede mantenerse temporalmente.

Crear:

```text
StockItem
```

Migrar:

```text
Product.stock
       ↓
StockItem(branchId, productId)
```

Cuando todos los consumers hayan migrado:

```text
Product.stock
```

deja de utilizarse.

Eliminarlo únicamente después de comprobar que:

```text
Counter
Reports
Closings
History
Sync
```

ya no dependen de él.

---

# 57. Migración de Movement

Todos los movimientos nuevos deben tener:

```text
organizationId
branchId
sellerUid
```

Los movimientos legacy deberán recibir:

```text
organizationId = negocio migrado
branchId = sucursal inicial
```

---

# 58. Migración de Closing

Los cierres existentes deben migrarse al:

```text
organizationId
branchId
```

y determinar:

```text
scope
```

cuando sea posible.

Si no existe suficiente información histórica para determinarlo:

```text
scope = SELLER
```

o utilizar una estrategia de migración documentada.

No inventar datos históricos.

---

# 59. FASE 0 — Baseline

## Objetivo

Conocer exactamente el estado actual.

## Tareas

- revisar repositories;
- revisar ViewModels;
- buscar todos los usos de `Product.stock`;
- buscar todos los usos de `Role`;
- buscar todos los accesos a JSON;
- buscar todos los usos de `Movement`;
- buscar todos los usos de `Closing`;
- revisar Appwrite;
- revisar tests.

## Acceptance criteria

```text
./gradlew test
```

verde.

```text
./gradlew assembleDebug
```

verde.

No realizar cambios funcionales importantes en esta fase.

---

# 60. FASE 1 — Tenant Context

## Objetivo

Implementar:

```text
Organization
Branch
Membership
Role
AppContext
PermissionService
```

## Cambios

Agregar:

```text
ADMIN
```

y actualizar:

```text
SELLER
```

para que no pueda modificar inventario.

## Acceptance criteria

```text
OWNER
ADMIN
SELLER
SUPERUSER
```

tienen permisos distintos.

Tests de permisos verdes.

Aplicación compila.

---

# 61. FASE 2 — Organization / Branch

## Objetivo

Permitir seleccionar contexto.

Implementar:

```text
OrganizationRepository
BranchRepository
MembershipRepository
```

UI:

```text
Organization selector
Branch selector
```

## Acceptance

OWNER:

```text
puede seleccionar negocio
puede seleccionar sucursal
```

ADMIN:

```text
solo branches asignadas
```

SELLER:

```text
solo branches asignadas
```

---

# 62. FASE 3 — Product / Stock

## Objetivo

Separar:

```text
Product
```

de:

```text
Stock
```

Crear:

```text
StockRepository
StockItem
```

## Acceptance

Producto:

```text
Arroz
```

Sucursal A:

```text
100
```

Sucursal B:

```text
20
```

Los stocks son independientes.

---

# 63. FASE 4 — Shared Inventory

## Objetivo

Todos los usuarios de una sucursal ven el mismo stock.

SELLER:

```text
VIEW ONLY
```

ADMIN:

```text
CRUD
```

OWNER:

```text
VIEW
```

## Acceptance

Seller A no puede crear stock.

Seller B no puede modificar stock.

Admin puede modificar stock.

Una venta reduce el stock.

---

# 64. FASE 5 — Branch Operations

Migrar:

```text
Sales
Credit Sales
Payments
Expenses
Writeoffs
Movements
```

Todos deben guardar:

```text
organizationId
branchId
sellerUid
```

## Acceptance

Una venta de:

```text
Branch A
```

nunca aparece en:

```text
Branch B
```

---

# 65. FASE 6 — History

Implementar filtros según rol.

SELLER:

```text
own history
```

ADMIN:

```text
branch history
```

OWNER:

```text
organization history
```

---

# 66. FASE 7 — Closings

Implementar:

```text
SELLER closing
BRANCH closing
```

SELLER:

```text
own closing
```

ADMIN:

```text
branch closing
```

OWNER:

```text
read all
```

---

# 67. FASE 8 — Owner Dashboard

Implementar:

```text
OwnerDashboardScreen
```

Métricas:

```text
Ventas
Cobros
Gastos
Mermas
Fiados
Stock
```

Filtros:

```text
Fecha
Sucursal
Vendedor
Moneda
```

---

# 68. FASE 9 — Analytics

Inicialmente:

```text
query por rango
```

Después:

```text
daily rollups
monthly rollups
```

Solo introducir rollups cuando exista una necesidad real de performance.

---

# 69. FASE 10 — Superuser Admin

Crear aplicación web.

Funciones:

```text
Create organization
Create branch
Create user
Assign membership
Assign role
Assign branch
Block user
Approve user
```

---

# 70. FASE 11 — Legacy Migration

Migrar:

```text
products
currencies
denominations
units
count history
receivables
payments
writeoffs
closings
```

Crear inicialmente:

```text
Organization
Branch
OWNER membership
```

y asociar los datos legacy a esa sucursal.

---

# 71. FASE 12 — Security Hardening

Tests:

```text
cross tenant
cross branch
role escalation
membership escalation
seller stock modification
admin organization access
owner cross organization
```

Casos críticos:

```text
SELLER -> ADD STOCK => DENIED
SELLER -> DELETE PRODUCT => DENIED
SELLER -> MERMA => DENIED
SELLER -> BRANCH B => DENIED
ADMIN -> ORGANIZATION B => DENIED
ADMIN -> OWNER role => DENIED
```

---

# 72. FASE 13 — Offline / Sync Hardening

Validar:

```text
offline sale
offline history
offline stock
retry
duplicate request
application restart
sync failure
sync recovery
```

---

# 73. FASE 14 — Performance

Implementar:

```text
pagination
indexes
date range queries
rollups
cache
```

No cargar todos los movimientos para una pantalla.

---

# 74. Definition of Done

## Multi-tenancy

- [ ] Organization implementado.
- [ ] Branch implementado.
- [ ] Membership implementado.
- [ ] usuarios aislados.
- [ ] organizaciones aisladas.
- [ ] sucursales aisladas.

## Roles

- [ ] OWNER.
- [ ] ADMIN.
- [ ] SELLER.
- [ ] SUPERUSER.
- [ ] PermissionService.
- [ ] backend authorization.

## SELLER

- [ ] puede vender.
- [ ] puede vender fiado.
- [ ] puede cobrar.
- [ ] puede ver inventario.
- [ ] NO puede dar altas.
- [ ] NO puede hacer entradas.
- [ ] NO puede modificar stock.
- [ ] NO puede eliminar productos.
- [ ] NO puede crear productos.
- [ ] NO puede hacer mermas.
- [ ] solo ve historial propio.
- [ ] solo puede hacer cierre propio.

## ADMIN

- [ ] CRUD productos.
- [ ] CRUD inventario.
- [ ] ve historial completo de branch.
- [ ] crea cierre de branch.
- [ ] gestiona operación de branch.

## OWNER

- [ ] ve todos sus negocios.
- [ ] ve todas sus sucursales.
- [ ] dashboard.
- [ ] estadísticas.
- [ ] reportes.
- [ ] inventario consolidado.
- [ ] historial consolidado.
- [ ] cierres consolidados.

## Inventario

- [ ] Product separado de Stock.
- [ ] stock por branch.
- [ ] stock compartido entre sellers.
- [ ] operaciones atómicas.

## Offline

- [ ] venta offline.
- [ ] sincronización.
- [ ] idempotencia.
- [ ] recuperación.

## Seguridad

- [ ] cross-tenant bloqueado.
- [ ] cross-branch bloqueado.
- [ ] privilege escalation bloqueado.
- [ ] seller inventory modification bloqueado.

---

# 75. Regla final para el agente

La prioridad NO es terminar rápido el multi-tenant.

La prioridad es terminarlo **sin romper la aplicación existente y sin introducir inconsistencias de datos**.

Por eso:

```text
NO:
big bang migration
```

Sí:

```text
baseline
  ↓
small change
  ↓
tests
  ↓
compile
  ↓
manual validation
  ↓
commit
  ↓
next milestone
```

Cada fase debe poder ser revisada independientemente.

Si una fase requiere modificar una entidad utilizada ampliamente, realizar primero una capa de compatibilidad y migrar consumers progresivamente.

No eliminar código legacy hasta demostrar que la nueva implementación funciona.

---

# 76. Arquitectura final esperada

```text
                         SUPERUSER
                            |
                     PLATFORM ADMIN
                            |
              +-------------+-------------+
              |                           |
           OWNER                       OWNER
              |                           |
          Business A                  Business B
              |
       +------+------+
       |             |
    Branch A      Branch B
       |
   +---+---+
   |       |
 ADMIN   SELLER
           |
        +--+--+
        |     |
     SELLER SELLER


Organization
    |
    +--- Catalog
    |
    +--- Branch
           |
           +--- Shared Stock
           |
           +--- Sales
           |
           +--- Movements
           |
           +--- Receivables
           |
           +--- Payments
           |
           +--- Expenses
           |
           +--- Writeoffs
           |
           +--- Closings
```

La regla central del sistema queda:

```text
USER
  ↓
MEMBERSHIP
  ↓
ORGANIZATION
  ↓
BRANCH
  ↓
OPERATIONAL DATA
```

Y la regla específica de SELLER queda:

```text
SELLER
  |
  +--- SELL
  +--- COLLECT
  +--- VIEW INVENTORY
  +--- VIEW OWN HISTORY
  +--- OWN CLOSING
  |
  X--- MODIFY INVENTORY
  X--- ADD STOCK
  X--- CREATE PRODUCT
  X--- DELETE PRODUCT
  X--- WRITE-OFF
  X--- BRANCH CLOSING
  X--- OTHER SELLERS HISTORY
```

Este modelo debe ser la referencia para toda implementación futura de El Luiso.