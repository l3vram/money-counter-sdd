package com.moneycounter.repository

import com.moneycounter.domain.Movement

interface MovementRepository {
    fun load(): List<Movement>
    fun saveAll(movements: List<Movement>)
}
