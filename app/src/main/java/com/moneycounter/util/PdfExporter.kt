package com.moneycounter.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.moneycounter.domain.Money
import com.moneycounter.domain.Product
import com.moneycounter.domain.SavedCount
import com.moneycounter.domain.UnitedCount
import com.moneycounter.ui.components.formatMoney
import com.moneycounter.ui.components.formatMoneyBigDecimal
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfExporter(private val context: Context) {

    private val titlePaint = Paint().apply {
        color = Color.parseColor("#1B6B3A")
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }
    private val headerPaint = Paint().apply {
        color = Color.parseColor("#333333")
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }
    private val bodyPaint = Paint().apply {
        color = Color.parseColor("#333333")
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = Color.parseColor("#666666")
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
    }
    private val linePaint = Paint().apply {
        color = Color.parseColor("#CCCCCC")
        strokeWidth = 1f
    }

    fun export(saved: SavedCount) {
        val file = buildPdf(saved)
        share(file)
    }

    fun exportStockReport(products: List<Product>, currency: String, generatedAt: Long) {
        val file = buildStockPdf(products, currency, generatedAt)
        share(file)
    }

    fun exportUnited(count: UnitedCount, currencySymbol: String) {
        val file = buildUnitedPdf(count, currencySymbol)
        share(file)
    }

    private fun buildPdf(saved: SavedCount): File {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 48f
        var y = 80f
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 0).create()
        )
        var canvas: Canvas = page.canvas

        fun newPageIfNeeded(needed: Float) {
            if (y + needed > pageHeight - margin) {
                document.finishPage(page)
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size).create()
                )
                canvas = page.canvas
                y = 80f
            }
        }

        // Title
        canvas.drawText("Reporte de conteo", margin, y, titlePaint)
        y += 30f

        // Date
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(saved.savedAt))
        canvas.drawText("Fecha: $dateStr", margin, y, headerPaint)
        y += 24f

        // Total
        canvas.drawText(
            "Moneda: ${saved.currency}",
            margin,
            y,
            headerPaint
        )
        y += 20f
        canvas.drawText(
            "Monto total: ${formatMoneyBigDecimal(saved.targetAmount, saved.currency)}",
            margin,
            y,
            headerPaint
        )
        y += 36f

        // Products section (if any)
        if (saved.products.isNotEmpty()) {
            canvas.drawText("PRODUCTOS", margin, y, headerPaint)
            y += 16f
            canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
            y += 20f
            canvas.drawText("PRODUCTO", margin, y, labelPaint)
            canvas.drawText("CANT.", 240f, y, labelPaint)
            canvas.drawText("TOTAL", 380f, y, labelPaint)
            y += 16f
            canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
            y += 20f
            for (product in saved.products) {
                newPageIfNeeded(20f)
                canvas.drawText(product.name, margin, y, bodyPaint)
                canvas.drawText("${product.quantity.stripTrailingZeros().toPlainString()} ${product.unit}", 240f, y, bodyPaint)
                canvas.drawText(formatMoneyBigDecimal(product.subtotal, saved.currency), 380f, y, bodyPaint)
                y += 22f
            }
            y += 16f
        }

        // Denomination header
        canvas.drawText("DENOMINACIONES", margin, y, headerPaint)
        y += 16f
        canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
        y += 20f
        canvas.drawText("DENOMINACIÓN", margin, y, labelPaint)
        canvas.drawText("CANTIDAD", 240f, y, labelPaint)
        canvas.drawText("TOTAL", 380f, y, labelPaint)
        y += 16f
        canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
        y += 20f

        if (saved.items.isEmpty()) {
            canvas.drawText("No hay denominaciones registradas.", margin, y, bodyPaint)
        } else {
            for (item in saved.items) {
                newPageIfNeeded(20f)
                val denom = formatMoney(item.denominationValue, saved.currency)
                val subtotal = formatMoneyBigDecimal(item.subtotal, saved.currency)
                canvas.drawText(denom, margin, y, bodyPaint)
                canvas.drawText(item.quantity.toString(), 240f, y, bodyPaint)
                canvas.drawText(subtotal, 380f, y, bodyPaint)
                y += 22f
            }
        }

        document.finishPage(page)

        val stamp = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault())
            .format(Date(saved.savedAt))
        val file = File(context.cacheDir, "reporte_$stamp.pdf")
        try {
            FileOutputStream(file).use { fos ->
                document.writeTo(fos)
            }
        } finally {
            document.close()
        }
        return file
    }

    private fun buildUnitedPdf(count: UnitedCount, currencySymbol: String): File {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 48f
        var y = 80f
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 0).create()
        )
        var canvas: Canvas = page.canvas

        fun newPageIfNeeded(needed: Float) {
            if (y + needed > pageHeight - margin) {
                document.finishPage(page)
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size).create()
                )
                canvas = page.canvas
                y = 80f
            }
        }

        // Title
        canvas.drawText("Reporte de conteo unificado", margin, y, titlePaint)
        y += 30f

        // Date
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        canvas.drawText("Fecha: $dateStr", margin, y, headerPaint)
        y += 24f

        canvas.drawText("Moneda: $currencySymbol (${count.currencyCode})", margin, y, headerPaint)
        y += 20f
        canvas.drawText("Nº de ventas: ${count.count}", margin, y, headerPaint)
        y += 20f
        canvas.drawText(
            "TOTAL GENERAL: ${formatMoneyBigDecimal(count.total(), currencySymbol)}",
            margin,
            y,
            headerPaint
        )
        y += 36f

        // Products section (if any)
        if (count.products.isNotEmpty()) {
            canvas.drawText("PRODUCTOS", margin, y, headerPaint)
            y += 16f
            canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
            y += 20f
            canvas.drawText("PRODUCTO", margin, y, labelPaint)
            canvas.drawText("CANT.", 240f, y, labelPaint)
            canvas.drawText("TOTAL", 380f, y, labelPaint)
            y += 16f
            canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
            y += 20f
            for (product in count.products) {
                newPageIfNeeded(20f)
                canvas.drawText(product.name, margin, y, bodyPaint)
                canvas.drawText("${product.quantity.stripTrailingZeros().toPlainString()} ${product.unit}", 240f, y, bodyPaint)
                canvas.drawText(formatMoneyBigDecimal(product.subtotal, currencySymbol), 380f, y, bodyPaint)
                y += 22f
            }
            y += 16f
        }

        // Denomination header
        canvas.drawText("DENOMINACIONES", margin, y, headerPaint)
        y += 16f
        canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
        y += 20f
        canvas.drawText("DENOMINACIÓN", margin, y, labelPaint)
        canvas.drawText("CANTIDAD", 240f, y, labelPaint)
        canvas.drawText("TOTAL", 380f, y, labelPaint)
        y += 16f
        canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
        y += 20f

        if (count.items.isEmpty()) {
            canvas.drawText("No hay denominaciones registradas.", margin, y, bodyPaint)
        } else {
            for (item in count.items) {
                newPageIfNeeded(20f)
                val denom = formatMoney(item.denominationValue, currencySymbol)
                val subtotal = formatMoneyBigDecimal(item.subtotal, currencySymbol)
                canvas.drawText(denom, margin, y, bodyPaint)
                canvas.drawText(item.quantity.toString(), 240f, y, bodyPaint)
                canvas.drawText(subtotal, 380f, y, bodyPaint)
                y += 22f
            }
        }

        document.finishPage(page)

        val stamp = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        val file = File(context.cacheDir, "resumen_$stamp.pdf")
        try {
            FileOutputStream(file).use { fos ->
                document.writeTo(fos)
            }
        } finally {
            document.close()
        }
        return file
    }

    private fun buildStockPdf(products: List<Product>, currency: String, generatedAt: Long): File {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 48f
        var y = 80f
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 0).create()
        )
        var canvas: Canvas = page.canvas

        fun newPageIfNeeded(needed: Float) {
            if (y + needed > pageHeight - margin) {
                document.finishPage(page)
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size).create()
                )
                canvas = page.canvas
                y = 80f
            }
        }

        // Title
        canvas.drawText("Reporte de existencias", margin, y, titlePaint)
        y += 30f

        // Date
        val dateMs = if (generatedAt > 0) generatedAt else System.currentTimeMillis()
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(dateMs))
        canvas.drawText("Fecha: $dateStr", margin, y, headerPaint)
        y += 24f

        canvas.drawText("Moneda: $currency", margin, y, headerPaint)
        y += 36f

        // Column headers
        canvas.drawText("PRODUCTO", margin, y, labelPaint)
        canvas.drawText("CANT.", 240f, y, labelPaint)
        canvas.drawText("V.UNIT", 360f, y, labelPaint)
        canvas.drawText("TOTAL", 450f, y, labelPaint)
        y += 16f
        canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
        y += 20f

        val inStock = products.filter { it.stock.signum() != 0 }
        if (inStock.isEmpty()) {
            canvas.drawText("No hay existencias.", margin, y, bodyPaint)
            y += 22f
        } else {
            for (product in inStock) {
                newPageIfNeeded(20f)
                canvas.drawText(product.name, margin, y, bodyPaint)
                canvas.drawText("${product.stock.stripTrailingZeros().toPlainString()} ${product.unit}", 240f, y, bodyPaint)
                canvas.drawText(formatMoneyBigDecimal(product.effectiveUnitPrice, currency), 360f, y, bodyPaint)
                canvas.drawText(formatMoneyBigDecimal(product.stockValue, currency), 450f, y, bodyPaint)
                y += 22f
            }
            y += 16f
            newPageIfNeeded(20f)
            val total = inStock.fold(Money.ZERO) { acc, product -> acc.add(product.stockValue) }
            canvas.drawText("TOTAL EN EXISTENCIA", margin, y, headerPaint)
            canvas.drawText(formatMoneyBigDecimal(total, currency), 450f, y, headerPaint)
        }

        document.finishPage(page)

        val stamp = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault())
            .format(Date(dateMs))
        val file = File(context.cacheDir, "existencias_$stamp.pdf")
        try {
            FileOutputStream(file).use { fos ->
                document.writeTo(fos)
            }
        } finally {
            document.close()
        }
        return file
    }

    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exportar reporte"))
    }
}
