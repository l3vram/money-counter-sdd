package com.moneycounter.domain

import java.math.BigDecimal

data class CounterResult(
    val countedTotal: BigDecimal,
    val remaining: BigDecimal,
    val excess: BigDecimal,
    val status: CounterStatus
)
