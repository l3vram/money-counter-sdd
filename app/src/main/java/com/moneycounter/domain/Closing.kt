package com.moneycounter.domain

import java.math.BigDecimal

/** One line of remaining stock captured at the moment a [Closing] was made. */
data class ClosingStockLine(
    val name: String,
    val unit: String,
    val quantity: BigDecimal
)

/**
 * An immutable snapshot of a period close ("cierre": corte de caja e inventario).
 * A Closing never rewrites how movements are written (plans 009/011) — it only
 * READS the OPEN movements selected at close time, tallies them, snapshots stock,
 * and remembers which movements it closed via [movementIds]. The caller (VM) stamps
 * those same movements' `closingId` with this Closing's [id] so none of them can be
 * selected into a future close (see MoneyCounterViewModel.createClosing/openMovements).
 */
data class Closing(
    val id: String,
    val at: Long,
    val currencyId: String,
    val movementIds: List<String>,
    val totalsByType: Map<MovementType, BigDecimal>,
    /**
     * Net cash change for the period: VENTA + COBRO - GASTO.
     * Deliberately excludes (though they still appear in [totalsByType]):
     *  - VENTA_FIADO: no cash moved yet, it's a receivable ("por cobrar").
     *  - MERMA: a stock loss, not a cash movement.
     *  - ALTA/ENTRADA: stock-in value, not a cash movement.
     */
    val netCash: BigDecimal,
    val stockSnapshot: List<ClosingStockLine>,
    val sellerUid: String = "",
    val sellerName: String = ""
) {
    init {
        require(id.isNotBlank()) { "Closing ID must not be blank" }
    }
}
