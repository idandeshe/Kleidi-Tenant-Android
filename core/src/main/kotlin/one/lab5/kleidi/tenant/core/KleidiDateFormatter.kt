// Display formatting shared by every screen — the same fixed formats as the Kleidi web
// clients (`@client/lib/formatDate`): DD/MM/YYYY, in the viewer's time zone, independent
// of the device locale.

package one.lab5.kleidi.tenant.core

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object KleidiDateFormatter {
    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun date(value: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        value?.atZone(zone)?.format(DATE) ?: ""

    /** "28/10/2026 10:15" — the web `formatDateTime` (24-hour clock). */
    fun dateTime(value: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        value?.atZone(zone)?.format(DATE_TIME) ?: ""

    /** "Οκτώβριος 2026" / "October 2026" — the standalone month name of [language]. */
    fun month(value: YearMonth, language: AppLanguage): String =
        value.format(DateTimeFormatter.ofPattern("LLLL yyyy", language.locale))
}

/** ISO-8601 with or without fractions / offset, or a date-only `YYYY-MM-DD` (UTC midnight). */
fun parseApiDate(raw: String): Instant? {
    try {
        return Instant.parse(raw)
    } catch (_: DateTimeParseException) {
    }
    try {
        return OffsetDateTime.parse(raw).toInstant()
    } catch (_: DateTimeParseException) {
    }
    return try {
        LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}
