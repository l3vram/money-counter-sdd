# El Luiso — Plan SUPERUSER
## Admin Web independiente desplegado en Appwrite Sites

## 1. Objetivo
Crear un panel web administrativo independiente para `SUPERUSER`.

El panel administra la plataforma El Luiso, no la operación diaria de los negocios.

La aplicación será:
- Web
- Independiente de Android
- Desplegada en Appwrite Sites
- Conectada al mismo proyecto/backend de Appwrite
- Exclusiva para `SUPERUSER`

Debe administrar Organizations, Owners, Administrators, Sellers, Branches, estados, actividad, auditoría y métricas globales.

## 2. Arquitectura
```text
                    APPWRITE
                       │
          ┌────────────┴────────────┐
          │                         │
   Android El Luiso            Admin Web
          │                         │
 OWNER / ADMIN / SELLER         SUPERUSER
```

El Admin Web no debe convertirse en una segunda aplicación operacional.

## 3. Stack
- React
- TypeScript
- Vite
- Appwrite Web SDK
- React Router
- TanStack Query
- Tailwind CSS
- shadcn/ui o equivalente
- Deployment: Appwrite Sites

## 4. Seguridad
Login mediante Appwrite Authentication.

Flujo:
```text
login → currentUser → resolver SUPERUSER → acceso permitido / denegado
```

La autorización real debe estar protegida en backend/Appwrite. El frontend solo controla UX.

## 5. Modelo de acceso
`SUPERUSER` pertenece al nivel plataforma y no es un usuario operativo de una organization/branch.

## 6. Dashboard
Mostrar inicialmente:
- Organizations totales
- Activas
- Suspendidas
- Branches
- Usuarios
- Owners
- Administrators
- Sellers
- Actividad reciente

Posteriormente:
- organizaciones por período
- usuarios nuevos
- branches nuevas
- actividad
- ventas globales
- usuarios activos

Introducir agregaciones/rollups solo cuando el volumen lo requiera.

## 7. Organizations
Funciones:
- Listar
- Buscar
- Filtrar por estado
- Ver detalle
- Crear
- Suspender
- Reactivar

Estados:
`ACTIVE`, `SUSPENDED`, `DELETED`

Preferir soft delete/inactivación para no romper históricos.

## 8. Organization Detail
```text
Organization
├── Información
├── Owner
├── Branches
├── Users
├── Activity
└── Metrics
```

## 9. Users
Debe permitir:
- Buscar por email
- Buscar por organización
- Filtrar por rol/estado
- Ver usuario
- Ver organization
- Ver branches
- Ver actividad

Roles: `OWNER`, `ADMIN`, `SELLER`, `SUPERUSER`.

## 10. Branches
Vista global con:
- Branch
- Organization
- Address
- Status
- Users
- Created

Detalle:
```text
Branch
├── Información
├── Users
├── Products
├── Stock
├── Sales
├── Movements
└── Closings
```

Inicialmente debe ser principalmente consultiva.

## 11. Audit Log
Entidad conceptual:
```text
AuditLog
id
actorUid
actorRole
organizationId
branchId
action
entityType
entityId
timestamp
metadata
```

Debe ser append-only para usuarios normales.

## 12. Suspensión
`Organization.status = SUSPENDED`

Android debe detectar el estado y bloquear operaciones.

## 13. Métricas
Fase 1: Organizations, Users, Branches, Activity.
Fase 2: Sales, Collections, Credit, Inventory.
Fase 3: Growth, Retention, Active users, Activity trends.

## 14. Navegación
```text
Dashboard
Organizations
  └── Organization Detail
Users
  └── User Detail
Branches
  └── Branch Detail
Audit Log
Platform Metrics
Settings
```

## 15. Milestones
### M1 — Proyecto Web
React, TypeScript, Vite, SDK, routing, layout, login/logout.

### M2 — SUPERUSER security
Resolver usuario/rol, route guard y acceso denegado para no-SUPERUSER.

### M3 — Organizations
List, search, detail, create, suspend, reactivate.

### M4 — Users
List, search, detail, filters.

### M5 — Branches
List, detail, read-only inspection.

### M6 — Audit
AuditLog, filters, detail.

### M7 — Dashboard
Platform metrics y activity.

### M8 — Hardening
Authorization, errores, loading, pagination y security tests.

### M9 — Appwrite Sites
Build, deploy, environment configuration y validación productiva.

## 16. Regla fundamental
El SUPERUSER administra la plataforma. No es un ADMIN gigante de todas las branches.

No introducir permisos como registrar ventas o editar stock directamente salvo necesidad de negocio explícita.

## 17. Definition of Done
- [ ] Admin Web separado del Android
- [ ] Appwrite Sites
- [ ] Login Appwrite
- [ ] SUPERUSER obligatorio
- [ ] Rechazo de usuarios normales
- [ ] Organizations
- [ ] Organization detail
- [ ] Users
- [ ] Branches
- [ ] Audit Log
- [ ] Dashboard
- [ ] Suspensión/reactivación
- [ ] Paginación
- [ ] Cross-tenant isolation
- [ ] Tests de autorización
- [ ] Sin operaciones de SELLER/ADMIN desde el panel
- [ ] Build production exitoso
