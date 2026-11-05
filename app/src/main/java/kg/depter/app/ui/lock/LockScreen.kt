@file:OptIn(ExperimentalMaterial3Api::class)

package kg.depter.app.ui.lock

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kg.depter.app.R
import kg.depter.app.container
import kg.depter.app.ui.LocalSettings
import kg.depter.app.ui.findActivity
import kg.depter.app.ui.theme.Depter
import kotlinx.coroutines.delay

private const val PIN_LEN = 4

private fun biometric(activity: FragmentActivity, title: String, negative: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setNegativeButtonText(negative)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    runCatching { prompt.authenticate(info) }
}

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val store = context.container.settings
    val settings = LocalSettings.current
    val activity = context.findActivity()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }
    var blockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val title = stringResource(R.string.unlock_title)
    val usePin = stringResource(R.string.use_pin)
    val canBio = settings.biometric && activity != null &&
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    BackHandler { activity?.moveTaskToBack(true) }

    LaunchedEffect(Unit) {
        if (canBio && activity != null) biometric(activity, title, usePin, onUnlocked)
    }
    LaunchedEffect(blockedUntil) {
        while (System.currentTimeMillis() < blockedUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val blocked = now < blockedUntil

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("lock_screen")) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Logo()
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.enter_pin), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Dots(filled = pin.length, error = error)
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    blocked -> stringResource(R.string.pin_blocked, ((blockedUntil - now) / 1000 + 1).toInt())
                    error -> stringResource(R.string.pin_wrong)
                    else -> " "
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            PinPad(
                enabled = !blocked,
                showBio = canBio,
                onBio = { if (activity != null) biometric(activity, title, usePin, onUnlocked) },
                onDigit = { d ->
                    error = false
                    if (pin.length < PIN_LEN) pin += d
                    if (pin.length == PIN_LEN) {
                        if (store.checkPin(pin)) {
                            onUnlocked()
                        } else {
                            error = true
                            attempts++
                            pin = ""
                            if (attempts % 5 == 0) blockedUntil = System.currentTimeMillis() + 30_000L
                        }
                    }
                },
                onBack = { pin = pin.dropLast(1) },
            )
        }
    }
}

@Composable
fun PinSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val store = context.container.settings
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.pin_lock)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(if (first == null) R.string.pin_create else R.string.pin_repeat),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.pin_create_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Dots(filled = pin.length, error = mismatch)
            Spacer(Modifier.height(10.dp))
            Text(
                if (mismatch) stringResource(R.string.pin_mismatch) else " ",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            PinPad(
                enabled = true,
                showBio = false,
                onBio = {},
                onDigit = { d ->
                    mismatch = false
                    if (pin.length < PIN_LEN) pin += d
                    if (pin.length == PIN_LEN) {
                        val f = first
                        when {
                            f == null -> {
                                first = pin
                                pin = ""
                            }
                            f == pin -> {
                                store.setPin(pin)
                                context.container.lock.unlock()
                                onDone()
                            }
                            else -> {
                                mismatch = true
                                first = null
                                pin = ""
                            }
                        }
                    }
                },
                onBack = { pin = pin.dropLast(1) },
            )
        }
    }
}

@Composable
private fun Logo() {
    val c = Depter.colors
    Box(
        Modifier
            .size(72.dp)
            .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(72.dp))
    }
}

@Composable
private fun Dots(filled: Int, error: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(PIN_LEN) { i ->
            val on = i < filled
            Box(
                Modifier
                    .size(16.dp)
                    .background(
                        when {
                            error -> MaterialTheme.colorScheme.error
                            on -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun PinPad(enabled: Boolean, showBio: Boolean, onBio: () -> Unit, onDigit: (Char) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.widthIn(max = 340.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { d -> PadKey(Modifier.weight(1f), enabled, "pin_$d", { onDigit(d) }) { Text(d.toString(), fontSize = 28.sp) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showBio) {
                PadKey(Modifier.weight(1f), enabled, "pin_bio", onBio) { Icon(Icons.Filled.Fingerprint, null, Modifier.size(30.dp)) }
            } else {
                Spacer(Modifier.weight(1f))
            }
            PadKey(Modifier.weight(1f), enabled, "pin_0", { onDigit('0') }) { Text("0", fontSize = 28.sp) }
            PadKey(Modifier.weight(1f), enabled, "pin_back", onBack, flat = true) {
                Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.erase))
            }
        }
    }
}

@Composable
private fun PadKey(
    modifier: Modifier,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    flat: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (flat) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(68.dp).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
