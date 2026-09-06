package com.moneycounter.domain

import java.math.BigDecimal

data class ProductSelection(
    val productId: String? = null,
    val quantityText: String = ""
) {
    fun quantity(): BigDecimal {
        return parseQuantity(quantityText)
    }

    companion object {
        fun parseQuantity(text: String): BigDecimal {
            val cleaned = text.trim().replace(',', '.')
            if (cleaned.isEmpty()) return Money.ZERO
            return runCatching { BigDecimal(cleaned).setScale(Money.SCALE) }
                .getOrNull() ?: Money.ZERO
        }
    }
}