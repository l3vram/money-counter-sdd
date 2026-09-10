package com.moneycounter.domain

import java.math.BigDecimal

enum class ReceivableStatus { OPEN, SETTLED }

/**
 * A "cuenta por cobrar" created by a credit sale (venta a crédito / fiado):
 * goods leave (stock deducted) but NO cash comes in at sale time. Records
 * who owes, how much, when, and what was taken, to collect later and to
 * show as outstanding debt in reports. Settlement/collection is out of
 * scope here.
 */
data class Receivable(
    val id: String,
    val at: Long,
    val debtorName: String,
    val amount: BigDecimal,                 // = sale total at credit-sale time, setScale(Money.SCALE)
    val currencyId: String,
    val products: List<SavedProductItem>,   // snapshot of what was taken
    val status: ReceivableStatus = ReceivableStatus.OPEN,
    val settledAt: Long? = null,
    val sellerUid: String = "",
    val sellerName: String = ""
) {
    init {
        require(id.isNotBlank()) { "Receivable ID must not be blank" }
        require(debtorName.isNotBlank()) { "Debtor name must not be blank" }
        require(amount.signum() > 0) { "Receivable amount must be positive" }
    }
}
