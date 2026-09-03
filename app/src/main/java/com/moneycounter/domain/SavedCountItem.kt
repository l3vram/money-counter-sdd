package com.moneycounter.domain

import java.math.BigDecimal

data class SavedCountItem(
    val denominationValue: Long,
    val quantity: Long,
    val subtotal: BigDecimal
) {
    init {
        require(denominationValue > 0) { "Denomination value must be positive" }
        require(quantity >= 0) { "Quantity must be non-negative" }
    }
}
