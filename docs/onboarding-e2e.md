# Onboarding El Luiso — Script E2E (release test)

> Fuente de verdad para probar el flujo completo registro → aprobación → primer login
> (FASE 2, planes 025-029). Última regresión: 2026-09-12 — **424 tests, 0 fallos**.

## URLs y IDs de producción

| Recurso | Valor |
|---|---|
| Web admin (Appwrite Sites) | https://6aa4cb0a8f6a4c30a83f.appwrite.network |
| Function `admin` (node-18, guard SUPERUSER) | proyecto `6aa332f40001072d0747`, deployment `6aa4c515687c4d9d7361` |
| Endpoint Appwrite | https://fra.cloud.appwrite.io/v1 |
| Plataforma Web registrada | `web-admin-site` → hostname del site |
| APK de prueba | `app/build/outputs/apk/debug/app-debug.apk` (build `cfd8809`) |
| Tablas (DB `main`) | `signups`, `orgs`, `branches`, `settings`, `users`, `members` |

## Preparación (una sola vez)

1. **Fila SUPERUSER**: crear en la tabla `members` una fila con id = UID del superusuario y
   `role = "SUPERUSER"`. Sin esta fila la function rechaza todas las acciones (403).
   *Estado 2026-09-12: tabla `members` vacía — falta decidir la cuenta y crearla.*
2. **WhatsApp del superusuario**: web admin → Configuración → guardar el número
   (se persiste en `settings/app.superuserWhatsapp`; con la fila vacía el registro usa el
   fallback `ContactConfig.WHATSAPP_NUMBER`).

## Script

### 1. Registro (app Android)

1. Instalación limpia del APK → botón **Crear cuenta** en el login.
2. Rol DUEÑO con negocio + sucursales; ADMIN/SELLER sin datos de negocio.
3. Anotar la **contraseña temporal** mostrada (se muestra 1 sola vez, 12 caracteres).
4. Verificar en consola Appwrite: fila `signups/{uid}` con `status=PENDING` y los datos
   capturados (rol, negocio, sucursales, `mustChangePassword=true`).
5. El botón WhatsApp abre `wa.me/<superuserWhatsapp>` con mensaje rol/email/pass
   (fallback `ContactConfig.WHATSAPP_NUMBER` si settings está vacío).

### 2. Web admin (superusuario)

6. Abrir https://6aa4cb0a8f6a4c30a83f.appwrite.network → login con email + contraseña del
   superusuario.
7. «Solicitudes» → aparece el PENDING del paso 2.
8. Aprobar el DUEÑO (org + sucursales se crean automáticamente); verificar contadores del
   «Panel» (+1) y filas nuevas en `orgs`/`branches`/`members`; signup queda APPROVED.
9. Aprobar un ADMIN/SELLER eligiendo org + sucursales; **rechazar** un caso (REJECTED).
10. «Configuración» → guardar el WhatsApp del superusuario.

### 3. App: primer login

11. Login con email + contraseña temporal → pantalla de **cambio obligatorio de
    contraseña** (plan 026; la temporal queda inmediatamente inservible).
12. Tras el cambio, el contador abre con la org/sucursal correctas (plan 028: seed del
    tenant cloud — adopta org+branches aprobadas, idempotente).

### 4. Reset de contraseña (web admin)

13. Web admin → «Usuarios» → Restablecer contraseña → nueva temporal.
14. App: login con la nueva temporal → cambio forzado otra vez.

### 5. Roles en la app (matriz)

15. SELLER: sin operaciones de stock (no alta/merma/ajuste), no borra productos; cierres
    solo de su sucursal.
16. ADMIN: stock + gastos + CRUD productos + historial de su sucursal.
17. OWNER: alcance org completa (todas las sucursales).

## Verificación automática ya cubierta (2026-09-12)

- `testDebugUnitTest`: **424 tests, 0 fallos**; `compileDebugKotlin` + `assembleDebug`
  verdes (APK 18.6 MB). Worktree `mc-wt-029` @ `cfd8809`.
- Caja intacta: planes 025-028 **no tocan** Money/Movement/MovementType/MoneyCounterViewModel
  (signos y `netCashTotal` preservados).
- Web admin LIVE: HTTP 200 en `/` y assets; bundle con endpoint/proyecto/`"admin"` horneados.
- Function smoke: `whoami` sin sesión → 403 `"Acceso denegado: se requiere sesión de
  superusuario"` (guard OK, execution `6aa4cb816b160c43a41c`).

## Si algo falla

| Síntoma | Causa probable | Fix |
|---|---|---|
| Login web rechazado (401 host) | plataforma Web sin el dominio | consola → Settings → Platforms (ya registrada `web-admin-site` 2026-09-12) |
| Todo 403 en el web admin | falta fila `members/{uid}` SUPERUSER | preparación paso 1 |
| Aprobación DUEÑO no crea org | function sin deployment activo | consola → Functions → `admin` → deployment ready |
| WhatsApp no pre-cargado en registro | `settings/app.superuserWhatsapp` vacío | web admin → Configuración |
| Registro no aparece en Solicitudes | signup falló antes de crear fila | consola → TablesDB → `signups`; revisar logs de la app |
