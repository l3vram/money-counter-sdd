package com.moneycounter.repository

import com.moneycounter.domain.SavedCount

interface SavedCountRepository {
    fun load(): List<SavedCount>
    fun saveAll(history: List<SavedCount>)
}
