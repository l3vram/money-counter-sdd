package com.moneycounter.domain

import java.math.BigDecimal

data class SavedCount(
    val id: String,
    val savedAt: Long,
    val targetAmount: BigDecimal,
    val items: List<SavedCountItem>,
    val currency: String = "$",
    val products: List<SavedProductItem> = emptyList(),
    val currencyId: String = DefaultCurrencies.CUP.id,
    val sellerUid: String = "",
    val sellerName: String = ""
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(targetAmount.signum() > 0) { "targetAmount must be positive" }
    }
}