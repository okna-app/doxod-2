package kg.depter.app.domain

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Pro activation codes are signed offline by the seller with a private ECDSA P-256 key.
 * The app only holds the public key, so codes cannot be forged without that key,
 * and each code is bound to one install ID.
 *
 * Code = "DPT1-" + base64url(payload[12] + signature[64 raw r||s]).
 * Payload: [0]=1 version, [1..8]=install id, [9..10]=expiry epoch-day (u16, BE, 65535=forever), [11]=plan.
 */
object License {
    const val PREFIX = "DPT1-"
    const val FOREVER: Int = 0xFFFF
    private const val PAYLOAD_LEN = 12
    private const val SIG_LEN = 64

    /** base64url length of 76 bytes without padding. */
    private const val CODE_BODY_LEN = 102

    const val PUBLIC_KEY_B64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEjaNUNvx030k39N1wxwViBjPFicb57BMsGjMzho7vW3DGTaVGsKMHH9h1piFTmJ+Xbg1NZuWCj1isBpe+csahFQ=="

    sealed interface Result {
        data class Valid(val expiryDay: Int, val plan: Int) : Result {
            val forever: Boolean get() = expiryDay == FOREVER
        }
        data class Expired(val expiryDay: Int) : Result
        data object WrongDevice : Result
        data object Invalid : Result
    }

    /** Extracts a code from any pasted text (a WhatsApp message may contain greetings around it). */
    fun extract(text: String): String? {
        val idx = text.indexOf(PREFIX)
        if (idx < 0) return null
        val sb = StringBuilder()
        var i = idx + PREFIX.length
        while (i < text.length && sb.length < CODE_BODY_LEN) {
            val c = text[i]
            val allowed = (c in 'A'..'Z') || (c in 'a'..'z') || (c in '0'..'9') || c == '-' || c == '_'
            if (allowed) sb.append(c) else if (!c.isWhitespace()) break
            i++
        }
        return if (sb.length == CODE_BODY_LEN) PREFIX + sb else null
    }

    fun verify(
        code: String,
        installIdHex: String,
        todayEpochDay: Long,
        publicKeyB64: String = PUBLIC_KEY_B64,
    ): Result {
        val clean = extract(code) ?: return Result.Invalid
        val raw = try {
            Base64.getUrlDecoder().decode(clean.removePrefix(PREFIX))
        } catch (e: IllegalArgumentException) {
            return Result.Invalid
        }
        if (raw.size != PAYLOAD_LEN + SIG_LEN || raw[0].toInt() != 1) return Result.Invalid
        val payload = raw.copyOfRange(0, PAYLOAD_LEN)
        val sig = raw.copyOfRange(PAYLOAD_LEN, raw.size)
        val ok = try {
            val key = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64)))
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(key)
            verifier.update(payload)
            verifier.verify(rawToDer(sig))
        } catch (e: Exception) {
            false
        }
        if (!ok) return Result.Invalid
        val id = payload.copyOfRange(1, 9).toHex()
        if (!id.equals(installIdHex, ignoreCase = true)) return Result.WrongDevice
        val expiry = ((payload[9].toInt() and 0xFF) shl 8) or (payload[10].toInt() and 0xFF)
        val plan = payload[11].toInt() and 0xFF
        if (expiry != FOREVER && todayEpochDay > expiry) return Result.Expired(expiry)
        return Result.Valid(expiry, plan)
    }

    fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    /** Groups an install id for display: A1B2-C3D4-E5F6-0718. */
    fun displayId(hex: String): String = hex.chunked(4).joinToString("-")

    /** Converts a raw r||s ECDSA signature into ASN.1 DER, which java.security expects. */
    internal fun rawToDer(raw: ByteArray): ByteArray {
        val half = raw.size / 2
        fun encInt(bytes: ByteArray): ByteArray {
            val v = BigInteger(1, bytes).toByteArray() // minimal two's complement, adds 0x00 when needed
            return byteArrayOf(0x02, v.size.toByte()) + v
        }
        val r = encInt(raw.copyOfRange(0, half))
        val s = encInt(raw.copyOfRange(half, raw.size))
        val body = ByteArrayOutputStream().apply { write(r); write(s) }.toByteArray()
        return byteArrayOf(0x30, body.size.toByte()) + body
    }
}

/** What the user currently has: paid Pro, the free trial, or the free plan. */
data class ProStatus(val kind: Kind, val daysLeft: Int, val expiryDay: Int?) {
    enum class Kind { PRO, TRIAL, FREE }

    val isPro: Boolean get() = kind != Kind.FREE

    companion object {
        const val TRIAL_DAYS = 14
        const val FREE_PARTY_LIMIT = 30

        fun compute(license: License.Result?, firstLaunchEpochDay: Long, todayEpochDay: Long): ProStatus {
            if (license is License.Result.Valid) {
                val left = if (license.forever) Int.MAX_VALUE else (license.expiryDay - todayEpochDay).toInt() + 1
                return ProStatus(Kind.PRO, left, license.expiryDay)
            }
            val trialLeft = (firstLaunchEpochDay + TRIAL_DAYS - todayEpochDay).toInt()
            if (trialLeft > 0) return ProStatus(Kind.TRIAL, trialLeft, null)
            return ProStatus(Kind.FREE, 0, null)
        }
    }
}
