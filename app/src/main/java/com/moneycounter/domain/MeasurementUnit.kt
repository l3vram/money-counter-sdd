package com.moneycounter.domain

data class MeasurementUnit(
    val id: String,
    val name: String
) {
    init {
        require(id.isNotBlank()) { "Unit ID must not be blank" }
        require(name.isNotBlank()) { "Unit name must not be blank" }
    }
}