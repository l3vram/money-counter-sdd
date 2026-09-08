package com.moneycounter.util

import com.moneycounter.config.ContactConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppLauncherTest {

    @Test
    fun `buildWhatsAppUrl uses wa me and encodes message`() {
        val url = buildWhatsAppUrl(number = "123456789", message = "Quiero solicitar acceso a la aplicación.")
        assertEquals("https://wa.me/123456789", url.substringBefore("?text="))
        val textParam = url.substringAfter("?text=")
        assertEquals("Quiero+solicitar+acceso+a+la+aplicaci%C3%B3n.", textParam)
    }

    @Test
    fun `buildWhatsAppUrl uses default ContactConfig values`() {
        val url = buildWhatsAppUrl()
        assertTrue(url.startsWith("https://wa.me/${ContactConfig.WHATSAPP_NUMBER}?text="))
        assertTrue(url.contains(URL_ENCODED_ACCESS_MESSAGE))
    }

    @Test
    fun `url does not contain raw spaces`() {
        val url = buildWhatsAppUrl()
        assertTrue(!url.contains(' '))
    }

    private companion object {
        const val URL_ENCODED_ACCESS_MESSAGE =
            "Quiero+solicitar+acceso+a+la+aplicaci%C3%B3n."
    }
}