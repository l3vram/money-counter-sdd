package com.moneycounter.domain

import java.math.BigDecimal

data class Product(
    val id: String,
    val name: String,
    val unit: String,
    val unitPrice: BigDecimal,
    val surcharge: BigDecimal = Money.ZERO
) {
    init {
        require(id.isNotBlank()) { "Product ID must not be blank" }
        require(name.isNotBlank()) { "Product name must not be blank" }
        require(unit.isNotBlank()) { "Product unit must not be blank" }
        require(unitPrice.signum() >= 0) { "Unit price must be non-negative" }
        require(surcharge.signum() >= 0) { "Surcharge must be non-negative" }
    }

    val effectiveUnitPrice: BigDecimal
        get() = unitPrice.add(surcharge).setScale(Money.SCALE)
}