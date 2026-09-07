package com.moneycounter.domain

import com.moneycounter.repository.ProductJson
import com.moneycounter.repository.SavedCountJson
import com.moneycounter.viewmodel.MoneyCounterViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ProductJsonCurrencyTest {

    private fun product(id: String, name: String, currencyId: String) = Product(
        id = id,
        name = name,
        unit = "Lb",
        unitPrice = BigDecimal("25.00"),
        surcharge = BigDecimal("2.00"),
        stock = BigDecimal("40.00"),
        currencyId = currencyId
    )

    @Test
    fun `ProductJson v3 round trip preserves currencyId`() {
        val products = listOf(
            product("p1", "Arroz", DefaultCurrencies.CUP.id),
            product("p2", "Arroz importado", DefaultCurrencies.USD.id)
        )

        val loaded = ProductJson.fromJson(ProductJson.toJson(products))

        assertEquals(2, loaded.size)
        assertEquals(DefaultCurrencies.CUP.id, loaded[0].currencyId)
        assertEquals(DefaultCurrencies.USD.id, loaded[1].currencyId)
    }

    @Test
    fun `ProductJson v2 migration defaults currencyId to CUP`() {
        val json = """
            {
              "version": 2,
              "products": [
                {"id": "p1", "name": "Arroz", "unit": "Lb", "unitPrice": "25.00", "surcharge": "2.00", "stock": "40.00"}
              ]
            }
        """.trimIndent()

        val loaded = ProductJson.fromJson(json)

        assertEquals(1, loaded.size)
        assertEquals(DefaultCurrencies.CUP.id, loaded[0].currencyId)
        assertEquals("p1", loaded[0].id)
        assertEquals("Arroz", loaded[0].name)
        assertEquals("Lb", loaded[0].unit)
        assertEquals(BigDecimal("25.00"), loaded[0].unitPrice)
        assertEquals(BigDecimal("2.00"), loaded[0].surcharge)
        assertEquals(BigDecimal("40.00"), loaded[0].stock)
    }

    @Test
    fun `SavedCountJson v3 round trip preserves currencyId and symbol`() {
        val saved = SavedCount(
            id = "abc-123",
            savedAt = 1750000000000L,
            targetAmount = BigDecimal("5000.00"),
            items = listOf(SavedCountItem(500, 1, BigDecimal("500.00"))),
            currency = "US$",
            products = emptyList(),
            currencyId = DefaultCurrencies.USD.id
        )

        val loaded = SavedCountJson.fromJson(SavedCountJson.toJson(listOf(saved)))

        assertEquals(1, loaded.size)
        assertEquals(DefaultCurrencies.USD.id, loaded[0].currencyId)
        assertEquals("US$", loaded[0].currency)
    }

    @Test
    fun `SavedCountJson v2 migration maps symbol to currencyId`() {
        val cupJson = """
            {
              "version": 2,
              "history": [
                {"id": "a", "savedAt": 1, "targetAmount": "100.00", "currency": "$", "items": []}
              ]
            }
        """.trimIndent()
        val usdJson = """
            {
              "version": 2,
              "history": [
                {"id": "b", "savedAt": 2, "targetAmount": "200.00", "currency": "US$", "items": []}
              ]
            }
        """.trimIndent()

        val cupLoaded = SavedCountJson.fromJson(cupJson)
        val usdLoaded = SavedCountJson.fromJson(usdJson)

        assertEquals(1, cupLoaded.size)
        assertEquals(DefaultCurrencies.CUP.id, cupLoaded[0].currencyId)
        assertEquals(1, usdLoaded.size)
        assertEquals(DefaultCurrencies.USD.id, usdLoaded[0].currencyId)
    }

    @Test
    fun `SavedCountJson v2 unknown symbol falls back to CUP`() {
        val json = """
            {
              "version": 2,
              "history": [
                {"id": "c", "savedAt": 1, "targetAmount": "100.00", "currency": "€", "items": []}
              ]
            }
        """.trimIndent()

        val loaded = SavedCountJson.fromJson(json)

        assertEquals(1, loaded.size)
        assertEquals(DefaultCurrencies.CUP.id, loaded[0].currencyId)
    }

    @Test
    fun `productsForCurrency filters to the given currency`() {
        val products = listOf(
            product("p1", "CUP product", DefaultCurrencies.CUP.id),
            product("p2", "USD product", DefaultCurrencies.USD.id),
            product("p3", "Another CUP product", DefaultCurrencies.CUP.id)
        )

        val cupOnly = MoneyCounterViewModel.productsForCurrency(products, DefaultCurrencies.CUP.id)

        assertEquals(2, cupOnly.size)
        assertEquals(listOf("p1", "p3"), cupOnly.map { it.id })

        val usdOnly = MoneyCounterViewModel.productsForCurrency(products, DefaultCurrencies.USD.id)
        assertEquals(listOf("p2"), usdOnly.map { it.id })
    }
}