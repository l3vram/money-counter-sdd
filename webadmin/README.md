# webadmin — Panel admin El Luiso (plan 027)

Panel web de superusuario (React + TypeScript + Vite) + Appwrite Function de administración,
ambos en su propio directorio. Aprobación de registros, usuarios, organizaciones, sucursales
y configuración del WhatsApp del superusuario. UI en español. Ninguna dependencia con el código
Android: solo `webadmin/` se ve afectado.

## Estructura

```
webadmin/
├── app/                 # SPA estática: React + TS + Vite (build → app/dist/)
│   └── src/             # main.tsx, App.tsx, api.ts, types.ts, styles.css, pages/*
└── function/            # Appwrite Function (Node, entrypoint src/index.js)
    └── src/index.js     # API de acciones con guard SUPERUSER
```

## Deploy LIVE (2026-09-12)

| Recurso | Valor |
|---|---|
| Site `admin-web` | https://6aa4cb0a8f6a4c30a83f.appwrite.network (deployment `6aa4cb0a3ae416106f82`, build runtime node-22, salida `dist`) |
| Function `admin` | deployment `6aa4c515687c4d9d7361` (node-18.0), entrypoint `src/index.js` |
| Plataforma Web | `web-admin-site` con hostname del site (sin ella el login web falla por CORS/401) |
| Vars del site | `VITE_APPWRITE_ENDPOINT`, `VITE_APPWRITE_PROJECT_ID`, `VITE_ADMIN_FUNCTION_ID=admin` (secretas; horneadas en el build) |
| Fuente del deploy | GitHub release `webadmin-deploy-2026-09-12` → asset `admin-web.tar.gz` (sha256 `60165618…`) |

Re-deploy del site: empaquetar desde `webadmin/app`
(`tar --exclude=node_modules --exclude=dist --exclude='.git' -czf admin-web.tar.gz .`),
subir el tarball a una URL pública alcanzable por los build servers de Appwrite (asset de
release GitHub funciona; hosts de archivos pequeños tipo catbox/x0.at/0x0 bloquean
datacenters; base64 inline vía MCP arriesga corrupción) y crear deployment:
`sites_create_deployment {site_id:"admin-web", code:{url, filename:"site.tar.gz",
mime_type:"application/gzip"}, install_command:"npm install", build_command:"npm run build",
output_directory:"dist", activate:true}`.

## Requisitos provisionados por el orquestador (NO ejecutar aquí)

1. **Function** en Appwrite: runtime Node, entrypoint `src/index.js`, dependencia
   `node-appwrite@^25.2.0` (desde `function/package.json`). Scopes requeridos:
   `users.read`, `users.write`, `databases.read`, `databases.write`. Usa su API key
   auto-generada por ejecución (`APPWRITE_FUNCTION_API_KEY`); no requiere variables externas.
   Registrar el ID de la función para pasarlo al sitio como `VITE_ADMIN_FUNCTION_ID`.
2. **Site** en Appwrite Sites con el contenido de `app/dist/` (resultado de `npm run build`
   en `webadmin/app`). Build del sitio con: endpoint/proyecto (por defecto embebidos) y
   `VITE_ADMIN_FUNCTION_ID=<id de la funcion>`.
3. **CORS / Web platform**: el dominio del Site (y `localhost` en desarrollo) deben estar
   registrados como plataforma Web del proyecto, o el SDK no podrá abrir sesión ni el SPA
   llamar a `fra.cloud.appwrite.io` desde el navegador (bloqueo CORS).
   ✅ Registrada `web-admin-site` (hostname del site) el 2026-09-12. Falta añadir `localhost`
   para desarrollo local.
4. **Superusuario**: debe existir una fila `members/{userId}` con `role == "SUPERUSER"`; la
   función rechaza (403) cualquier otra sesión.

### Variables de entorno del sitio (`webadmin/app/.env` o build)

| Variable | Valor |
|---|---|
| `VITE_APPWRITE_ENDPOINT` | `https://fra.cloud.appwrite.io/v1` (por defecto en código) |
| `VITE_APPWRITE_PROJECT_ID` | `6aa332f40001072d0747` (por defecto en código) |
| `VITE_ADMIN_FUNCTION_ID` | ID de la función Appwrite (obligatorio) |

No hay API keys en el bundle: el sitio usa el SDK Web Appwrite (sesión por email+contraseña
+ JWT) y llama a `/functions/{id}/executions` para cada acción.

## Verificación local

```bash
# Sitio
cd webadmin/app
npm install
npm run build        # → dist/ (bundle estático)

# Función
cd ../function
npm install
npm run check        # node --check src/index.js
```

## Smoke test tras el deploy (primer flujo de aprobación)

1. Abrir el Site y entrar con la cuenta superusuario.
2. Crear una solicitud desde la app Android (FASE 2, plan 025) en estado PENDING.
3. En «Solicitudes»: aprobar el rol DUEÑO (se crean org + sucursales) y verificar los
   contadores del «Panel» (organizaciones/sucursales) y el acceso de la app Android.
4. Aprobar un ADMIN/SELLER eligiendo orgId y sucursales; verificar la sincronización en
   Android (plan 028).
5. En «Usuarios», restablecer una contraseña temporal y confirmar que la app exige cambiarla
   al primer ingreso (plan 026).

## Notas

- Autorización: el frontend solo controla la UX. La función valida el rol SUPERUSER al
  inicio de cada acción y responde 403 ante cualquier otra sesión.
- Paginación diferida (volumen de superusuario); `listSignups/listOrgs/listBranches/listUsers`
  devuelven hasta 1000 filas.
- Acciones implementadas: `whoami`, `listSignups(status?)`, `approve(signupId, orgId?,
  branchIds?)`, `reject(signupId)`, `listOrgs`, `listBranches`, `listUsers`,
  `resetPassword(userId, password)`, `getSettings`, `setSettings(superuserWhatsapp)`.
- Versiones: `appwrite` (Web SDK) y `node-appwrite` fijadas a la línea `^25.2.0`, igual que el
  SDK del proyecto Android.