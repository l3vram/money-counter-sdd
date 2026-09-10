package com.moneycounter.domain

import java.math.BigDecimal

/**
 * One merged denomination line across the selected cash movements
 * (VENTA / COBRO): same [MovementDenomination.value] summed together.
 */
data class UnitedDenomination(
    val denominationValue: Long,
    val quantity: Long,
    val subtotal: BigDecimal
)

/**
 * One merged product line across the selected movements: same name+unit
 * summed together. [surcharge] is always zero because [MovementProductLine]
 * carries no surcharge (unitPrice already includes the price).
 */
data class UnitedProduct(
    val name: String,
    val unit: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val surcharge: BigDecimal,
    val subtotal: BigDecimal
)

/** A "reporte seleccionable": consolidation of the cash movements the user picked. */
data class UnitedCount(
    val currencyId: String,
    val currencyCode: String,
    val targetAmountTotal: BigDecimal,
    val items: List<UnitedDenomination>,
    val products: List<UnitedProduct>,
    val count: Int
) {
    fun total(): BigDecimal = targetAmountTotal
}

/**
 * Merges the denomination-bearing movements (VENTA / COBRO) the user selected
 * into a single report: total, denominations summed by value and products
 * summed by name+unit. An empty selection or movements of mixed currencies
 * throw [IllegalArgumentException].
 */
fun uniteMovements(movements: List<Movement>, currencyCode: String = ""): UnitedCount {
    if (movements.isEmpty()) throw IllegalArgumentException("La lista de ventas está vacía")
    val ids = movements.map { it.currencyId }.distinct()
    if (ids.size != 1) throw IllegalArgumentException(
        "Las ventas seleccionadas son de monedas distintas (${ids.joinToString(", ")})"
    )

    val total = movements.fold(Money.ZERO) { acc, m -> acc.add(m.amount) }

    val denominations = movements
        .flatMap { it.denominations }
        .groupBy { it.value }
        .map { (value, lines) ->
            val qty = lines.sumOf { it.quantity }
            UnitedDenomination(value, qty, Money.fromLong(value * qty))
        }
        .sortedByDescending { it.denominationValue }

    val products = movements
        .flatMap { it.products }
        .groupBy { it.name to it.unit }
        .map { (key, lines) ->
            val firstLine = lines.first()
            val qty = lines.fold(Money.ZERO) { acc, line -> acc.add(line.quantity) }
            UnitedProduct(
                name = key.first,
                unit = key.second,
                quantity = qty,
                unitPrice = firstLine.unitPrice,
                surcharge = Money.ZERO,
                subtotal = lines.fold(Money.ZERO) { acc, line -> acc.add(line.subtotal) }
            )
        }
        .sortedWith(compareBy({ it.name }, { it.unit }))

    return UnitedCount(
        currencyId = ids.first(),
        currencyCode = currencyCode,
        targetAmountTotal = total.setScale(Money.SCALE),
        items = denominations,
        products = products,
        count = movements.size
    )
}