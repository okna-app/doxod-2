package kg.depter.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import kg.depter.app.R
import kg.depter.app.data.AppSettings
import kg.depter.app.data.EntryKind
import kg.depter.app.data.LedgerLine
import kg.depter.app.data.PartyRow
import kg.depter.app.data.PartyType
import kg.depter.app.data.ReportEntry
import kg.depter.app.domain.Currencies
import kg.depter.app.domain.Money
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A4 PDF statements and reports drawn with the platform PdfDocument (no extra libraries). */
object PdfExport {
    private const val W = 595
    private const val H = 842
    private const val M = 36f
    private const val ROW_H = 20f

    private val BRAND = Color.rgb(0x33, 0x46, 0xD3)
    private val GAVE = Color.rgb(0xD1, 0x2F, 0x45)
    private val GOT = Color.rgb(0x12, 0x87, 0x5A)
    private val MUTED = Color.rgb(0x6B, 0x70, 0x80)
    private val LINE = Color.rgb(0xE3, 0xE5, 0xEC)
    private val HEAD_BG = Color.rgb(0xF2, 0xF3, 0xF8)

    private class Column(val title: String, val width: Float, val right: Boolean)

    private class Writer(private val context: Context, private val footer: String) {
        val doc = PdfDocument()
        private var page: PdfDocument.Page? = null
        lateinit var canvas: Canvas
        var y = M
        private var pageNo = 0

        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(0x15, 0x17, 0x1F) }
        val h2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(0x15, 0x17, 0x1F) }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.rgb(0x22, 0x24, 0x2C) }
        val bold = TextPaint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = MUTED }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            page = p
            canvas = p.canvas
            y = M
            fill.color = BRAND
            canvas.drawRect(0f, 0f, W.toFloat(), 6f, fill)
            canvas.drawText(footer, M, H - 18f, small)
            val pn = context.getString(R.string.pdf_page, pageNo)
            canvas.drawText(pn, W - M - small.measureText(pn), H - 18f, small)
        }

        fun ensure(space: Float): Boolean {
            if (y + space > H - M - 16f) {
                newPage()
                return true
            }
            return false
        }

        fun text(s: String, x: Float, paint: TextPaint, width: Float, right: Boolean = false, baseline: Float = y) {
            val t = TextUtils.ellipsize(s, paint, width, TextUtils.TruncateAt.END).toString()
            val dx = if (right) width - paint.measureText(t) else 0f
            canvas.drawText(t, x + dx, baseline, paint)
        }

        fun tableHeader(cols: List<Column>) {
            fill.color = HEAD_BG
            canvas.drawRoundRect(RectF(M, y, W - M, y + ROW_H), 4f, 4f, fill)
            var x = M + 6f
            cols.forEach { c ->
                text(c.title, x, bold, c.width - 8f, c.right, y + 13.5f)
                x += c.width
            }
            y += ROW_H + 2f
        }

        fun tableRow(cols: List<Column>, values: List<String>, colors: List<Int?>) {
            var x = M + 6f
            cols.forEachIndexed { i, c ->
                val paint = colors[i]?.let { col -> TextPaint(body).apply { color = col } } ?: body
                text(values[i], x, paint, c.width - 8f, c.right, y + 13f)
                x += c.width
            }
            fill.color = LINE
            canvas.drawRect(M, y + ROW_H - 0.5f, W - M, y + ROW_H, fill)
            y += ROW_H
        }

        fun summaryBoxes(items: List<Triple<String, String, Int>>) {
            val gap = 10f
            val w = (W - 2 * M - gap * (items.size - 1)) / items.size
            var x = M
            items.forEach { (label, value, color) ->
                fill.color = HEAD_BG
                canvas.drawRoundRect(RectF(x, y, x + w, y + 52f), 8f, 8f, fill)
                text(label, x + 10f, small, w - 20f, baseline = y + 18f)
                val vp = TextPaint(h2).apply { this.color = color; textSize = 14f }
                text(value, x + 10f, vp, w - 20f, baseline = y + 40f)
                x += w + gap
            }
            y += 64f
        }

        fun finish(file: File): File {
            page?.let { doc.finishPage(it) }
            file.parentFile?.mkdirs()
            file.outputStream().use { doc.writeTo(it) }
            doc.close()
            return file
        }
    }

    private fun english(context: Context) = context.resources.configuration.locales[0].language == "en"

    private fun outFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 86_400_000L) it.delete() }
        val safe = name.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").take(60)
        return File(dir, "$safe.pdf")
    }

    fun statement(context: Context, s: AppSettings, row: PartyRow, linesOldestFirst: List<LedgerLine>): File {
        val cur = Currencies.byCode(s.currency)
        val en = english(context)
        fun m(v: Long) = Money.format(v, cur, en)
        val locale = context.resources.configuration.locales[0]
        val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", locale)
        val zone = ZoneId.systemDefault()
        val shop = s.businessName.ifBlank { context.getString(R.string.app_name) }

        val w = Writer(context, context.getString(R.string.pdf_footer))
        w.newPage()
        w.y += 22f
        w.canvas.drawText(shop, M, w.y, w.title)
        if (s.ownerPhone.isNotBlank()) {
            w.y += 14f
            w.canvas.drawText(s.ownerPhone, M, w.y, w.small)
        }
        w.y += 24f
        w.canvas.drawText(context.getString(R.string.pdf_statement_title), M, w.y, w.h2)
        w.y += 16f
        w.canvas.drawText(row.party.name + if (row.party.phone.isNotBlank()) " · " + row.party.phone else "", M, w.y, w.body)
        w.y += 14f
        w.canvas.drawText(context.getString(R.string.pdf_generated, dateFmt.format(LocalDate.now())), M, w.y, w.small)
        w.y += 16f

        val gave = linesOldestFirst.filter { it.entry.kind == EntryKind.GAVE }.sumOf { it.entry.amount }
        val got = linesOldestFirst.filter { it.entry.kind == EntryKind.GOT }.sumOf { it.entry.amount }
        val balanceLabel = context.getString(
            when {
                row.balance > 0 -> R.string.to_receive
                row.balance < 0 -> R.string.to_pay
                else -> R.string.settled
            },
        )
        w.summaryBoxes(
            listOf(
                Triple(context.getString(R.string.pdf_total_gave), m(gave), GAVE),
                Triple(context.getString(R.string.pdf_total_got), m(got), GOT),
                Triple(balanceLabel, m(kotlin.math.abs(row.balance)), if (row.balance >= 0) GOT else GAVE),
            ),
        )

        val cols = listOf(
            Column(context.getString(R.string.pdf_col_date), 70f, false),
            Column(context.getString(R.string.pdf_col_note), 189f, false),
            Column(context.getString(R.string.gave), 88f, true),
            Column(context.getString(R.string.got), 88f, true),
            Column(context.getString(R.string.pdf_col_balance), 88f, true),
        )
        w.tableHeader(cols)
        linesOldestFirst.forEach { line ->
            if (w.ensure(ROW_H)) w.tableHeader(cols)
            val e = line.entry
            val day = Instant.ofEpochMilli(e.date).atZone(zone).toLocalDate()
            val note = e.note.ifBlank {
                context.getString(if (e.kind == EntryKind.GAVE) R.string.gave_default_note else R.string.got_default_note)
            }
            w.tableRow(
                cols,
                listOf(
                    dateFmt.format(day),
                    note,
                    if (e.kind == EntryKind.GAVE) m(e.amount) else "",
                    if (e.kind == EntryKind.GOT) m(e.amount) else "",
                    Money.format(line.balanceAfter, cur, en, signed = false),
                ),
                listOf(null, null, GAVE, GOT, null),
            )
        }
        if (linesOldestFirst.isEmpty()) {
            w.y += 14f
            w.canvas.drawText(context.getString(R.string.ledger_empty_title), M + 6f, w.y, w.small)
        }
        return w.finish(outFile(context, "Depter_" + row.party.name + "_" + LocalDate.now()))
    }

    fun report(
        context: Context,
        s: AppSettings,
        periodTitle: String,
        entries: List<ReportEntry>,
        parties: List<PartyRow>,
    ): File {
        val cur = Currencies.byCode(s.currency)
        val en = english(context)
        fun m(v: Long) = Money.format(v, cur, en)
        val locale = context.resources.configuration.locales[0]
        val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", locale)
        val zone = ZoneId.systemDefault()
        val shop = s.businessName.ifBlank { context.getString(R.string.app_name) }

        val w = Writer(context, context.getString(R.string.pdf_footer))
        w.newPage()
        w.y += 22f
        w.canvas.drawText(shop, M, w.y, w.title)
        w.y += 24f
        w.canvas.drawText(context.getString(R.string.pdf_report_title) + " · " + periodTitle, M, w.y, w.h2)
        w.y += 14f
        w.canvas.drawText(context.getString(R.string.pdf_generated, dateFmt.format(LocalDate.now())), M, w.y, w.small)
        w.y += 16f

        val customerEntries = entries.filter { it.partyType == PartyType.CUSTOMER }
        val gave = customerEntries.filter { it.entry.kind == EntryKind.GAVE }.sumOf { it.entry.amount }
        val got = customerEntries.filter { it.entry.kind == EntryKind.GOT }.sumOf { it.entry.amount }
        val receivable = parties.filter { it.balance > 0 }.sumOf { it.balance }
        val payable = -parties.filter { it.balance < 0 }.sumOf { it.balance }
        w.summaryBoxes(
            listOf(
                Triple(context.getString(R.string.report_gave), m(gave), GAVE),
                Triple(context.getString(R.string.report_got), m(got), GOT),
            ),
        )
        w.summaryBoxes(
            listOf(
                Triple(context.getString(R.string.total_to_receive), m(receivable), GOT),
                Triple(context.getString(R.string.total_to_pay), m(payable), GAVE),
            ),
        )

        val debtors = parties.filter { it.balance != 0L }.sortedByDescending { kotlin.math.abs(it.balance) }
        if (debtors.isNotEmpty()) {
            w.ensure(40f)
            w.canvas.drawText(context.getString(R.string.pdf_outstanding), M, w.y + 10f, w.h2)
            w.y += 20f
            val cols = listOf(
                Column(context.getString(R.string.field_name), 220f, false),
                Column(context.getString(R.string.field_phone), 130f, false),
                Column(context.getString(R.string.pdf_col_balance), 173f, true),
            )
            w.tableHeader(cols)
            debtors.forEach { r ->
                if (w.ensure(ROW_H)) w.tableHeader(cols)
                w.tableRow(
                    cols,
                    listOf(r.party.name, r.party.phone, m(kotlin.math.abs(r.balance)) + " · " + context.getString(if (r.balance > 0) R.string.to_receive else R.string.to_pay)),
                    listOf(null, null, if (r.balance > 0) GOT else GAVE),
                )
            }
            w.y += 16f
        }

        if (entries.isNotEmpty()) {
            w.ensure(40f)
            w.canvas.drawText(context.getString(R.string.pdf_entries), M, w.y + 10f, w.h2)
            w.y += 20f
            val cols = listOf(
                Column(context.getString(R.string.pdf_col_date), 66f, false),
                Column(context.getString(R.string.field_name), 130f, false),
                Column(context.getString(R.string.pdf_col_note), 151f, false),
                Column(context.getString(R.string.gave), 88f, true),
                Column(context.getString(R.string.got), 88f, true),
            )
            w.tableHeader(cols)
            entries.asReversed().forEach { r ->
                if (w.ensure(ROW_H)) w.tableHeader(cols)
                val e = r.entry
                w.tableRow(
                    cols,
                    listOf(
                        dateFmt.format(Instant.ofEpochMilli(e.date).atZone(zone).toLocalDate()),
                        r.partyName,
                        e.note,
                        if (e.kind == EntryKind.GAVE) m(e.amount) else "",
                        if (e.kind == EntryKind.GOT) m(e.amount) else "",
                    ),
                    listOf(null, null, null, GAVE, GOT),
                )
            }
        }
        return w.finish(outFile(context, "Depter_report_" + LocalDate.now()))
    }

    /** CSV that opens in Excel/Google Sheets (UTF-8 with BOM, semicolon for CIS Excel). */
    fun csv(context: Context, s: AppSettings, entries: List<ReportEntry>): String {
        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val sb = StringBuilder("﻿")
        sb.append(
            listOf(
                context.getString(R.string.pdf_col_date),
                context.getString(R.string.field_name),
                context.getString(R.string.pdf_col_note),
                context.getString(R.string.gave),
                context.getString(R.string.got),
                s.currency,
            ).joinToString(";"),
        ).append("\r\n")
        entries.forEach { r ->
            val e = r.entry
            val amount = Money.toEditable(e.amount).replace('.', ',')
            sb.append(
                listOf(
                    fmt.format(Instant.ofEpochMilli(e.date).atZone(zone)),
                    r.partyName,
                    e.note,
                    if (e.kind == EntryKind.GAVE) amount else "",
                    if (e.kind == EntryKind.GOT) amount else "",
                    "",
                ).joinToString(";") { cell -> "\"" + cell.replace("\"", "\"\"") + "\"" },
            ).append("\r\n")
        }
        return sb.toString()
    }
}
