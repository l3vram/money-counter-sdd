package com.moneycounter.repository

import com.moneycounter.domain.Currency

data class CurrencySettings(
    val currencies: List<Currency>,
    val selectedCurrencyId: String
)

interface CurrencyRepository {
    fun load(): CurrencySettings
    fun save(settings: CurrencySettings)
}