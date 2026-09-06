package com.moneycounter.domain

import com.moneycounter.repository.ProductJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ProductJsonTest {

    @Test
    fun `round trip v2 preserves all fields including stock`() {
        val product = Product(
            id = "p1",
            name = "Arroz",
            unit = "Lb",
            unitPrice = BigDecimal("25.00"),
            surcharge = BigDecimal("2.00"),
            stock = BigDecimal("40.00")
        )

        val json = ProductJson.toJson(listOf(product))
        val loaded = ProductJson.fromJson(json)

        assertEquals(1, loaded.size)
        assertEquals("p1", loaded[0].id)
        assertEquals("Arroz", loaded[0].name)
        assertEquals("Lb", loaded[0].unit)
        assertEquals(BigDecimal("25.00"), loaded[0].unitPrice)
        assertEquals(BigDecimal("2.00"), loaded[0].surcharge)
        assertEquals(BigDecimal("40.00"), loaded[0].stock)
    }

    @Test
    fun `v1 json without stock loads with zero stock`() {
        val json = """
            {
              "version": 1,
              "products": [
                {"id": "p1", "name": "Arroz", "unit": "Lb", "unitPrice": "25.00", "surcharge": "0.00"}
              ]
            }
        """.trimIndent()

        val loaded = ProductJson.fromJson(json)

        assertEquals(1, loaded.size)
        assertEquals(Money.ZERO, loaded[0].stock)
        assertEquals(BigDecimal("25.00"), loaded[0].unitPrice)
        assertEquals("Arroz", loaded[0].name)
    }

    @Test
    fun `unsupported version returns empty list`() {
        val json = """
            {
              "version": 3,
              "products": [
                {"id": "p1", "name": "Arroz", "unit": "Lb", "unitPrice": "25.00", "surcharge": "0.00", "stock": "10.00"}
              ]
            }
        """.trimIndent()

        assertTrue(ProductJson.fromJson(json).isEmpty())
    }

    @Test
    fun `negative stock entry survives round trip`() {
        val product = Product(
            id = "p1",
            name = "Mal",
            unit = "Lb",
            unitPrice = BigDecimal("25.00"),
            surcharge = BigDecimal("0.00"),
            stock = BigDecimal("-3.00")
        )

        val json = ProductJson.toJson(listOf(product))
        val loaded = ProductJson.fromJson(json)

        assertEquals(1, loaded.size)
        assertEquals("p1", loaded[0].id)
        assertEquals(BigDecimal("-3.00"), loaded[0].stock)
    }

    @Test
    fun `blank input returns empty list`() {
        assertTrue(ProductJson.fromJson("").isEmpty())
    }
}