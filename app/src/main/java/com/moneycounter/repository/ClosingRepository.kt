package com.moneycounter.repository

import com.moneycounter.domain.Closing

interface ClosingRepository {
    suspend fun load(): List<Closing>
    suspend fun saveAll(closings: List<Closing>)
}
