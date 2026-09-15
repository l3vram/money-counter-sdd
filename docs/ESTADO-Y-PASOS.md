# Estado y pasos — El Luiso

> **Punto de entrada para retomar el trabajo.** Última actualización: **2026-09-15, 16:00 UTC**.
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

| Rama | Commit | Estado |
|---|---|---|
| `main` | `458bab2` | **Todo mergeado**: planes 030, 032, 033, 034, 035, 036 y 037 |
| `plan/038` | `47742fc` | ✅ verde, revisado, **sin mergear** — espera el Gate B |
| `plan/039` | `3777f95` | ✅ verde, revisado, **sin mergear** — espera el Gate B |

Los planes 038 y 039 están verificados y su integración también (`integration/f2-step3`:
merge limpio, 516 + 64 tests). **No se mergearon**: mergear a `main` despliega el panel, así
que la decisión es del dueño.

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
| Tests app | **509** en `main`; **516** con el plan 038 |
| Tests Function | **25** en `main`; **64** con el plan 039 (antes de todo esto eran **cero**) |
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
| `movements` | `[]` | ✅ true | creada el 15/09 para el plan 039 |
| `stock` | `[]` | ✅ true | creada el 15/09 para el plan 039 |

Antes, `members`, `users`, `orgs` y `branches` tenían `read("users")`: cualquier usuario
autenticado leía el rol y el negocio de todos. La Function usa API key, así que **no le afecta**
el cierre; la app lee sólo su propia fila.

### El esquema de F2 (provisionado por MCP el 15/09, antes de que el plan 039 corriera)

Sin permisos de tabla: **sólo la Function escribe**. Los permisos de lectura se reparten por fila
por miembro, igual que hace `approveSignup` (§9.11).

| Tabla | Row id | Columnas |
|---|---|---|
| `movements` | **el id del movimiento** — es la clave de idempotencia | `orgId`, `branchId`, `sellerUid`, `type`, `at` (bigint), `currencyId`, `amountCents` (bigint), `linesJson` (text), `appliedAt` (bigint) |
| `stock` | `{branchId}_{productId}` | `orgId`, `branchId`, `productId`, `quantityCents` (bigint, default `0`), `updatedAt` (bigint) |

`quantityCents` es **bigint en centiunidades** (cantidad × 100), nunca float: los deltas de un
float derivan y el stock deja de cuadrar. El default `0` es el cinturón del upsert del plan 039
—incrementar una fila que no existe da 404, no la crea—, y el planificador igual escribe el `0`
explícito.

`stock` lleva un índice **único** en `(branchId, productId)`. El row id ya lo garantiza; el
índice está para que, si alguien algún día escribe una fila con otro id, la base lo rechace en
vez de quedarse con dos stocks del mismo producto.

`linesJson` es `text`, no `varchar`: un `varchar` grande se come el presupuesto de bytes de la
fila (65535 por fila en Appwrite) y el `text` se guarda fuera.

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
| ~~1~~ | ~~`createClosing` debe autorizar por `permissionService`~~ | Agente | ✅ plan 037. Verificado: **cero** lecturas de `can*` desde el estado en todo el ViewModel |
| ~~2~~ | ~~**F2 paso 3** — schema + Function `applyMovement`~~ | Agente | ✅ planes 038 + 039, **en ramas, sin mergear**. El schema ya está en producción |
| 2 | **F2 paso 4/5** — que la app llame a `applyMovement` y lea el stock del servidor | Agente | El servidor ya acepta movimientos; **nadie se los manda todavía**. Hasta que esto se haga, el stock compartido no existe en la práctica |
| — | Cruzar las dos matrices duplicadas | Agente | `Role.kt` y `movementPlan.js` codifican la **misma** matriz rol→tipo, y ningún test puede cruzar Kotlin y JS. Hoy coinciden (verificado). Mitigación mínima: un comentario en `Role.kt` apuntando a `movementPlan.js` |
| — | Permisos de lectura en `movements` y `stock` | Agente | Las filas se crean sin ningún permiso (a propósito, falla cerrado). Cuando la app tenga que leerlas, hay que repartir lecturas por miembro como hace `approveSignup` |
| 3 | **Respaldo de los JSON locales** | **Dueño** | **Antes de F2.** Los datos operativos son locales y no tienen copia en el servidor |
| 4 | **Plan 031** — limpieza | Agente | P3, archivos disjuntos |
| — | `providerBranches` vacío | Agente | Construye en **todas** las ramas: empujar `plan/035` gastó un build al vacío (no se activó, no rompe nada). Acotarlo a `main` |
| — | Limpieza menor | Agente | Plataformas Web y reglas de deployments viejos (`*.appwrite.network`), y `adm.elluiso.com` sin DNS |
| — | Paginación | Agente | Las lecturas masivas de la Function usan `Query.limit(1000)`: pasadas mil filas **truncan en silencio** |
| — | Expulsión inmediata al revocar | — | Aceptado como limitación. Ver §9.11bis |

### Lo hecho, en orden

| Plan | Qué cerró |
|---|---|
| 030 | El rol no se degrada por un fallo de red (caché local + no degradar) |
| 033 | Sin membresía usable no se entra — ni como SELLER. Tablas de tenant cerradas |
| 034 | La app abre sin conexión, con cartel; un 401 al arrancar expulsa |
| 035 | Aprobar es una transacción, y la Function estrena 25 tests (tenía cero) |
| 032 | Las 4 interfaces de repositorio son `suspend` — habilita cualquier implementación de red |
| 036 | El I/O sale del hilo principal, hay estado de carga, y los permisos por defecto fallan cerrado |
| 037 | `createClosing` autoriza por el servicio, no por el estado de UI |
| 038 | Cada línea de movimiento lleva su `productId` — sin eso el diario no podía derivar stock |
| 039 | `applyMovement`: autoriza, es idempotente y aplica el delta de stock en una transacción |
| — | Panel operativo: dominio propio, deploy por Git, y los ~10 bugs del alta |

---

## 6. Cómo verificar---

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
| Tablas | `users`, `members`, `signups`, `orgs`, `branches`, `settings`, `movements`, `stock` |
| Repo | `github.com/l3vram/money-counter-sdd` (público) |
| DNS | Cloudflare en `l3vram.com`: `elluiso` y `api.elluiso` → CNAME `fastly.appwrite.systems`, **DNS-only (nube gris)** |
| Reglas de proxy | site `ab9f3b7e336130d966494e480d290c45`, api `ac4b789cdb6c8669bac01d9f912e76f7` |
| MCP Appwrite | `.mcp.json`, `https://mcp.appwrite.io/`, OAuth con scope **muy amplio** (`project:all`, `organization:all`) — aceptado conscientemente |

---

## 8. El deploy: por Git, desde `main`

**Conectado el 14/09.** Se terminó el baile del tarball. Ahora un push a `main` que toque el
webadmin dispara el deploy solo.

| | Function `admin` | Site `admin-web` |
|---|---|---|
| Instalación VCS | `6aa8234ea75791feec3a` (org `l3vram`) | la misma |
| Repositorio | `1356205416` (`l3vram/money-counter-sdd`) | el mismo |
| Rama de producción | `main` | `main` |
| **Root directory** | **`webadmin/function`** | **`webadmin/app`** |
| Paths que disparan | `webadmin/function/**` | `webadmin/app/**` |
| Silent mode | sí | sí |

**El root directory es el campo que decide si funciona.** Al listar el repo, Appwrite lo
detecta como `runtime: java-25` — ve el proyecto Android de la raíz. Sin el root directory
intentaría compilar Kotlin como si fuera la Function.

**Los `providerPaths` no son opcionales en la práctica.** `main` recibe sobre todo commits de
Android; sin acotar los paths, cada uno dispararía dos builds inútiles.

**Las variables del sitio sobreviven**: `VITE_APPWRITE_ENDPOINT`,
`VITE_APPWRITE_PROJECT_ID` y `VITE_ADMIN_FUNCTION_ID` viven en el recurso, no en el repo.
Verificado en el bundle del primer deploy por Git: el endpoint horneado sigue siendo
`https://api.elluiso.l3vram.com/v1`, y el hash del bundle salió idéntico al del deploy
manual anterior — build reproducible.

**Primer deploy por Git verificado** (commit `bebec7c`): Function `6aa8827dc4384136f48d` →
`whoami` responde 200 con `role: SUPERUSER`; Site `6aa88283f083a436e014` → sirviendo en
`elluiso.l3vram.com`.

**Detalle a no confundir**: al conectar Git, ambos recursos quedan `live: false` hasta el
primer deploy por Git. Es Appwrite avisando que el deployment activo se construyó con otra
configuración, no un error.

### Cómo desplegar de ahora en más

Un push a `main` que toque `webadmin/`. Nada más. Si hace falta forzarlo sin commit nuevo:
`functions_create_vcs_deployment` / `sites_create_vcs_deployment` con
`{type: "branch", reference: "main", activate: true}`.

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

### 9.11bis. La revocación no se puede detectar leyendo filas (limitación aceptada)

Corolario de §9.11, verificado con curl el 14/09:

| Llamada con la sesión muerta | Respuesta |
|---|---|
| `members/{uid}` — lo que hace el poll | **404** `row_not_found` |
| `account.get()` — lo que hace el arranque | **401** |

Cuando la sesión muere, el cliente pasa a ser invitado y la fila —que sólo tiene
`read("user:<uid>")`— deja de existir para él. Appwrite contesta 404, y 404 significa "no hay
membresía asignada", que por el plan 030 **conserva la membresía cacheada**. Así que la app
sigue operando.

Consecuencia: `MembershipUpdate.Revoked` existe y está bien implementado, pero **desde el poll
de membresía nunca se emite**. La revocación se aplica al siguiente arranque, cuando
`currentUser()` recibe su 401.

**Decisión del dueño (14/09): se acepta así.** Al cerrar y abrir la app el usuario queda
afuera, y eso alcanza.

Si algún día se quiere expulsión inmediata, el arreglo **no** es tocar la política del poll:
hay que consultar un endpoint de `/account/*`, el único que responde 401 con una sesión muerta.
Cambiar el mapeo del 404 sería peor — rompería el caso legítimo de "aún no me asignaron".

### 9.11ter. Auth no entra en una transacción de filas

**Sólo las filas de TablesDB son transaccionales** (verificado en `node-appwrite@25.2.0`:
`createTransaction`, `createOperations`, `updateTransaction`, y `transactionId` en doce métodos
de fila). Appwrite Auth queda afuera.

Por eso `resetPassword` —contraseña en Auth, marca en `signups`— **nunca** puede ser atómico, y
su parche best-effort no fue descuido. Cuando una acción mezcle Auth y filas: lo esencial
primero, lo accesorio best-effort, y que quede en el log.

### 9.11quater. Un default puede reabrir un agujero ya cerrado

El plan 033 hizo que sin membresía no se conceda ningún permiso, y dio vuelta los helpers
`Role?.may*()` a `false`. Pero **se le escaparon los defaults de `MoneyCounterUiState`**, donde
once flags de permiso arrancaban en `true`. Y `refreshPermissions()` se llama sólo desde
`setSellerContext`, nunca desde `init`.

Peor: `createClosing()` **autoriza leyendo el UiState**, no el `permissionService`. Así que la
ventana entre construir el ViewModel y el `LaunchedEffect` de MainActivity permitía crear un
cierre de sucursal sin rol resuelto. Escalada real, no cosmética. Cerrado por el plan 036.

Dos reglas que salen de esto:
1. **El valor inicial de un campo de estado debe ser lo que la app sabe al construirse**, que
   suele ser "nada". Un default que afirma capacidad es cómo vuelve un fail-open.
2. **La autorización se consulta en un solo lugar.** Que `createClosing` la leyera del UiState
   era una segunda fuente de verdad. **Cerrado por el plan 037**: hoy `grep -n
   "state\.can[A-Z]"` sobre el ViewModel no devuelve nada, y los flags del UiState existen
   sólo para renderizar. La regla general: una proyección refrescada por un callback de ciclo
   de vida nunca puede ser la autoridad de una decisión.

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

### 9.14. Un `upsertRow` que manda la columna la **resetea**

El plan 039 pide asegurar la fila de stock antes de incrementarla, porque incrementar una fila
que no existe da **404 y no la crea**. La primera implementación la aseguraba así:

```js
{ action: 'upsert', tableId: 'stock', rowId: id, data: { ..., quantityCents: 0 } }
```

Eso destruye el stock. `upsertRow` aplica los campos que le mandas, así que en una fila que
**ya existe** pone `quantityCents` en 0 y después aplica el delta: el stock queda valiendo
sólo el último movimiento y todo lo anterior se pierde **en silencio, sin ningún error**.

Verificado contra la tabla real el 15/09, no leído en la documentación:

| Fila antes | Upsert | Fila después |
|---|---|---|
| `quantityCents: 500` | **con** `quantityCents: 0` | **0** ← el desastre |
| `quantityCents: 500` | **sin** la columna | `500` ← correcto |
| no existe | **sin** la columna | `0` ← el default de la columna |

La forma correcta es **omitir la columna**. Por eso `quantityCents` se provisionó con
`default: 0`: es lo que hace que omitirla sea seguro en una fila nueva. Y por eso el test que
afirma que el `data` del upsert **no** trae la clave `quantityCents` es el que importa — sin
él, el próximo que pase vuelve a "arreglarlo" agregando el campo.

Hermano de la §9.11quater: ahí un default reabría un agujero, acá un campo de más borra datos.
Los dos son "escribir lo que no hacía falta escribir".

### 9.15. `Number(x) * 100` no es convertir dinero a centésimos

La app manda cantidades como `BigDecimal.toPlainString()`, o sea cadenas exactas: `"4.00"`,
`"1.005"`. Pasar por float antes de redondear pierde la precisión primero:
`Math.round(Number("1.005") * 100)` da **100**, no 101, porque `1.005 * 100` es
`100.49999999999999`.

Es el peor tipo de error de contabilidad: pierde centésimos en algunos valores y no en otros,
así que el total queda *casi* bien y nadie lo nota hasta que hay que cuadrar. Se convierte
desde la cadena, partiendo por el punto, nunca por float.

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
