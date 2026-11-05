@file:OptIn(ExperimentalMaterial3Api::class)

package kg.depter.app.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kg.depter.app.R
import kg.depter.app.data.AppSettings
import kg.depter.app.domain.Currencies
import kg.depter.app.domain.Currency
import kg.depter.app.domain.Money
import kg.depter.app.domain.ProStatus
import kg.depter.app.ui.theme.Depter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val LocalSettings = staticCompositionLocalOf<AppSettings> { error("settings not provided") }
val LocalPro = staticCompositionLocalOf { ProStatus(ProStatus.Kind.FREE, 0, null) }

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

class MoneyFmt(val currency: Currency, val english: Boolean) {
    fun format(minor: Long, signed: Boolean = false): String = Money.format(minor, currency, english, true, signed)
    fun plain(minor: Long): String = Money.format(minor, currency, english, false)
}

@Composable
fun rememberMoney(): MoneyFmt {
    val code = LocalSettings.current.currency
    val lang = currentLocale().language
    return remember(code, lang) { MoneyFmt(Currencies.byCode(code), lang == "en") }
}

object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()
    fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun startOfDay(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    fun long(day: LocalDate, locale: Locale, today: LocalDate = LocalDate.now()): String {
        val pattern = if (day.year == today.year) "d MMMM" else "d MMMM yyyy"
        return DateTimeFormatter.ofPattern(pattern, locale).format(day)
    }

    fun short(day: LocalDate, locale: Locale): String = DateTimeFormatter.ofPattern("dd.MM.yyyy", locale).format(day)

    fun time(ms: Long, locale: Locale): String =
        DateTimeFormatter.ofPattern("HH:mm", locale).format(Instant.ofEpochMilli(ms).atZone(zone))
}

@Composable
fun dayTitle(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> Dates.long(day, currentLocale(), today)
    }
}

@Composable
fun relativeDay(ms: Long): String {
    val day = Dates.localDate(ms)
    val days = (LocalDate.now().toEpochDay() - day.toEpochDay()).toInt()
    return when {
        days <= 0 -> stringResource(R.string.today)
        days == 1 -> stringResource(R.string.yesterday)
        days < 31 -> pluralStringResource(R.plurals.days_ago, days, days)
        else -> Dates.long(day, currentLocale())
    }
}

@Composable
fun balanceColor(balance: Long): Color = when {
    balance > 0 -> Depter.colors.got
    balance < 0 -> Depter.colors.gave
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@StringRes
fun balanceLabelRes(balance: Long): Int = when {
    balance > 0 -> R.string.to_receive
    balance < 0 -> R.string.to_pay
    else -> R.string.settled
}

private val AvatarPalette = listOf(
    Color(0xFF3346D3), Color(0xFF0E8A7A), Color(0xFFB0447C), Color(0xFFC0692B),
    Color(0xFF5B3FD1), Color(0xFF2B7BBF), Color(0xFF7A8B1F), Color(0xFFB23A3A),
)

fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@Composable
fun Avatar(name: String, size: Dp = 44.dp) {
    val color = AvatarPalette[(name.trim().lowercase().hashCode() and 0x7FFFFFFF) % AvatarPalette.size]
    Box(
        modifier = Modifier
            .size(size)
            .background(color.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(name),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp,
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** Shown when a free user taps a Pro feature. */
@Composable
fun ProRequiredDialog(message: String, onDismiss: () -> Unit, onOpenPro: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Depter.colors.warning) },
        title = { Text(stringResource(R.string.pro_required_title)) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onOpenPro) { Text(stringResource(R.string.pro_more)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.later)) } },
    )
}

fun Context.findActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun countryName(iso: String): String = stringResource(
    when (iso) {
        "KZ" -> R.string.country_kz
        "UZ" -> R.string.country_uz
        "RU" -> R.string.country_ru
        "TJ" -> R.string.country_tj
        "TR" -> R.string.country_tr
        else -> R.string.country_kg
    },
)
