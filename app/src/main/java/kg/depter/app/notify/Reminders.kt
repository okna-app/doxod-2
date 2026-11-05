package kg.depter.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kg.depter.app.MainActivity
import kg.depter.app.R
import kg.depter.app.container
import kg.depter.app.data.PartyRow
import kg.depter.app.data.Repository
import kg.depter.app.domain.Currencies
import kg.depter.app.domain.Money
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Local reminders on the agreed payment day. No server, works offline. */
object Reminders {
    const val CHANNEL = "due_dates"
    const val EXTRA_OPEN_PARTY = "open_party"
    private const val EXTRA_PARTY = "party_id"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            CHANNEL,
            context.getString(R.string.notif_channel_due),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        ch.description = context.getString(R.string.notif_channel_due_desc)
        nm.createNotificationChannel(ch)
    }

    private fun pending(context: Context, partyId: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, DueReceiver::class.java)
            .setAction("kg.depter.app.DUE.$partyId")
            .putExtra(EXTRA_PARTY, partyId)
        return PendingIntent.getBroadcast(context, partyId.toInt(), intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(context: Context, partyId: Long, dueDay: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        pending(context, partyId, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it) }
        if (dueDay == null) return
        val at = LocalDate.ofEpochDay(dueDay).atTime(10, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (at <= System.currentTimeMillis()) return
        val pi = pending(context, partyId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        am.set(AlarmManager.RTC_WAKEUP, at, pi)
    }

    suspend fun rescheduleAll(context: Context, repo: Repository) {
        repo.partiesNow().forEach { row ->
            if (row.party.dueDay != null) schedule(context, row.party.id, row.party.dueDay)
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, row: PartyRow) {
        if (!canNotify(context)) return
        val settings = context.container.settings.value
        val english = context.resources.configuration.locales[0].language == "en"
        val currency = Currencies.byCode(settings.currency)
        val amount = Money.format(kotlin.math.abs(row.balance), currency, english)
        val text = if (row.balance > 0) {
            context.getString(R.string.notif_due_receive, row.party.name, amount)
        } else {
            context.getString(R.string.notif_due_pay, row.party.name, amount)
        }
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_PARTY, row.party.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            row.party.id.toInt(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(context.getString(R.string.notif_due_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(row.party.id.toInt(), n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    class DueReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(EXTRA_PARTY, -1L)
            if (id < 0) return
            val result = goAsync()
            context.container.scope.launch {
                try {
                    val row = context.container.repo.partyNow(id)
                    if (row != null && row.balance != 0L) show(context, row)
                } finally {
                    result.finish()
                }
            }
        }
    }

    /** Starting the process is enough: DepterApp.onCreate reschedules every reminder. */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = Unit
    }
}
