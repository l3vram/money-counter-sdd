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
| Function `admin` | `6aa5cb786166e18d64f0` | `ready`, **activa** — permiso de `members` + la key dinámica del header (ver §6ter) |
| Site `admin-web` | `6aa5d181a6457d79024a` | `ready` y activa, sirviendo en `elluiso.l3vram.com` (ver abajo: la URL vieja NO se actualiza) |

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
| **`elluiso.l3vram.com`** | **deployment actual (2026-09-12d) — usar esta hoy** |
| `6aa5c4103a466b90b992.appwrite.network` | deployment 2026-09-12b, ya viejo |
| `adm.elluiso.com` | sigue al deployment activo (ya se movió solo al nuevo), pero **sin DNS** |

Se diagnostica con el header **`x-appwrite-deployment-id`** de la respuesta, que dice qué
deployment está sirviendo realmente.

**Consecuencia para CORS:** cada URL nueva necesita su plataforma Web, o Appwrite responde el
mismo `Invalid Origin` del §6. Se registraron tres: la URL del deployment nuevo,
`adm.elluiso.com` y `localhost` (esta última destraba el desarrollo local del panel).

**Dato que cambia el calculo: con Git conectado, la URL de la rama es estable** entre
deployments ("The branch URL will remain consistent for all deployments made for code pushed
to a specific branch"). O sea que conectar Git no es solo comodidad de deploy: termina con la
ruleta de URLs y con tener que registrar una plataforma Web nueva cada vez.

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

## 6ter. La causa raíz del 403: el panel nunca funcionó

Tras arreglar el `Failed to fetch`, el login devolvía *"Acceso denegado: se requiere sesión de
superusuario"* — con la fila SUPERUSER creada y la identidad del llamador bien resuelta. La
causa era anterior a todo este trabajo:

```js
.setKey(process.env.APPWRITE_FUNCTION_API_KEY || '')   // ← vacío en ejecución
```

La doc de Appwrite es explícita: la key dinámica llega **de dos formas distintas**, y sólo una
sirve en ejecución. `APPWRITE_FUNCTION_API_KEY` existe durante el **build**; durante la
**ejecución** la key viaja en el header **`x-appwrite-key`**. Leyendo sólo la variable de
entorno, el cliente quedaba **sin credencial**: toda lectura fallaba, `memberRole` convertía
la excepción en `null`, y `null` se responde con un 403 plano.

O sea que **el panel nunca funcionó**, ni una vez. El 403 que el plan 029 anotó como
"comportamiento correcto con `members` vacía" era en realidad este bug, tapado por la
coincidencia de que la tabla estaba vacía.

Arreglado: `req.headers['x-appwrite-key']` primero, la env var como respaldo, y un `error()`
si no hay ninguna. Además `memberRole` ahora **loguea** por qué falló la lectura, porque su
`catch` silencioso es lo que hizo que esto costara tanto: "no tengo membresía" y "mi cliente
está roto" terminaban indistinguibles.

Verificado con un JWT emitido por `users_create_jwt` para la cuenta de Luis, llamando la
Function igual que el panel: `whoami` → **200** con `role: "SUPERUSER"`, y `listUsers`,
`listOrgs`, `listSignups` y `getSettings` → 200. Deployment `6aa5cb786166e18d64f0`.

**Truco de diagnóstico reutilizable:** `users_create_jwt` permite reproducir exactamente lo
que hace el panel sin la contraseña del usuario ni un navegador. Ojo con el payload: al crear
una ejecución por REST el cuerpo va como `{"body":"<json como string>","method":"POST"}`, no
como el JSON de la acción directamente.

## 6quater. El tercer bug del mismo origen, y por qué el panel tarda ~4 s

Con la key arreglada, el panel devolvía **"Acción desconocida: undefined"**. Misma raíz que
los dos anteriores: `callFunction` armaba el REST a mano. Al crear una ejecución, el JSON de
la acción **va envuelto en el campo `body`** de la petición (`{"body":"{\"action\":...}"}`),
no como la petición misma. Mandándolo directo, Appwrite no encuentra `body`, la Function
recibe el cuerpo vacío y `params.action` queda `undefined`.

`callFunction` ahora usa **`Functions.createExecution()` del SDK**, con un cliente propio
(`setJWT` es por cliente, y el cliente de sesión no debe empezar a mandar un JWT en cada
llamada a `account`). Los tres bugs del panel salieron de esquivar el SDK: headers, forma del
payload y manejo de la respuesta. **No armar peticiones a Appwrite a mano.**

### Los ~4 segundos, medidos

| | |
|---|---|
| Duración interna de la Function | **0,10 – 0,22 s** |
| Round-trip completo de una ejecución | **0,87 – 1,16 s** |
| RTT de una llamada normal a la API | ~0,25 s (el edge contesta a 8 ms; el origen está en Frankfurt) |
| Arranque en frío de la Function | ~1,2 s la primera vez tras estar inactiva |

O sea que **~85% del tiempo es plataforma, no código nuestro**: el salto al origen y el
arranque sincrónico del contenedor. El login encadena cuatro viajes secuenciales
(`deleteSession` → `createEmailPasswordSession` → `createJWT` → `createExecution`), y ahí
salen los ~4 s.

Las palancas reales, en orden de rendimiento:

1. **Menos ejecuciones.** Cada acción cuesta ~1 s de plataforma. Si el Dashboard dispara
   varias al entrar, juntarlas en una sola acción (un `bootstrap` que devuelva todo) convierte
   N segundos en uno. Es la única mejora grande.
2. **`listUsers` tiene un N+1**: hace un `getRow` de `signups` por cada usuario. Con 2
   usuarios no se nota; con 50 va a doler. Se arregla con un `listRows` y un mapa.
3. `getCaller` hace `users.get` y el `getRow` de `members` en serie; un `Promise.all` ahorra
   un salto interno (~50 ms). Menor.
4. Arranque en frío: no hay nada que hacer en el plan tier-0.
5. Región: el origen está en Frankfurt. Acercarlo implicaría recrear el proyecto; no vale la
   pena.

Nada de esto está hecho: es diagnóstico, no plan.

## 6quinquies. Dominio propio: el panel y la API bajo `l3vram.com` (2026-09-14)

Resuelto de raíz. El panel y la API ahora comparten dominio registrable, así que para el
navegador son **el mismo sitio**: las cookies son de primera parte y se termina la clase de
fallos de Safari (`Load failed`) que venía del cross-site.

| Qué | URL | Regla |
|---|---|---|
| Panel | **https://elluiso.l3vram.com** | `ab9f3b7e336130d966494e480d290c45` (site, sigue al deployment activo) |
| API | **https://api.elluiso.l3vram.com/v1** | `ac4b789cdb6c8669bac01d9f912e76f7` (api) |

**DNS en Cloudflare** — dos CNAME, los dos en **DNS-only (nube gris)**; con el proxy de
Cloudflare activo Appwrite no puede verificar ni emitir el certificado:

```
elluiso      CNAME  fastly.appwrite.systems
api.elluiso  CNAME  fastly.appwrite.systems
```

Ese destino no está en la doc: se deduce de que tanto los sitios `*.appwrite.network` como
`fra.cloud.appwrite.io` resuelven por CNAME a `fastly.appwrite.systems`, su ingress.
`l3vram.com` no tiene registros CAA, así que no hubo que agregar ninguno.

**Trampa al verificar:** la regla de la API falló **un segundo** después de empezar, con
*"missing CNAME record"* — pero el registro ya resolvía. Era **caché negativa**: Appwrite
consultó el DNS al crear la regla, cuando el nombre todavía no existía, y el resolver guardó
el NXDOMAIN. Se arregla **reintentando** (`proxy_update_rule_status`), no cambiando el DNS.
El sitio tardó ~3 min en tener certificado; la API, ~2 min tras el reintento.

**Variable del sitio:** `VITE_APPWRITE_ENDPOINT` pasó a `https://api.elluiso.l3vram.com/v1`
y se redeployó para hornearla. Verificado en el bundle servido.

**Verificado de punta a punta** por el dominio nuevo, con un JWT de la cuenta de Luis:
preflight con `access-control-allow-origin: https://elluiso.l3vram.com`, y `whoami`,
`listUsers` y `getSettings` → **200**, `role: "SUPERUSER"`.

**La app Android no se tocó**: sigue apuntando a `fra.cloud.appwrite.io`, que funciona igual.

Pendiente menor de limpieza: las reglas y plataformas Web de los deployments viejos
(`*.appwrite.network`) y la de `adm.elluiso.com`, que quedó sin DNS, ya no hacen falta.

## 6sexies. Fuera el JWT: la sesión es la credencial (2026-09-14)

Último eslabón del `Failed to fetch` / 403. Con el dominio propio, la cookie de sesión pasó a
ser de primera parte, y el JWT —que era el parche para el cross-site— quedó no sólo de más,
sino **dañino**:

- **Cuando hay cookie, Appwrite ignora el JWT.** Verificado: con una cookie basura más un JWT
  válido, la petición se resuelve como invitado (401 `user_unauthorized`).
- **Un JWT muere con la sesión que lo emitió.** Nuestro propio `login()` hace
  `deleteSession('current')` al empezar, así que mataba JWTs emitidos antes. Esto invalidó una
  prueba de diagnóstico intermedia: un JWT vigente y sin vencer fallaba como invitado porque
  su sesión ya no existía.
- Costaba un round-trip (~250 ms) por cada arranque de sesión.

**La prueba que lo decidió** la corrió el dueño en su navegador: dos `fetch` idénticos a
`executions` con el header de proyecto, diferenciándose sólo en `credentials`:

| | Resultado |
|---|---|
| A · sin cookies | 401 `user_unauthorized` (invitado) — esperado |
| B · con cookies | **201**, objeto de ejecución real |

O sea que la cookie sola autentica y ejecuta. Eso descartó de una vez el bloqueador de
contenido, la manipulación de headers y el CORS: el navegador estaba bien.

`callFunction` ahora usa **un solo cliente** (el de sesión) y llama a `createExecution` sin
`setJWT`. `clearAuth()` quedó vacío a propósito: ya no hay nada cacheado que limpiar, y
`logout()` borra la sesión.

**Lección transferible:** el `deleteSession` preventivo del login aparece en la consola como
`DELETE /account/sessions/current 401` cuando no hay sesión. Es inofensivo y esperado —
está dentro de un try/catch—, pero ensucia el diagnóstico. No perseguirlo.

## 6septies. Desajuste de contrato: `rows` vs `users`/`signups` (2026-09-14)

Con el login ya funcionando, los menús **Usuarios** y **Solicitudes** tiraban
`Cannot read properties of undefined (reading 'length')`. El panel espera `{ rows, total }`
(`ListResult<T>` en `types.ts:52`) y hace `setUsers(result.rows)`, pero la Function devolvía
`{ users, total }` y `{ signups, total }`. `result.rows` quedaba `undefined`, entraba así al
estado y explotaba al renderizar.

Organizaciones y Sucursales funcionaban porque pasan por `listAll`, que ya devuelve `rows`.

Arreglado en el servidor, que era donde estaba la inconsistencia: las cuatro acciones de lista
responden `{ rows, total }`. Verificado por API: las cuatro devuelven 200 con `rows` como
lista. Deployment `6aa83188ab226e5f893e`.

**Por qué TypeScript no lo atrapó:** la respuesta de la Function cruza el cable como `unknown`
y se castea (`return payload.data as T`). El tipo declarado era correcto y la realidad no, y
el cast lo tapó. Cualquier acción nueva conviene verificarla contra la Function, no confiar en
la firma.

Sigue pendiente (rendimiento, no bug): el **N+1 de `listUsers`**, que hace un `getRow` de
`signups` por usuario.

## 6octies. Bugs de la prueba de onboarding (2026-09-14)

Seis fallas encontradas por el dueño probando el alta de punta a punta. Todas arregladas
salvo donde se indica.

### 1. Aprobar fallaba: `Row with the requested ID ... could not be found`

`approveSignup` hacía `updateRow` sobre `users`, pero **el alta no crea esa fila**: la escribe
`ensureUserDocument()` recién cuando la app arranca con sesión. Quien se registra y cierra la
app no la tiene, así que era **imposible aprobarlo desde el panel**. Ahora es `upsertRow`, con
el email que ya trae la fila de `signups`.

### 2. La aprobación a medias duplicaba organizaciones

Consecuencia del bug 1, y peor que él. `approveSignup` escribe varias filas en secuencia y
**no es una transacción**: creaba la organización y las sucursales, después fallaba en `users`
y dejaba el `signup` en PENDING. Cada reintento generaba un `orgId` nuevo — **tres
organizaciones "Las Pepas" salieron de tres intentos**, con sus tres sucursales, y los permisos
vacíos porque el otorgamiento corre después del paso que fallaba.

Arreglado con idempotencia: si el signup ya tiene membresía apuntando a una organización, se
**reanuda esa** en vez de crear otra. Los huérfanos se borraron a mano (3 orgs, 3 branches, 1
members) para que la prueba arrancara limpia.

**Pendiente de fondo:** la aprobación sigue sin ser atómica. Appwrite tiene transacciones
(`transaction_id` en las operaciones de TablesDB) y sería el arreglo correcto. Candidato a
plan propio.

### 3. El login creaba cuentas, y una contraseña mal escrita decía "la cuenta ya existe"

La raíz de dos síntomas a la vez:

```kotlin
private fun isUserNotFound(e: AppwriteException): Boolean =
    e.type?.contains("user_not_found") == true || e.code == 401   // ← el problema
```

Una **contraseña incorrecta también es 401**. Así que el login leía "credencial inválida" como
"no existe la cuenta", intentaba registrarla, y el error que salía era *"la cuenta ya existe"*.
Y cuando el correo de verdad no existía, **creaba la cuenta desde la pantalla de login**: sin
fila en `signups`, o sea sin rol ni negocio, invisible en Solicitudes e imposible de aprobar.
Así quedó la cuenta `marvelalvarez89@gmail.com` durante la prueba.

Arreglado: **iniciar sesión nunca crea cuentas.** El registro tiene su propia pantalla, que es
donde se piden rol y negocio.

### 4. Cuenta existente sin aprobar

Sale del arreglo anterior: con la contraseña correcta hay sesión y la app muestra la pantalla
de pendiente de aprobación, que ya existía. Con la contraseña incorrecta, dice eso.

### 5. Los mensajes de error del login

Appwrite responde **lo mismo** para una contraseña incorrecta y para un correo que no existe,
a propósito, para que el formulario no sirva para descubrir quién tiene cuenta. Así que el
mensaje ofrece las dos lecturas: *"Correo o contraseña incorrectos. Si todavía no tienes
cuenta, crea una primero."*

Se separó del caso del **cambio** de contraseña (`user_invalid_password`), donde ofrecer crear
una cuenta no tiene sentido: ahí dice *"La contraseña actual es incorrecta"*.

### 6. La contraseña temporal no se podía copiar

Era un `Text` común: no se podía ni seleccionar, y es el único modo de entrar a la cuenta.
Ahora está dentro de un `SelectionContainer` y tiene botón **Copiar contraseña**, que confirma
en el propio botón.

### Y antes, el reset de contraseña del superusuario

Mismo patrón que el bug 1: `users.updatePassword` corría **primero** y funcionaba, después el
`updateRow` de la marca de cambio forzado tiraba 404 para una cuenta sin fila en `signups`, y
el panel reportaba fallo **sobre un éxito**. La contraseña ya había cambiado. Ahora la marca es
best-effort y sólo tolera un 404.

### El patrón que comparten

Cuatro de estos bugs son el mismo error de diseño: **una secuencia de escrituras donde un paso
accesorio falla y se lleva puesto el resultado del paso esencial**. Vale la pena mirar con esa
lente cualquier acción nueva de la Function: qué pasa si el paso 3 de 4 falla, y si el paso que
falló era realmente indispensable.

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

1. ✅ **Hecho y verificado por API**: `whoami` devuelve `role: "SUPERUSER"` y las acciones de
   lectura responden 200. Falta que Luis entre por el navegador
   (**https://elluiso.l3vram.com**) y cargue su WhatsApp desde
   Configuración (la fila `settings/app` existe con el campo vacío).
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
| Site | `admin-web` → deployment activo en https://elluiso.l3vram.com · estable pendiente `adm.elluiso.com` (sin DNS) |
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
