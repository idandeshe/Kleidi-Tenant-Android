// The small decisions of the "I paid by bank transfer" form and the Payments screen, kept
// pure so they are unit-tested: period labels, the amount prefilled from the picked months,
// what the server accepts as a proof (server/types/wireDeclarations.types.ts
// WIRE_DECLARATIONS_VALIDATION + WIRE_PROOF_MIME_TYPES), HEIC detection.

package one.lab5.kleidi.tenant.core

import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeParseException

object RentFormatting {
    /** `2026-10` → "October 2026" / "Οκτώβριος 2026"; anything that is not `YYYY-MM` is shown as is. */
    fun period(period: String, language: AppLanguage): String {
        val month = parsePeriod(period) ?: return period
        return KleidiDateFormatter.month(month, language)
    }

    fun parsePeriod(period: String): YearMonth? = try {
        YearMonth.parse(period)
    } catch (_: DateTimeParseException) {
        null
    }

    /** "October 2026, November 2026" — the months one declaration covers. */
    fun periods(periods: List<String>, language: AppLanguage): String =
        periods.joinToString(", ") { period(it, language) }
}

object WireDeclarationRules {
    const val PROOF_MAX_BYTES: Long = 15L * 1024 * 1024
    const val REFERENCE_MAX_LENGTH = 140
    const val MAX_PERIODS = 12

    /** What the server stores as a proof; a photo in any other format is re-encoded as JPEG first. */
    val PROOF_MIME_TYPES: Set<String> = setOf("application/pdf", "image/jpeg", "image/png", "image/webp")

    /** The multipart field names, in the order the server reads them (text first, the file last). */
    const val FIELD_PERIODS = "periods"
    const val FIELD_DECLARED_AMOUNT = "declaredAmount"
    const val FIELD_TRANSFER_DATE = "transferDate"
    const val FIELD_REFERENCE = "reference"
    const val FIELD_PROOF = "proof"

    enum class ProofProblem { UnsupportedType, TooLarge }

    /** Why a file cannot be sent as the proof, or null when the server will take it. */
    fun proofProblem(mimeType: String, sizeBytes: Long): ProofProblem? = when {
        mimeType.lowercase() !in PROOF_MIME_TYPES -> ProofProblem.UnsupportedType
        sizeBytes > PROOF_MAX_BYTES -> ProofProblem.TooLarge
        else -> null
    }

    /** HEIC / HEIF (iPhone photos, some Android cameras) — by MIME type or file name. */
    fun isHeic(mimeType: String?, fileName: String?): Boolean {
        val type = mimeType?.lowercase()
        if (type == "image/heic" || type == "image/heif" || type == "image/heic-sequence" || type == "image/heif-sequence") return true
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()
        return ext == "heic" || ext == "heif"
    }

    /** Sum of the expected rent of the picked months (months without an expected amount count as 0). */
    fun expectedTotal(selectedPeriods: Collection<String>, expectedAmounts: Map<String, Double?>): Double =
        // In whole cents: rents with cents must not add up to 2260.1299999.
        Math.round(selectedPeriods.sumOf { expectedAmounts[it] ?: 0.0 } * 100) / 100.0

    /**
     * The amount field's prefill while the tenant has not typed in it: the expected total as a
     * plain number ("800", "812.5"), or empty when nothing is picked (web: `sum > 0 ? String(sum) : ''`).
     */
    fun prefillAmount(expectedTotal: Double): String =
        if (expectedTotal > 0) plainNumber(expectedTotal) else ""

    /** "800,50" or "800.50" → 800.5; null when empty, not a number, or not positive. */
    fun parseAmount(text: String): Double? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toBigDecimalOrNull() ?: return null
        return if (value > BigDecimal.ZERO) value.toDouble() else null
    }

    /** The `declaredAmount` multipart value: a plain decimal string, never scientific notation. */
    fun amountField(amount: Double): String = plainNumber(amount)

    /** "2026-10-26" — the `transferDate` multipart value for a calendar day. */
    fun transferDateField(year: Int, month: Int, day: Int): String =
        "%04d-%02d-%02d".format(year, month, day)

    private fun plainNumber(value: Double): String =
        BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
}
