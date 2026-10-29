package one.lab5.kleidi.tenant.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WireDeclarationRulesTest {
    @Test
    fun periodsReadInTheTenantsLanguage() {
        assertEquals("October 2026", RentFormatting.period("2026-10", AppLanguage.EN))
        assertEquals("Οκτώβριος 2026", RentFormatting.period("2026-10", AppLanguage.EL))
        assertEquals("September 2026, October 2026", RentFormatting.periods(listOf("2026-09", "2026-10"), AppLanguage.EN))
        assertEquals("not-a-period", RentFormatting.period("not-a-period", AppLanguage.EN))
    }

    @Test
    fun amountIsPrefilledFromThePickedMonthsUntilEdited() {
        val expected = mapOf("2026-09" to 800.0, "2026-10" to 812.5, "2026-11" to null)
        assertEquals(1612.5, WireDeclarationRules.expectedTotal(listOf("2026-09", "2026-10"), expected))
        assertEquals(800.0, WireDeclarationRules.expectedTotal(listOf("2026-09", "2026-11"), expected))
        assertEquals("800", WireDeclarationRules.prefillAmount(800.0))
        assertEquals("1612.5", WireDeclarationRules.prefillAmount(1612.5))
        assertEquals("", WireDeclarationRules.prefillAmount(0.0))
    }

    @Test
    fun amountsParseWithCommaOrDotAndMustBePositive() {
        assertEquals(800.5, WireDeclarationRules.parseAmount("800,50"))
        assertEquals(800.5, WireDeclarationRules.parseAmount(" 800.50 "))
        assertEquals(800.0, WireDeclarationRules.parseAmount("800"))
        assertNull(WireDeclarationRules.parseAmount(""))
        assertNull(WireDeclarationRules.parseAmount("0"))
        assertNull(WireDeclarationRules.parseAmount("-5"))
        assertNull(WireDeclarationRules.parseAmount("abc"))
    }

    @Test
    fun multipartValuesArePlainStrings() {
        assertEquals("800", WireDeclarationRules.amountField(800.0))
        assertEquals("812.5", WireDeclarationRules.amountField(812.5))
        assertEquals("1000000", WireDeclarationRules.amountField(1_000_000.0))
        assertEquals("2026-10-05", WireDeclarationRules.transferDateField(2026, 10, 5))
    }

    @Test
    fun proofRulesMirrorTheServer() {
        assertNull(WireDeclarationRules.proofProblem("application/pdf", 1024))
        assertNull(WireDeclarationRules.proofProblem("image/JPEG", WireDeclarationRules.PROOF_MAX_BYTES))
        assertEquals(WireDeclarationRules.ProofProblem.TooLarge, WireDeclarationRules.proofProblem("image/png", WireDeclarationRules.PROOF_MAX_BYTES + 1))
        assertEquals(WireDeclarationRules.ProofProblem.UnsupportedType, WireDeclarationRules.proofProblem("image/heic", 10))
        assertEquals(WireDeclarationRules.ProofProblem.UnsupportedType, WireDeclarationRules.proofProblem("application/msword", 10))
    }

    @Test
    fun heicIsDetectedByTypeOrName() {
        assertTrue(WireDeclarationRules.isHeic("image/heic", null))
        assertTrue(WireDeclarationRules.isHeic("image/heif", "x"))
        assertTrue(WireDeclarationRules.isHeic(null, "IMG_0001.HEIC"))
        assertTrue(WireDeclarationRules.isHeic("application/octet-stream", "photo.heif"))
        assertFalse(WireDeclarationRules.isHeic("image/jpeg", "photo.jpg"))
        assertFalse(WireDeclarationRules.isHeic(null, null))
    }
}
