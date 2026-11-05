@file:OptIn(ExperimentalMaterial3Api::class)

package kg.depter.app.ui.reports

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kg.depter.app.R
import kg.depter.app.container
import kg.depter.app.data.PartyRow
import kg.depter.app.data.PeriodReport
import kg.depter.app.pdf.PdfExport
import kg.depter.app.ui.Dates
import kg.depter.app.ui.LocalPro
import kg.depter.app.ui.LocalSettings
import kg.depter.app.ui.ProRequiredDialog
import kg.depter.app.ui.Routes
import kg.depter.app.ui.currentLocale
import kg.depter.app.ui.home.PartyListItem
import kg.depter.app.ui.rememberMoney
import kg.depter.app.ui.theme.Depter
import kg.depter.app.ui.theme.NumberStyle
import kg.depter.app.util.Intents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle

enum class Period(val label: Int) {
    TODAY(R.string.period_today),
    WEEK(R.string.period_week),
    MONTH(R.string.period_month),
    YEAR(R.string.period_year),
    ALL(R.string.period_all),
    ;

    fun range(today: LocalDate): Pair<Long, Long> {
        val end = Dates.startOfDay(today.plusDays(1))
        return when (this) {
            TODAY -> Dates.startOfDay(today) to end
            WEEK -> Dates.startOfDay(today.minusDays(6)) to end
            MONTH -> Dates.startOfDay(today.withDayOfMonth(1)) to end
            YEAR -> Dates.startOfDay(today.withDayOfYear(1)) to end
            ALL -> 0L to Long.MAX_VALUE
        }
    }
}

data class Bucket(val label: String, val gave: Long, val got: Long)

private fun buckets(period: Period, report: PeriodReport, today: LocalDate, locale: java.util.Locale): List<Bucket> {
    val byDay = report.days.associateBy { it.epochDay }
    return when (period) {
        Period.TODAY -> emptyList()
        Period.WEEK -> (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val t = byDay[d.toEpochDay()]
            Bucket(d.dayOfWeek.getDisplayName(JTextStyle.SHORT, locale), t?.gave ?: 0, t?.got ?: 0)
        }
        Period.MONTH -> (1..today.dayOfMonth).map { dom ->
            val d = today.withDayOfMonth(dom)
            val t = byDay[d.toEpochDay()]
            Bucket(dom.toString(), t?.gave ?: 0, t?.got ?: 0)
        }
        Period.YEAR, Period.ALL -> {
            val byMonth = LinkedHashMap<YearMonth, LongArray>()
            report.days.forEach { t ->
                val ym = YearMonth.from(LocalDate.ofEpochDay(t.epochDay))
                val a = byMonth.getOrPut(ym) { LongArray(2) }
                a[0] += t.gave
                a[1] += t.got
            }
            val last = YearMonth.from(today)
            val first = if (period == Period.YEAR) YearMonth.of(today.year, 1) else last.minusMonths(11)
            val fmt = DateTimeFormatter.ofPattern("LLL", locale)
            generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.map { ym ->
                val a = byMonth[ym]
                Bucket(fmt.format(ym), a?.get(0) ?: 0, a?.get(1) ?: 0)
            }.toList()
        }
    }
}

@Composable
fun ReportsTab(padding: PaddingValues, nav: NavController) {
    val context = LocalContext.current
    val repo = context.container.repo
    val settings = LocalSettings.current
    val pro = LocalPro.current
    val money = rememberMoney()
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    var periodId by rememberSaveable { mutableIntStateOf(Period.MONTH.ordinal) }
    var proDialog by remember { mutableStateOf(false) }
    val period = Period.entries[periodId]
    val today = LocalDate.now()
    val range = remember(period, today) { period.range(today) }
    val report by remember(range) { repo.report(range.first, range.second) }.collectAsStateWithLifecycle(initialValue = null)
    val parties by remember { repo.allParties() }.collectAsStateWithLifecycle(initialValue = null)
    val periodName = stringResource(period.label)

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.Main.immediate) {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = PdfExport.csv(context, settings, repo.reportEntries(range.first, range.second))
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) R.string.export_done else R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    val all = parties ?: emptyList()
    val receivable = all.filter { it.balance > 0 }.sumOf { it.balance }
    val payable = -all.filter { it.balance < 0 }.sumOf { it.balance }
    val todayDay = today.toEpochDay()
    val overdue = all.filter { it.balance > 0 && (it.party.dueDay ?: Long.MAX_VALUE) < todayDay }
        .sortedBy { it.party.dueDay }
    val top = all.filter { it.balance > 0 }.sortedByDescending { it.balance }.take(5)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("reports_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Period.entries.toList()) { p ->
                    FilterChip(
                        selected = p == period,
                        onClick = { periodId = p.ordinal },
                        label = { Text(stringResource(p.label)) },
                    )
                }
            }
        }
        item {
            val r = report
            Card {
                Row(Modifier.fillMaxWidth()) {
                    Stat(stringResource(R.string.report_gave), money.format(r?.gave ?: 0), Depter.colors.gave, Modifier.weight(1f))
                    Stat(stringResource(R.string.report_got), money.format(r?.got ?: 0), Depter.colors.got, Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    pluralStringResource(R.plurals.report_entries, r?.count ?: 0, r?.count ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val b = if (r != null) buckets(period, r, today, locale) else emptyList()
                if (b.isNotEmpty() && (r?.count ?: 0) > 0) {
                    Spacer(Modifier.height(14.dp))
                    BarChart(b)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Legend(Depter.colors.gave, stringResource(R.string.gave))
                        Spacer(Modifier.width(16.dp))
                        Legend(Depter.colors.got, stringResource(R.string.got))
                    }
                }
            }
        }
        item {
            Card {
                Text(stringResource(R.string.report_now), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Stat(stringResource(R.string.total_to_receive), money.format(receivable), Depter.colors.got, Modifier.weight(1f))
                    Stat(stringResource(R.string.total_to_pay), money.format(payable), Depter.colors.gave, Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    pluralStringResource(R.plurals.report_debtors, all.count { it.balance > 0 }, all.count { it.balance > 0 }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        if (!pro.isPro) {
                            proDialog = true
                        } else {
                            scope.launch(Dispatchers.Main.immediate) {
                                val entries = repo.reportEntries(range.first, range.second)
                                val file = withContext(Dispatchers.IO) { PdfExport.report(context, settings, periodName, entries, all) }
                                Intents.shareFile(context, file, "application/pdf")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.report_pdf))
                }
                OutlinedButton(
                    onClick = {
                        if (!pro.isPro) proDialog = true
                        else csvLauncher.launch("Depter_" + LocalDate.now() + ".csv")
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.TableChart, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.report_excel))
                }
            }
        }
        if (overdue.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.report_overdue), Depter.colors.warning) }
            items(overdue.take(10), key = { "o" + it.party.id }) { row ->
                PartyListItem(row = row, onClick = { nav.navigate(Routes.party(row.party.id)) })
            }
        }
        if (top.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.report_top), MaterialTheme.colorScheme.onSurfaceVariant) }
            items(top, key = { "t" + it.party.id }) { row: PartyRow ->
                PartyListItem(row = row, onClick = { nav.navigate(Routes.party(row.party.id)) })
            }
        }
    }

    if (proDialog) {
        ProRequiredDialog(
            message = stringResource(R.string.pro_feature_reports),
            onDismiss = { proDialog = false },
            onOpenPro = {
                proDialog = false
                nav.navigate(Routes.PRO)
            },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.merge(NumberStyle),
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun BarChart(buckets: List<Bucket>) {
    val gaveColor = Depter.colors.gave
    val gotColor = Depter.colors.got
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val max = buckets.maxOf { maxOf(it.gave, it.got) }.coerceAtLeast(1L)
    val step = ((buckets.size + 7) / 8).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val labelH = 16.dp.toPx()
        val chartH = size.height - labelH
        val slot = size.width / buckets.size
        val barW = (slot * 0.34f).coerceAtMost(14.dp.toPx())
        for (i in 0..3) {
            val y = chartH * i / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        buckets.forEachIndexed { i, b ->
            val cx = slot * i + slot / 2f
            val hG = chartH * (b.gave.toFloat() / max)
            val hR = chartH * (b.got.toFloat() / max)
            if (b.gave > 0) {
                drawRoundRect(gaveColor, Offset(cx - barW - 1f, chartH - hG), Size(barW, hG), CornerRadius(3.dp.toPx()))
            }
            if (b.got > 0) {
                drawRoundRect(gotColor, Offset(cx + 1f, chartH - hR), Size(barW, hR), CornerRadius(3.dp.toPx()))
            }
            if (i % step == 0 || i == buckets.lastIndex) {
                val layout = measurer.measure(b.label, labelStyle)
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, chartH + 3.dp.toPx()))
            }
        }
    }
}
