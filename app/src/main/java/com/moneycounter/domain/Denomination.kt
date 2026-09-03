package com.moneycounter.domain

data class Denomination(
    val id: String,
    val value: Long
) {
    init {
        require(id.isNotBlank()) { "Denomination ID must not be blank" }
        require(value > 0) { "Denomination value must be positive" }
    }
}
