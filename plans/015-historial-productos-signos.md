# Plan 015 — Historial con productos+cantidades, señales +/− y COBRO con productos

> Rama: `feature/historial-productos-signos`. Creado 2026-09-11. Cierra los pendientes
> "Cantidades en el Historial" y "Señales +/− en reportes y cierres", y atiende la petición
> del dueño: "los cobros deben quedar como una venta — con producto y cantidad — en listado y detalle".

## 1. COBRO lleva productos (nuevo cobro + migración legada)

- `MoneyCounterViewModel.buildCobroMovement(..., products = ...)` acepta las líneas de producto
  (default vacío) y `recordCollection()` las toma del fiado (`fiado.products`): cada COBRO nuevo
  registra exactamente los productos y cantidades que se cobran.
- `MovementMigration.fromLegacy`: los `Payment` legados mapean a COBRO llevando los productos del
  receivable linkado (`receivableId`), si existe.
- Efecto: en Historial y en el detalle (`MovementDetailScreen`, que ya renderiza `movement.products`
  para cualquier movimiento) un COBRO muestra sus productos, igual que una venta.
- Nota: los COBRO históricos creados antes de este cambio (sin products) no muestran productos;
  solo los nuevos y los migrados. Aceptado.

## 2. Cantidades en el Historial

- `ReportsScreen.MovementRow`: ahora lista el concepto (deudor/gasto) y debajo los productos con
  cantidad y unidad (`"Arroz 20 Lb  ·  Frijol 5 Lb"`) vía `MovementProductSummary`.
- Lo mismo en `CierresScreen.OpenMovementRow` por consistencia.

## 3. Señales +/− en historial y cierres

- `MovementType.moneySign()` en `ui/components/Components.kt`: `-` para GASTO/MERMA, `~` para
  VENTA_FIADO (crédito pendiente, NO es caja), `+` para el resto.
- Historial y Cierres muestran el monto precedido del signo; GASTO/MERMA en color error,
  VENTA_FIADO en tono apagado (onSurfaceVariant) para diferenciarlo del efectivo.
- `ClosingPreviewCard`: los totales por tipo llevan signo (GASTO/MERMA en rojo, fiado apagado).
  El NETO en caja no lleva signo manual (ya es un neto firmado).

## 3b. Corrección del TOTAL del día (fiado no es caja)

- El día sumaba el monto de TODOS los movimientos con signo positivo, mostrando el fiado como
  efectivo en caja y sumando el gasto. Corregido:
  - `MovementType.cashSign()` (domain): `+1` VENTA/COBRO, `-1` GASTO/MERMA, `null` (no toca caja)
    ALTA/ENTRADA/VENTA_FIADO.
  - `netCashTotal(movements)` = `VENTA + COBRO − GASTO − MERMA` (fiados/altas/entradas excluidos),
    consistente con el neto del cierre (que ya ignoraba el fiado).
  - `receivableTotal(movements)` = suma de VENTA_FIADO.
  - `MovementDayRow` en ReportsScreen: muestra `TOTAL ±<neto>` (color error si neto negativo) y,
    cuando hay fiados, una línea aparte `Por cobrar (fiado): ~<monto>`.

## 4. Decisiones del dueño

- **ELIMINAR lote del Historial**: CANCELADO — el historial nunca se borra.
- **Stock solo OWNER borra** y **fiado por partes (plan 014, Variante 3)**: siguen pendientes.
- **Productos en detalle del cobro**: quedan cubiertos por el punto 1 (MovementDetailScreen ya renderiza products).

## 5. Verificación

- `compileDebugKotlin` OK, `testDebugUnitTest` 246/246 verde (COBRO lleva productos del fiado;
  migración legada lleva productos del receivable; `cashSign`/`netCashTotal`/`receivableTotal`),
  `assembleDebug` OK.