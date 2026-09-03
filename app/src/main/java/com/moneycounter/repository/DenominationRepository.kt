package com.moneycounter.repository

import com.moneycounter.domain.Denomination

interface DenominationRepository {
    fun load(): List<Denomination>
    fun save(denominations: List<Denomination>)
}
