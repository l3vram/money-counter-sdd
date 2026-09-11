# Plan 016 — Pendientes menores (stock OWNER, avatar, LuisoButton)

> Rama: `feature/pendientes-menores`. Creado 2026-09-11. Cierra tres pendientes del README:
> stock solo OWNER borra, avatar con photoUrl sin inicial superpuesta y LuisoButton 48dp.

## 1. Stock: solo el OWNER puede eliminar productos

- `StockScreen` deriva `canEditStock = member?.role.mayEditStock()` (OWNER=true; SELLER/SUPERUSER=false;
  null role = true, mantiene los privilegios de hoy).
- `ProductRow` recibe `canDelete` y solo renderiza el botón Eliminar cuando es verdadero.
- `mayEditStock`/`mayEditStock gating` ya estaban cubiertos en `RoleTest` (246 tests).

## 2. Avatar: photoUrl sin inicial superpuesta

- `LuisoAvatar` en `Components.kt`: cuando hay `photoUrl` se muestra SOLO la foto (ya no se superpone
  la inicial). Si no hay foto, se muestra la inicial; si no hay inicial, el icono Person.

## 3. LuisoButton 48dp (a11y)

- Alto del botón principal `LuisoButton` de 40dp → 48dp (touch target mínimo del design kit).

## 4. No hacer

- **Fiado por partes** (plan 014): el dueño decidió que NO se hace. Queda como referencia, no planificado.

## 5. Verificación

- `compileDebugKotlin` OK, `testDebugUnitTest` 246/246 verde, `assembleDebug` OK.