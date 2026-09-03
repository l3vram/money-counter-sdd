package com.moneycounter.domain

object DefaultDenominations {
    fun get(): List<Denomination> = listOf(
        Denomination("d1", 5000),
        Denomination("d2", 2000),
        Denomination("d3", 1000),
        Denomination("d4", 500),
        Denomination("d5", 200),
        Denomination("d6", 100),
        Denomination("d7", 50),
        Denomination("d8", 20)
    )
}
