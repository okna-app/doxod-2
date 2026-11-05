@file:OptIn(ExperimentalMaterial3Api::class)

package kg.depter.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kg.depter.app.R
import kg.depter.app.container
import kg.depter.app.domain.Countries
import kg.depter.app.ui.AppLanguage
import kg.depter.app.ui.countryName
import kg.depter.app.ui.currentLocale
import kg.depter.app.ui.theme.Depter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val store = context.container.settings
    var shop by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf(store.value.country) }
    val lang = AppLanguage.effective(currentLocale().language)
    val c = Depter.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppLanguage.tags.forEach { tag ->
                    FilterChip(
                        selected = tag == lang,
                        onClick = { AppLanguage.set(tag) },
                        label = { Text(AppLanguage.nativeName(tag)) },
                        modifier = Modifier.testTag("lang_$tag"),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .size(88.dp)
                    .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)), RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(88.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.onboarding_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Feature(Icons.Filled.Bolt, stringResource(R.string.feature_fast_title), stringResource(R.string.feature_fast_text))
            Feature(
                Icons.Filled.NotificationsActive,
                stringResource(R.string.feature_remind_title),
                stringResource(R.string.feature_remind_text),
            )
            Feature(Icons.Filled.Lock, stringResource(R.string.feature_private_title), stringResource(R.string.feature_private_text))
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = shop,
                        onValueChange = { if (it.length <= 50) shop = it },
                        label = { Text(stringResource(R.string.field_shop)) },
                        placeholder = { Text(stringResource(R.string.field_shop_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Storefront, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().testTag("onb_shop"),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Text(stringResource(R.string.field_country), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Countries.all.forEach { item ->
                            FilterChip(
                                selected = item.iso == country,
                                onClick = { country = item.iso },
                                label = { Text(item.flag + " " + countryName(item.iso)) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 6.dp) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = {
                        val ctry = Countries.byIso(country)
                        store.update {
                            it.copy(
                                onboarded = true,
                                businessName = shop.trim(),
                                country = ctry.iso,
                                currency = ctry.currency,
                            )
                        }
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("onb_start"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.start), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    pluralStringResource(
                        R.plurals.onboarding_trial,
                        kg.depter.app.domain.ProStatus.TRIAL_DAYS,
                        kg.depter.app.domain.ProStatus.TRIAL_DAYS,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Feature(
    icon: ImageVector,
    title: String,
    text: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
