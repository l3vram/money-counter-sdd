package com.moneycounter.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.moneycounter.domain.Closing
import com.moneycounter.domain.Money
import com.moneycounter.domain.MovementType
import com.moneycounter.domain.Product
import com.moneycounter.domain.SavedCount
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

    fun exportStockReport(products: List<Product>, currencyId: String, currencySymbol: String, currencyCode: String, generatedAt: Long) {
        val file = buildStockPdf(products, currencyId, currencySymbol, currencyCode, generatedAt)
        share(file)
    }

    fun exportClosing(closing: Closing, currencySymbol: String) {
        val file = buildClosingPdf(closing, currencySymbol)
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

    private fun buildStockPdf(products: List<Product>, currencyId: String, currencySymbol: String, currencyCode: String, generatedAt: Long): File {
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

        canvas.drawText("Moneda: $currencySymbol ($currencyCode)", margin, y, headerPaint)
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
                canvas.drawText(formatMoneyBigDecimal(product.effectiveUnitPriceFor(currencyId) ?: Money.ZERO, currencySymbol), 360f, y, bodyPaint)
                canvas.drawText(formatMoneyBigDecimal(product.stockValueFor(currencyId) ?: Money.ZERO, currencySymbol), 450f, y, bodyPaint)
                y += 22f
            }
            y += 16f
            newPageIfNeeded(20f)
            val total = inStock.fold(Money.ZERO) { acc, product ->
                acc.add(product.stockValueFor(currencyId) ?: Money.ZERO)
            }
            canvas.drawText("TOTAL EN EXISTENCIA", margin, y, headerPaint)
            canvas.drawText(formatMoneyBigDecimal(total, currencySymbol), 450f, y, headerPaint)
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

    private fun buildClosingPdf(closing: Closing, currencySymbol: String): File {
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
        canvas.drawText("Cierre", margin, y, titlePaint)
        y += 30f

        // Date
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(closing.at))
        canvas.drawText("Fecha: $dateStr", margin, y, headerPaint)
        y += 24f

        canvas.drawText("Moneda: $currencySymbol", margin, y, headerPaint)
        y += 20f
        canvas.drawText("Movimientos incluidos: ${closing.movementIds.size}", margin, y, headerPaint)
        y += 20f
        canvas.drawText(
            "NETO EN CAJA: ${formatMoneyBigDecimal(closing.netCash, currencySymbol)}",
            margin,
            y,
            headerPaint
        )
        y += 36f

        // Totals by type
        canvas.drawText("TOTALES POR TIPO", margin, y, headerPaint)
        y += 16f
        canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
        y += 20f
        for (type in MovementType.entries) {
            newPageIfNeeded(20f)
            val amount = closing.totalsByType[type] ?: Money.ZERO
            canvas.drawText(type.name, margin, y, bodyPaint)
            canvas.drawText(formatMoneyBigDecimal(amount, currencySymbol), 380f, y, bodyPaint)
            y += 22f
        }
        y += 16f

        // Stock snapshot
        newPageIfNeeded(60f)
        canvas.drawText("EXISTENCIAS AL CIERRE", margin, y, headerPaint)
        y += 16f
        canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
        y += 20f
        canvas.drawText("PRODUCTO", margin, y, labelPaint)
        canvas.drawText("CANTIDAD", 380f, y, labelPaint)
        y += 16f
        canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
        y += 20f

        if (closing.stockSnapshot.isEmpty()) {
            canvas.drawText("No hay existencias.", margin, y, bodyPaint)
        } else {
            for (line in closing.stockSnapshot) {
                newPageIfNeeded(20f)
                canvas.drawText(line.name, margin, y, bodyPaint)
                canvas.drawText("${line.quantity.stripTrailingZeros().toPlainString()} ${line.unit}", 380f, y, bodyPaint)
                y += 22f
            }
        }

        document.finishPage(page)

        val stamp = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date(closing.at))
        val file = File(context.cacheDir, "cierre_$stamp.pdf")
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
