package com.moneycounter.repository

import com.moneycounter.domain.Movement

interface MovementRepository {
    suspend fun load(): List<Movement>
    suspend fun saveAll(movements: List<Movement>)
}
