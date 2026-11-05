package kg.depter.app.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** In-app language switch. AppCompat stores the choice and recreates the activity. */
object AppLanguage {
    val tags = listOf("ky", "ru", "uz", "kk", "en")

    fun nativeName(tag: String): String = when (tag) {
        "ky" -> "Кыргызча"
        "ru" -> "Русский"
        "uz" -> "Oʻzbekcha"
        "kk" -> "Қазақша"
        "en" -> "English"
        else -> tag
    }

    fun set(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** The language the UI is shown in, falling back to [systemLanguage] if none was chosen. */
    fun effective(systemLanguage: String): String {
        val chosen = AppCompatDelegate.getApplicationLocales()
        val lang = if (chosen.isEmpty) systemLanguage else chosen[0]?.language ?: systemLanguage
        return if (lang in tags) lang else "en"
    }
}
