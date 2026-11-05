@file:OptIn(ExperimentalMaterial3Api::class)

package kg.depter.app.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kg.depter.app.BuildConfig
import kg.depter.app.DepterApp
import kg.depter.app.R
import kg.depter.app.container
import kg.depter.app.domain.Countries
import kg.depter.app.domain.Currencies
import kg.depter.app.domain.ProStatus
import kg.depter.app.notify.Reminders
import kg.depter.app.ui.AppLanguage
import kg.depter.app.ui.Dates
import kg.depter.app.ui.LocalPro
import kg.depter.app.ui.LocalSettings
import kg.depter.app.ui.ProRequiredDialog
import kg.depter.app.ui.Routes
import kg.depter.app.ui.countryName
import kg.depter.app.ui.currentLocale
import kg.depter.app.ui.theme.Depter
import kg.depter.app.util.Intents
import kg.depter.app.util.ReminderText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

private enum class Dialog { NONE, SHOP, PHONE, COUNTRY, CURRENCY, LANGUAGE, THEME, TEMPLATE, PIN_OFF, IMPORT, PRIVACY, PRO_TEMPLATE }

@Composable
fun SettingsTab(padding: PaddingValues, nav: NavController) {
    val context = LocalContext.current
    val c = context.container
    val s = LocalSettings.current
    val pro = LocalPro.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(Dialog.NONE) }
    var pendingImport by remember { mutableStateOf<JSONObject?>(null) }
    val locale = currentLocale()
    val lang = AppLanguage.effective(locale.language)
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.Main.immediate) {
            val ok = runCatching {
                val json = c.repo.exportJson().put("businessName", s.businessName).toString()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
            }.isSuccess
            if (ok) c.settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
            Toast.makeText(context, if (ok) R.string.backup_done else R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.Main.immediate) {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    JSONObject(text ?: "").takeIf { it.optString("app") == "depter" }
                }.getOrNull()
            }
            if (json == null) {
                Toast.makeText(context, R.string.restore_bad_file, Toast.LENGTH_LONG).show()
            } else {
                pendingImport = json
                dialog = Dialog.IMPORT
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("settings_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ProCard(pro = pro, onClick = { nav.navigate(Routes.PRO) }) }
        item {
            Group(stringResource(R.string.settings_business)) {
                SettingRow(Icons.Filled.Storefront, stringResource(R.string.field_shop), s.businessName.ifBlank { "—" }) { dialog = Dialog.SHOP }
                SettingRow(Icons.Filled.Phone, stringResource(R.string.owner_phone), s.ownerPhone.ifBlank { stringResource(R.string.owner_phone_hint) }) { dialog = Dialog.PHONE }
                SettingRow(Icons.Filled.Flag, stringResource(R.string.field_country), Countries.byIso(s.country).flag + " " + countryName(s.country)) { dialog = Dialog.COUNTRY }
                SettingRow(Icons.Filled.Payments, stringResource(R.string.currency), s.currency + " · " + Currencies.byCode(s.currency).symbol) { dialog = Dialog.CURRENCY }
            }
        }
        item {
            Group(stringResource(R.string.settings_look)) {
                SettingRow(Icons.Filled.Language, stringResource(R.string.language), AppLanguage.nativeName(lang)) { dialog = Dialog.LANGUAGE }
                SettingRow(
                    Icons.Filled.DarkMode,
                    stringResource(R.string.theme),
                    stringResource(listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)[s.theme.coerceIn(0, 2)]),
                ) { dialog = Dialog.THEME }
            }
        }
        item {
            Group(stringResource(R.string.settings_reminders)) {
                SettingRow(
                    Icons.Filled.TextSnippet,
                    stringResource(R.string.reminder_template),
                    if (pro.isPro && s.reminderTemplate.isNotBlank()) s.reminderTemplate else stringResource(R.string.reminder_template_default),
                ) { dialog = if (pro.isPro) Dialog.TEMPLATE else Dialog.PRO_TEMPLATE }
            }
        }
        item {
            Group(stringResource(R.string.settings_security)) {
                SwitchRow(Icons.Filled.Lock, stringResource(R.string.pin_lock), stringResource(R.string.pin_lock_desc), s.pinEnabled, "switch_pin") { on ->
                    if (on) nav.navigate(Routes.PIN_SETUP) else dialog = Dialog.PIN_OFF
                }
                if (s.pinEnabled && biometricAvailable) {
                    SwitchRow(Icons.Filled.Fingerprint, stringResource(R.string.biometric), stringResource(R.string.biometric_desc), s.biometric, "switch_bio") { on ->
                        c.settings.update { it.copy(biometric = on) }
                    }
                }
            }
        }
        item {
            Group(stringResource(R.string.settings_data)) {
                SettingRow(
                    Icons.Filled.Backup,
                    stringResource(R.string.backup_create),
                    if (s.lastBackupAt > 0) {
                        stringResource(R.string.backup_last, Dates.long(Dates.localDate(s.lastBackupAt), locale))
                    } else {
                        stringResource(R.string.backup_never)
                    },
                ) { exportLauncher.launch("Depter_backup_" + LocalDate.now() + ".json") }
                SettingRow(Icons.Filled.Restore, stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_desc)) {
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                }
            }
        }
        item {
            Group(stringResource(R.string.settings_help)) {
                SettingRow(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.support), stringResource(R.string.support_desc)) {
                    Intents.whatsapp(context, Intents.SUPPORT_WHATSAPP, context.getString(R.string.support_message, BuildConfig.VERSION_NAME))
                }
                SettingRow(Icons.Filled.Star, stringResource(R.string.rate_app), stringResource(R.string.rate_app_desc)) { Intents.openStore(context) }
                SettingRow(Icons.Filled.Share, stringResource(R.string.share_app), stringResource(R.string.share_app_desc)) {
                    Intents.shareText(context, context.getString(R.string.share_app_text, Intents.PLAY_URL))
                }
                SettingRow(Icons.Filled.PrivacyTip, stringResource(R.string.privacy), stringResource(R.string.privacy_desc)) { dialog = Dialog.PRIVACY }
                SettingRow(Icons.Filled.Info, stringResource(R.string.version), BuildConfig.VERSION_NAME, showArrow = false) { }
            }
        }
    }

    when (dialog) {
        Dialog.SHOP -> TextDialog(stringResource(R.string.field_shop), s.businessName, KeyboardType.Text, 50, { dialog = Dialog.NONE }) { v ->
            c.settings.update { it.copy(businessName = v.trim()) }
        }
        Dialog.PHONE -> TextDialog(stringResource(R.string.owner_phone), s.ownerPhone, KeyboardType.Phone, 24, { dialog = Dialog.NONE }) { v ->
            c.settings.update { it.copy(ownerPhone = v.trim()) }
        }
        Dialog.TEMPLATE -> TextDialog(
            stringResource(R.string.reminder_template),
            s.reminderTemplate.ifBlank { ReminderText.defaultTemplate(context) },
            KeyboardType.Text,
            500,
            { dialog = Dialog.NONE },
            help = stringResource(R.string.reminder_template_help),
            singleLine = false,
        ) { v -> c.settings.update { it.copy(reminderTemplate = v.trim()) } }
        Dialog.COUNTRY -> ChoiceDialog(
            stringResource(R.string.field_country),
            Countries.all.map { it.iso to (it.flag + " " + countryName(it.iso) + "  +" + it.phoneCode) },
            s.country,
            { dialog = Dialog.NONE },
        ) { iso -> c.settings.update { it.copy(country = iso) } }
        Dialog.CURRENCY -> ChoiceDialog(
            stringResource(R.string.currency),
            Currencies.all.map { it.code to (it.code + " · " + it.symbol) },
            s.currency,
            { dialog = Dialog.NONE },
        ) { code -> c.settings.update { it.copy(currency = code) } }
        Dialog.LANGUAGE -> ChoiceDialog(
            stringResource(R.string.language),
            AppLanguage.tags.map { it to AppLanguage.nativeName(it) },
            lang,
            { dialog = Dialog.NONE },
        ) { tag -> AppLanguage.set(tag) }
        Dialog.THEME -> ChoiceDialog(
            stringResource(R.string.theme),
            listOf("0" to stringResource(R.string.theme_system), "1" to stringResource(R.string.theme_light), "2" to stringResource(R.string.theme_dark)),
            s.theme.toString(),
            { dialog = Dialog.NONE },
        ) { v ->
            val t = v.toInt()
            c.settings.update { it.copy(theme = t) }
            AppCompatDelegate.setDefaultNightMode(DepterApp.themeToNightMode(t))
        }
        Dialog.PIN_OFF -> AlertDialog(
            onDismissRequest = { dialog = Dialog.NONE },
            title = { Text(stringResource(R.string.pin_off_title)) },
            text = { Text(stringResource(R.string.pin_off_text)) },
            confirmButton = {
                TextButton(onClick = {
                    c.settings.setPin(null)
                    dialog = Dialog.NONE
                }) { Text(stringResource(R.string.turn_off)) }
            },
            dismissButton = { TextButton(onClick = { dialog = Dialog.NONE }) { Text(stringResource(R.string.cancel)) } },
        )
        Dialog.IMPORT -> AlertDialog(
            onDismissRequest = { dialog = Dialog.NONE },
            title = { Text(stringResource(R.string.restore_title)) },
            text = { Text(stringResource(R.string.restore_text)) },
            confirmButton = {
                TextButton(onClick = {
                    val json = pendingImport
                    dialog = Dialog.NONE
                    if (json != null) {
                        scope.launch(Dispatchers.Main.immediate) {
                            val result = runCatching { c.repo.importJson(json) }
                            if (result.isSuccess) {
                                val name = json.optString("businessName")
                                if (name.isNotBlank() && s.businessName.isBlank()) c.settings.update { it.copy(businessName = name) }
                                Reminders.rescheduleAll(context, c.repo)
                                Toast.makeText(context, context.getString(R.string.restore_done, result.getOrDefault(0)), Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, R.string.restore_bad_file, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) { Text(stringResource(R.string.restore_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = Dialog.NONE }) { Text(stringResource(R.string.cancel)) } },
        )
        Dialog.PRIVACY -> AlertDialog(
            onDismissRequest = { dialog = Dialog.NONE },
            title = { Text(stringResource(R.string.privacy)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.privacy_text))
                }
            },
            confirmButton = { TextButton(onClick = { dialog = Dialog.NONE }) { Text(stringResource(R.string.ok)) } },
        )
        Dialog.PRO_TEMPLATE -> ProRequiredDialog(
            message = stringResource(R.string.pro_feature_template),
            onDismiss = { dialog = Dialog.NONE },
            onOpenPro = {
                dialog = Dialog.NONE
                nav.navigate(Routes.PRO)
            },
        )
        Dialog.NONE -> Unit
    }
}

@Composable
private fun ProCard(pro: ProStatus, onClick: () -> Unit) {
    val colors = Depter.colors
    val locale = currentLocale()
    val subtitle = when (pro.kind) {
        ProStatus.Kind.PRO -> if (pro.expiryDay == null || pro.expiryDay == 0xFFFF) {
            stringResource(R.string.pro_forever)
        } else {
            stringResource(R.string.pro_until, Dates.long(LocalDate.ofEpochDay((pro.expiryDay ?: 0).toLong()), locale))
        }
        ProStatus.Kind.TRIAL -> pluralStringResource(R.plurals.pro_trial_left, pro.daysLeft, pro.daysLeft)
        ProStatus.Kind.FREE -> stringResource(R.string.pro_free_plan, ProStatus.FREE_PARTY_LIMIT)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(colors.heroStart, colors.heroEnd)), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(18.dp)
            .testTag("pro_card"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WorkspacePremium, null, tint = colors.onHero, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.titleLarge, color = colors.onHero)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onHero.copy(alpha = 0.85f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.onHero)
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
        )
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, showArrow: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (showArrow) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    keyboard: KeyboardType,
    maxLen: Int,
    onDismiss: () -> Unit,
    help: String? = null,
    singleLine: Boolean = true,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.length <= maxLen) value = it },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 4,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (help != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(value)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (key, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = key == selected, role = Role.RadioButton) {
                                onSelect(key)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = key == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
