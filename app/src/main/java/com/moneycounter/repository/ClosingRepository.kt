package com.moneycounter.repository

import com.moneycounter.domain.Closing

interface ClosingRepository {
    fun load(): List<Closing>
    fun saveAll(closings: List<Closing>)
}
