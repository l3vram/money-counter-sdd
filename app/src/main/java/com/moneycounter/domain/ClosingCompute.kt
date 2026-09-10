package com.moneycounter.domain

import java.math.BigDecimal

/**
 * Pure computation for a period close. [movements] is expected to already be the
 * OPEN set selected for this close (e.g. via `openMovements`/manual selection),
 * filtered to [currencyId] by the caller — this function does not filter by
 * closingId or currency itself, it just tallies whatever it is given.
 *
 * Does not mutate [movements] or [products]. The caller (ViewModel) stamps the
 * selected movements' `closingId` with the returned Closing's id and persists both
 * movements and closings — this function only computes the snapshot.
 */
fun computeClosing(
    id: String,
    at: Long,
    movements: List<Movement>,
    products: List<Product>,
    currencyId: String,
    sellerUid: String = "",
    sellerName: String = ""
): Closing {
    val totalsByType: Map<MovementType, BigDecimal> = MovementType.entries.associateWith { type ->
        movements.filter { it.type == type }
            .fold(Money.ZERO) { acc, m -> acc.add(m.amount) }
            .setScale(Money.SCALE)
    }

    val netCash = totalsByType.getValue(MovementType.VENTA)
        .add(totalsByType.getValue(MovementType.COBRO))
        .subtract(totalsByType.getValue(MovementType.GASTO))
        .setScale(Money.SCALE)

    val stockSnapshot = products
        .filter { it.stock.signum() != 0 }
        .map { ClosingStockLine(name = it.name, unit = it.unit, quantity = it.stock) }

    return Closing(
        id = id,
        at = at,
        currencyId = currencyId,
        movementIds = movements.map { it.id },
        totalsByType = totalsByType,
        netCash = netCash,
        stockSnapshot = stockSnapshot,
        sellerUid = sellerUid,
        sellerName = sellerName
    )
}
