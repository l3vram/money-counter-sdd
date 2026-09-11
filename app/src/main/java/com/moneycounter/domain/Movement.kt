package com.moneycounter.domain

import java.math.BigDecimal

/**
 * A single entry ("asiento") in the unified movement journal. FOUNDATION ONLY:
 * this is a read model seeded from the four legacy stores (count_history.json,
 * receivables.json, payments.json, writeoffs.json). Nothing writes here yet —
 * that is a later migration (see plan 009).
 */
enum class MovementType { ALTA, ENTRADA, GASTO, MERMA, VENTA, VENTA_FIADO, COBRO }

data class MovementProductLine(
    val name: String,
    val unit: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal
)

data class MovementDenomination(
    val value: Long,
    val quantity: Long,
    val subtotal: BigDecimal
)

data class Movement(
    val id: String,
    val at: Long,
    val type: MovementType,
    val currencyId: String,
    val concept: String? = null,
    val products: List<MovementProductLine> = emptyList(),
    val denominations: List<MovementDenomination> = emptyList(),
    val amount: BigDecimal,
    val linkId: String? = null,
    val closingId: String? = null,
    val sellerUid: String = "",
    val sellerName: String = ""
) {
    init {
        require(id.isNotBlank()) { "Movement ID must not be blank" }
        require(amount.signum() >= 0) { "Movement amount must be non-negative" }
    }
}

/** +1 when the movement adds stock, -1 when it removes stock, 0 when it does not touch stock. */
fun MovementType.affectsStockSign(): Int = when (this) {
    MovementType.ALTA, MovementType.ENTRADA -> 1
    MovementType.MERMA, MovementType.VENTA, MovementType.VENTA_FIADO -> -1
    MovementType.GASTO, MovementType.COBRO -> 0
}

/** Whether movements of this type carry a cash-denomination breakdown. */
fun MovementType.hasDenominations(): Boolean = when (this) {
    MovementType.VENTA, MovementType.COBRO -> true
    else -> false
}

/** Cash-flow delta for a movement type: +1 cash in, -1 cash out, null = does not touch cash. */
fun MovementType.cashSign(): Int? = when (this) {
    MovementType.VENTA, MovementType.COBRO -> 1
    MovementType.GASTO, MovementType.MERMA -> -1
    else -> null
}

/**
 * Net cash total of movements: VENTA + COBRO minus GASTO + MERMA.
 * Fiados, altas and entradas do not touch cash and are excluded (see [receivableTotal]).
 */
fun netCashTotal(movements: List<Movement>): BigDecimal = movements.fold(BigDecimal.ZERO) { acc, m ->
    val sign = m.type.cashSign()
    if (sign == null) acc else acc.add(m.amount.multiply(BigDecimal.valueOf(sign.toLong())))
}

/** Total outstanding credit (VENTA_FIADO) in a list of movements. */
fun receivableTotal(movements: List<Movement>): BigDecimal = movements.fold(BigDecimal.ZERO) { acc, m ->
    if (m.type == MovementType.VENTA_FIADO) acc.add(m.amount) else acc
}
