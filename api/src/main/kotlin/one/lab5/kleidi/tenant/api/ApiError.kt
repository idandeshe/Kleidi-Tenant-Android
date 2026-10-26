package one.lab5.kleidi.tenant.api

import kotlinx.serialization.Serializable

/** Error codes the server sends in `{ code }` (server/types/tenantAuth.types.ts TENANT_AUTH_ERROR_CODES + the update gate). */
object TenantErrorCode {
    const val INVALID_CODE = "INVALID_CODE"
    const val CODE_EXPIRED = "CODE_EXPIRED"
    const val TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS"
    const val NOT_A_TENANT = "NOT_A_TENANT"
    const val TOKEN_EXPIRED = "TOKEN_EXPIRED"
    const val TOKEN_REVOKED = "TOKEN_REVOKED"
    const val SMS_NOT_CONFIGURED = "SMS_NOT_CONFIGURED"
    const val APP_UPDATE_REQUIRED = "APP_UPDATE_REQUIRED"
}

/**
 * What a request can fail with. The app turns these into localized copy (`ApiErrorText` in :app);
 * this module is pure Kotlin and holds no user-facing strings beyond what the server sent.
 */
sealed class ApiError(message: String) : Exception(message) {
    /** No stored session: the tenant has to sign in. */
    data object NotSignedIn : ApiError("Not signed in")

    /** 401 the session could not recover from (refresh refused) or a rejected code / phone. */
    data class Unauthorized(val code: String?, val serverMessage: String) : ApiError(serverMessage)

    /** 403 — e.g. `NOT_A_TENANT`: the number is no longer linked to an open lease. */
    data class Forbidden(val code: String?, val serverMessage: String) : ApiError(serverMessage)

    /** 426 — this build is older than the server's TENANT_ANDROID_MIN_SUPPORTED_BUILD. */
    data class UpdateRequired(val minSupportedBuild: Int?) : ApiError("App update required")

    /** Any other non-2xx answer. [serverMessage] is the server's `message` or `error` field. */
    data class Http(val status: Int, val serverMessage: String, val code: String?, val field: String?) :
        ApiError(serverMessage)

    /** The request never got an HTTP answer (offline, timeout, TLS…). */
    data class Transport(val detail: String) : ApiError(detail)

    /** The server answered 2xx with a body this build cannot decode. */
    data class Decoding(val detail: String) : ApiError(detail)

    /** The HTTP status behind the error, when there was one. */
    val statusCode: Int?
        get() = when (this) {
            is Unauthorized -> 401
            is Forbidden -> 403
            is UpdateRequired -> 426
            is Http -> status
            else -> null
        }

    /** The server's `code`, when there was one. */
    val serverCode: String?
        get() = when (this) {
            is Unauthorized -> code
            is Forbidden -> code
            is Http -> code
            is UpdateRequired -> TenantErrorCode.APP_UPDATE_REQUIRED
            else -> null
        }
}

/** `{ error, message?, code?, field?, minSupportedBuild? }` — the server's error body. */
@Serializable
internal data class ServerErrorBody(
    val error: String? = null,
    val message: String? = null,
    val code: String? = null,
    val field: String? = null,
    val minSupportedBuild: Int? = null,
) {
    val text: String? get() = message ?: error
}
