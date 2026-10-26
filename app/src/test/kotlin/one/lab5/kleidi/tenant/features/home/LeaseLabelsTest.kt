package one.lab5.kleidi.tenant.features.home

import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.ApiError
import one.lab5.kleidi.tenant.api.TenantErrorCode
import one.lab5.kleidi.tenant.core.FloorLevel
import one.lab5.kleidi.tenant.core.LeaseStatus
import one.lab5.kleidi.tenant.core.PaymentMethod
import one.lab5.kleidi.tenant.shell.errorMessageRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM test (no Robolectric): resource ids are compile-time constants. */
class LeaseLabelsTest {
    @Test
    fun everyKnownValueHasAResource() {
        listOf(LeaseStatus.Upcoming, LeaseStatus.Active, LeaseStatus.Ended, LeaseStatus.Terminated).forEach {
            assertTrue(it.raw, LeaseLabels.STATUS.containsKey(it.labelKey))
        }
        listOf(PaymentMethod.BankTransfer, PaymentMethod.Cheques, PaymentMethod.Cash, PaymentMethod.StandingOrder, PaymentMethod.Other).forEach {
            assertTrue(it.raw, LeaseLabels.PAYMENT_METHOD.containsKey(it.labelKey))
        }
        listOf(FloorLevel.Basement, FloorLevel.SemiBasement, FloorLevel.Ground, FloorLevel.UpperGround).forEach {
            assertTrue(it.raw, LeaseLabels.FLOOR.containsKey(it.labelKey))
        }
    }

    @Test
    fun errorsMapToTheirCopy() {
        assertEquals(R.string.error_invalid_code, errorMessageRes(ApiError.Unauthorized(TenantErrorCode.INVALID_CODE, "x")))
        assertEquals(R.string.error_code_expired, errorMessageRes(ApiError.Unauthorized(TenantErrorCode.CODE_EXPIRED, "x")))
        assertEquals(R.string.error_too_many_attempts, errorMessageRes(ApiError.Unauthorized(TenantErrorCode.TOO_MANY_ATTEMPTS, "x")))
        assertEquals(R.string.error_not_a_tenant, errorMessageRes(ApiError.Forbidden(TenantErrorCode.NOT_A_TENANT, "x")))
        assertEquals(R.string.error_sms_not_configured, errorMessageRes(ApiError.Http(503, "x", TenantErrorCode.SMS_NOT_CONFIGURED, null)))
        assertEquals(R.string.error_rate_limited, errorMessageRes(ApiError.Http(429, "x", null, null)))
        assertEquals(R.string.error_invalid_phone, errorMessageRes(ApiError.Http(400, "x", null, "phone")))
        assertEquals(R.string.error_offline, errorMessageRes(ApiError.Transport("x")))
        assertEquals(R.string.error_update_required, errorMessageRes(ApiError.UpdateRequired(7)))
        assertEquals(R.string.error_generic, errorMessageRes(IllegalStateException("x")))
    }
}
