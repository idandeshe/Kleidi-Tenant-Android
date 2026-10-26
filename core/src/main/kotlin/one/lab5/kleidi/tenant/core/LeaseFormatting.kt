// Formatting of the numbers on the lease screens, and the small decisions the Home screen
// makes (which lease to show, which month the rent card is about). Pure Kotlin, unit-tested.

package one.lab5.kleidi.tenant.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency

object LeaseFormatting {
    /**
     * `800` + `EUR` → "€ 800"; `1250.5` + `EUR` → "€ 1.250,50" (el) / "€ 1,250.50" (en).
     * Whole amounts drop the decimals (the approved mock-up: "€ 800 · Rent for …").
     * Without a currency the bare number is shown.
     */
    fun money(amount: Double?, currencyCode: String?, language: AppLanguage): String {
        if (amount == null) return ""
        val value = BigDecimal(amount).setScale(2, RoundingMode.HALF_EVEN)
        val whole = value.stripTrailingZeros().scale() <= 0
        val format = DecimalFormat(if (whole) "#,##0" else "#,##0.00", DecimalFormatSymbols(language.locale))
        val number = format.format(value)
        val symbol = currencySymbol(currencyCode) ?: return number
        return "$symbol $number"
    }

    /** "€" for EUR, "$" for USD, "£" for GBP; other codes stay as the code ("CHF"). */
    fun currencySymbol(currencyCode: String?): String? {
        if (currencyCode.isNullOrBlank()) return null
        return when (currencyCode.uppercase()) {
            "EUR" -> "€"
            "USD" -> "$"
            "GBP" -> "£"
            else -> runCatching { Currency.getInstance(currencyCode.uppercase()).currencyCode }.getOrDefault(currencyCode)
        }
    }

    /** The month the rent card is about: the current calendar month. */
    fun rentMonth(today: LocalDate): YearMonth = YearMonth.from(today)

    /**
     * The day of the month as it reads in a sentence: "5th" (en) / "5" (el — the sentence
     * carries the case: "έως 5 του μήνα").
     */
    fun dayOfMonthLabel(day: Int, language: AppLanguage): String = when (language) {
        AppLanguage.EL -> day.toString()
        AppLanguage.EN -> day.toString() + englishOrdinalSuffix(day)
    }

    private fun englishOrdinalSuffix(day: Int): String {
        if (day % 100 in 11..13) return "th"
        return when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }

    /** "2" + months → the number for the notice-period string; kept for symmetry and tests. */
    fun months(count: Int?): String = count?.toString() ?: ""

    /**
     * Which of the tenant's leases Home shows first: the active one, else the next upcoming,
     * else the most recent (the server lists them newest first, so the first wins ties).
     */
    fun <T> primaryLease(leases: List<T>, status: (T) -> LeaseStatus): T? =
        leases.firstOrNull { status(it) == LeaseStatus.Active }
            ?: leases.firstOrNull { status(it) == LeaseStatus.Upcoming }
            ?: leases.firstOrNull()
}
