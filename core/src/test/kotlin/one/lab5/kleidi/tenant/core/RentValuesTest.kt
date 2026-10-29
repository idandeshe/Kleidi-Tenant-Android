package one.lab5.kleidi.tenant.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RentValuesTest {
    @Test
    fun monthStatusLabelKeys() {
        assertEquals("rent_status_paid", RentMonthStatus.Paid.labelKey)
        assertEquals("rent_status_declared", RentMonthStatus.Declared.labelKey)
        assertEquals("rent_status_due", RentMonthStatus.Due.labelKey)
        assertEquals("rent_status_overdue", RentMonthStatus.Overdue.labelKey)
        assertEquals("rent_status_upcoming", RentMonthStatus.Upcoming.labelKey)
        assertNull(RentMonthStatus("waived").labelKey)
        assertEquals("Waived", humanize(RentMonthStatus("waived").raw))
    }

    @Test
    fun paidThroughAWireReadsConfirmedByOwner() {
        assertEquals("rent_confirmed_by_wire", RentMonthStatus.labelKey(RentMonthStatus.Paid, confirmedByWire = true))
        assertEquals("rent_status_paid", RentMonthStatus.labelKey(RentMonthStatus.Paid, confirmedByWire = false))
        // Only a paid month gets the wire wording.
        assertEquals("rent_status_declared", RentMonthStatus.labelKey(RentMonthStatus.Declared, confirmedByWire = true))
        assertNull(RentMonthStatus.labelKey(RentMonthStatus("waived"), confirmedByWire = true))
    }

    @Test
    fun declarationStatusLabelKeys() {
        assertEquals("rent_declaration_status_pending", WireDeclarationStatus.Pending.labelKey)
        assertEquals("rent_declaration_status_confirmed", WireDeclarationStatus.Confirmed.labelKey)
        assertEquals("rent_declaration_status_rejected", WireDeclarationStatus.Rejected.labelKey)
        assertNull(WireDeclarationStatus("withdrawn").labelKey)
    }
}
