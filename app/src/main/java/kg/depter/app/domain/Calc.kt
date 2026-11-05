package kg.depter.app.domain

/**
 * The amount keypad supports quick sums like "1200+350+80" so a seller can add up
 * a basket without leaving the app. Expressions only contain digits, '.', '+' and '-'.
 */
object Calc {
    private const val MAX_INT_DIGITS = 11

    fun isOperator(c: Char) = c == '+' || c == '-'

    private fun currentNumber(expr: String): String {
        val idx = expr.indexOfLast { isOperator(it) }
        return if (idx < 0) expr else expr.substring(idx + 1)
    }

    fun press(expr: String, key: Char): String {
        val number = currentNumber(expr)
        return when {
            key.isDigit() -> {
                val dot = number.indexOf('.')
                when {
                    dot >= 0 && number.length - dot - 1 >= 2 -> expr
                    dot < 0 && number.length >= MAX_INT_DIGITS -> expr
                    number == "0" -> expr.dropLast(1) + key
                    else -> expr + key
                }
            }
            key == '.' -> when {
                number.contains('.') -> expr
                number.isEmpty() -> "${expr}0."
                else -> "$expr."
            }
            isOperator(key) -> {
                if (expr.isEmpty()) return expr
                var base = expr
                if (base.endsWith('.')) base = base.dropLast(1)
                if (base.isNotEmpty() && isOperator(base.last())) base = base.dropLast(1)
                if (base.isEmpty()) expr else base + key
            }
            else -> expr
        }
    }

    fun pressDoubleZero(expr: String): String = press(press(expr, '0'), '0')

    fun backspace(expr: String): String = expr.dropLast(1)

    fun hasOperator(expr: String): Boolean = expr.drop(1).any { isOperator(it) }

    /** Evaluates to minor units. Trailing operators/dots are ignored. Returns null when empty. */
    fun evaluate(expr: String): Long? {
        var e = expr
        while (e.isNotEmpty() && (isOperator(e.last()) || e.last() == '.')) e = e.dropLast(1)
        if (e.isEmpty()) return null
        var total = 0L
        var sign = 1
        val token = StringBuilder()
        fun flush(): Boolean {
            if (token.isEmpty()) return true
            val v = Money.parse(token.toString()) ?: return false
            total += sign * v
            token.clear()
            return true
        }
        for (c in e) {
            if (isOperator(c)) {
                if (!flush()) return null
                sign = if (c == '-') -1 else 1
            } else {
                token.append(c)
            }
        }
        if (!flush()) return null
        return total
    }
}
