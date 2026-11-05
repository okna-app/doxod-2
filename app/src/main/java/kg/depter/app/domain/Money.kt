package kg.depter.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** A currency the shop keeps its book in. Amounts are always stored in minor units (1/100). */
data class Currency(val code: String, val symbol: String, val symbolBefore: Boolean)

object Currencies {
    val all: List<Currency> = listOf(
        Currency("KGS", "сом", false),
        Currency("KZT", "₸", false),
        Currency("UZS", "so‘m", false),
        Currency("RUB", "₽", false),
        Currency("TJS", "смн", false),
        Currency("USD", "$", true),
        Currency("EUR", "€", false),
        Currency("TRY", "₺", false),
        Currency("CNY", "¥", true),
    )

    fun byCode(code: String): Currency = all.firstOrNull { it.code == code } ?: all.first()
}

object Money {
    private const val NBSP = ' '
    const val MAX_MINOR: Long = 99_999_999_999_99L

    /**
     * Formats an amount in minor units. English uses "12,500.50", the other app languages
     * use "12 500,50" with non-breaking spaces so amounts never wrap.
     */
    fun format(
        minor: Long,
        currency: Currency,
        english: Boolean,
        withSymbol: Boolean = true,
        signed: Boolean = false,
    ): String {
        val negative = minor < 0
        val abs = if (negative) -minor else minor
        val whole = abs / 100
        val frac = (abs % 100).toInt()
        val groupSep = if (english) ',' else NBSP
        val decSep = if (english) '.' else ','
        val wholeStr = group(whole.toString(), groupSep)
        val number = if (frac == 0) wholeStr else wholeStr + decSep + frac.toString().padStart(2, '0')
        val sign = when {
            negative -> "−"
            signed && minor > 0 -> "+"
            else -> ""
        }
        if (!withSymbol) return sign + number
        return if (currency.symbolBefore) sign + currency.symbol + number else "$sign$number$NBSP${currency.symbol}"
    }

    private fun group(digits: String, sep: Char): String {
        if (digits.length <= 3) return digits
        val sb = StringBuilder()
        val first = digits.length % 3
        if (first > 0) sb.append(digits, 0, first)
        var i = first
        while (i < digits.length) {
            if (sb.isNotEmpty()) sb.append(sep)
            sb.append(digits, i, i + 3)
            i += 3
        }
        return sb.toString()
    }

    /** Parses user/backup text such as "12 500,5" or "12500.50" into minor units. */
    fun parse(text: String): Long? {
        val cleaned = text.trim()
            .replace(" ", "")
            .replace(NBSP.toString(), "")
            .replace(',', '.')
        if (cleaned.isEmpty()) return null
        return try {
            val value = BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
            value.movePointRight(2).longValueExact()
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Minor units to a plain editable string: 1250050 -> "12500.5". */
    fun toEditable(minor: Long): String {
        if (minor <= 0) return ""
        return BigDecimal.valueOf(minor).movePointLeft(2).stripTrailingZeros().toPlainString()
    }
}
