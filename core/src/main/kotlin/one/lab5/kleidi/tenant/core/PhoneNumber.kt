// The phone number typed on the sign-in screen → the E.164 string the server expects
// (`+306912345678`). Greece (+30) is the default country code.

package one.lab5.kleidi.tenant.core

object PhoneNumber {
    const val DEFAULT_COUNTRY_CODE = "30"

    /**
     * Joins the country code and the national number into E.164, tolerating spaces, dashes,
     * dots, a leading `+`/`00` in the country code and a leading `0` on the national number
     * (Greek numbers are dialled without it internationally). Null when nothing usable was typed.
     */
    fun toE164(countryCode: String, national: String): String? {
        val cc = countryCode.filter { it.isDigit() }.removePrefix("00").trimStart('0')
        var digits = national.filter { it.isDigit() }
        // A national number pasted with its own country code ("+30 69…", "0030 69…").
        val trimmedNational = national.trim()
        if (trimmedNational.startsWith("+") || trimmedNational.startsWith("00")) {
            val full = digits.removePrefix("00")
            return if (full.length in 8..15) "+$full" else null
        }
        if (digits.startsWith("0")) digits = digits.trimStart('0')
        if (cc.isEmpty() || digits.isEmpty()) return null
        val full = cc + digits
        return if (full.length in 8..15) "+$full" else null
    }

    /** `+306912345678` → "+30 691 234 5678" for display; anything unrecognised is shown as is. */
    fun display(e164: String): String {
        if (!e164.startsWith("+30") || e164.length != 13) return e164
        val n = e164.substring(3)
        return "+30 ${n.substring(0, 3)} ${n.substring(3, 6)} ${n.substring(6)}"
    }
}
