package com.artem.medtracker

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.TextPaint
import android.text.TextUtils
import java.io.File
import java.util.Locale

object ReportPdfGenerator {
    private const val pageWidth = 595
    private const val pageHeight = 842
    private const val left = 40f
    private const val right = 555f
    private const val top = 42f
    private const val bottom = 800f

    fun generate(context: Context, payload: ReportPayload): File {
        val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "reports").apply {
            mkdirs()
        }
        val safeDate = payload.dateDe.ifBlank { payload.dateRu.ifBlank { "report" } }.replace('.', '-')
        val outFile = File(outDir, "report_${safeDate}.pdf")

        val document = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = 0xFF555555.toInt()
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 1f
            color = 0xFFBDBDBD.toInt()
        }
        val textPaint = TextPaint(bodyPaint)

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = drawHeader(canvas, payload, titlePaint, subtitlePaint, linePaint, pageNumber)
        y = drawTableHeader(canvas, y, headerPaint, linePaint)

        payload.rows.forEachIndexed { index, row ->
            if (y + 26f > bottom - 110f) {
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = drawHeader(canvas, payload, titlePaint, subtitlePaint, linePaint, pageNumber)
                y = drawTableHeader(canvas, y, headerPaint, linePaint)
            }

            val number = "${index + 1}."
            canvas.drawText(number, left, y, bodyPaint)
            canvas.drawText(ellipsize(row.name.ifBlank { "—" }, textPaint, 250f), left + 24f, y, bodyPaint)
            canvas.drawText(row.von.ifBlank { "—" }, 350f, y, bodyPaint)
            canvas.drawText(row.bis.ifBlank { "—" }, 455f, y, bodyPaint)
            canvas.drawLine(left, y + 8f, right, y + 8f, linePaint)
            y += 26f
        }

        if (y + 110f > bottom) {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = drawHeader(canvas, payload, titlePaint, subtitlePaint, linePaint, pageNumber)
        }

        y += 12f
        canvas.drawText("Итоговая смена: ${payload.totalDuration}", left, y, headerPaint)
        y += 18f
        canvas.drawText("Часы (десятичные): ${payload.totalHoursDecimal}", left, y, bodyPaint)
        y += 18f
        canvas.drawText("Строк в отчёте: ${payload.rows.size}", left, y, bodyPaint)

        payload.location?.let { loc ->
            y += 24f
            canvas.drawText("Последняя геолокация:", left, y, headerPaint)
            y += 18f
            canvas.drawText("${String.format(Locale.US, "%.5f", loc.lat)}, ${String.format(Locale.US, "%.5f", loc.lng)}", left, y, bodyPaint)
            y += 18f
            canvas.drawText("Точность: ±${loc.accuracy.toInt()} м • ${loc.updatedAt}", left, y, bodyPaint)
        }

        document.finishPage(page)
        outFile.outputStream().use(document::writeTo)
        document.close()
        return outFile
    }

    private fun drawHeader(
        canvas: android.graphics.Canvas,
        payload: ReportPayload,
        titlePaint: Paint,
        subtitlePaint: Paint,
        linePaint: Paint,
        pageNumber: Int,
    ): Float {
        var y = top
        canvas.drawText(payload.title.ifBlank { "Shift Report" }, left, y, titlePaint)
        y += 22f
        canvas.drawText("Дата: ${payload.dateRu} / ${payload.dateDe}", left, y, subtitlePaint)
        y += 16f
        canvas.drawText("Сформировано: ${payload.generatedAt}", left, y, subtitlePaint)
        canvas.drawText("Страница ${pageNumber}", right - 70f, y, subtitlePaint)
        y += 16f
        canvas.drawLine(left, y, right, y, linePaint)
        return y + 22f
    }

    private fun drawTableHeader(
        canvas: android.graphics.Canvas,
        yStart: Float,
        headerPaint: Paint,
        linePaint: Paint,
    ): Float {
        var y = yStart
        canvas.drawText("Пациент", left + 24f, y, headerPaint)
        canvas.drawText("von", 350f, y, headerPaint)
        canvas.drawText("bis", 455f, y, headerPaint)
        canvas.drawLine(left, y + 8f, right, y + 8f, linePaint)
        return y + 24f
    }

    private fun ellipsize(text: String, paint: TextPaint, width: Float): String {
        return TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()
    }
}
