package kg.depter.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate

data class AppSettings(
    val onboarded: Boolean,
    val businessName: String,
    val ownerPhone: String,
    val country: String,
    val currency: String,
    /** 0 = follow system, 1 = light, 2 = dark. */
    val theme: Int,
    val pinHash: String,
    val pinSalt: String,
    val biometric: Boolean,
    val reminderTemplate: String,
    val installId: String,
    val firstLaunchDay: Long,
    val licenseCode: String,
    val lastBackupAt: Long,
) {
    val pinEnabled: Boolean get() = pinHash.isNotEmpty()
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("depter_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val value: AppSettings get() = _state.value

    init {
        // Install id and first-launch day are created once and never change afterwards.
        if (prefs.getString(K_INSTALL_ID, null).isNullOrEmpty()) {
            val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
            val hex = bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
            prefs.edit {
                putString(K_INSTALL_ID, hex)
                putLong(K_FIRST_DAY, LocalDate.now().toEpochDay())
            }
            _state.value = read()
        }
    }

    private fun read() = AppSettings(
        onboarded = prefs.getBoolean(K_ONBOARDED, false),
        businessName = prefs.getString(K_BUSINESS, "") ?: "",
        ownerPhone = prefs.getString(K_OWNER_PHONE, "") ?: "",
        country = prefs.getString(K_COUNTRY, "KG") ?: "KG",
        currency = prefs.getString(K_CURRENCY, "KGS") ?: "KGS",
        theme = prefs.getInt(K_THEME, 0),
        pinHash = prefs.getString(K_PIN_HASH, "") ?: "",
        pinSalt = prefs.getString(K_PIN_SALT, "") ?: "",
        biometric = prefs.getBoolean(K_BIOMETRIC, false),
        reminderTemplate = prefs.getString(K_TEMPLATE, "") ?: "",
        installId = prefs.getString(K_INSTALL_ID, "") ?: "",
        firstLaunchDay = prefs.getLong(K_FIRST_DAY, LocalDate.now().toEpochDay()),
        licenseCode = prefs.getString(K_LICENSE, "") ?: "",
        lastBackupAt = prefs.getLong(K_LAST_BACKUP, 0L),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val n = block(_state.value)
        prefs.edit {
            putBoolean(K_ONBOARDED, n.onboarded)
            putString(K_BUSINESS, n.businessName)
            putString(K_OWNER_PHONE, n.ownerPhone)
            putString(K_COUNTRY, n.country)
            putString(K_CURRENCY, n.currency)
            putInt(K_THEME, n.theme)
            putString(K_PIN_HASH, n.pinHash)
            putString(K_PIN_SALT, n.pinSalt)
            putBoolean(K_BIOMETRIC, n.biometric)
            putString(K_TEMPLATE, n.reminderTemplate)
            putString(K_LICENSE, n.licenseCode)
            putLong(K_LAST_BACKUP, n.lastBackupAt)
        }
        _state.value = n
    }

    fun setPin(pin: String?) {
        if (pin == null) {
            update { it.copy(pinHash = "", pinSalt = "", biometric = false) }
            return
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        update { it.copy(pinHash = hash(salt, pin), pinSalt = salt) }
    }

    fun checkPin(pin: String): Boolean {
        val s = _state.value
        return s.pinEnabled && hash(s.pinSalt, pin) == s.pinHash
    }

    private fun hash(salt: String, pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest((salt + ":" + pin).toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val K_ONBOARDED = "onboarded"
        const val K_BUSINESS = "business"
        const val K_OWNER_PHONE = "owner_phone"
        const val K_COUNTRY = "country"
        const val K_CURRENCY = "currency"
        const val K_THEME = "theme"
        const val K_PIN_HASH = "pin_hash"
        const val K_PIN_SALT = "pin_salt"
        const val K_BIOMETRIC = "biometric"
        const val K_TEMPLATE = "template"
        const val K_INSTALL_ID = "install_id"
        const val K_FIRST_DAY = "first_day"
        const val K_LICENSE = "license"
        const val K_LAST_BACKUP = "last_backup"
    }
}
