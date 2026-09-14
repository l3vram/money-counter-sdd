# Estado y pasos — El Luiso

> **Punto de entrada para retomar el trabajo.** Última actualización: **2026-09-14, 23:30 UTC**.
>
> Si sos un agente que arranca de cero: leé las secciones 1 a 5 antes de tocar nada. La
> sección 9 ("Trampas") te va a ahorrar horas — cada una costó un diagnóstico equivocado.
> Los planes formales están en `plans/`; este documento es el hilo que los une.

---

## 1. Qué estamos haciendo y por qué importa

El Luiso es una app Android **offline-first** de conteo de caja e inventario, convirtiéndose
en **multi-tenant** (varios negocios, varias sucursales, roles por persona). Hay dos frentes:

**Frente A — el alta de usuarios.** Un panel web de superusuario (`webadmin/`, React +
Appwrite Function) aprueba registros, crea negocios y sucursales, y asigna roles. El objetivo:
dar de alta usuarios **desde el sitio**, no tocando la base a mano.

**Frente B — la escalada de privilegios.** El rol de la sesión se resuelve por red, y si no se
podía resolver la app concedía **todos los permisos**. En una app offline-first, un vendedor
sin señal operaba con poderes de dueño. El plan 030 cerró la caída transitoria; el **plan 033**
cerró la ausencia permanente. Importa más de lo que parece: el modelo de permisos es la
**única** frontera real entre negocios, y los tests del plan 024 verifican aislamiento de
dominio pero pasaban con la base abierta de par en par.

---

## 2. Dónde está el código, y un riesgo activo

| Rama | Commit | Qué tiene |
|---|---|---|
| `main` | `948fc9f` | **Todo, incluido el plan 033 completo.** Rama de deploy del sitio |
| `plan/033` | `948fc9f` | idéntica a `main`; ya mergeada, se puede borrar |

> El riesgo de divergencia que figuraba acá **quedó resuelto el 14/09**: el dueño cerró el
> flujo de login (Gate B), se mergeó `plan/033` a `main` por fast-forward sin conflictos, y se
> verificó sobre `main` — 471 tests, 0 fallos, `assembleDebug` OK. Producción y `main` vuelven
> a coincidir.

Los cambios de la **app Android** del plan 033 y de los bugs de hoy **no están desplegados**:
requieren instalar un APK nuevo (`./gradlew :app:installDebug`).

---

## 3. Qué está vivo ahora

| Recurso | Valor |
|---|---|
| Panel | **https://elluiso.l3vram.com** (estable, no cambia por deploy) |
| API | **https://api.elluiso.l3vram.com/v1** |
| Function `admin` | deployment **`6aa842e61d6162ac0269`** |
| Site `admin-web` | deployment **`6aa8344490f6a26140fd`** |
| Tests | **471**, 0 fallos, en `plan/033` |
| Duración de sesión | 1 año (máximo de Appwrite; no existe "para siempre") |

### Permisos de las tablas (ya aplicado en producción)

| Tabla | Permisos | rowSecurity | Estado |
|---|---|---|---|
| `members` | `[]` | ✅ true | cerrada |
| `users` | `create("users")` | ✅ true | cerrada |
| `orgs` | `[]` | ✅ true | cerrada |
| `branches` | `[]` | ✅ true | cerrada |
| `signups` | `create("users")` | ✅ true | cerrada; el `rowSecurity` se activó el 14/09 para que el usuario pueda leer SU fila — ver §9.11 |
| `settings` | `read("users")` | false | **legible a propósito**: la app necesita el WhatsApp del superusuario |

Antes, `members`, `users`, `orgs` y `branches` tenían `read("users")`: cualquier usuario
autenticado leía el rol y el negocio de todos. La Function usa API key, así que **no le afecta**
el cierre; la app lee sólo su propia fila.

### Datos actuales

- **SUPERUSER**: `luisricoblanco2014@gmail.com`, uid `6aa352520004960be987`, fila en `members`
  con `orgId: "platform"`.
- `settings/app.superuserWhatsapp = "+5352405401"` — **es el número que usa la app** en el alta
  (`SignUpScreen.kt:271`), con el hardcodeado de `ContactConfig` como respaldo.
- Cuentas de prueba en curso: `juanperez890621@gmail.com` (signup PENDING, OWNER, "Las Pepas")
  y `marvelalvarez89@gmail.com` (fila en `users` PENDING, **sin** fila en `signups` — quedó así
  por el bug del login que creaba cuentas, §9.7. Conviene borrarla y registrarla de nuevo).

---

## 4. Decisiones del dueño (no volver a discutirlas sin él)

| Fecha | Decisión | Por qué |
|---|---|---|
| 09-12 | **SUPERUSER = Luis**, fila en `members` con `orgId: "platform"` | No pertenece a ningún negocio; `"platform"` es el centinela |
| 09-12 | **Los labels de Auth se rechazaron** para el SUPERUSER | Se evaluó y el dueño prefirió no tocar la Function. Luis usa **otra cuenta** para probar roles de negocio |
| 09-12 | El panel es **sólo para SUPERUSER**; los reportes viven en la app bajo OWNER/ADMIN | El panel no tiene datos operativos que reportar: ventas, cierres y stock son JSON local |
| 09-12 | **Sin membresía usable no se entra — ni como SELLER** | SELLER igual vende y cierra caja; sin `orgId` ni sucursal generaría movimientos sin dueño |
| 09-12 | **Se endurece `DefaultPermissionService`** (revierte al plan 030) | No hay instalaciones legacy: la app nunca se publicó |
| 09-12 | No se introduce un rol `PENDING` | La ausencia de membresía ya es la señal; dos representaciones terminan discrepando |
| 09-14 | Con rol nulo **tampoco se ve el historial** | Era el mismo fail-open en un segundo lugar. Fuera del alcance escrito del 033, aprobado aparte |
| 09-14 | La **sesión offline no expira** | Sin red sólo ve datos viejos, nada se refresca, y sus operaciones quedan pendientes |
| 09-14 | **Un 401 expulsa** | Es lo que hace aceptable lo anterior: la revocación surte efecto al recuperar la red |
| 09-14 | El estado desconectado se ve **siempre**, en todas las pantallas | — |
| 09-14 | Deploy por **Git**, rama `main`; pruebas de APK en ramas aparte | Ver §8 |
| 09-12 | Inventario compartido: **stock server-authoritative** | `advisor-plans/008-shared-inventory-DESIGN.md`. El movimiento es la unidad de verdad; ningún cliente escribe una cantidad de stock |

---

## 5. Pendiente, en orden

| # | Qué | Quién | Notas |
|---|---|---|---|
| 1 | ~~Paso 7 del plan 033~~ | **Dueño** | ✅ **Flujo de login y alta cerrado por el dueño el 14/09.** Queda sin probar en dispositivo sólo la regresión offline del plan 030 (modo avión), y el caso `AwaitingAssignment`, descartado por el dueño: sin sucursal la cuenta queda pendiente, así que no es un caso de uso |
| ~~2~~ | ~~Mergear `plan/033` → `main`~~ | Agente | ✅ hecho el 14/09, fast-forward sin conflictos |
| 3 | **Instalar la Appwrite GitHub App** sobre `l3vram/money-counter-sdd` | **Dueño** | Consola → Function `admin` → Settings → Git. Hoy hay **0** instalaciones de VCS. Con eso el deploy deja de ser manual (§8) |
| 4 | **Plan 034** — sesión offline + 401 que expulsa + indicador | Agente | `plans/034-offline-session.md`, escrito y listo para ejecutar |
| 5 | **Aprobación atómica** (plan nuevo, sin escribir) | Agente | La aprobación escribe 5+ filas sin transacción. Appwrite tiene `transaction_id` en TablesDB. Ver §9.5 |
| 6 | **Plan 032** — repos `suspend` | Agente | Habilita cualquier implementación de red; paso 2 del diseño F2 |
| 7 | **F2 paso 3** — schema + Function `applyMovement` | Agente | El diseño 008 tiene 3 preguntas abiertas (§8) y 2 verificaciones (§7) sin cerrar |
| 8 | **Plan 031** — limpieza | Agente | P3, archivos disjuntos |
| — | Limpieza menor | Agente | Las plataformas Web y reglas de los deployments viejos (`*.appwrite.network`) y la de `adm.elluiso.com` (sin DNS) ya no hacen falta |
| — | Paginación | Agente | Las lecturas masivas de la Function usan `Query.limit(1000)` (`MAX_ROWS`). Pasadas mil filas **truncan en silencio** |

---

## 6. Cómo verificar (el camino de prueba)

> **Estado al 14/09**: el dueño probó y cerró el flujo completo — registro con validación de
> correo, aprobación desde el panel, cambio forzado de contraseña y entrada a la caja.

El flujo real, que es también la prueba del alta:

1. Luis entra al panel → `whoami` devuelve `role: "SUPERUSER"`. ✅ **verificado por API**
2. Desde la app, registrar una cuenta como **DUEÑO** con negocio y sucursales → crea el
   `signup` en PENDING y muestra la contraseña temporal (ahora copiable).
3. Luis lo aprueba en el panel → se crean `orgs`, `branches`, la fila `members` **con su
   permiso de lectura**, la fila `users` en APPROVED, y el `signup` pasa a APPROVED.
4. Esa cuenta entra a la app → permisos OWNER, org y sucursales sincronizadas (plan 028).
5. Registrar un **SELLER** contra la misma org, aprobarlo con una sucursal, verificar sus
   restricciones.

**Lo nuevo del plan 033 que hay que probar (paso 7):**

- Aprobar **sin** asignar sucursal → la app debe quedar en *"Falta asignarte tu puesto"*.
- Asignar sucursal → entra.
- Entrar con la cuenta de Luis en la app → *"Cuenta de administración"*.
- **Regresión del plan 030**: SELLER, modo avión, reiniciar → los botones de alta, merma y
  stock siguen ocultos.

Si Android Studio no reconoce dispositivos: `adb kill-server && adb start-server && adb devices`,
y para instalar sin el IDE, `./gradlew :app:installDebug`.

---

## 7. Datos del entorno

| | |
|---|---|
| Endpoint | `https://api.elluiso.l3vram.com/v1` (el viejo `https://fra.cloud.appwrite.io/v1` sigue funcionando; la app Android usa ese) |
| Project ID | `6aa332f40001072d0747` ("El luiso (MoneyCounter)", región `fra`) |
| Organización | `6aa332f3000389e6ee16` ("Personal Projects", tier-0) |
| Database | `main` |
| Tablas | `users`, `members`, `signups`, `orgs`, `branches`, `settings` |
| Repo | `github.com/l3vram/money-counter-sdd` (público) |
| DNS | Cloudflare en `l3vram.com`: `elluiso` y `api.elluiso` → CNAME `fastly.appwrite.systems`, **DNS-only (nube gris)** |
| Reglas de proxy | site `ab9f3b7e336130d966494e480d290c45`, api `ac4b789cdb6c8669bac01d9f912e76f7` |
| MCP Appwrite | `.mcp.json`, `https://mcp.appwrite.io/`, OAuth con scope **muy amplio** (`project:all`, `organization:all`) — aceptado conscientemente |

---

## 8. El deploy, hoy y como debería ser

**Hoy, a mano** — nueve releases el 14/09 dan la pauta de lo tedioso que es:

1. Empaquetar `webadmin/function` o `webadmin/app` en un tarball.
2. Subirlo como asset de un release de GitHub (`gh release create`) — los build servers de
   Appwrite necesitan una URL pública alcanzable. **Ojo**: catbox y x0.at bloquean datacenters,
   y el base64 inline arriesga corrupción.
3. `functions_create_deployment` / `sites_create_deployment` por MCP con esa URL.
4. Esperar el build y verificar contra el deployment nuevo.

**Como debería ser**: con la GitHub App instalada, se cablean Function (`webadmin/function`) y
Site (`webadmin/app`) como root directories sobre `main`, y el ciclo pasa a ser commit + push.
Bonus documentado: **la URL de una rama es estable** entre deployments, así que se termina
también el registro manual de plataformas Web.

---

## 9. Trampas (cada una costó un diagnóstico equivocado)

### 9.1. Las URLs `.appwrite.network` están clavadas a un deployment

Tras activar un deployment nuevo, la URL vieja seguía sirviendo el build viejo. **No era caché
de edge** (primera hipótesis, falsa): Appwrite crea **una URL por deployment**, y la regla de
ese dominio tiene el `deploymentId` fijo. Se diagnostica con el header
**`x-appwrite-deployment-id`** de la respuesta, que dice qué deployment sirve realmente.
Un query string de cache-busting **no** lo sortea.

### 9.2. La key dinámica de la Function llega por header, no por env var

`APPWRITE_FUNCTION_API_KEY` existe durante el **build**; en **ejecución** la key viaja en el
header **`x-appwrite-key`**. La Function leía sólo la env var, así que corría **sin credencial**:
toda lectura fallaba, `memberRole` convertía la excepción en `null`, y `null` se respondía con
403. **El panel nunca funcionó**; el 403 que el plan 029 anotó como "correcto porque `members`
estaba vacía" era este bug, tapado por esa coincidencia.

### 9.3. Sin `X-Appwrite-Project`, Appwrite miente sobre el origen

Una petición sin ese header no puede resolver el proyecto, así que no encuentra la plataforma
Web y responde `403 general_unknown_origin` con el texto *"Register your new client as a Web
platform"* — señalando un problema que no existe. Y ese 403 **no lleva
`Access-Control-Allow-Origin`**, así que el navegador lo reporta como un `Failed to fetch` /
`Load failed` opaco. **No armar peticiones a Appwrite a mano**: usar el SDK.

### 9.4. Cookie y JWT no se suman: la cookie gana

Con una cookie de sesión presente, Appwrite **ignora el JWT**. Y un JWT **muere con la sesión
que lo emitió** — el `deleteSession` del login mataba JWTs recién emitidos. Ahora que el panel
y la API comparten dominio registrable, la cookie es de primera parte y **el JWT se eliminó**.
Para depurar, `users_create_jwt` permite reproducir lo que hace el panel sin contraseña ni
navegador; ojo con el payload: el cuerpo va como `{"body":"<json en string>","method":"POST"}`.

### 9.5. El patrón de bugs más frecuente: el paso accesorio que hunde al esencial

Cuatro bugs distintos fueron **la misma cosa**: una secuencia de escrituras donde un paso
accesorio falla y se lleva puesto el resultado del esencial.

- `resetPassword`: la contraseña **ya había cambiado** y el panel reportaba fallo porque la
  marca de cambio forzado tiraba 404.
- `approveSignup`: creaba org y sucursales, después fallaba en `users` (fila inexistente) y
  dejaba el signup PENDING → **imposible aprobar**, y cada reintento **duplicaba** la
  organización (tres salieron de tres intentos).

Mirá cualquier acción nueva de la Function con esa lente: qué pasa si el paso 3 de 4 falla, y
si ese paso era realmente indispensable. **La aprobación sigue sin ser atómica** — es el
pendiente #5 de §5.

### 9.6. Verificación DNS y caché negativa

La regla de la API falló **un segundo** después de crearse con *"missing CNAME record"*, con el
registro ya resolviendo. Era **caché negativa**: Appwrite consultó al crear la regla, cuando el
nombre no existía, y el resolver guardó el `NXDOMAIN`. Se arregla **reintentando**
(`proxy_update_rule_status`), no rehaciendo el DNS.

### 9.7. Un 401 no significa "no existe el usuario"

`isUserNotFound` tomaba **cualquier** 401 como "no existe", y una contraseña incorrecta también
es 401. De ahí dos síntomas: *"la cuenta ya existe"* ante una contraseña mal escrita, y cuentas
**creadas desde la pantalla de login** sin fila en `signups` — sin rol ni negocio, invisibles
en Solicitudes e imposibles de aprobar. Iniciar sesión ya no crea cuentas nunca.

Relacionado: Appwrite responde **lo mismo** para contraseña incorrecta y correo inexistente, a
propósito, para que el login no sirva para descubrir quién tiene cuenta. El mensaje tiene que
ofrecer las dos lecturas.

### 9.8. El contrato de la Function no lo valida el compilador

Los menús Usuarios y Solicitudes crasheaban con `Cannot read properties of undefined (reading
'length')`: el panel espera `{ rows, total }` y la Function devolvía `{ users }` / `{ signups }`.
TypeScript no podía atraparlo porque la respuesta cruza el cable como `unknown` y se castea
(`payload.data as T`). **Verificar cada acción nueva contra la Function**, no confiar en la firma.

### 9.9. El rol autoritativo está en `members`, no en `signups`

`signups.role` es el rol **solicitado** al registrarse, y su enum (`OWNER|ADMIN|SELLER`) no
puede expresar SUPERUSER. Leer el rol de ahí hacía que la cuenta con el rol más alto fuera
justamente la única que nunca podía mostrarlo.

### 9.11. Una fila que no podés leer responde 404, no 403

`signups` tenía `rowSecurity: false`, así que sus permisos **por fila se ignoraban** y sólo
aplicaba el de tabla: `create("users")`, sin lectura. La app no podía leer su propia fila y
Appwrite contesta **404** para una fila que no podés leer — oculta su existencia. Como
`readMustChangePassword` trata el 404 como "no hay nada que cambiar", el cambio forzado de
contraseña **nunca se disparaba**: falla silenciosa perfecta, sin error en ningún log.

Regla: al cerrar una tabla, verificar qué lee la app de ella. Un permiso faltante no se
manifiesta como "prohibido" sino como "no existe", y el código de arriba suele interpretar
"no existe" como un estado legítimo.

### 9.12. Orden de inicialización en Kotlin

`init` llamaba a `refreshTenantScope()`, que termina leyendo `sellerUid` — declarado **después**
del bloque `init`. Kotlin corre inicializadores y bloques init en orden de declaración, así
que el campo todavía era JVM-null y `visibleForRole`, cuyo `uid` es no-nulable, tiraba
"Parameter specified as non-null is null" y mataba la app justo después del login.

Regla: cualquier propiedad que `init` lea, transitivamente, va declarada **arriba** de `init`.
No es testeable en unit test acá porque el ViewModel toma un `Application`, así que la
protección es el comentario en el código.

### 9.13. No mapear dos veces un mensaje de error

`authRepository.changePassword` ya devuelve el texto traducido por `mapAuthError`. Volver a
mapearlo en el ViewModel lo degradaba a "Error inesperado", porque el español ya no coincide
con ningún patrón en inglés. Lo atrapó un test.

### 9.10. Cuidado con el N+1 en la Function

`listUsers` hacía un `getRow` por usuario. Se arregló con lecturas masivas + `Map`. El olor a
buscar: un `await` dentro de un bucle o de un `.map`.

---

## 10. Archivos clave

| Para entender | Archivo |
|---|---|
| La guardia del panel y el alta | `webadmin/function/src/index.js` (`getCaller`, guardia del 403, `approveSignup`, `grantTenantRead`) |
| El cliente del panel | `webadmin/app/src/api.ts` (SDK, sin JWT, errores etiquetados por paso) |
| La matriz de permisos | `app/src/main/java/com/moneycounter/domain/Role.kt` |
| El deny-all | `app/src/main/java/com/moneycounter/domain/PermissionService.kt` (`NoAccessPermissionService`) |
| "¿Puede operar esta membresía?" | `app/src/main/java/com/moneycounter/domain/Member.kt` (`isOperable`) |
| La decisión de acceso | `app/src/main/java/com/moneycounter/access/EffectiveAccess.kt` |
| Estados de acceso | `app/src/main/java/com/moneycounter/access/AppAccessState.kt` |
| La política del 404 | `app/src/main/java/com/moneycounter/appwrite/AppwriteMembershipRepository.kt` |
| El caché de rol offline | `app/src/main/java/com/moneycounter/access/MemberCache.kt` |
| Login (ya no crea cuentas) | `app/src/main/java/com/moneycounter/appwrite/AppwriteAuthRepository.kt` |
| Estado de todos los planes | `plans/README.md` |
| El bootstrap del superuser | `docs/superuser-bootstrap.md` |
| El guion E2E del alta | `docs/onboarding-e2e.md` |
| El diseño del inventario compartido | `advisor-plans/008-shared-inventory-DESIGN.md` |
