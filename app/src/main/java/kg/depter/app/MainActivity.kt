package kg.depter.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kg.depter.app.notify.Reminders
import kg.depter.app.ui.AppNav
import kg.depter.app.ui.LocalPro
import kg.depter.app.ui.LocalSettings
import kg.depter.app.ui.lock.LockScreen
import kg.depter.app.ui.theme.DepterTheme

class MainActivity : AppCompatActivity() {

    /** Party to open when the user taps a due-date notification. */
    private val openParty = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readIntent(intent)
        val c = container
        setContent {
            val settings by c.settings.state.collectAsStateWithLifecycle()
            val pro by c.pro.collectAsStateWithLifecycle()
            val locked by c.lock.locked.collectAsStateWithLifecycle()
            DepterTheme {
                CompositionLocalProvider(LocalSettings provides settings, LocalPro provides pro) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        AppNav(
                            startOnboarding = !settings.onboarded,
                            openParty = openParty.value,
                            onPartyOpened = { openParty.value = null },
                        )
                        if (locked && settings.pinEnabled) {
                            LockScreen(onUnlocked = { c.lock.unlock() })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        val id = intent?.getLongExtra(Reminders.EXTRA_OPEN_PARTY, -1L) ?: -1L
        if (id > 0) openParty.value = id
    }
}
