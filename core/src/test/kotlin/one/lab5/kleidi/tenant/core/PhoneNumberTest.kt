package one.lab5.kleidi.tenant.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneNumberTest {
    @Test
    fun greekMobileWithDefaultCountryCode() {
        assertEquals("+306912345678", PhoneNumber.toE164("30", "6912345678"))
        assertEquals("+306912345678", PhoneNumber.toE164("+30", "691 234 5678"))
        assertEquals("+306912345678", PhoneNumber.toE164("0030", "0691-234-5678"))
    }

    @Test
    fun fullInternationalNumberPastedIntoTheNationalField() {
        assertEquals("+306912345678", PhoneNumber.toE164("30", "+30 691 234 5678"))
        assertEquals("+447700900123", PhoneNumber.toE164("30", "0044 7700 900123"))
    }

    @Test
    fun rejectsEmptyOrAbsurdInput() {
        assertNull(PhoneNumber.toE164("30", ""))
        assertNull(PhoneNumber.toE164("", "6912345678"))
        assertNull(PhoneNumber.toE164("30", "12"))
        assertNull(PhoneNumber.toE164("30", "69123456789012345"))
    }

    @Test
    fun displayGroupsGreekNumbers() {
        assertEquals("+30 691 234 5678", PhoneNumber.display("+306912345678"))
        assertEquals("+447700900123", PhoneNumber.display("+447700900123"))
    }
}
