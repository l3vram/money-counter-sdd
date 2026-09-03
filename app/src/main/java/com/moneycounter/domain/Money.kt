package com.moneycounter.domain

import java.math.BigDecimal

object Money {
    const val SCALE = 2
    val ZERO = BigDecimal.ZERO.setScale(SCALE)

    fun of(value: String): BigDecimal = BigDecimal(value).setScale(SCALE)
    fun fromLong(value: Long): BigDecimal = BigDecimal.valueOf(value).setScale(SCALE)
    fun fromInt(value: Int): BigDecimal = BigDecimal.valueOf(value.toLong()).setScale(SCALE)
}
