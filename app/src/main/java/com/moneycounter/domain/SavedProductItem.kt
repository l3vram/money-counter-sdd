package com.moneycounter.domain

import java.math.BigDecimal

data class SavedProductItem(
    val name: String,
    val unit: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val surcharge: BigDecimal = Money.ZERO,
    val subtotal: BigDecimal
) {
    init {
        require(name.isNotBlank()) { "Product name must not be blank" }
        require(quantity.signum() >= 0) { "Quantity must be non-negative" }
    }
}