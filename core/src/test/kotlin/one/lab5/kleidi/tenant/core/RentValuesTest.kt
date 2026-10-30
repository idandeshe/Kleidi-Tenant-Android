package one.lab5.kleidi.tenant.core

import kotlin.test.Test
import one.lab5.kleidi.tenant.core.RentMonthStatus.Companion.proofLine
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
    fun theProofLineFollowsTheDeclaration() {
        // No declaration → nothing to open, whatever the month says.
        assertEquals(RentProofLine.None, proofLine(RentMonthStatus.Due, confirmedByWire = false, declaration = null))
        assertEquals(RentProofLine.None, proofLine(RentMonthStatus.Paid, confirmedByWire = true, declaration = null))
        // Waiting for confirmation: the declared date is already on the right of the row.
        assertEquals(RentProofLine.ViewProof, proofLine(RentMonthStatus.Declared, confirmedByWire = false, declaration = WireDeclarationStatus.Pending))
        // Paid through a confirmed wire: "Declared dd/mm/yyyy · View proof".
        assertEquals(RentProofLine.DeclaredAndViewProof, proofLine(RentMonthStatus.Paid, confirmedByWire = true, declaration = WireDeclarationStatus.Confirmed))
        // Rejected on an owed month: "Declared dd/mm/yyyy · View proof" next to the red note.
        assertEquals(RentProofLine.DeclaredAndViewProof, proofLine(RentMonthStatus.Overdue, confirmedByWire = false, declaration = WireDeclarationStatus.Rejected))
        // Paid some other way with an old pending declaration, or an unknown declaration status → nothing.
        assertEquals(RentProofLine.None, proofLine(RentMonthStatus.Paid, confirmedByWire = false, declaration = WireDeclarationStatus.Pending))
        assertEquals(RentProofLine.None, proofLine(RentMonthStatus.Due, confirmedByWire = false, declaration = WireDeclarationStatus("withdrawn")))
    }
}
