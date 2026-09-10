package com.moneycounter.domain

import java.math.BigDecimal

/**
 * A "recibo de cobro": records cash coming IN when a debtor pays off a
 * Receivable (cuenta por cobrar). Settlement of a receivable creates one
 * Payment and flips that receivable to SETTLED. It must NOT change stock
 * (already deducted at the credit sale) and must NOT re-create the debt.
 */
data class Payment(
    val id: String,
    val at: Long,
    val receivableId: String,
    val debtorName: String,      // snapshot for reporting
    val amount: BigDecimal,      // = receivable.amount, setScale(Money.SCALE)
    val currencyId: String,
    val sellerUid: String = "",
    val sellerName: String = ""
) {
    init {
        require(id.isNotBlank()) { "Payment ID must not be blank" }
        require(receivableId.isNotBlank()) { "Payment receivableId must not be blank" }
        require(amount.signum() > 0) { "Payment amount must be positive" }
    }
}
