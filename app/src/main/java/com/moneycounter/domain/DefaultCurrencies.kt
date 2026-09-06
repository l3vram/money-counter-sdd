package com.moneycounter.domain

object DefaultCurrencies {

    val CUP: Currency = Currency("cup", "CUP", "Peso Cubano", "$")
    val USD: Currency = Currency("usd", "USD", "Dólar Americano", "US$")

    fun get(): List<Currency> = listOf(CUP, USD)
}