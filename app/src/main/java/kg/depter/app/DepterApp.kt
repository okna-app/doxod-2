package kg.depter.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kg.depter.app.data.AppSettings
import kg.depter.app.data.DepterDb
import kg.depter.app.data.Repository
import kg.depter.app.data.SettingsStore
import kg.depter.app.domain.License
import kg.depter.app.domain.ProStatus
import kg.depter.app.notify.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class DepterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Reminders.createChannel(this)
        AppCompatDelegate.setDefaultNightMode(themeToNightMode(container.settings.value.theme))
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.lock)
        container.scope.launch { Reminders.rescheduleAll(this@DepterApp, container.repo) }
    }

    companion object {
        fun themeToNightMode(theme: Int): Int = when (theme) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as DepterApp).container

class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repo = Repository(DepterDb(app))
    val settings = SettingsStore(app)
    val lock = AppLock(settings)

    val pro: StateFlow<ProStatus> = settings.state
        .map { computePro(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, computePro(settings.value))

    fun licenseResult(s: AppSettings, code: String = s.licenseCode): License.Result? =
        if (code.isBlank()) null else License.verify(code, s.installId, LocalDate.now().toEpochDay())

    private fun computePro(s: AppSettings): ProStatus =
        ProStatus.compute(licenseResult(s), s.firstLaunchDay, LocalDate.now().toEpochDay())
}

/**
 * Locks the app with the PIN on a cold start and when the user comes back after
 * [TIMEOUT_MS] in the background.
 */
class AppLock(private val settings: SettingsStore) : DefaultLifecycleObserver {
    private val _locked = MutableStateFlow(settings.value.pinEnabled)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var stoppedAt = 0L

    override fun onStop(owner: LifecycleOwner) {
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (settings.value.pinEnabled && stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > TIMEOUT_MS) {
            _locked.value = true
        }
    }

    fun unlock() {
        _locked.value = false
    }

    /** Locks immediately (used on cold start paths and in tests). */
    fun lockNow() {
        if (settings.value.pinEnabled) _locked.value = true
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
