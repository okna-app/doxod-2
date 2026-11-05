package kg.depter.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class DomainTest {
    private val kgs = Currencies.byCode("KGS")
    private val usd = Currencies.byCode("USD")

    @Test
    fun formatsMoney() {
        assertEquals("12 500 сом", Money.format(1_250_000, kgs, english = false))
        assertEquals("12 500,50 сом", Money.format(1_250_050, kgs, english = false))
        assertEquals("−\$0.99", Money.format(-99, usd, english = true))
        assertEquals("\$1,234,567.89", Money.format(123_456_789, usd, english = true))
        assertEquals("0 сом", Money.format(0, kgs, english = false))
    }

    @Test
    fun parsesMoney() {
        assertEquals(1_250_050L, Money.parse("12 500,5"))
        assertEquals(1L, Money.parse("0.005"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
        assertEquals("12500.5", Money.toEditable(1_250_050))
    }

    @Test
    fun keypadCalculator() {
        var e = ""
        "1200+350-50".forEach { e = Calc.press(e, it) }
        assertEquals(150_000L, Calc.evaluate(e))
        e = ""
        "+.5.55".forEach { e = Calc.press(e, it) }
        assertEquals("0.55", e)
        assertEquals("12-", Calc.press(Calc.press("12", '+'), '-'))
        assertEquals(1_200L, Calc.evaluate("12-"))
        assertNull(Calc.evaluate(""))
        assertEquals("100", Calc.pressDoubleZero("1"))
        assertTrue(Calc.hasOperator("5+5"))
    }

    @Test
    fun normalizesPhones() {
        assertEquals("996555123456", Phone.toInternational("0555 12-34-56", "996"))
        assertEquals("996700111222", Phone.toInternational("+996 700 111 222", "996"))
        assertEquals("77011234567", Phone.toInternational("8 701 123 45 67", "7"))
        assertEquals("998901234567", Phone.toInternational("90 123 45 67", "998"))
        assertNull(Phone.toInternational("123", "996"))
    }

    private fun issue(privateKey: java.security.PrivateKey, idHex: String, expiryDay: Int): String {
        val payload = byteArrayOf(1) + idHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray() +
            byteArrayOf((expiryDay shr 8).toByte(), expiryDay.toByte(), 1)
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(payload)
            sign()
        }
        return License.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload + derToRaw(der))
    }

    private fun derToRaw(der: ByteArray): ByteArray {
        var i = 2
        fun int(): ByteArray {
            val len = der[i + 1].toInt()
            val v = der.copyOfRange(i + 2, i + 2 + len)
            i += 2 + len
            val trimmed = v.dropWhile { it == 0.toByte() }.toByteArray()
            return ByteArray(32 - trimmed.size) + trimmed
        }
        return int() + int()
    }

    @Test
    fun verifiesLicenses() {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val pub = Base64.getEncoder().encodeToString(kp.public.encoded)
        val id = "A1B2C3D4E5F60718"
        val today = 20_000L
        val code = issue(kp.private, id, 20_030)
        val r = License.verify("Code: $code thanks", id, today, pub)
        assertTrue(r is License.Result.Valid)
        assertEquals(License.Result.WrongDevice, License.verify(code, "A1B2C3D4E5F60719", today, pub))
        assertTrue(License.verify(code, id, 20_031, pub) is License.Result.Expired)
        assertEquals(License.Result.Invalid, License.verify(code.dropLast(3) + "AAA", id, today, pub))
        assertEquals(License.Result.Invalid, License.verify(code, id, today))
        val forever = issue(kp.private, id, License.FOREVER)
        val f = License.verify(forever, id, 99_999, pub)
        assertTrue(f is License.Result.Valid && f.forever)
    }

    @Test
    fun proStatus() {
        val t = ProStatus.compute(null, 100, 103)
        assertEquals(ProStatus.Kind.TRIAL, t.kind)
        assertEquals(11, t.daysLeft)
        assertEquals(ProStatus.Kind.FREE, ProStatus.compute(License.Result.Invalid, 100, 120).kind)
        assertEquals(30, ProStatus.compute(License.Result.Valid(129, 1), 0, 100).daysLeft)
    }
}
