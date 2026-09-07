package com.moneycounter.domain

import java.math.BigDecimal

data class UnitedDenomination(
    val denominationValue: Long,
    val quantity: Long,
    val subtotal: BigDecimal
)

data class UnitedProduct(
    val name: String,
    val unit: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val surcharge: BigDecimal,
    val subtotal: BigDecimal
)

data class UnitedCount(
    val currencyId: String,
    val currencySymbol: String,
    val currencyCode: String,
    val targetAmountTotal: BigDecimal,
    val items: List<UnitedDenomination>,
    val products: List<UnitedProduct>,
    val count: Int
) {
    fun total(): BigDecimal = targetAmountTotal
}

fun uniteCounts(counts: List<SavedCount>, currencyCode: String = ""): UnitedCount {
    if (counts.isEmpty()) throw IllegalArgumentException("La lista de ventas está vacía")
    val ids = counts.map { it.currencyId }.distinct()
    if (ids.size != 1) throw IllegalArgumentException(
        "Las ventas seleccionadas son de monedas distintas (${ids.joinToString(", ")})"
    )

    val first = counts.first()
    val total = counts.fold(Money.ZERO) { acc, c -> acc.add(c.targetAmount) }

    val denominations = counts
        .flatMap { it.items }
        .groupBy { it.denominationValue }
        .map { (value, lines) ->
            val qty = lines.sumOf { it.quantity }
            UnitedDenomination(value, qty, Money.fromLong(value * qty))
        }
        .sortedByDescending { it.denominationValue }

    val products = counts
        .flatMap { it.products }
        .groupBy { it.name to it.unit }
        .map { (key, lines) ->
            val firstLine = lines.first()
            val effective = firstLine.unitPrice.add(firstLine.surcharge)
            val qty = lines.fold(Money.ZERO) { acc, line -> acc.add(line.quantity) }
            UnitedProduct(
                name = key.first,
                unit = key.second,
                quantity = qty,
                unitPrice = firstLine.unitPrice,
                surcharge = firstLine.surcharge,
                subtotal = effective.multiply(qty).setScale(Money.SCALE)
            )
        }
        .sortedWith(compareBy({ it.name }, { it.unit }))

    return UnitedCount(
        currencyId = ids.first(),
        currencySymbol = first.currency,
        currencyCode = currencyCode,
        targetAmountTotal = total.setScale(Money.SCALE),
        items = denominations,
        products = products,
        count = counts.size
    )
}