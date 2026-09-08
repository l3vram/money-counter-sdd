package com.moneycounter.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.moneycounter.config.ContactConfig
import java.net.URLEncoder

fun buildWhatsAppUrl(
    number: String = ContactConfig.WHATSAPP_NUMBER,
    message: String = ContactConfig.ACCESS_REQUEST_MESSAGE
): String = "https://wa.me/${number}?text=${URLEncoder.encode(message, "UTF-8")}"

fun buildWhatsAppUri(
    number: String = ContactConfig.WHATSAPP_NUMBER,
    message: String = ContactConfig.ACCESS_REQUEST_MESSAGE
): Uri = Uri.parse(buildWhatsAppUrl(number, message))

fun openWhatsApp(
    context: Context,
    number: String = ContactConfig.WHATSAPP_NUMBER,
    message: String = ContactConfig.ACCESS_REQUEST_MESSAGE
) {
    val uri = buildWhatsAppUri(number, message)
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "Instala WhatsApp o abre el enlace en tu navegador para solicitar acceso.",
            Toast.LENGTH_LONG
        ).show()
    }
}