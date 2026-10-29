package one.lab5.kleidi.tenant.features.payments

import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.ApiError
import one.lab5.kleidi.tenant.core.RentMonthStatus
import one.lab5.kleidi.tenant.core.WireDeclarationStatus
import one.lab5.kleidi.tenant.ui.PillTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM test (no Robolectric): resource ids are compile-time constants. */
class RentLabelsTest {
    @Test
    fun everyKnownStatusHasAResource() {
        listOf(RentMonthStatus.Paid, RentMonthStatus.Declared, RentMonthStatus.Due, RentMonthStatus.Overdue, RentMonthStatus.Upcoming).forEach {
            assertTrue(it.raw, RentLabels.MONTH_STATUS.containsKey(it.labelKey))
        }
        assertEquals(R.string.rent_confirmed_by_wire, RentLabels.MONTH_STATUS[RentMonthStatus.labelKey(RentMonthStatus.Paid, confirmedByWire = true)])
        listOf(WireDeclarationStatus.Pending, WireDeclarationStatus.Confirmed, WireDeclarationStatus.Rejected).forEach {
            assertTrue(it.raw, RentLabels.DECLARATION_STATUS.containsKey(it.labelKey))
        }
    }

    @Test
    fun tonesFollowTheWebStyles() {
        assertEquals(PillTone.Success, RentLabels.monthTone(RentMonthStatus.Paid))
        assertEquals(PillTone.Accent, RentLabels.monthTone(RentMonthStatus.Declared))
        assertEquals(PillTone.Warning, RentLabels.monthTone(RentMonthStatus.Due))
        assertEquals(PillTone.Error, RentLabels.monthTone(RentMonthStatus.Overdue))
        assertEquals(PillTone.Neutral, RentLabels.monthTone(RentMonthStatus.Upcoming))
        assertEquals(PillTone.Neutral, RentLabels.monthTone(RentMonthStatus("waived")))
        assertEquals(PillTone.Error, RentLabels.declarationTone(WireDeclarationStatus.Rejected))
    }

    @Test
    fun declarationErrorsMapToTheirCopy() {
        assertEquals(R.string.wire_months_required, RentLabels.declareErrorMessageRes(ApiError.Http(400, "x", null, "periods")))
        assertEquals(R.string.wire_amount_required, RentLabels.declareErrorMessageRes(ApiError.Http(400, "x", null, "declaredAmount")))
        assertEquals(R.string.wire_date_required, RentLabels.declareErrorMessageRes(ApiError.Http(400, "x", null, "transferDate")))
        assertEquals(R.string.wire_proof_required, RentLabels.declareErrorMessageRes(ApiError.Http(400, "x", null, "proof")))
        assertEquals(R.string.error_wire_conflict, RentLabels.declareErrorMessageRes(ApiError.Http(409, "x", null, null)))
        assertEquals(R.string.wire_proof_too_large, RentLabels.declareErrorMessageRes(ApiError.Http(413, "x", null, "proof")))
        assertEquals(R.string.wire_proof_hint, RentLabels.declareErrorMessageRes(ApiError.Http(415, "x", null, "proof")))
        assertEquals(R.string.error_rate_limited, RentLabels.declareErrorMessageRes(ApiError.Http(429, "x", null, null)))
        assertEquals(R.string.error_offline, RentLabels.declareErrorMessageRes(ApiError.Transport("x")))
    }
}
