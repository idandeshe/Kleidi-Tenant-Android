package one.lab5.kleidi.tenant.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class KleidiDateFormatterTest {
    private val instant = Instant.parse("2026-10-28T08:15:00Z")

    @Test
    fun dateAndDateTimeUseTheFixedKleidiFormats() {
        assertEquals("28/10/2026", KleidiDateFormatter.date(instant, ZoneOffset.UTC))
        assertEquals("28/10/2026 08:15", KleidiDateFormatter.dateTime(instant, ZoneOffset.UTC))
        // In the viewer's zone, 24-hour clock.
        assertEquals("28/10/2026 10:15", KleidiDateFormatter.dateTime(instant, ZoneId.of("Europe/Athens")))
        assertEquals("", KleidiDateFormatter.dateTime(null))
    }
}
