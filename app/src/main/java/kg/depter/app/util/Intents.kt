package kg.depter.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kg.depter.app.R
import java.io.File

object Intents {
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=kg.depter.app"
    const val SUPPORT_WHATSAPP = "996709992092"

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_no_app, Toast.LENGTH_SHORT).show()
        false
    }

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(context, Intent.createChooser(send, null))
    }

    fun shareFile(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    /** Opens a WhatsApp chat (regular or Business) with a prepared message. */
    fun whatsapp(context: Context, internationalPhone: String?, text: String) {
        val base = if (internationalPhone.isNullOrEmpty()) "https://wa.me/" else "https://wa.me/$internationalPhone"
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(base + "?text=" + Uri.encode(text))))
    }

    fun sms(context: Context, phone: String, text: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone.trim(), null)).putExtra("sms_body", text)
        start(context, intent)
    }

    fun dial(context: Context, phone: String) {
        start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone.trim(), null)))
    }

    fun openUrl(context: Context, url: String) {
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun openStore(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.packageName))
        try {
            context.startActivity(market)
        } catch (e: ActivityNotFoundException) {
            openUrl(context, PLAY_URL)
        }
    }

    fun copy(context: Context, label: String, text: String) {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    fun pasteText(context: Context): String? {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java) ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }
}
