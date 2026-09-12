# Estado, decisiones y pasos — El Luiso (actualizado 2026-09-12)

> **Leé esto primero si retomás el trabajo.** Es el punto de entrada: qué estamos haciendo,
> por qué importa, qué ya se cambió (incluida la base de datos **en producción**), qué quedó
> a medias y en qué orden seguir. Los planes formales están en `plans/`; este documento es el
> hilo que los une.

---

## 1. Qué estamos haciendo y por qué importa

El Luiso es una app Android **offline-first** de conteo de caja e inventario, que se está
convirtiendo en **multi-tenant** (varios negocios, varias sucursales, roles por persona).
Ahora mismo hay dos frentes abiertos a la vez, y conviene no confundirlos:

**Frente A — poner en marcha el alta de usuarios.** Hay un panel web de superusuario
(`webadmin/`, React + Appwrite Function) que sirve para aprobar registros, crear negocios y
sucursales, y asignar roles. El objetivo inmediato del dueño: **dar de alta usuarios desde el
sitio, no tocando la base a mano**, y de paso probar el alta y el panel al mismo tiempo.

**Frente B — cerrar una escalada de privilegios.** El rol de la sesión se resuelve por red.
Si no se puede resolver, la app concedía **todos los permisos**. En una app offline-first eso
significa que un vendedor sin señal operaba con poderes de dueño. El plan 030 cerró la mitad
(la caída transitoria); el plan 033 cierra la otra (la ausencia permanente de membresía).

Por qué importa el frente B más de lo que parece: el modelo de permisos es la **única**
frontera real entre negocios. Los tests del plan 024 verifican aislamiento a nivel de dominio,
pero pasaban con la base abierta de par en par, porque nadie cubría ese cableado.

---

## 2. Dónde está el código

| Rama | Qué tiene | Estado |
|---|---|---|
| `main` | base estable | El webadmin **no** está acá todavía |
| `feature/multi-tenant` | planes 017–029 (fundación multi-tenant + onboarding + webadmin) | sin mergear a `main`, 424 tests |
| `plan/030` | permisos offline fail-closed, 6 commits, **444 tests** | sin mergear, **Gate B pendiente** del dueño |

**Decisión del dueño (2026-09-12):** el deploy del sitio apunta a **`main`**. Las pruebas del
APK siguen en ramas aparte, y a `main` sólo suben los cambios de la página. O sea que el
webadmin tiene que estar en `main` para que el auto-deploy sirva de algo.

---

## 3. Decisiones tomadas (no volver a discutirlas sin el dueño)

| Fecha | Decisión | Por qué |
|---|---|---|
| 2026-09-12 | **SUPERUSER = `luisricoblanco2014@gmail.com`**, como fila `members` con `orgId: "platform"` | El superusuario no pertenece a ningún negocio; `"platform"` es el centinela |
| 2026-09-12 | **Los labels de Auth fueron rechazados** para el SUPERUSER | Se evaluó mover SUPERUSER a un label de Appwrite (permitiría que Luis tenga también rol de negocio con la misma cuenta). El dueño prefirió no tocar la Function: **Luis usa otra cuenta para probar roles de negocio** |
| 2026-09-12 | El panel web queda **sólo para SUPERUSER**; los reportes viven en la app bajo OWNER/ADMIN | El panel no tiene datos operativos que reportar: ventas, cierres y stock siguen siendo JSON local en el teléfono. Además `Role.kt:105` ya define `canViewReports()` = OWNER/ADMIN, y el contrato del plan 018 dice que el SUPERUSER está bloqueado de las operaciones |
| 2026-09-12 | **Sin membresía usable no se entra — ni como SELLER** | SELLER igual vende, cobra fiado y cierra caja; un usuario sin `orgId` ni sucursal generaría movimientos sin dueño. Queda bloqueado hasta que el SUPERUSER le asigne negocio, sucursal y rol |
| 2026-09-12 | **Revierte al 030**: `DefaultPermissionService` **sí** se endurece | El 030 lo dejó permisivo "para instalaciones legacy". No hay instalaciones legacy: la app nunca se publicó |
| 2026-09-12 | No se introduce un rol `PENDING` | La ausencia de membresía ya es la señal. Dos representaciones de "sin acceso" terminan discrepando |
| 2026-09-12 | `settings` queda legible por cualquier usuario | La app necesita `superuserWhatsapp` para mandar el mensaje del alta. Excepción aceptada y documentada |
| 2026-09-12 | **Deploy por Git** en vez de tarball | Ver §7 |
| 2026-09-12 | Arquitectura de inventario compartido: **stock server-authoritative** | `advisor-plans/008-shared-inventory-DESIGN.md`. El movimiento es la unidad de verdad y ningún cliente escribe nunca una cantidad de stock |

---

## 4. Cambios YA APLICADOS en Appwrite (producción, sin vuelta atrás por código)

Proyecto `6aa332f40001072d0747` · endpoint `https://fra.cloud.appwrite.io/v1` · base `main`.

1. **Fila SUPERUSER creada**: `members/6aa352520004960be987` =
   `{ orgId: "platform", role: "SUPERUSER", branchIds: [] }`, con permiso
   `read("user:6aa352520004960be987")`.
2. **Tabla `members` cerrada**: pasó de `read("users")` + `rowSecurity: false` a
   `$permissions: []` + `rowSecurity: true`. Antes, **cualquier usuario autenticado leía el
   rol y el `orgId` de cualquier otro**.
3. **Cuenta del dueño borrada para probar el alta desde cero**: se eliminaron el usuario de
   Auth `6aa3494c0036c13b898d` (`marvelalvarez89@gmail.com`) y su fila `users`. Queda **un
   solo usuario**: Luis. La cuenta de consola de Appwrite del dueño
   (`6aa332f15392d347e2d3`) es otra y no se tocó.

### Tablas y su estado de permisos

| Tabla | Permisos | rowSecurity | Pendiente |
|---|---|---|---|
| `members` | `[]` | ✅ true | — |
| `users` | `read("users")`, `create("users")` | false | **cerrar** (plan 033): filtra email/nombre/access de todos |
| `orgs` | `read("users")` | false | **cerrar** (plan 033): filtra todos los negocios y sus WhatsApp |
| `branches` | `read("users")` | false | **cerrar** (plan 033): filtra todas las sucursales |
| `signups` | `create("users")` | false | ya está bien (sin lectura) |
| `settings` | `read("users")` | false | se deja así a propósito |

---

## 5. ⚠️ La trampa que hay que entender antes de tocar permisos

`members` ya está cerrada, así que **cada fila tiene que llevar su propio
`read("user:<uid>")`**. Si a una fila le falta, Appwrite responde **404** a la lectura que el
usuario hace de su *propia* fila. Y 404 es exactamente cómo la app escribe "no tengo
membresía" (`AppwriteMembershipRepository.kt:24`, `isMemberRowMissing`), lo que **hoy todavía
significa todos los permisos**.

Es decir: **endurecer los permisos puede reabrir el fail-open del plan 030 por otra puerta.**
Por eso el plan 033 pone primero el default en deny-all (Parte A) y sólo después cierra las
tablas (Parte B). Nunca al revés.

Corolario operativo: **`approveSignup` tiene que escribir la fila con su permiso de lectura, y
ese fix tiene que estar desplegado antes de aprobar a nadie.** Aprobar con la Function vieja
crea una fila ilegible → 404 → permisos totales para ese usuario.

---

## 6bis. Deploy del 2026-09-12 (hecho)

Se desplegaron los dos fixes por el camino del tarball (release de GitHub como asset),
como medida puntual hasta que Git quede conectado:

| Recurso | Deployment | Estado |
|---|---|---|
| Function `admin` | `6aa5c4098cf09f8e0956` | `ready`, **activa** — el fix del permiso de `members` ya está en producción |
| Site `admin-web` | `6aa5c40fcf23d03f5bc7` | `ready` y activa, sirviendo en `6aa5c4103a466b90b992.appwrite.network` (ver abajo: la URL vieja NO se actualiza) |

Fuente: release `webadmin-deploy-2026-09-12b` en `l3vram/money-counter-sdd`, assets
`admin-web.tar.gz` (24.643 B) y `admin-function.tar.gz` (4.781 B). Los `sourceSize` que
reportó Appwrite coinciden byte a byte, así que no hubo corrupción. Build del sitio en verde
(`npm run typecheck` y `npm run build` locales también).

### ⚠️ Las URLs `.appwrite.network` están clavadas a un deployment

Esto costó un diagnóstico equivocado, así que queda escrito. Tras activar el deployment nuevo,
`https://6aa4cb0a8f6a4c30a83f.appwrite.network` seguía sirviendo el build viejo. **No era
caché de edge** (esa fue la primera hipótesis, y era falsa): Appwrite crea **una URL por
deployment**, y esa URL es la del deployment de las 03:46. Se ve en `proxy_list_rules`: la
regla de ese dominio tiene `deploymentId: 6aa4cb0a3ae416106f82` fijo. Va a mostrar el build
viejo para siempre.

| URL | Sirve |
|---|---|
| `6aa4cb0a8f6a4c30a83f.appwrite.network` | deployment viejo — **la de todos los docs anteriores, ya no sirve** |
| **`6aa5c4103a466b90b992.appwrite.network`** | **deployment nuevo, con los fixes — usar esta hoy** |
| `adm.elluiso.com` | sigue al deployment activo (ya se movió solo al nuevo), pero **sin DNS** |

Se diagnostica con el header **`x-appwrite-deployment-id`** de la respuesta, que dice qué
deployment está sirviendo realmente.

**Consecuencia para CORS:** cada URL nueva necesita su plataforma Web, o Appwrite responde el
mismo `Invalid Origin` del §6. Se registraron tres: la URL del deployment nuevo,
`adm.elluiso.com` y `localhost` (esta última destraba el desarrollo local del panel).

**La URL estable es `adm.elluiso.com`**, cuya regla ya sigue al deployment activo. Le falta
DNS: la verificación de Appwrite falla con *"missing CNAME record"*, y de hecho **`elluiso.com`
no resuelve nada todavía** (ni el apex), así que no es sólo el CNAME. El valor exacto del CNAME
lo muestra la consola en la pestaña Domains del sitio. Mientras tanto, cada deploy va a generar
una URL nueva que hay que registrar como plataforma — razón de más para terminar el dominio
propio.

## 6. Cambios de código hechos (ya desplegados — ver §6bis)

| Archivo | Cambio | Estado |
|---|---|---|
| `webadmin/function/src/index.js` (~línea 130) | `approveSignup` escribe la fila `members` con `permissions: [sdk.Permission.read(sdk.Role.user(signupId))]` | **necesario para que el alta funcione**; sin desplegar |
| `webadmin/app/src/api.ts` (`login()`) | `deleteSession('current')` en try/catch antes de crear sesión | arregla `Creation of a session is prohibited when a session is active` |
| `webadmin/app/src/api.ts` (`callFunction()`) | agrega `X-Appwrite-Project` y cambia `Authorization: Bearer` por `X-Appwrite-JWT` | arregla el `Failed to fetch`; **sin esto el panel no puede llamar a la Function** |
| `plans/033-no-membership-no-access.md` | plan nuevo, P0, 7 pasos | TODO |
| `plans/README.md` | fila del 033, waves, decisiones, y los cambios de base ya aplicados | — |
| `docs/superuser-bootstrap.md` | actualizado de "bloqueado" a "sembrado" | — |

### El bug del login, en detalle

`login()` llamaba a `createEmailPasswordSession` sin borrar la sesión activa. Cuando Luis
intentó entrar **antes** de que existiera la fila SUPERUSER, Appwrite creó la sesión bien y el
403 llegó después, en `whoami`. La sesión quedó viva y el formulario quedó condenado a ese
error. Atajo sin desplegar: recarga dura — `App.tsx:33` llama a `whoami()` al montar con la
sesión existente, y ahora que la fila existe debería entrar derecho.

### El `Failed to fetch`, diagnosticado y arreglado (2026-09-12)

Segundo error, distinto del anterior y con la misma raíz: el panel no podía llamar a la
Function. `callFunction` en `api.ts` esquiva el SDK y hace un `fetch` a mano, pero le faltaban
los dos headers que el SDK agrega solo:

- **`X-Appwrite-Project` no es opcional.** Sin él Appwrite no sabe a qué proyecto pertenece la
  petición, así que no puede encontrar la plataforma Web registrada y responde
  `403 general_unknown_origin` con el mensaje *"Invalid Origin. Register your new client…"*.
- Ese 403 **no lleva `Access-Control-Allow-Origin`**, así que el navegador bloquea la respuesta
  y `fetch` falla con un TypeError: el **`Failed to fetch`** opaco que se veía en pantalla.
- Y un JWT de proyecto viaja en **`X-Appwrite-JWT`**, no en `Authorization: Bearer`.

Verificado con curl contra el endpoint real: sin el header → 403 sin CORS; con
`X-Appwrite-Project` + `X-Appwrite-JWT` → llega a validar el JWT (401 con un token falso, que
es lo correcto). La plataforma Web `web-admin-site` estaba bien registrada desde el principio:
el mensaje de "registrá tu cliente" era una pista falsa.

**Ojo para el futuro:** cualquier llamada a la API de Appwrite que no pase por el SDK necesita
`X-Appwrite-Project`, y su ausencia se disfraza de problema de CORS. Lo ideal sería usar
`Functions.createExecution()` del SDK en vez del `fetch` a mano; se dejó el `fetch` para no
ampliar el cambio.

`localhost` ya quedó registrado como plataforma Web para desarrollar el panel en local.

---

## 7. Deploy: por qué se cambia a Git

El proceso documentado en `webadmin/README.md` es frágil: empaquetar un tarball, subirlo a una
URL pública que los build servers de Appwrite puedan alcanzar (catbox y x0.at bloquean
datacenters; el base64 inline por MCP arriesga corrupción) y crear el deployment a mano.

Con Git conectado, un solo repo sirve para los dos recursos vía `providerRootDirectory`: la
Function desde `webadmin/function`, el Site desde `webadmin/app`; y push a la rama = deploy.

**Verificado**: el proyecto **no** tiene instalación de VCS (`vcs_list_installations` → 0).
Conectarla es un paso manual del dueño en el navegador: consola de Appwrite → Function `admin`
→ Settings → Git → instalar la Appwrite GitHub App sobre `l3vram/money-counter-sdd`. Después
se cablean los dos recursos por MCP (`installationId`, `providerRepositoryId`,
`providerRootDirectory`, `providerBranch`). **Rama de deploy: `main`** (decisión del dueño).

---

## 8. Pasos siguientes, en orden

| # | Paso | Bloquea / por qué |
|---|---|---|
| ~~1~~ | ~~Mergear el webadmin a `main`~~ | ✅ hecho: `origin/main` en `bcdfa45` (incluye planes 017–030 + webadmin) |
| 2 | **Conectar la GitHub App** (paso manual del dueño) y cablear Function + Site | Sin esto sigue el tarball a mano |
| ~~3~~ | ~~Desplegar la Function~~ | ✅ hecho: deployment `6aa5c4098cf09f8e0956` activo |
| ~~4~~ | ~~Desplegar el sitio~~ | ✅ hecho: deployment `6aa5c40fcf23d03f5bc7` activo |
| 5 | **Gate B del plan 030**: probar el APK debug y mergear | 033 y 032 salen de esa rama; cada plan apilado encima es un rebase peor |
| 6 | **Plan 033** (P0) — fail-closed + cerrar `users`/`orgs`/`branches` | La mitad de base ya está aplicada; el código tiene que alcanzarla |
| 7 | **Plan 032** — interfaces de repositorio `suspend` | Habilita cualquier implementación de red (paso 2 del diseño F2) |
| 8 | **F2 paso 3** — schema + Function `applyMovement` | El diseño 008 tiene 3 preguntas abiertas (§8) y 2 verificaciones técnicas (§7) sin cerrar |
| 9 | **Plan 031** — limpieza de glosario y ramas | P3, archivos disjuntos, cuando se quiera |

---

## 9. Cómo verificar (el camino de prueba real)

`orgs`, `branches` y `signups` están **vacías**: no hay nada que sembrar a mano, la org y las
sucursales las crea `approveSignup` al aprobar un DUEÑO. El camino es el flujo real:

1. Luis entra al panel (**https://6aa5c4103a466b90b992.appwrite.network**) → `whoami` devuelve `role: "SUPERUSER"` en vez de 403, y carga su
   WhatsApp desde Configuración (la fila `settings/app` existe con el campo vacío).
2. Con la Function desplegada, desde la app se registra una cuenta nueva como **DUEÑO** con
   negocio y sucursales → crea el `signup` en PENDING.
3. Luis lo aprueba en el panel → se crean `orgs`, `branches`, la fila `members` **con su
   permiso de lectura**, y `users.access = APPROVED`.
4. Esa cuenta entra a la app → permisos OWNER, org y sucursales sincronizadas (plan 028).
5. Se registra un **SELLER** contra la misma org, se aprueba con una sucursal, y se verifican
   sus restricciones.
6. **Regresión del plan 030**: modo avión, reiniciar la app, y los botones de alta, merma y
   stock siguen ocultos para el SELLER.

El criterio del panel no es "creé las filas": es que la Function **deje de responder 403**.
Antes del bootstrap el smoke test daba 403, que era el comportamiento correcto con `members`
vacía.

---

## 10. Datos del entorno

| | |
|---|---|
| Endpoint | `https://fra.cloud.appwrite.io/v1` |
| Project ID | `6aa332f40001072d0747` ("El luiso (MoneyCounter)", región `fra`) |
| Organización | `6aa332f3000389e6ee16` ("Personal Projects", tier-0) |
| Database | `main` |
| Tablas | `users`, `members`, `signups`, `orgs`, `branches`, `settings` |
| Function | `admin` — node-18, entrypoint `src/index.js`, deployment `6aa4c515687c4d9d7361` (ready), `execute: ["users"]`, scopes `tables.*`/`rows.*`/`users.*` |
| Site | `admin-web` → deployment activo en https://6aa5c4103a466b90b992.appwrite.network · estable pendiente `adm.elluiso.com` (sin DNS) |
| Repo | `github.com/l3vram/money-counter-sdd` |
| SUPERUSER | `luisricoblanco2014@gmail.com`, uid `6aa352520004960be987` |
| MCP Appwrite | `.mcp.json`, `https://mcp.appwrite.io/`, OAuth con scope **muy amplio** (`project:all`, `organization:all`) — aceptado conscientemente |

---

## 11. Archivos clave

| Para entender | Archivo |
|---|---|
| La guardia del panel y el alta | `webadmin/function/src/index.js` (`getCaller` :51, guardia :192, `approveSignup` :80) |
| La matriz de permisos | `app/src/main/java/com/moneycounter/domain/Role.kt` |
| El default que concede todo (a endurecer) | `app/src/main/java/com/moneycounter/domain/PermissionService.kt:33` |
| La lectura del rol y la política del 404 | `app/src/main/java/com/moneycounter/appwrite/AppwriteMembershipRepository.kt` |
| El caché de rol offline | `app/src/main/java/com/moneycounter/access/MemberCache.kt` |
| Estados de acceso de la app | `app/src/main/java/com/moneycounter/access/AppAccessState.kt` |
| Lectura de org/sucursales desde el cliente | `app/src/main/java/com/moneycounter/appwrite/AppwriteCloudOrgRepository.kt` |
| Estado de todos los planes | `plans/README.md` |
| El bootstrap del superuser | `docs/superuser-bootstrap.md` |
| El guion E2E del alta | `docs/onboarding-e2e.md` |
| El diseño del inventario compartido | `advisor-plans/008-shared-inventory-DESIGN.md` |
