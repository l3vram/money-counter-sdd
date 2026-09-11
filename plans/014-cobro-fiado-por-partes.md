# Plan 014 — Cobro de fiado con productos visibles (ahora) + Fiado por partes (futuro)

> Rama: `feature/cobro-con-productos`. Creado 2026-09-11.

## 1. Hecho hoy (no-modificable)

Al cobrar una deuda (modo collecting), la vista del contador muestra los **productos
y cantidades de la deuda** en tarjeta de solo lectura (`FiadoProductsCard`), reemplazando
la sección editable de productos. El deudor ve qué debe, sin poder cambiar nada todavía.

Además, el botón **"COBRAR / SALDAR CUENTA"** solo se muestra cuando existen deudas
abiertas por cobrar para la moneda seleccionada. Cuando no hay fiados pendientes, el botón
queda oculto (no ensucia la vista).

- `MoneyCounterScreen.kt`: `FiadoProductsCard` (read-only) + swap `ProductsSection` ↔ `FiadoProductsCard` según `collectingFiado`; botón cobrar condicionado a `openFiadoMovements.isNotEmpty() && collectingFiado == null`.
- `MovementDetailScreen.kt`: `MovementProductLineRow` pasó de `private` a pública para reutilizarse.
- **Fix teclado**: `AndroidManifest.xml` `windowSoftInputMode` `adjustPan` → `adjustResize`. Con `enableEdgeToEdge()` + `.imePadding()`, `adjustPan` producía pan del sistema + padding de Compose a la vez → hueco vacío entre el campo seleccionado y el teclado (visible en contador/denominaciones). `adjustResize` dispacha los IME insets para que `.imePadding()` actúe solo.
- **Fix espacio residual**: `MainActivity.kt` — al estar visible el teclado (`WindowInsets.ime.getBottom(...) > 0`) se oculta la bottom nav flotante. Su altura (~92dp) quedaba como padding inferior vacío entre el top del teclado y el contenido; ocultándola el contenido llega hasta el borde del teclado y se ve todo lo posible. La pill reaparece al cerrar el teclado.
- Verificado: `compileDebugKotlin` + 242 tests verde.

## 2. Futuro — Fiado por partes (deferido, direccion preferida: Variante 3)

El dueño quiere, más adelante, poder **cobrar parcialmente** una deuda: el pago puede o no
ser completo, puede modificar cantidades, quitar productos, y lo no pagado queda como deuda.

### Variante 3 (PREFERIDA en discusión) — Cierre + reapertura flexible

1. Al cobrar una deuda, el usuario puede **modificar cantidades** de cada producto y/o
   **eliminar productos** del cobro. El monto a cobrar se recalcula en tiempo real
   = Σ (cantidad × precio unitario) de los productos/porciones que sí se cobran.
2. Se registra el COBRO por lo efectivamente cobrado (con las denominaciones contadas),
   y el COBRO lleva la lista de productos/porciones que cubre (product lines).
3. La deuda original se **cierra** (se marca como saldada/cerrada) y se **crea un nuevo
   VENTA_FIADO** con lo pendiente: los productos que quedaron sin cobrar + las cantidades
   restantes (ej: Frijol 5lb completo, y Arroz 10lb de 20lb). Ese nuevo fiado queda OPEN.
4. Repetir indefinidamente hasta liquidar todo: si el nuevo fiado se cobra en su totalidad,
   se liquida; si otra vez es parcial, se cierra y se crea otro fiado pendiente.

Reglas de consistencia:
- Permite **producto** completo, **porción** de cantidad, o combinación.
- La deuda SOLO se liquida cuando se paga el total de TODOS los productos con TODAS sus cantidades.
- Sin pagos de más: monto a cobrar = lo que el deudor paga; si cuenta efectivo que excede → OVER (igual que hoy).

### Variantes descartadas (registro)

- **V1 — Cobro parcial con deuda residual automática**: 1 cobro por fiado, se crea uno nuevo con lo pendiente. Más simple pero multiplica fiados en pagos múltiples.
- **V2 — Cobros parciales acumulativos**: el fiado original queda abierto hasta liquidar; "saldo pendiente" = total − Σ(cobros). Más historia en un solo registro, cálculo más complejo y más superficie de bug (ruedas, reportes).

### Impacto estimado (futuro, cuando se planifique)

- Domain: COBRO necesita `products: List<MovementProductLine>` (hoy `buildCobroMovement` no las lleva).
- VM: `recordCollection()` → variante heurística para decidir cierre + nueva apertura; estado de edición de cantidades por-producto en el modo collecting.
- UI: `FiadoProductsCard` pasa de solo-lectura a editable (campos de cantidad + quitar producto).
- `isFiadoOpen`/`openFiadoMovementsPure`: el fiado original queda excluido al tener COBRO linkId; el nuevo fiado nace OPEN. Cuidado con doble stock: el COBRO no toca stock, el nuevo VENTA_FIADO tampoco deduce (la salida ya se dedujo en el primer fiado).
- Reportes/cierres: el COBRO con product lines + products queda neto igual (COBRO no afecta stock).