# Money Counter — Contador de Dinero (El Luiso)

Aplicación Android para contar efectivo físico por denominación. Orientada al uso en Cuba,
donde el usuario define y mantiene sus propias denominaciones. Funciona **100% offline**, sin
cuenta, backend ni base de datos.

Compara la cantidad contada contra un objetivo y muestra en tiempo real:
- objetivo
- contado
- cuánto falta (`FALTAN`) cuando el conteo está por debajo
- excedente (`EXCEDENTE`) cuando supera el objetivo
- estado completado (`✓ MONTO COMPLETADO`) cuando coincide

## Stack

- Lenguaje: Kotlin
- UI: Jetpack Compose + Material 3
- Arquitectura: ViewModel → dominio puro → repository (JSON local). Sin frameworks de DI,
  sin Room, sin backend.
- Mínimo Android SDK 26 (Android 8.0), target 34.

## Características

- Entrada de monto objetivo.
- Conteo por denominación configurable: botones `+`/`−`, edición directa y subtotal automático.
  - Campo vacío = nada (no contribuye al cálculo).
  - Ceros al frente se ignoran (`007` → `7`), ceros al final se respetan (`10` → `10`).
- Configuración de denominaciones: agregar, editar, eliminar y reordenar (con confirmación al
  eliminar, y bloqueo de edición mientras haya un conteo activo).
- Persistencia local de la configuración de denominaciones (JSON en almacenamiento privado,
  sobrevive reinicios, tolera JSON corrupto devolviéndose a los valores por defecto).
- Acción "BORRAR TODO" segura (limpieza del conteo con confirmación, nunca borra denominaciones).
- Cálculo monetario con `BigDecimal` (sin errores de coma flotante).

## Estructura del proyecto

```text
spec.md            Requerimientos y criterios de aceptación.
plan.md            Decisiones técnicas de arquitectura.
tasks.md           Checklist de implementación.
data-model.md      Estructuras de datos de dominio y persistencia.
research.md        Racional de decisiones técnicas.
quickstart.md      Procedimiento de validación manual.
app/               Módulo Android (código fuente y tests).
plans/             Planes de implementación por funcionalidad.
```

## Compilación

Requisitos: JDK 17+, Android SDK (ver `local.properties`).

```bash
# Compilar el APK de depuración
./gradlew assembleDebug

# Ejecutar los tests unitarios
./gradlew test

# Compilar el APK de release (firmado)
./gradlew assembleRelease
```

El APK de release queda en `app/build/outputs/apk/release/app-release.apk`.

## Firma del release

La firma de release está desactivada si no existe `key.properties`. Para firmar:

1. Genera una keystore (una sola vez, consérvala a buen recaudo — sin ella no puedes
   publicar actualizaciones del mismo app):

```bash
keytool -genkeypair -v -keystore moneycounter-release.jks \
  -alias moneycounter -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Money Counter, OU=Dev, O=MoneyCounter, L=Havana, ST=La Habana, C=CU"
```

2. Crea `key.properties` en la raíz del proyecto:

```properties
storeFile=moneycounter-release.jks
storePassword=<tu-clave>
keyAlias=moneycounter
keyPassword=<tu-clave>
```

> **Importante (seguridad):** `key.properties`, `moneycounter-release.jks` y `local.properties`
> están gitignoreados y **no deben subirse al repositorio**. Guarda la keystore en un lugar
> seguro (por ejemplo, un gestor de contraseñas o un backup cifrado).

## Seguridad

- Sin permisos de red, sin cuentas, sin recolección de datos.
- La configuración de denominaciones es dato local de la aplicación.
- No se suben claves ni materiales de firma al repositorio.

## Pruebas

Los tests unitarios cubren el dominio de cálculo (`MoneyCounterCalculator`) y el parseo de
cantidad (`QuantityParser`), incluyendo los casos límite de ceros y campos vacíos.

```bash
./gradlew test
```

## Licencia

Proyecto personal. Sin licencia específica — uso libre con atribución.
