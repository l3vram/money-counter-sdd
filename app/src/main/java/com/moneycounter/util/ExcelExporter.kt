package com.moneycounter.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.moneycounter.domain.SavedCount
import com.moneycounter.ui.components.formatMoney
import com.moneycounter.ui.components.formatMoneyBigDecimal
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExcelExporter(private val context: Context) {

    fun export(saved: SavedCount) {
        val file = buildCsv(saved)
        share(file)
    }

    private fun buildCsv(saved: SavedCount): File {
        val lines = mutableListOf<String>()

        lines += csvRow("Reporte de conteo")
        lines += csvRow(
            "Fecha",
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(saved.savedAt))
        )
        lines += csvRow("Moneda", saved.currency)
        lines += csvRow(
            "Monto total",
            formatMoneyBigDecimal(saved.targetAmount, saved.currency)
        )
        lines += ""

        if (saved.products.isNotEmpty()) {
            lines += csvRow("PRODUCTOS")
            lines += csvRow("Producto", "Unidad", "Cantidad", "Precio unitario", "Recargo", "Subtotal")
            for (p in saved.products) {
                lines += csvRow(
                    p.name,
                    p.unit,
                    p.quantity.stripTrailingZeros().toPlainString(),
                    formatMoneyBigDecimal(p.unitPrice, saved.currency),
                    if (p.surcharge.signum() > 0) formatMoneyBigDecimal(p.surcharge, saved.currency) else "",
                    formatMoneyBigDecimal(p.subtotal, saved.currency)
                )
            }
            lines += ""
        }

        lines += csvRow("DENOMINACIONES")
        lines += csvRow("Denominación", "Cantidad", "Total")
        if (saved.items.isEmpty()) {
            lines += csvRow("No hay denominaciones registradas")
        } else {
            for (item in saved.items) {
                lines += csvRow(
                    formatMoney(item.denominationValue, saved.currency),
                    item.quantity.toString(),
                    formatMoneyBigDecimal(item.subtotal, saved.currency)
                )
            }
        }

        val content = lines.joinToString("\r\n")

        val stamp = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault())
            .format(Date(saved.savedAt))
        val file = File(context.cacheDir, "reporte_$stamp.csv")
        FileOutputStream(file).use { fos ->
            // UTF-8 BOM so Excel detects the encoding and keeps accents
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            fos.write(content.toByteArray(Charsets.UTF_8))
        }
        return file
    }

    private fun csvRow(vararg values: String): String {
        return values.joinToString(",") { value -> escapeCsvField(value) }
    }

    private fun escapeCsvField(value: String): String {
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }

    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exportar reporte"))
    }
}