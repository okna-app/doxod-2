package kg.depter.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.content.res.Configuration
import java.util.Locale
import android.os.ParcelFileDescriptor
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kg.depter.app.data.EntryKind
import kg.depter.app.data.PartyType
import kg.depter.app.pdf.PdfExport
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Walks through every main screen with realistic data and saves screenshots.
 * CI pulls them from the app's files/screens folder. It doubles as a smoke test:
 * any crash or missing element fails the run.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as DepterApp
    private val outDir by lazy { File(instrumentation.targetContext.filesDir, "screens").apply { mkdirs() } }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(700)
        instrumentation.waitForIdleSync()
        val bmp: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private data class Demo(
        val shop: String,
        val names: List<String>,
        val suppliers: List<String>,
        val notes: List<String>,
        val paid: String,
    )

    private val demos = mapOf(
        "ru" to Demo(
            "Магазин «Айгерим»",
            listOf("Айгуль Бекова", "Бакыт Асанов", "Нурлан ака", "Мээрим", "Азамат (такси)", "Гульнара эже", "Тилек"),
            listOf("«Шоро» дистрибьютор", "Хлебозавод №1"),
            listOf("Хлеб, молоко, яйца", "Мука 5 кг", "Сахар, чай", "Масло, рис", "Сигареты", "Овощи"),
            "Наличные",
        ),
        "ky" to Demo(
            "«Айгерим» дүкөнү",
            listOf("Айгүл Бекова", "Бакыт Асанов", "Нурлан ава", "Мээрим", "Азамат (такси)", "Гүлнара эже", "Тилек"),
            listOf("«Шоро» дистрибьютор", "Нан заводу №1"),
            listOf("Нан, сүт, жумуртка", "Ун 5 кг", "Кант, чай", "Май, күрүч", "Тамеки", "Жашылча"),
            "Накталай",
        ),
        "en" to Demo(
            "Aigerim Market",
            listOf("Aigul Bekova", "Bakyt Asanov", "Nurlan", "Meerim", "Azamat (taxi)", "Gulnara", "Tilek"),
            listOf("Shoro Distribution", "City Bakery"),
            listOf("Bread, milk, eggs", "Flour 5 kg", "Sugar, tea", "Oil, rice", "Snacks", "Vegetables"),
            "Cash",
        ),
    )

    private fun clearAll() = runBlocking {
        app.container.repo.importJson(
            JSONObject().put("app", "depter").put("parties", JSONArray()).put("entries", JSONArray()),
        )
    }

    /** Seeds a month of realistic activity. Returns the id of the most recently active customer. */
    private fun seed(d: Demo): Long = runBlocking {
        val repo = app.container.repo
        clearAll()
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        val today = java.time.LocalDate.now().toEpochDay()
        val phones = listOf("0555 12 34 56", "0700 98 76 54", "0777 45 61 23", "0550 31 22 10", "0709 11 22 33", "0222 64 50 01", "0505 80 90 70")
        val amounts = listOf(
            listOf(185_000L, 120_000L, -60_000L, 60_000L),
            listOf(420_000L, 360_000L),
            listOf(950_000L, 780_000L, -200_000L),
            listOf(240_000L, 120_000L),
            listOf(65_000L, 50_000L),
            listOf(300_000L, -300_000L),
            listOf(92_000L),
        )
        var top = -1L
        d.names.forEachIndexed { i, name ->
            val id = repo.addParty(PartyType.CUSTOMER, name, phones[i], "")
            amounts[i].forEachIndexed { j, a ->
                val kind = if (a > 0) EntryKind.GAVE else EntryKind.GOT
                val note = if (a > 0) d.notes[(i + j) % d.notes.size] else d.paid
                val at = now - day * (24 - i * 3 - j * 2).coerceAtLeast(0) - (i * 37 + j * 11) * 60_000L
                repo.addEntry(id, kind, kotlin.math.abs(a), note, at)
            }
            when (i) {
                1 -> repo.setDueDay(id, today - 3)
                0 -> repo.setDueDay(id, today + 2)
                3 -> repo.setDueDay(id, today + 5)
            }
        }
        // The first customer gets today's entries so they appear on top of the list.
        val first = repo.partiesNow().first { it.party.name == d.names[0] }.party.id
        repo.addEntry(first, EntryKind.GAVE, 45_000L, d.notes[2], now - 30 * 60_000L)
        top = first
        d.suppliers.forEachIndexed { i, name ->
            val id = repo.addParty(PartyType.SUPPLIER, name, "0312 90 00 0$i", "")
            repo.addEntry(id, EntryKind.GOT, if (i == 0) 3_600_000L else 950_000L, d.notes[i], now - day * (10 - i))
            repo.addEntry(id, EntryKind.GAVE, if (i == 0) 1_200_000L else 300_000L, d.paid, now - day * (4 - i))
        }
        top
    }

    /** Text as the app shows it in [lang], used to confirm the language really switched. */
    private fun str(lang: String, id: Int): String {
        val cfg = Configuration(instrumentation.targetContext.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(lang))
        return instrumentation.targetContext.createConfigurationContext(cfg).getString(id)
    }

    /** Database work runs on background threads that Compose does not track, so wait explicitly. */
    private fun waitTag(tag: String, timeout: Long = 8_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitText(text: String, timeout: Long = 8_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun setTheme(t: Int) {
        app.container.settings.update { it.copy(theme = t) }
        instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(DepterApp.themeToNightMode(t)) }
    }

    private fun renderPdf(file: File, name: String) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(0).use { page ->
                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
    }

    @Test
    fun tour() {
        outDir.listFiles()?.forEach { it.delete() }
        for (lang in listOf("ky", "ru", "en")) {
            tourFor(lang)
        }
        lockScreen()
    }

    private fun tourFor(lang: String) {
        val d = demos.getValue(lang)
        setTheme(1)
        val top = seed(d)
        app.container.settings.update { it.copy(onboarded = false, businessName = "", pinHash = "", pinSalt = "", licenseCode = "") }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitTag("onb_start")
        compose.onNodeWithTag("lang_$lang").performClick()
        waitText(str(lang, R.string.start))
        waitTag("onb_start")
        shot("${lang}_01_onboarding")
        compose.onNodeWithTag("onb_shop").performScrollTo().performTextInput(d.shop)
        compose.onNodeWithTag("onb_start").performClick()
        waitTag("home_list")
        shot("${lang}_02_home")

        compose.onNodeWithTag("party_$top").performClick()
        waitTag("ledger_balance")
        shot("${lang}_03_ledger")

        compose.onNodeWithTag("btn_gave").performClick()
        "350+1200".forEach { k -> compose.onNodeWithTag("key_$k").performClick() }
        shot("${lang}_04_entry")
        compose.onNodeWithTag("entry_save").performClick()
        waitTag("ledger_balance")

        // PDF statement rendered to an image so the layout can be reviewed.
        runBlocking {
            val row = app.container.repo.partyNow(top)!!
            val lines = app.container.repo.ledgerNow(top)
            var file: File? = null
            scenario.onActivity { file = PdfExport.statement(it, app.container.settings.value, row, lines) }
            assertTrue(file!!.length() > 1000)
            renderPdf(file!!, "${lang}_09_pdf_statement")
        }

        Espresso.pressBack()
        compose.onNodeWithTag("seg_suppliers").performClick()
        shot("${lang}_05_suppliers")
        compose.onNodeWithTag("seg_customers").performClick()

        compose.onNodeWithTag("tab_reports").performClick()
        waitTag("reports_list")
        shot("${lang}_06_reports")

        compose.onNodeWithTag("tab_settings").performClick()
        waitTag("settings_list")
        shot("${lang}_07_settings")

        compose.onNodeWithTag("pro_card").performClick()
        waitTag("device_id")
        shot("${lang}_08_pro")
        Espresso.pressBack()
        compose.onNodeWithTag("tab_book").performClick()

        if (lang == "ky") {
            setTheme(2)
            scenario.recreate()
            waitTag("home_list")
            shot("${lang}_10_home_dark")
            setTheme(1)
        }
        scenario.close()
    }

    private fun lockScreen() {
        app.container.settings.setPin("1234")
        app.container.settings.update { it.copy(onboarded = true) }
        app.container.lock.lockNow()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitTag("lock_screen")
        compose.onNodeWithTag("pin_1").performClick()
        compose.onNodeWithTag("pin_9").performClick()
        shot("lock_11")
        compose.onNodeWithTag("pin_back").performClick()
        compose.onNodeWithTag("pin_back").performClick()
        "1234".forEach { compose.onNodeWithTag("pin_$it").performClick() }
        waitTag("home_list")
        app.container.settings.setPin(null)
        scenario.close()
    }
}
