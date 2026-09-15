package com.moneycounter.repository

import com.moneycounter.domain.Product

interface ProductRepository {
    suspend fun load(): List<Product>
    suspend fun save(products: List<Product>)
}