@file:OptIn(ExperimentalMaterial3Api::class)

package kg.depter.app.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kg.depter.app.R
import kg.depter.app.container
import kg.depter.app.domain.License
import kg.depter.app.domain.ProStatus
import kg.depter.app.ui.Dates
import kg.depter.app.ui.LocalPro
import kg.depter.app.ui.LocalSettings
import kg.depter.app.ui.currentLocale
import kg.depter.app.ui.theme.Depter
import kg.depter.app.util.Intents
import java.time.LocalDate

@Composable
fun ProScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = context.container.settings
    val settings = LocalSettings.current
    val pro = LocalPro.current
    val locale = currentLocale()
    val colors = Depter.colors
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val idText = License.displayId(settings.installId)

    val msgValid = stringResource(R.string.code_valid)
    val msgExpired = stringResource(R.string.code_expired)
    val msgWrongDevice = stringResource(R.string.code_wrong_device)
    val msgInvalid = stringResource(R.string.code_invalid)

    fun activate() {
        val result = License.verify(code, settings.installId, LocalDate.now().toEpochDay())
        message = when (result) {
            is License.Result.Valid -> {
                store.update { it.copy(licenseCode = License.extract(code) ?: code.trim()) }
                code = ""
                msgValid to true
            }
            is License.Result.Expired -> msgExpired to false
            License.Result.WrongDevice -> msgWrongDevice to false
            License.Result.Invalid -> msgInvalid to false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.pro_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(colors.heroStart, colors.heroEnd)), RoundedCornerShape(24.dp))
                    .padding(20.dp),
            ) {
                Column {
                    Icon(Icons.Filled.WorkspacePremium, null, tint = Color(0xFFFFD166), modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.headlineSmall, color = colors.onHero)
                    Spacer(Modifier.height(4.dp))
                    val status = when (pro.kind) {
                        ProStatus.Kind.PRO -> if ((pro.expiryDay ?: License.FOREVER) == License.FOREVER) {
                            stringResource(R.string.pro_forever)
                        } else {
                            stringResource(R.string.pro_until, Dates.long(LocalDate.ofEpochDay((pro.expiryDay ?: 0).toLong()), locale))
                        }
                        ProStatus.Kind.TRIAL -> pluralStringResource(R.plurals.pro_trial_left, pro.daysLeft, pro.daysLeft)
                        ProStatus.Kind.FREE -> stringResource(R.string.pro_free_plan, ProStatus.FREE_PARTY_LIMIT)
                    }
                    Text(status, style = MaterialTheme.typography.bodyLarge, color = colors.onHero.copy(alpha = 0.9f), modifier = Modifier.testTag("pro_status"))
                }
            }

            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.pro_includes), style = MaterialTheme.typography.titleMedium)
                    listOf(
                        stringResource(R.string.pro_b1, ProStatus.FREE_PARTY_LIMIT),
                        stringResource(R.string.pro_b2),
                        stringResource(R.string.pro_b3),
                        stringResource(R.string.pro_b4),
                        stringResource(R.string.pro_b5),
                    ).forEach { b ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.CheckCircle, null, tint = colors.got, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(b, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.device_id), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.device_id_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        idText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("device_id"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { Intents.copy(context, "Depter ID", idText) }) {
                            Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.copy))
                        }
                        FilledTonalButton(onClick = {
                            Intents.shareText(context, context.getString(R.string.device_id_share, idText))
                        }) {
                            Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.share))
                        }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.activation_title), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = code,
                        onValueChange = {
                            code = it
                            message = null
                        },
                        placeholder = { Text("DPT1-…") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("code_input"),
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            IconButton(onClick = {
                                Intents.pasteText(context)?.let { pasted ->
                                    code = License.extract(pasted) ?: pasted
                                    message = null
                                }
                            }) { Icon(Icons.Filled.ContentPaste, stringResource(R.string.paste)) }
                        },
                    )
                    message?.let { (text, ok) ->
                        Text(text, color = if (ok) colors.got else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = { activate() },
                        enabled = code.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("code_activate"),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(stringResource(R.string.activate)) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
