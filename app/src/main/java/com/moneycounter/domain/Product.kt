package com.moneycounter.domain

import java.math.BigDecimal

data class Product(
    val id: String,
    val name: String,
    val unit: String,
    val stock: BigDecimal = Money.ZERO,
    val prices: Map<String, ProductPrice> = emptyMap()
) {
    init {
        require(id.isNotBlank()) { "Product ID must not be blank" }
        require(name.isNotBlank()) { "Product name must not be blank" }
        require(unit.isNotBlank()) { "Product unit must not be blank" }
    }

    fun priceFor(currencyId: String): ProductPrice? = prices[currencyId]

    fun effectiveUnitPriceFor(currencyId: String): BigDecimal? =
        prices[currencyId]?.effectiveUnitPrice?.setScale(Money.SCALE)

    fun stockValueFor(currencyId: String): BigDecimal? =
        prices[currencyId]?.effectiveUnitPrice?.multiply(stock)?.setScale(Money.SCALE)

    fun hasPriceIn(currencyId: String): Boolean = prices.containsKey(currencyId)
}
