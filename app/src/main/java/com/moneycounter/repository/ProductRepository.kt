package com.moneycounter.repository

import com.moneycounter.domain.Product

interface ProductRepository {
    fun load(): List<Product>
    fun save(products: List<Product>)
}