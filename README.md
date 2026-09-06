# Money Counter — Contador de Dinero (El Luiso)

Aplicación Android para contar efectivo físico por denominación. Orientada al uso en Cuba,
donde el usuario define y mantiene sus propias denominaciones. Funciona **100% offline**, sin
cuenta, backend ni base de datos.

## Qué hace

- Define **productos** (nombre, unidad de medida, precio por unidad y recargo fijo) y agrega
  filas con cantidades decimales (ej. 1,5 Lb) — el **objetivo = total de productos**.
- Compara el monto contado en denominaciones contra ese objetivo y muestra en tiempo real:
  - objetivo / contado / progreso en porcentaje
  - cuánto falta (`FALTAN`) cuando el conteo está por debajo
  - excedente (`EXCEDENTE`) cuando supera el objetivo
  - estado completado (`✓ MONTO COMPLETADO`) cuando coincide
- Multi-moneda por operación: **CUP** (Peso Cubano, `$`) y **USD** (Dólar Americano, `US$`)
  por defecto, con monedas **agregables/editable/eliminables**. El símbolo se muestra y se usa
  en todo el conteo.
- Guarda cada conteo completado en el **historial** con fecha, moneda, productos y
  denominaciones.
- Exporta cada registro a **PDF** o a **Excel (CSV)** desde el detalle del historial.

## Stack

- Lenguaje: Kotlin
- UI: Jetpack Compose + Material 3
- Arquitectura: ViewModel → dominio puro → repository (JSON local). Sin frameworks de DI,
  sin Room, sin backend.
- Mínimo Android SDK 26 (Android 8.0), target 34.

## Características

### Conteo (pantalla principal)
- Sección **PRODUCTOS**: filas estilo "destinos múltiples" —
  seleccionar producto, cantidad decimal, subtotal por línea (`precio efectivo × cantidad`,
  incluye recargo si lo hay) y botón `+ AGREGAR PRODUCTO`.
- Selector de **moneda activa** de la operación en la cabecera de productos.
- **RESUMEN** debajo de los productos (objetivo = total de productos, contado, progreso,
  estado, botón `GUARDAR EN HISTORIAL` visible al completar), y debajo las **DENOMINACIONES**.
- Conteo por denominación configurable: botones `+`/`−`, edición directa y subtotal automático.
  - Campo vacío = nada (no contribuye al cálculo).
  - Ceros al frente se ignoran (`007` → `7`), ceros al final se respetan (`10` → `10`).
- Cálculo monetario con `BigDecimal` (sin errores de coma flotante).

### Ajustes
- **MONEDA**: seleccionar la activa, agregar/editar/eliminar (código de 3 letras, nombre,
  símbolo). No se elimina la moneda en uso o la única restante.
- **PRODUCTOS**: agregar/editar/eliminar con nombre, unidad (dropdown desde unidades
  configuradas), precio por unidad y recargo fijo por unidad.
- **UNIDADES DE MEDIDA**: agregar/editar/eliminar. Al renombrar una unidad se actualizan los
  productos que la usaban; no se elimina si es la única o está en uso por un producto.
- **DENOMINACIONES**: agregar, editar, eliminar y reordenar (confirmación al eliminar, bloqueo
  de edición mientras haya un conteo activo).

### Historial y exportación
- Lista de conteos guardados con total, fecha y moneda.
- Detalle con moneda, total, líneas de producto y denominaciones.
- **EXPORTAR PDF**: reporte con fecha, moneda, total, productos y denominaciones.
- **EXPORTAR EXCEL (CSV)**: mismo contenido en CSV con BOM UTF-8 (abre en Excel conservando
  acentos), compartido por el menú de Android.

### Persistencia
- Denominaciones, monedas, unidades y productos en JSON en almacenamiento privado
  (sobrevive reinicios, tolera JSON corrupto devolviéndose a los valores por defecto).
- Historial en `count_history.json` (versión 2, compatible con la 1).
- Escritura atómica (archivo temporal + rename).

## Estructura del proyecto

```text
app/src/main/java/com/moneycounter/
  domain/          Modelos de dominio puro (dinero, denominaciones, productos, monedas, unidades, historial)
  repository/      Persistencia JSON (denominaciones, monedas, productos, unidades, historial)
  ui/              Compose: componentes + screens (Contador, Ajustes, Historial, Detalle)
  util/            PdfExporter (PDF) y ExcelExporter (CSV)
  viewmodel/       MoneyCounterViewModel (estado + lógica de UI)
app/src/test/      Tests unitarios del dominio
README.md          Este documento
spec.md            Requerimientos y criterios de aceptación.
plan.md            Decisiones técnicas de arquitectura.
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

El APK de release firmado queda en
`app/build/outputs/apk/release/MoneyCounter-v1.1.apk`.

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
- Toda la configuración y el historial son datos locales de la aplicación.
- No se suben claves ni materiales de firma al repositorio.

## Pruebas

Los tests unitarios cubren el dominio de cálculo (`MoneyCounterCalculator`), el parseo de
cantidad (`QuantityParser`), el parseo de cantidades de producto y el precio efectivo
(`Product`/`ProductSelection`), y la serialización del historial (`SavedCountJson`).

```bash
./gradlew test
```

## Licencia

Proyecto personal. Sin licencia específica — uso libre con atribución.