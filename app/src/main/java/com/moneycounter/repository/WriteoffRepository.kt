package com.moneycounter.repository

import com.moneycounter.domain.InventoryWriteoff

interface WriteoffRepository {
    fun load(): List<InventoryWriteoff>
    fun saveAll(writeoffs: List<InventoryWriteoff>)
}
