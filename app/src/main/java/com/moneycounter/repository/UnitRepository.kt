package com.moneycounter.repository

import com.moneycounter.domain.MeasurementUnit

interface UnitRepository {
    fun load(): List<MeasurementUnit>
    fun save(units: List<MeasurementUnit>)
}