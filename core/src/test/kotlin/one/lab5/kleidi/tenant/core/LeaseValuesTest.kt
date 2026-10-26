package one.lab5.kleidi.tenant.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeaseValuesTest {
    @Test
    fun knownValuesHaveLabelKeysUnknownOnesHumanize() {
        assertEquals("lease_status_active", LeaseStatus.Active.labelKey)
        assertEquals("lease_status_terminated", LeaseStatus("terminated").labelKey)
        assertNull(LeaseStatus("paused_by_court").labelKey)
        assertEquals("Paused by court", humanize("paused_by_court"))

        assertEquals("payment_method_bank_transfer", PaymentMethod.BankTransfer.labelKey)
        assertNull(PaymentMethod("crypto").labelKey)

        assertTrue(LeaseStatus.Active.isOpen)
        assertTrue(LeaseStatus.Upcoming.isOpen)
        assertFalse(LeaseStatus.Ended.isOpen)
    }

    @Test
    fun floorsAreNamedOrNumbered() {
        assertEquals("floor_ground", FloorLevel.Ground.labelKey)
        assertNull(FloorLevel.Ground.number)
        assertEquals(3, FloorLevel("floor_3").number)
        assertNull(FloorLevel("floor_3").labelKey)
        assertNull(FloorLevel("penthouse").number)
        assertNull(FloorLevel("penthouse").labelKey)
    }
}
