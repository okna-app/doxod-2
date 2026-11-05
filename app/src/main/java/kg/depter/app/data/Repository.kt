package kg.depter.app.data

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Single source of truth over SQLite. Every write bumps [version], and every observed
 * query re-runs on the IO dispatcher, so all screens stay in sync without a heavy ORM.
 */
class Repository(private val helper: DepterDb) {

    private val version = MutableStateFlow(0L)

    private fun changed() = version.update { it + 1 }

    private fun <T> observe(block: () -> T): Flow<T> = version.map { block() }.flowOn(Dispatchers.IO)

    // ---------- Reads ----------

    private val partySelect = """
        SELECT p.id, p.type, p.name, p.phone, p.note, p.due_day, p.created_at, p.updated_at,
               COALESCE(SUM(CASE WHEN e.kind = 0 THEN e.amount ELSE -e.amount END), 0) AS balance,
               MAX(e.date) AS last_at,
               COUNT(e.id) AS cnt
        FROM parties p LEFT JOIN entries e ON e.party_id = p.id
    """.trimIndent()

    private fun Cursor.toPartyRow(): PartyRow = PartyRow(
        party = Party(
            id = getLong(0),
            type = PartyType.of(getInt(1)),
            name = getString(2),
            phone = getString(3),
            note = getString(4),
            dueDay = if (isNull(5)) null else getLong(5),
            createdAt = getLong(6),
            updatedAt = getLong(7),
        ),
        balance = getLong(8),
        lastAt = if (isNull(9)) null else getLong(9),
        entryCount = getInt(10),
    )

    private fun Cursor.toEntry(): Entry = Entry(
        id = getLong(0),
        partyId = getLong(1),
        kind = EntryKind.of(getInt(2)),
        amount = getLong(3),
        note = getString(4),
        date = getLong(5),
        createdAt = getLong(6),
    )

    private val entryColumns = "id, party_id, kind, amount, note, date, created_at"

    private fun queryParties(): List<PartyRow> {
        val out = ArrayList<PartyRow>()
        helper.readableDatabase.rawQuery("$partySelect GROUP BY p.id", null).use { c ->
            while (c.moveToNext()) out += c.toPartyRow()
        }
        return out
    }

    private fun queryParty(id: Long): PartyRow? =
        helper.readableDatabase.rawQuery("$partySelect WHERE p.id = ? GROUP BY p.id", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.toPartyRow() else null
        }

    private fun queryEntries(partyId: Long): List<Entry> {
        val out = ArrayList<Entry>()
        helper.readableDatabase.rawQuery(
            "SELECT $entryColumns FROM entries WHERE party_id = ? ORDER BY date ASC, id ASC",
            arrayOf(partyId.toString()),
        ).use { c -> while (c.moveToNext()) out += c.toEntry() }
        return out
    }

    fun allParties(): Flow<List<PartyRow>> = observe { queryParties() }

    fun party(id: Long): Flow<PartyRow?> = observe { queryParty(id) }

    /** Ledger lines newest first, each with the balance right after that entry. */
    fun ledger(partyId: Long): Flow<List<LedgerLine>> = observe {
        var running = 0L
        queryEntries(partyId).map { e ->
            running += e.signed
            LedgerLine(e, running)
        }.asReversed()
    }

    suspend fun partyNow(id: Long): PartyRow? = withContext(Dispatchers.IO) { queryParty(id) }

    suspend fun partiesNow(): List<PartyRow> = withContext(Dispatchers.IO) { queryParties() }

    suspend fun ledgerNow(partyId: Long): List<LedgerLine> = withContext(Dispatchers.IO) {
        var running = 0L
        queryEntries(partyId).map { e ->
            running += e.signed
            LedgerLine(e, running)
        }
    }

    suspend fun entry(id: Long): Entry? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT $entryColumns FROM entries WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.toEntry() else null
        }
    }

    suspend fun partyCount(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM parties", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** Recent distinct notes for quick suggestions on the entry screen. */
    suspend fun recentNotes(limit: Int = 12): List<String> = withContext(Dispatchers.IO) {
        val out = ArrayList<String>()
        helper.readableDatabase.rawQuery(
            "SELECT note, MAX(date) d FROM entries WHERE note <> '' GROUP BY note ORDER BY d DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c -> while (c.moveToNext()) out += c.getString(0) }
        out
    }

    private fun queryReportEntries(from: Long, to: Long): List<ReportEntry> {
        val out = ArrayList<ReportEntry>()
        helper.readableDatabase.rawQuery(
            """
            SELECT e.id, e.party_id, e.kind, e.amount, e.note, e.date, e.created_at, p.name, p.type
            FROM entries e JOIN parties p ON p.id = e.party_id
            WHERE e.date >= ? AND e.date < ?
            ORDER BY e.date ASC, e.id ASC
            """.trimIndent(),
            arrayOf(from.toString(), to.toString()),
        ).use { c ->
            while (c.moveToNext()) out += ReportEntry(c.toEntry(), c.getString(7), PartyType.of(c.getInt(8)))
        }
        return out
    }

    suspend fun reportEntries(from: Long, to: Long): List<ReportEntry> =
        withContext(Dispatchers.IO) { queryReportEntries(from, to) }

    fun report(from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): Flow<PeriodReport> = observe {
        val entries = queryReportEntries(from, to)
        var gave = 0L
        var got = 0L
        val byDay = LinkedHashMap<Long, LongArray>()
        for (r in entries) {
            // Period totals describe customer credit only; supplier debts are shown separately.
            if (r.partyType != PartyType.CUSTOMER) continue
            val day = Instant.ofEpochMilli(r.entry.date).atZone(zone).toLocalDate().toEpochDay()
            val acc = byDay.getOrPut(day) { LongArray(2) }
            if (r.entry.kind == EntryKind.GAVE) {
                gave += r.entry.amount
                acc[0] += r.entry.amount
            } else {
                got += r.entry.amount
                acc[1] += r.entry.amount
            }
        }
        PeriodReport(gave, got, entries.count { it.partyType == PartyType.CUSTOMER }, byDay.map { (d, a) -> DayTotal(d, a[0], a[1]) })
    }

    // ---------- Writes ----------

    suspend fun addParty(type: PartyType, name: String, phone: String, note: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("type", type.id)
            put("name", name.trim())
            put("phone", phone.trim())
            put("note", note.trim())
            put("created_at", now)
            put("updated_at", now)
        }
        helper.writableDatabase.insertOrThrow("parties", null, cv).also { changed() }
    }

    suspend fun updateParty(id: Long, type: PartyType, name: String, phone: String, note: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("type", type.id)
            put("name", name.trim())
            put("phone", phone.trim())
            put("note", note.trim())
            put("updated_at", System.currentTimeMillis())
        }
        helper.writableDatabase.update("parties", cv, "id = ?", arrayOf(id.toString()))
        changed()
    }

    suspend fun setDueDay(id: Long, dueDay: Long?) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            if (dueDay == null) putNull("due_day") else put("due_day", dueDay)
            put("updated_at", System.currentTimeMillis())
        }
        helper.writableDatabase.update("parties", cv, "id = ?", arrayOf(id.toString()))
        changed()
    }

    suspend fun deleteParty(id: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("parties", "id = ?", arrayOf(id.toString()))
        changed()
    }

    suspend fun addEntry(partyId: Long, kind: EntryKind, amount: Long, note: String, date: Long): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("party_id", partyId)
            put("kind", kind.id)
            put("amount", amount)
            put("note", note.trim())
            put("date", date)
            put("created_at", System.currentTimeMillis())
        }
        val db = helper.writableDatabase
        val id = db.insertOrThrow("entries", null, cv)
        touch(partyId)
        changed()
        id
    }

    suspend fun updateEntry(id: Long, partyId: Long, kind: EntryKind, amount: Long, note: String, date: Long) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("kind", kind.id)
            put("amount", amount)
            put("note", note.trim())
            put("date", date)
        }
        helper.writableDatabase.update("entries", cv, "id = ?", arrayOf(id.toString()))
        touch(partyId)
        changed()
    }

    suspend fun deleteEntry(id: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("entries", "id = ?", arrayOf(id.toString()))
        changed()
    }

    private fun touch(partyId: Long) {
        val cv = ContentValues().apply { put("updated_at", System.currentTimeMillis()) }
        helper.writableDatabase.update("parties", cv, "id = ?", arrayOf(partyId.toString()))
    }

    // ---------- Backup ----------

    suspend fun exportJson(): JSONObject = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        val parties = JSONArray()
        db.rawQuery("SELECT id, type, name, phone, note, due_day, created_at, updated_at FROM parties", null).use { c ->
            while (c.moveToNext()) {
                parties.put(
                    JSONObject()
                        .put("id", c.getLong(0))
                        .put("type", c.getInt(1))
                        .put("name", c.getString(2))
                        .put("phone", c.getString(3))
                        .put("note", c.getString(4))
                        .put("dueDay", if (c.isNull(5)) JSONObject.NULL else c.getLong(5))
                        .put("createdAt", c.getLong(6))
                        .put("updatedAt", c.getLong(7)),
                )
            }
        }
        val entries = JSONArray()
        db.rawQuery("SELECT $entryColumns FROM entries", null).use { c ->
            while (c.moveToNext()) {
                val e = c.toEntry()
                entries.put(
                    JSONObject()
                        .put("id", e.id)
                        .put("partyId", e.partyId)
                        .put("kind", e.kind.id)
                        .put("amount", e.amount)
                        .put("note", e.note)
                        .put("date", e.date)
                        .put("createdAt", e.createdAt),
                )
            }
        }
        JSONObject()
            .put("app", "depter")
            .put("format", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("parties", parties)
            .put("entries", entries)
    }

    /** Replaces all data with the backup. Throws IllegalArgumentException for foreign files. */
    suspend fun importJson(json: JSONObject): Int = withContext(Dispatchers.IO) {
        require(json.optString("app") == "depter") { "not a depter backup" }
        val parties = json.getJSONArray("parties")
        val entries = json.getJSONArray("entries")
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("entries", null, null)
            db.delete("parties", null, null)
            for (i in 0 until parties.length()) {
                val p = parties.getJSONObject(i)
                val cv = ContentValues().apply {
                    put("id", p.getLong("id"))
                    put("type", p.getInt("type"))
                    put("name", p.getString("name"))
                    put("phone", p.optString("phone"))
                    put("note", p.optString("note"))
                    if (p.isNull("dueDay")) putNull("due_day") else put("due_day", p.getLong("dueDay"))
                    put("created_at", p.getLong("createdAt"))
                    put("updated_at", p.getLong("updatedAt"))
                }
                db.insertOrThrow("parties", null, cv)
            }
            for (i in 0 until entries.length()) {
                val e = entries.getJSONObject(i)
                val cv = ContentValues().apply {
                    put("id", e.getLong("id"))
                    put("party_id", e.getLong("partyId"))
                    put("kind", e.getInt("kind"))
                    put("amount", e.getLong("amount"))
                    put("note", e.optString("note"))
                    put("date", e.getLong("date"))
                    put("created_at", e.getLong("createdAt"))
                }
                db.insertOrThrow("entries", null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        parties.length()
    }
}
