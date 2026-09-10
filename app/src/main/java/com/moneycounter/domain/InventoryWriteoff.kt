package com.moneycounter.domain

import java.math.BigDecimal

/**
 * A "baja por merma" (inventory write-off): inventory lost without a sale
 * (spoiled/damaged/etc). Reduces on-hand stock and records a VALUED loss
 * (quantity x unit price) for reporting. Moves NO cash — this is not a sale
 * and not a customer document.
 */
data class InventoryWriteoff(
    val id: String,
    val at: Long,
    val productId: String,
    val name: String,        // snapshot (survives product edits/deletes)
    val unit: String,        // snapshot
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,   // effective unit price in currency at write-off time
    val lossValue: BigDecimal,   // quantity * unitPrice, setScale(Money.SCALE)
    val currencyId: String,
    val reason: String? = null,
    val sellerUid: String = "",
    val sellerName: String = ""
) {
    init {
        require(id.isNotBlank()) { "InventoryWriteoff ID must not be blank" }
        require(quantity.signum() > 0) { "InventoryWriteoff quantity must be positive" }
    }
}
