package kg.depter.app.domain

/** Country presets: phone code used to turn local numbers into WhatsApp-ready ones, plus a default currency. */
data class Country(val iso: String, val phoneCode: String, val currency: String, val flag: String)

object Countries {
    val all: List<Country> = listOf(
        Country("KG", "996", "KGS", "🇰🇬"),
        Country("KZ", "7", "KZT", "🇰🇿"),
        Country("UZ", "998", "UZS", "🇺🇿"),
        Country("RU", "7", "RUB", "🇷🇺"),
        Country("TJ", "992", "TJS", "🇹🇯"),
        Country("TR", "90", "TRY", "🇹🇷"),
    )

    fun byIso(iso: String): Country = all.firstOrNull { it.iso == iso } ?: all.first()
}

object Phone {
    fun digits(raw: String): String = raw.filter { it.isDigit() }

    /**
     * Converts what a seller typed ("0555 12 34 56", "+996555123456", "8 701 ...")
     * into international digits without '+', e.g. "996555123456". Returns null when there
     * are too few digits to dial.
     */
    fun toInternational(raw: String, countryCode: String): String? {
        val trimmed = raw.trim()
        var d = digits(trimmed)
        if (d.length < 6) return null
        if (trimmed.startsWith("+")) return d
        if (d.startsWith("00")) return d.drop(2)
        when (countryCode) {
            "996" -> {
                if (d.length == 12 && d.startsWith("996")) return d
                if (d.length == 10 && d.startsWith("0")) return "996" + d.drop(1)
                if (d.length == 9) return "996$d"
            }
            "7" -> {
                if (d.length == 11 && d.startsWith("8")) return "7" + d.drop(1)
                if (d.length == 11 && d.startsWith("7")) return d
                if (d.length == 10) return "7$d"
            }
            "998" -> {
                if (d.length == 12 && d.startsWith("998")) return d
                if (d.length == 9) return "998$d"
            }
            "992" -> {
                if (d.length == 12 && d.startsWith("992")) return d
                if (d.length == 9) return "992$d"
            }
            "90" -> {
                if (d.length == 12 && d.startsWith("90")) return d
                if (d.length == 11 && d.startsWith("0")) return "90" + d.drop(1)
                if (d.length == 10) return "90$d"
            }
        }
        if (d.startsWith(countryCode)) return d
        if (d.startsWith("0")) d = d.drop(1)
        return countryCode + d
    }
}
