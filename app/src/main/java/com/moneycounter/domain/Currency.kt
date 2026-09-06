package com.moneycounter.domain

data class Currency(
    val id: String,
    val code: String,
    val name: String,
    val symbol: String
) {
    init {
        require(id.isNotBlank()) { "Currency ID must not be blank" }
        require(code.isNotBlank()) { "Currency code must not be blank" }
    }
}