# Money Counter — Implementation Plan: Google Login + Access Control + Cloud Sync Foundation

## 0. Objetivo

Agregar a la aplicación actual de **Money Counter / El Luiso**:

1. Inicio de sesión con cuenta de Google (Gmail).
2. Control remoto de acceso mediante una whitelist administrada en Firebase.
3. Pantallas para usuarios autenticados pero sin acceso aprobado.
4. Botón de WhatsApp para solicitar acceso mediante un mensaje prearmado.
5. Preparar la arquitectura para sincronizar posteriormente los datos de la app con la nube.
6. Mantener el funcionamiento actual de la aplicación y minimizar cambios en el código existente.

**Este trabajo NO debe migrar todavía toda la persistencia JSON a Firestore.** La primera entrega debe resolver identidad + autorización + pantalla de acceso y dejar una base segura para la futura sincronización.

---

## 1. Estado actual del proyecto que debes respetar

Repositorio:

```text
https://github.com/l3vram/money-counter-sdd
```

La aplicación actual está construida con:

- Kotlin.
- Jetpack Compose + Material 3.
- `compileSdk = 34`.
- `minSdk = 26`.
- `targetSdk = 34`.
- Una única app module (`:app`).
- `MainActivity.kt` como entry point.
- Navegación interna sencilla mediante estado (`currentScreen`) en `MoneyCounterApp`.
- `MoneyCounterViewModel` como estado/lógica de UI.
- Interfaces de repositorio ya existentes.
- Persistencia local en archivos JSON privados.
- Sin backend.
- Sin Firebase.
- Sin Room.

El build actual usa Kotlin/Compose y una versión de Compose BOM fija; **no actualizar versiones de Android/Kotlin/Compose de forma innecesaria como parte de esta tarea**. Agrega solamente las dependencias estrictamente necesarias para Firebase/Auth/Google y conserva el resto estable. 

La estructura ya contiene interfaces como:

```text
repository/
  CurrencyRepository.kt
  DenominationRepository.kt
  ProductRepository.kt
  SavedCountRepository.kt
  UnitRepository.kt
```

y las implementaciones JSON:

```text
JsonCurrencyRepository.kt
JsonDenominationRepository.kt
JsonProductRepository.kt
JsonSavedCountRepository.kt
JsonUnitRepository.kt
```

Esto es importante:

**Aprovechar esta abstracción en lugar de reescribir el dominio o la UI.** 

---

# 2. Regla principal de esta implementación

## NO romper la app actual

La app debe continuar funcionando exactamente igual para un usuario aprobado.

En particular, NO modificar de manera innecesaria:

- cálculo de dinero;
- modelos de dominio existentes;
- lógica de productos;
- lógica de monedas;
- historial;
- generación de PDF/CSV;
- comportamiento de Compose existente;
- diseño actual de las pantallas funcionales;
- formato de los JSON actuales;
- lógica de cantidades y `BigDecimal`;
- navegación existente salvo para introducir el gate de autenticación.

No hacer una migración masiva a Room.

No agregar Retrofit.

No crear un backend Spring Boot.

No crear múltiples módulos.

No introducir Hilt/Koin solo para esta feature.

No crear una arquitectura excesivamente abstracta.

---

# 3. Arquitectura objetivo para esta fase

La arquitectura mínima deseada es:

```text
                    ┌─────────────────────┐
                    │     MainActivity    │
                    └──────────┬──────────┘
                               │
                               ▼
                     ┌──────────────────┐
                     │ Authentication   │
                     │ / Access Gate    │
                     └────────┬─────────┘
                              │
                ┌─────────────┴─────────────┐
                │                           │
          APPROVED                    NOT APPROVED
                │                           │
                ▼                           ▼
       MoneyCounterApp              AccessRequiredScreen
                │                           │
                │                           └── WhatsApp
                │
                ▼
      MoneyCounterViewModel
                │
                ▼
        Existing repositories
                │
                ▼
        Local JSON persistence

Firebase side:

Google Account
      │
      ▼
Firebase Authentication
      │
      ▼
Firebase UID
      │
      ▼
Firestore: users/{uid}
      │
      └── access = PENDING / APPROVED / BLOCKED
```

La decisión importante es que **Firebase Auth y Firestore se incorporan como una capa de infraestructura nueva y no como reemplazo inmediato de los repositorios actuales**.

---

# 4. Fase 1 — Configuración de Firebase

## 4.1 Crear proyecto Firebase

Configurar un proyecto Firebase para Money Counter.

Registrar la aplicación Android con:

```text
applicationId = com.moneycounter
```

Usar el mismo package/application ID real del proyecto. 

## 4.2 Añadir configuración Android

Agregar el archivo:

```text
app/google-services.json
```

siguiendo la configuración oficial de Firebase.

**Nunca crear ni subir un `google-services.json` ficticio.**

El agente debe dejar la app preparada para que el archivo sea colocado desde Firebase Console si no está disponible en el entorno de desarrollo.

Si el archivo ya existe localmente, no modificar su contenido manualmente.

## 4.3 Dependencias

Agregar únicamente las dependencias necesarias para:

- Firebase Authentication.
- Google Authentication usando Credential Manager.
- Firebase Firestore.
- Google services Gradle plugin, si es requerido por la integración elegida.

Usar versiones compatibles con el proyecto actual.

**No actualizar todo el stack Android solo porque Firebase tenga una versión nueva.**

Verificar compatibilidad con:

```text
compileSdk 34
minSdk 26
JDK 17
Kotlin existente
Compose existente
```

---

# 5. Fase 2 — Google Login

## 5.1 Crear una pequeña capa de autenticación

Crear:

```text
auth/
    AuthRepository.kt
    FirebaseAuthRepository.kt
```

Interfaz conceptual:

```kotlin
interface AuthRepository {
    fun currentUser(): AuthUser?
    suspend fun signInWithGoogle(activity: Activity): Result<AuthUser>
    fun signOut()
}
```

No exponer directamente `FirebaseUser` a la UI si se puede evitar.

Crear un modelo pequeño de aplicación:

```kotlin
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?
)
```

El `uid` será la identidad principal.

**No usar el email como identificador de datos.**

## 5.2 Flujo de login

Usar el flujo moderno de Google/Firebase compatible con Android actual, basado en Credential Manager + Firebase Authentication.

Flujo esperado:

```text
LoginScreen
    ↓
Continuar con Google
    ↓
Google account picker
    ↓
Google credential
    ↓
Firebase Authentication
    ↓
AuthUser(uid, email, ...)
```

## 5.3 Estado de login

Crear un estado explícito. Ejemplo:

```kotlin
enum class AuthState {
    LOADING,
    SIGNED_OUT,
    SIGNED_IN,
    ERROR
}
```

No usar múltiples booleanos como:

```text
isLogged
isLoading
hasError
```

si un estado explícito evita combinaciones inválidas.

---

# 6. Fase 3 — Modelo de autorización

Crear un modelo de acceso separado de autenticación:

```kotlin
enum class AccessStatus {
    PENDING,
    APPROVED,
    BLOCKED
}
```

Autenticación responde:

```text
¿Quién eres?
```

Autorización responde:

```text
¿Puedes usar la aplicación?
```

Nunca mezclar ambos conceptos.

---

# 7. Fase 4 — Documento del usuario en Firestore

Crear:

```text
users/{uid}
```

Documento mínimo:

```json
{
  "email": "user@gmail.com",
  "displayName": "Nombre Usuario",
  "access": "PENDING",
  "createdAt": "server timestamp",
  "updatedAt": "server timestamp"
}
```

Opcionalmente guardar `photoUrl` si ya viene disponible del proveedor.

No guardar información innecesaria.

## 7.1 Primer login

Cuando un usuario inicia sesión por primera vez:

```text
Firebase Auth user
        ↓
users/{uid} does not exist
        ↓
crear documento
        ↓
access = PENDING
```

## 7.2 Login posterior

```text
Firebase Auth user
        ↓
users/{uid}
        ↓
leer access
```

**No sobrescribir `access` con `PENDING` en cada login.**

Solo establecer `PENDING` cuando el documento se crea inicialmente.

---

# 8. Fase 5 — Access Gate en la app

El gate debe ejecutarse antes de mostrar `MoneyCounterApp`.

Comportamiento:

```text
App start
   ↓
¿Firebase user existe?
   ├── NO → LoginScreen
   └── SI
         ↓
      leer users/{uid}
         ↓
      access
         ├── APPROVED → MoneyCounterApp
         ├── PENDING  → AccessRequiredScreen
         └── BLOCKED  → AccessRequiredScreen
```

No permitir entrar a las pantallas normales cuando el acceso remoto no está aprobado.

Esto debe ser un gate real de UI y también debe estar respaldado por reglas de Firestore para proteger los datos futuros.

---

# 9. LoginScreen

Crear una pantalla sencilla y coherente con el estilo visual existente.

Debe contener:

```text
El Luiso
Contador de dinero

[ Continuar con Google ]
```

No agregar:

- username/password;
- registro tradicional;
- email/password.

No rediseñar el resto de la aplicación.

## Errores

Mostrar mensajes comprensibles para:

- cancelación del selector;
- cuenta no disponible;
- error de red;
- error Firebase;
- error desconocido.

No mostrar stack traces ni mensajes internos.

---

# 10. AccessRequiredScreen

Crear una pantalla específica para usuarios autenticados que todavía no tienen acceso aprobado.

Debe funcionar para:

```text
PENDING
BLOCKED
```

Texto recomendado:

```text
Acceso no habilitado

Tu cuenta de Google está autenticada, pero todavía no tiene
acceso a la aplicación.

Contacta al administrador para solicitar acceso.
```

Para `BLOCKED` el texto puede indicar que el acceso está bloqueado.

No revelar información de otros usuarios ni detalles internos de la whitelist.

---

# 11. Botón WhatsApp

Agregar un botón claro:

```text
[ Solicitar acceso por WhatsApp ]
```

El botón debe abrir WhatsApp con un mensaje prellenado.

Usar `wa.me` o mecanismo equivalente soportado por Android.

Ejemplo conceptual:

```text
https://wa.me/<NUMERO>?text=Quiero%20solicitar%20acceso%20a%20la%20aplicaci%C3%B3n.
```

El número NO debe quedar disperso dentro de varias clases.

Centralizarlo en una configuración, por ejemplo:

```kotlin
object ContactConfig {
    const val WHATSAPP_NUMBER = "REEMPLAZAR"
    const val ACCESS_MESSAGE =
        "Quiero solicitar acceso a la aplicación."
}
```

**No inventar el número.** Dejar un valor claramente reemplazable si todavía no está definido.

Si WhatsApp no está instalado:

- intentar abrir el navegador con `wa.me`, o
- mostrar un mensaje claro de que no se pudo abrir WhatsApp.

No lanzar un crash.

---

# 12. Fase 6 — Integración con MainActivity

Actualmente `MainActivity` es directamente el launcher y carga `MoneyCounterApp()`. 

Mantener ese comportamiento para el flujo aprobado, pero agregar un gate delante.

Patrón recomendado:

```kotlin
setContent {
    MoneyCounterTheme {
        AuthenticationGate {
            MoneyCounterApp()
        }
    }
}
```

Conceptualmente:

```text
MainActivity
   └── AuthenticationGate
         ├── Loading
         ├── LoginScreen
         ├── AccessRequiredScreen
         └── MoneyCounterApp
```

No modificar innecesariamente el estado de navegación interno de `MoneyCounterApp`.

El `currentScreen` actual y sus destinos deben permanecer funcionando igual después del gate. 

---

# 13. Fase 7 — Sign out

La primera versión debe permitir cerrar sesión.

Agregar una opción pequeña dentro de Settings, no en la pantalla principal si eso requiere rediseño importante.

Flujo:

```text
Settings
   ↓
Cerrar sesión
   ↓
Firebase signOut
   ↓
LoginScreen
```

Al cerrar sesión:

- no borrar datos locales;
- no borrar JSON;
- no borrar configuración;
- no resetear productos/monedas/denominaciones;
- no eliminar historial local.

Esto es importante para no destruir el comportamiento actual.

---

# 14. Fase 8 — Seguridad Firestore

Crear reglas mínimas desde el comienzo.

Principio:

```text
Usuario autenticado:
    puede leer SU documento users/{uid}

Usuario no autenticado:
    no puede leer users
```

Para el campo `access`, el cliente Android NO debe poder autoaprobarse.

Por tanto:

```text
users/{uid}.access
```

debe poder ser modificado por el administrador y/o backend confiable, no por el cliente.

**No implementar una regla que permita al usuario escribir su propio `access`.**

Para la colección de datos futuros:

```text
users/{uid}/...
```

las reglas deberán exigir:

```text
request.auth.uid == uid
```

No crear reglas excesivamente complejas para datos que todavía no existen.

---

# 15. Fase 9 — Preparar la futura sincronización sin migrarla todavía

Esta es la parte arquitectónica más importante.

Actualmente `MoneyCounterViewModel` crea directamente las implementaciones JSON. 

No reemplazar todas estas dependencias inmediatamente.

## 15.1 Mantener contratos existentes

Conservar:

```text
DenominationRepository
ProductRepository
CurrencyRepository
UnitRepository
SavedCountRepository
```

No cambiar sus APIs salvo que sea estrictamente necesario. Las interfaces existentes son precisamente el punto de extensión que debemos conservar.  

## 15.2 Agregar una capa pequeña de sincronización futura

Crear una abstracción mínima, por ejemplo:

```text
sync/
    SyncManager.kt
    SyncState.kt
```

No hace falta implementar toda la sincronización ahora.

La intención es que posteriormente pueda existir:

```text
Local JSON Repository
        ↕
Sync Manager
        ↕
Firestore Repository
```

sin cambiar la UI.

---

# 16. Decisión sobre datos locales vs Firestore

Para la primera entrega:

```text
LOCAL JSON = fuente operativa actual
FIREBASE   = identidad + autorización
```

NO usar Firestore como fuente principal de todos los datos todavía.

Motivos:

1. Minimiza riesgo.
2. No altera el funcionamiento existente.
3. Permite probar login/whitelist independientemente.
4. Evita migración accidental de datos.
5. Mantiene funcionamiento offline.
6. Permite implementar sincronización posteriormente.

Los repositorios JSON actuales ya encapsulan la lectura/escritura local. Aprovecharlos.  

---

# 17. Preparación de sincronización futura

La arquitectura futura debería poder evolucionar hacia:

```text
                         Firestore
                            ▲
                            │
                       SyncManager
                       ▲        │
                       │        ▼
                    Local JSON
                       ▲
                       │
                 Existing repositories
                       ▲
                       │
                 MoneyCounterViewModel
```

Pero **NO construir toda esta fase ahora**.

Solo dejar puntos de extensión razonables.

---

# 18. Futura estructura Firestore sugerida

No implementarla completamente en esta tarea salvo las partes necesarias para usuarios.

Estructura futura:

```text
users/{uid}
    email
    displayName
    access
    createdAt
    updatedAt

users/{uid}/products/{productId}
users/{uid}/denominations/{denominationId}
users/{uid}/currencies/{currencyId}
users/{uid}/units/{unitId}
users/{uid}/savedCounts/{savedCountId}
```

La elección de subcolecciones permite sincronización incremental y evita guardar el estado completo de la aplicación como un único documento gigante.

No guardar inicialmente:

```text
users/{uid}/appData = JSON gigante
```

La exportación JSON seguirá siendo una feature de backup/import/export, no el formato primario de Firestore.

---

# 19. Exportación/importación futura

No implementarla como parte obligatoria de esta primera fase.

Sin embargo, no romper los formatos actuales.

La app ya tiene JSON versionado. Mantener exactamente sus formatos y versiones.

Por ejemplo, productos utilizan una versión de JSON explícita y manejan versiones anteriores. 

El historial también utiliza un esquema JSON versionado. 

La exportación futura debería envolver los datos con una versión global sin modificar los formatos internos innecesariamente.

Ejemplo futuro:

```json
{
  "schemaVersion": 1,
  "exportedAt": 123456789,
  "app": "MoneyCounter",
  "products": {},
  "currencies": {},
  "denominations": {},
  "units": {},
  "history": {}
}
```

Esto queda explícitamente fuera de la primera fase salvo que sea necesario para no romper código existente.

---

# 20. Manejo offline del login/access

La primera experiencia debe distinguir:

## Usuario ya autenticado

Firebase Auth mantiene el estado de sesión localmente. La aplicación debe detectar al usuario autenticado sin obligarlo a seleccionar nuevamente la cuenta en cada apertura.

## Access status no disponible

Si el usuario tiene una sesión válida pero no se puede consultar Firestore por falta de red:

**NO inventar `APPROVED`.**

Comportamiento recomendado:

```text
no se puede verificar autorización
        ↓
mostrar estado de verificación / error de conexión
        ↓
permitir reintentar
```

No convertir un error de red en acceso concedido.

La política exacta de cache offline del `access` puede evolucionar después, pero la primera versión debe ser conservadora con la autorización.

---

# 21. Estados de UI

Crear estados explícitos para evitar pantallas inconsistentes.

Ejemplo:

```kotlin
sealed interface AppAccessState {
    data object Loading : AppAccessState
    data object SignedOut : AppAccessState
    data class Pending(val user: AuthUser) : AppAccessState
    data class Approved(val user: AuthUser) : AppAccessState
    data class Blocked(val user: AuthUser) : AppAccessState
    data class Error(val message: String) : AppAccessState
}
```

No es obligatorio usar exactamente esta clase, pero el comportamiento debe ser equivalente.

---

# 22. Lifecycle

La implementación debe observar correctamente los cambios de autenticación.

Escenarios a probar:

```text
app arrancada sin sesión
app arrancada con sesión
login exitoso
login cancelado
logout
access PENDING
access APPROVED
access BLOCKED
Firestore temporalmente no disponible
rotación/recreación de Activity
volver desde WhatsApp
```

No lanzar requests repetitivas en cada recomposición de Compose.

Los efectos secundarios deben ejecutarse con `LaunchedEffect`, ViewModel/coroutines u otro mecanismo correcto y estable.

---

# 23. WhatsApp y configuración

Crear una única fuente para:

```text
WHATSAPP_NUMBER
ACCESS_REQUEST_MESSAGE
```

No hardcodear la URL completa en una pantalla Compose.

La pantalla solo debería invocar algo similar a:

```kotlin
onRequestAccess()
```

La construcción del URI debe estar aislada en una utilidad pequeña si simplifica el código.

---

# 24. Qué NO hacer

No hacer ninguna de estas cosas:

- no migrar todos los JSON a Firestore en el mismo cambio;
- no reemplazar `MoneyCounterViewModel` completo;
- no reescribir las pantallas actuales;
- no convertir la app a navegación compleja solo para login;
- no introducir Room;
- no introducir Retrofit para Firestore;
- no crear Spring Boot;
- no almacenar contraseña;
- no implementar email/password;
- no usar el email como primary key;
- no permitir que el cliente cambie `access`;
- no permitir acceso a la app ante error de autorización;
- no borrar datos locales en logout;
- no modificar el formato de los JSON existentes;
- no actualizar versiones de Compose/Gradle/Kotlin sin necesidad;
- no hardcodear credenciales, API keys privadas o secretos;
- no subir keystore, `key.properties` ni otros secretos.

---

# 25. Compatibilidad con la aplicación actual

Debe comprobarse que después del cambio siguen funcionando:

## Contador

- ingresar objetivo;
- editar cantidades;
- incrementar/decrementar;
- calcular total;
- mostrar faltante;
- mostrar excedente;
- completar objetivo.

## Productos

- agregar;
- editar;
- eliminar;
- cantidades decimales;
- precios por moneda;
- recargos;
- stock.

## Monedas

- seleccionar;
- agregar;
- editar;
- eliminar;
- persistencia.

## Denominaciones

- agregar;
- editar;
- eliminar;
- reordenar;
- persistencia.

## Historial

- guardar conteo;
- abrir detalle;
- PDF;
- CSV.

No debe producirse ninguna regresión por el hecho de incorporar el login.

---

# 26. Tests requeridos

## 26.1 Tests de dominio existentes

Deben seguir pasando sin modificaciones innecesarias.

```bash
./gradlew test
```

## 26.2 Tests de autenticación

Agregar tests donde sea razonable para:

- estado signed out;
- usuario autenticado;
- logout;
- errores de login representados correctamente.

Evitar tests fuertemente acoplados a Firebase real en unit tests.

Usar abstracciones/mocks/fakes cuando corresponda.

## 26.3 Tests de acceso

Probar al menos:

```text
PENDING  → AccessRequiredScreen
APPROVED → MoneyCounterApp
BLOCKED  → AccessRequiredScreen
```

Y:

```text
sin sesión → LoginScreen
```

## 26.4 Tests de regresión UI

Verificar especialmente que `MoneyCounterApp()` se comporte igual una vez que el usuario está aprobado.

---

# 27. Firestore Rules — criterios de aceptación

La implementación no se considera completa si:

- el usuario puede escribir `access = APPROVED` por sí mismo;
- un usuario puede leer los documentos de otro usuario;
- un usuario sin autenticación puede leer usuarios;
- un usuario puede acceder a datos de otro UID.

Regla conceptual para datos privados futuros:

```text
request.auth != null
&& request.auth.uid == requestedUid
```

La autorización de `access` debe mantenerse controlada por una fuente confiable de administración.

---

# 28. Configuración administrativa mínima

Para la primera versión NO construir panel web de administración.

El administrador puede utilizar Firebase Console para cambiar manualmente:

```text
users/{uid}.access
```

de:

```text
PENDING → APPROVED
PENDING → BLOCKED
BLOCKED → APPROVED
```

Un panel administrativo es una fase posterior.

---

# 29. Flujo completo esperado

```text
                 APP START
                     │
                     ▼
              Firebase Auth
                     │
            ┌────────┴────────┐
            │                 │
        no session         session
            │                 │
            ▼                 ▼
        LoginScreen       Firestore
                              │
                              ▼
                         users/{uid}
                              │
                    ┌─────────┼─────────┐
                    │         │         │
                 PENDING   APPROVED   BLOCKED
                    │         │         │
                    ▼         ▼         ▼
                Access     Main App   Access
                Screen                Screen
                    │
                    ▼
                 WhatsApp
```

---

# 30. Implementación recomendada por commits

Para reducir riesgo, preferir cambios pequeños y fáciles de revertir.

## Commit 1

```text
chore: configure Firebase dependencies
```

Solo configuración/dependencias.

## Commit 2

```text
feat: add Google authentication
```

Auth repository + login.

## Commit 3

```text
feat: add Firebase access control
```

User document + access status + Firestore read.

## Commit 4

```text
feat: add authentication gate and access screen
```

Integración con MainActivity + screens.

## Commit 5

```text
feat: add WhatsApp access request
```

Botón + deep link + configuración de contacto.

## Commit 6

```text
feat: add logout
```

Cerrar sesión desde ajustes.

## Commit 7

```text
security: add Firestore rules
```

Reglas mínimas y seguras.

## Commit 8

```text
refactor: prepare sync abstraction
```

Solo si el cambio final deja una abstracción realmente útil y pequeña.

No implementar todavía toda la sincronización.

---

# 31. Criterios de aceptación finales

La feature está terminada cuando se cumple TODO lo siguiente:

- [ ] La app compila con las versiones actuales del proyecto.
- [ ] `./gradlew test` pasa.
- [ ] Un usuario puede iniciar sesión con Google.
- [ ] La sesión persiste entre aperturas normales de la app.
- [ ] Primer login crea `users/{uid}` con `access = PENDING`.
- [ ] Usuario PENDING no puede entrar a la aplicación.
- [ ] Usuario BLOCKED no puede entrar a la aplicación.
- [ ] Usuario APPROVED puede entrar a la aplicación completa.
- [ ] El usuario PENDING/BLOCKED ve la pantalla de acceso.
- [ ] El botón de WhatsApp abre el contacto con un mensaje prellenado.
- [ ] El número y mensaje de WhatsApp están centralizados y son fáciles de cambiar.
- [ ] El logout vuelve al LoginScreen.
- [ ] Logout no elimina JSON local.
- [ ] La whitelist no puede ser modificada por el cliente Android.
- [ ] Un usuario no puede leer el documento de otro usuario.
- [ ] La aplicación existente sigue funcionando igual para usuarios APPROVED.
- [ ] No se migraron todavía los JSON a Firestore.
- [ ] No se modificó innecesariamente el dominio existente.
- [ ] No se añadieron dependencias arquitectónicas innecesarias.
- [ ] No quedaron secretos en Git.

---

# 32. Verificación manual obligatoria

Antes de marcar la tarea como terminada, ejecutar una verificación real en dispositivo/emulador.

## Caso A — Usuario nuevo

```text
instalar app
↓
Login Google
↓
Firestore crea PENDING
↓
se muestra AccessRequiredScreen
```

## Caso B — Aprobar usuario

Desde Firebase Console:

```text
PENDING → APPROVED
```

Luego volver a la app y verificar que el usuario pueda entrar sin reinstalar.

## Caso C — Bloquear usuario

```text
APPROVED → BLOCKED
```

La siguiente verificación de acceso debe impedir entrar.

## Caso D — Logout

```text
APPROVED
↓
logout
↓
LoginScreen
```

Comprobar que productos, monedas, denominaciones e historial locales siguen intactos.

## Caso E — Regresión

Con usuario APPROVED, ejecutar las operaciones principales de la app y comprobar que funcionan exactamente como antes.

---

# 33. Después de esta fase

La siguiente feature separada será **Cloud Sync**.

Objetivo futuro:

```text
Login Google
      ↓
UID
      ↓
Local JSON / repository
      ↕
SyncManager
      ↕
Firestore
```

Con sincronización incremental por entidad:

```text
products
currencies
denominations
units
savedCounts
```

y posteriormente:

```text
Export JSON
Import JSON
Backup/Restore
Multi-device sync
```

No implementar estas capacidades como parte de la presente tarea salvo las interfaces mínimas que sean necesarias para no cerrar futuras opciones arquitectónicas.

---

# 34. Regla final para el agente

**Prioridad absoluta: preservar la aplicación existente.**

La aplicación actual ya tiene una separación útil entre dominio, ViewModel y repositorios JSON. Aprovecha esa estructura.

La autenticación y autorización deben quedar alrededor de la aplicación, no dentro de la lógica de negocio existente.

El resultado debe sentirse como:

```text
                Google Login
                      ↓
                Access Gate
                      ↓
             ┌────────┴────────┐
             │                 │
          Denegado           Aprobado
             │                 │
         WhatsApp        aplicación actual
                              │
                        mismos JSON
                        misma lógica
                        mismas pantallas
```

La nube se incorpora primero para **identidad y control de acceso**. La migración/sincronización de datos es una etapa posterior y debe diseñarse sin romper los contratos actuales.