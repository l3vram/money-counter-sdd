package com.moneycounter.repository

import com.moneycounter.domain.StockItem

interface StockRepository {
    fun load(): List<StockItem>
    fun saveAll(items: List<StockItem>)
}