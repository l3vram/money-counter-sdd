package com.moneycounter.repository

import com.moneycounter.domain.StockItem

interface StockRepository {
    suspend fun load(): List<StockItem>
    suspend fun saveAll(items: List<StockItem>)
}