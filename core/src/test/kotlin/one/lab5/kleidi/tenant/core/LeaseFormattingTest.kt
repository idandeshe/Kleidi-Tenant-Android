package one.lab5.kleidi.tenant.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LeaseFormattingTest {
    @Test
    fun moneyDropsDecimalsOnWholeAmounts() {
        assertEquals("€ 800", LeaseFormatting.money(800.0, "EUR", AppLanguage.EN))
        assertEquals("€ 800", LeaseFormatting.money(800.0, "EUR", AppLanguage.EL))
        assertEquals("€ 1,250.50", LeaseFormatting.money(1250.5, "EUR", AppLanguage.EN))
        assertEquals("€ 1.250,50", LeaseFormatting.money(1250.5, "EUR", AppLanguage.EL))
        assertEquals("€ 12,000", LeaseFormatting.money(12_000.0, "eur", AppLanguage.EN))
    }

    @Test
    fun moneyWithoutCurrencyIsTheBareNumber() {
        assertEquals("800", LeaseFormatting.money(800.0, null, AppLanguage.EN))
        assertEquals("", LeaseFormatting.money(null, "EUR", AppLanguage.EN))
        assertEquals("CHF 900", LeaseFormatting.money(900.0, "CHF", AppLanguage.EN))
    }

    @Test
    fun datesAreDayMonthYear() {
        val instant = Instant.parse("2026-03-01T00:00:00.000Z")
        assertEquals("01/03/2026", KleidiDateFormatter.date(instant, ZoneOffset.UTC))
        assertEquals("", KleidiDateFormatter.date(null))
        assertEquals(instant, parseApiDate("2026-03-01"))
        assertEquals(instant, parseApiDate("2026-03-01T00:00:00.000Z"))
        assertNull(parseApiDate("yesterday"))
    }

    @Test
    fun rentMonthIsLocalised() {
        val month = LeaseFormatting.rentMonth(LocalDate.of(2026, 10, 26))
        assertEquals(YearMonth.of(2026, 10), month)
        assertEquals("October 2026", KleidiDateFormatter.month(month, AppLanguage.EN))
        assertEquals("Οκτώβριος 2026", KleidiDateFormatter.month(month, AppLanguage.EL))
    }

    @Test
    fun dayOfMonthLabels() {
        assertEquals("1st", LeaseFormatting.dayOfMonthLabel(1, AppLanguage.EN))
        assertEquals("2nd", LeaseFormatting.dayOfMonthLabel(2, AppLanguage.EN))
        assertEquals("3rd", LeaseFormatting.dayOfMonthLabel(3, AppLanguage.EN))
        assertEquals("5th", LeaseFormatting.dayOfMonthLabel(5, AppLanguage.EN))
        assertEquals("11th", LeaseFormatting.dayOfMonthLabel(11, AppLanguage.EN))
        assertEquals("21st", LeaseFormatting.dayOfMonthLabel(21, AppLanguage.EN))
        assertEquals("5", LeaseFormatting.dayOfMonthLabel(5, AppLanguage.EL))
    }

    @Test
    fun primaryLeasePrefersActiveThenUpcomingThenNewest() {
        data class L(val id: String, val status: LeaseStatus)
        val ended = L("ended", LeaseStatus.Ended)
        val upcoming = L("upcoming", LeaseStatus.Upcoming)
        val active = L("active", LeaseStatus.Active)
        assertEquals(active, LeaseFormatting.primaryLease(listOf(ended, upcoming, active)) { it.status })
        assertEquals(upcoming, LeaseFormatting.primaryLease(listOf(ended, upcoming)) { it.status })
        assertEquals(ended, LeaseFormatting.primaryLease(listOf(ended, L("older", LeaseStatus.Terminated))) { it.status })
        assertNull(LeaseFormatting.primaryLease(emptyList<L>()) { it.status })
    }
}
