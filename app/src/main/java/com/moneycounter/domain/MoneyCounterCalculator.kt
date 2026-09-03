package com.moneycounter.domain

import java.math.BigDecimal
import java.math.RoundingMode

object MoneyCounterCalculator {

    private val ZERO = BigDecimal.ZERO.setScale(Money.SCALE)

    fun calculate(
        targetAmount: BigDecimal?,
        denominations: List<Denomination>,
        quantities: Map<String, Long>
    ): CounterResult {
        var total = ZERO

        for (denomination in denominations) {
            val quantity = quantities[denomination.id] ?: 0L
            if (quantity < 0) continue

            val subtotal = BigDecimal.valueOf(denomination.value * quantity)
                .setScale(Money.SCALE, RoundingMode.HALF_UP)

            total = total.add(subtotal)
        }

        val target = targetAmount

        return when {
            target == null || target == ZERO -> {
                when {
                    total == ZERO -> CounterResult(ZERO, ZERO, ZERO, CounterStatus.EMPTY)
                    else -> CounterResult(total, ZERO, ZERO, CounterStatus.COUNTING)
                }
            }

            total < target -> CounterResult(
                countedTotal = total,
                remaining = target.subtract(total),
                excess = ZERO,
                status = CounterStatus.COUNTING
            )

            total == target -> CounterResult(
                countedTotal = total,
                remaining = ZERO,
                excess = ZERO,
                status = CounterStatus.COMPLETED
            )

            else -> CounterResult(
                countedTotal = total,
                remaining = ZERO,
                excess = total.subtract(target),
                status = CounterStatus.OVER
            )
        }
    }
}
