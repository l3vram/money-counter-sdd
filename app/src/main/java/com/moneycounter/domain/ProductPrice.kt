package com.moneycounter.domain

import java.math.BigDecimal

data class ProductPrice(
    val unitPrice: BigDecimal,
    val surcharge: BigDecimal = Money.ZERO
) {
    init {
        require(unitPrice.signum() >= 0) { "Unit price must be non-negative" }
        require(surcharge.signum() >= 0) { "Surcharge must be non-negative" }
    }

    val effectiveUnitPrice: BigDecimal
        get() = unitPrice.add(surcharge).setScale(Money.SCALE)
}
