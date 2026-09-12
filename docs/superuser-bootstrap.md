# Bootstrap del SUPERUSER + puesta en marcha del web admin

> **Estado: SEMBRADO (2026-09-12 17:23 UTC) — falta la verificación end-to-end del dueño.**
> La fila `members` del SUPERUSER ya existe. Lo único pendiente es entrar al panel con esa
> cuenta y confirmar que la Function ya no responde 403.

## Lo que se hizo

| | |
|---|---|
| Cuenta elegida | `luisricoblanco2014@gmail.com` — decisión del dueño |
| uid | `6aa352520004960be987` |
| Fila creada | `main/members/6aa352520004960be987` = `{ orgId: "platform", role: "SUPERUSER", branchIds: [] }` |
| Vía | MCP remoto de Appwrite (OAuth de consola), `tables_db_create_row` |

Verificado antes de escribir:

- `members` estaba vacía (`total: 0`) y sus tres columnas son **opcionales**
  (`orgId` varchar 64, `role` varchar 32, `branchIds` varchar 255 **array**).
- La tabla tiene `rowSecurity: false` y permiso `read("users")`, así que la fila es legible
  por la cuenta sin necesidad de permisos por fila.
- Ambas cuentas ya tenían fila en `users` con `access = "APPROVED"`, así que el paso 6 del
  procedimiento original no hacía falta.
- Function `admin`: `enabled`, `live`, deployment `6aa4c515687c4d9d7361` en `ready`,
  `execute: ["users"]`. Sitio admin responde HTTP 200.

Lo único que la Function exige para no dar 403 es `members/{uid}.role === 'SUPERUSER'`
(`webadmin/function/src/index.js:41-49` y `:192`). Eso ya está.

## Lo que falta (sólo el dueño puede hacerlo)

1. **Entrar** a `https://6aa4cb0a8f6a4c30a83f.appwrite.network` con
   `luisricoblanco2014@gmail.com` y confirmar que `whoami` devuelve `role: "SUPERUSER"`
   en lugar de 403. Requiere la contraseña de esa cuenta, que no está en el repo.
2. **Cargar el WhatsApp del superusuario** desde el propio panel: la fila `settings/app`
   existe con `superuserWhatsapp: ""`, y la Function tiene la acción para setearlo, así que
   no hace falta tocar la base a mano.
3. Recién con eso verde, dar de alta el resto de los usuarios desde el panel.

## Hallazgo a tratar aparte (no bloquea)

`members` tiene `rowSecurity: false` + `read("users")`: **cualquier usuario autenticado puede
leer la fila de cualquier otro**, incluidos `orgId` y `role` de otras organizaciones. Es
anterior a este bootstrap y contradice el aislamiento multi-tenant del plan 024. Candidato a
plan propio.

## Objetivo

Dejar operativo el panel web de administración para que los usuarios se creen **desde el
sitio** y no tocando la base a mano. Se prueban dos cosas a la vez: el alta de usuarios y
el propio panel.

## Por qué está bloqueado (huevo y gallina)

`webadmin/function/src/index.js:192`:

```js
const caller = await getCaller(req, tablesDB, users);
if (!caller || caller.role !== 'SUPERUSER') {
  return res.json({ ok: false, error: 'Acceso denegado: se requiere sesión de superusuario' }, 403);
}
```

El rol sale de `members/{uid}.role` y **la tabla `members` está vacía**. El panel no puede
crear el primer SUPERUSER porque para entrar al panel ya hay que serlo. Hay que sembrarlo
desde fuera.

## Datos del entorno (verificados en el repo)

| | |
|---|---|
| Endpoint | `https://fra.cloud.appwrite.io/v1` |
| Project ID | `6aa332f40001072d0747` |
| Database ID | `main` |
| Tablas | `users`, `members`, `signups`, `orgs`, `branches`, `settings` |
| Sitio admin | `https://6aa4cb0a8f6a4c30a83f.appwrite.network` |
| Fuentes | `app/src/main/java/com/moneycounter/appwrite/Appwrite.kt`, `webadmin/app/.env.example` |

Forma de `members`: `orgId`, `role`, `branchIds`. El id de fila **es el uid** del usuario.

## Decisiones ya tomadas por el dueño (2026-09-12)

1. **Cuenta SUPERUSER**: una cuenta **que ya existe** en Appwrite Auth. ⚠️ **Falta que el
   dueño indique cuál.** Plan: listar los usuarios de Auth por MCP, mostrárselos y que elija.
   No crear cuentas nuevas.
2. **`orgId` del SUPERUSER**: centinela `"platform"`. El §17 del plan maestro dice que el
   SUPERUSER no pertenece a ninguna organización, pero la columna existe; `"platform"` se lee
   explícitamente como "ningún negocio" y satisface la columna si resulta obligatoria.
   No usar el orgId de un negocio real.
3. **Vía de acceso**: MCP remoto de Appwrite con su OAuth (alcance amplio, aceptado).

## Configuración ya aplicada

`.mcp.json` (en el árbol de trabajo, **sin commitear**, rama `plan/030`):

```json
"appwrite": { "type": "http", "url": "https://mcp.appwrite.io/" }
```

Sin secretos: el endpoint autentica por OAuth. Verificado que responde HTTP 401 con
`www-authenticate: Bearer` y `resource_metadata` OAuth, y que acepta POST JSON-RPC
(transporte HTTP streamable).

⚠️ El OAuth pide `project:all` y `organization:all` — control total de lectura y escritura
sobre **toda la organización** de Appwrite, no sólo este proyecto. Aceptado conscientemente.

## Procedimiento original (histórico — ya ejecutado, ver "Lo que se hizo")

1. `/mcp` → autorizar `appwrite` por OAuth en el navegador.
2. Listar usuarios de Appwrite Auth; el dueño elige cuál será SUPERUSER. Anotar su `uid`.
3. **Inspeccionar el esquema de la tabla `members`** antes de escribir: si `orgId` es
   requerida y de qué tipo es `branchIds`. No asumir.
4. Crear la fila `members/{uid}`:
   `{ orgId: "platform", role: "SUPERUSER", branchIds: [] }`
   Confirmar con el dueño antes de escribir.
5. Crear/actualizar `settings` fila `app` con `superuserWhatsapp`. ⚠️ **Falta el número.**
6. Verificar que el usuario tenga fila en `users` con `access = APPROVED` si el panel lo exige.

## Criterio de verificación (no basta con "creé las filas")

Iniciar sesión en `https://6aa4cb0a8f6a4c30a83f.appwrite.network` con esa cuenta y
comprobar que la Function **deja de responder 403** y que `whoami` devuelve
`role: "SUPERUSER"`. Antes de este bootstrap el smoke test documentado daba 403, que era
el comportamiento correcto con `members` vacía.

Recién con eso verde, crear el resto de usuarios desde el panel.

## Datos que faltaban del dueño (resueltos/pendientes)

- ~~Correo de la cuenta existente que será SUPERUSER.~~ → `luisricoblanco2014@gmail.com`.
- Número de WhatsApp para `settings.superuserWhatsapp` → se carga desde el panel, no hace falta acá.

## Contexto: qué se estaba haciendo antes

Rama `plan/030` — plan 030 (permisos offline) **terminado y verificado**, 444 tests, sin
mergear a `feature/multi-tenant` (Gate B pendiente del dueño, que iba a probar el APK debug).
Ver `plans/030-EXECUTION-LOG.md`. El siguiente estructural es `plans/032-suspend-repositories.md`.
