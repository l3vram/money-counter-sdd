# Function admin — El Luiso (plan 027)

Appwrite Function (Node runtime) del panel web admin. Toda accion esta protegida con el
guard `isSuperuser`/: si el usuario autenticado no tiene una fila `members/{userId}` con
`role == "SUPERUSER"`, la funcion responde 403. El frontend solo controla la UX; esta
funcion es el punto real de enforcement (principio SUPERUSER-PLAN).

## Entrypoint

`src/index.js` (CommonJS). Se publica como ejecucion HTTP: el sitio hace
`POST {endpoint}/functions/{functionId}/executions` con `Authorization: Bearer <JWT>`
y el cuerpo `{"action":"...","param":...}`.

## Variables de entorno esperadas

| Variable | Valor |
|---|---|
| `APPWRITE_FUNCTION_ENDPOINT` | `https://fra.cloud.appwrite.io/v1` (auto en runtime) |
| `APPWRITE_FUNCTION_PROJECT_ID` | `6aa332f40001072d0747` (auto en runtime) |
| `APPWRITE_FUNCTION_API_KEY` | API key auto-generada por ejecucion (la provee la plataforma; requiere que la funcion este habilitada con su API key) |

La funcion usa `setKey(process.env.APPWRITE_FUNCTION_API_KEY || '')`. Si no hay key, todas
las llamadas fallaran con 500; no se registran secretos ni payloads de signups.

## Scopes requeridos en la funcion (provisionados por el orquestador)

- `users.read`, `users.write` (whoami, resetPassword)
- `databases.read`, `databases.write` (TablesDB: tablas users, members, signups, orgs,
  branches, settings del DB `main`)

## Acciones

`whoami`, `listSignups(status?)`, `approve(signupId, orgId?, branchIds?)`,
`reject(signupId)`, `listOrgs`, `listBranches`, `listUsers`, `resetPassword(userId, password)`,
`getSettings`, `setSettings(superuserWhatsapp)`.

## Verificacion local

```bash
npm install
npm run check   # node --check src/index.js
```