package kg.depter.app.util

import android.content.Context
import kg.depter.app.R
import kg.depter.app.data.AppSettings
import kg.depter.app.domain.ProStatus

object ReminderText {
    const val NAME = "{name}"
    const val AMOUNT = "{amount}"
    const val SHOP = "{shop}"

    fun defaultTemplate(context: Context): String = context.getString(R.string.reminder_default)

    /**
     * Builds the message sent to a customer. When the shop owes money instead
     * (supplier or prepayment) a neutral "our balance" message is used.
     */
    fun build(
        context: Context,
        settings: AppSettings,
        pro: ProStatus,
        name: String,
        amountText: String,
        theyOwe: Boolean,
    ): String {
        val template = when {
            !theyOwe -> context.getString(R.string.reminder_statement)
            pro.isPro && settings.reminderTemplate.isNotBlank() -> settings.reminderTemplate
            else -> defaultTemplate(context)
        }
        val shop = settings.businessName.ifBlank { context.getString(R.string.app_name) }
        var text = template.replace(NAME, name).replace(AMOUNT, amountText).replace(SHOP, shop)
        if (!pro.isPro) text += "\n\n" + context.getString(R.string.reminder_signature, Intents.PLAY_URL)
        return text
    }
}
