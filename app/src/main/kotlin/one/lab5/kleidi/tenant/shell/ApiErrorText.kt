// Localized copy for every way a request can fail. :api is pure Kotlin and holds no strings;
// the screens call `error.localizedMessage()` and never show a server message verbatim
// (the server speaks English only).

package one.lab5.kleidi.tenant.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.ApiError
import one.lab5.kleidi.tenant.api.TenantErrorCode

/** The string resource that explains [error] to the tenant. */
fun errorMessageRes(error: Throwable): Int {
    if (error !is ApiError) return R.string.error_generic
    return when (error) {
        ApiError.NotSignedIn -> R.string.error_not_signed_in
        is ApiError.Unauthorized -> when (error.code) {
            TenantErrorCode.INVALID_CODE -> R.string.error_invalid_code
            TenantErrorCode.CODE_EXPIRED -> R.string.error_code_expired
            TenantErrorCode.TOO_MANY_ATTEMPTS -> R.string.error_too_many_attempts
            TenantErrorCode.NOT_A_TENANT -> R.string.error_not_a_tenant
            else -> R.string.notice_session_ended
        }
        is ApiError.Forbidden -> if (error.code == TenantErrorCode.NOT_A_TENANT) R.string.error_not_a_tenant else R.string.error_generic
        is ApiError.UpdateRequired -> R.string.error_update_required
        is ApiError.Http -> when {
            error.code == TenantErrorCode.SMS_NOT_CONFIGURED || error.status == 503 -> R.string.error_sms_not_configured
            error.status == 429 -> R.string.error_rate_limited
            error.status == 400 && error.field == "phone" -> R.string.error_invalid_phone
            error.status == 400 -> R.string.error_generic
            error.status >= 500 -> R.string.error_server
            else -> R.string.error_generic
        }
        is ApiError.Transport -> R.string.error_offline
        is ApiError.Decoding -> R.string.error_decoding
    }
}

@Composable
@ReadOnlyComposable
fun Throwable.localizedMessage(): String = stringResource(errorMessageRes(this))
