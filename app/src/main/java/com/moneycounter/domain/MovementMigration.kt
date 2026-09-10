package com.moneycounter.domain

/**
 * Pure, one-shot mapping from the four legacy stores into the unified Movement
 * journal. Used only by JsonMovementRepository the first time movements.json is
 * absent. Never deletes or mutates the legacy data it reads.
 *
 * A SETTLED Receivable does NOT itself emit a COBRO here — payments.json is the
 * source of truth for collections, so each Payment already carries its own COBRO.
 * Emitting one from the receivable too would double-count the collection.
 */
object MovementMigration {

    fun fromLegacy(
        sales: List<SavedCount>,
        receivables: List<Receivable>,
        payments: List<Payment>,
        writeoffs: List<InventoryWriteoff>
    ): List<Movement> {
        val fromSales = sales.map { saved ->
            Movement(
                id = saved.id,
                at = saved.savedAt,
                type = MovementType.VENTA,
                currencyId = saved.currencyId,
                products = saved.products.map { it.toMovementLine() },
                denominations = saved.items.map { it.toMovementDenomination() },
                amount = saved.targetAmount
            )
        }

        val fromReceivables = receivables.map { r ->
            Movement(
                id = r.id,
                at = r.at,
                type = MovementType.VENTA_FIADO,
                currencyId = r.currencyId,
                concept = r.debtorName,
                products = r.products.map { it.toMovementLine() },
                amount = r.amount
            )
        }

        val fromPayments = payments.map { p ->
            Movement(
                id = p.id,
                at = p.at,
                type = MovementType.COBRO,
                currencyId = p.currencyId,
                concept = p.debtorName,
                amount = p.amount,
                linkId = p.receivableId
            )
        }

        val fromWriteoffs = writeoffs.map { w ->
            Movement(
                id = w.id,
                at = w.at,
                type = MovementType.MERMA,
                currencyId = w.currencyId,
                concept = w.reason,
                products = listOf(
                    MovementProductLine(
                        name = w.name,
                        unit = w.unit,
                        quantity = w.quantity,
                        unitPrice = w.unitPrice,
                        subtotal = w.lossValue
                    )
                ),
                amount = w.lossValue
            )
        }

        return (fromSales + fromReceivables + fromPayments + fromWriteoffs)
            .sortedByDescending { it.at }
    }

    private fun SavedProductItem.toMovementLine() =
        MovementProductLine(name = name, unit = unit, quantity = quantity, unitPrice = unitPrice, subtotal = subtotal)

    private fun SavedCountItem.toMovementDenomination() =
        MovementDenomination(value = denominationValue, quantity = quantity, subtotal = subtotal)
}
