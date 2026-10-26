package one.lab5.kleidi.tenant.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLanguageTest {
    @Test
    fun deviceEnglishDefaultsToEnglishEverythingElseToGreek() {
        assertEquals(AppLanguage.EN, AppLanguage.defaultFor("en"))
        assertEquals(AppLanguage.EN, AppLanguage.defaultFor("en-GB"))
        assertEquals(AppLanguage.EN, AppLanguage.defaultFor("en_US"))
        assertEquals(AppLanguage.EL, AppLanguage.defaultFor("el"))
        assertEquals(AppLanguage.EL, AppLanguage.defaultFor("de"))
        assertEquals(AppLanguage.EL, AppLanguage.defaultFor(null))
        assertEquals(AppLanguage.EL, AppLanguage.defaultFor(""))
    }

    @Test
    fun serverCodesRoundTrip() {
        assertEquals(AppLanguage.EL, AppLanguage.fromCode("el"))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("EN"))
        assertNull(AppLanguage.fromCode("fr"))
        assertNull(AppLanguage.fromCode(null))
        assertEquals("el", AppLanguage.EL.code)
    }
}
