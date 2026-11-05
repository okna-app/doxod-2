package kg.depter.app.data

enum class PartyType(val id: Int) {
    CUSTOMER(0), SUPPLIER(1);

    companion object {
        fun of(id: Int): PartyType = if (id == 1) SUPPLIER else CUSTOMER
    }
}

/** GAVE = goods or money left the shop (debt grows), GOT = money or goods came in (debt shrinks). */
enum class EntryKind(val id: Int) {
    GAVE(0), GOT(1);

    companion object {
        fun of(id: Int): EntryKind = if (id == 1) GOT else GAVE
    }
}

data class Party(
    val id: Long,
    val type: PartyType,
    val name: String,
    val phone: String,
    val note: String,
    /** Agreed payment day as epoch day, or null. */
    val dueDay: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * A party with its running balance. balance > 0: they owe the shop (to receive);
 * balance < 0: the shop owes them (to pay).
 */
data class PartyRow(
    val party: Party,
    val balance: Long,
    val lastAt: Long?,
    val entryCount: Int,
)

data class Entry(
    val id: Long,
    val partyId: Long,
    val kind: EntryKind,
    val amount: Long,
    val note: String,
    val date: Long,
    val createdAt: Long,
) {
    val signed: Long get() = if (kind == EntryKind.GAVE) amount else -amount
}

data class LedgerLine(val entry: Entry, val balanceAfter: Long)

enum class PartySort { RECENT, AMOUNT, NAME, DUE }

data class DayTotal(val epochDay: Long, val gave: Long, val got: Long)

data class PeriodReport(
    val gave: Long,
    val got: Long,
    val count: Int,
    val days: List<DayTotal>,
)

data class ReportEntry(val entry: Entry, val partyName: String, val partyType: PartyType)
