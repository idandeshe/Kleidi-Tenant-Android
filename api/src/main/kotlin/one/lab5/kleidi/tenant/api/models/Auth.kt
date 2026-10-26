// Mirrors server/types/tenantAuth.types.ts (TenantSessionTenant, TenantNativeTokens,
// TenantConfigResponse and the request / response bodies of /api/tenant/auth/*).
@file:UseSerializers(ApiInstantSerializer::class)

package one.lab5.kleidi.tenant.api.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import one.lab5.kleidi.tenant.api.ApiInstantSerializer
import one.lab5.kleidi.tenant.core.AppLanguage
import java.time.Instant

@Serializable
data class SessionTokens(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
)

/** The signed-in tenant, as the tenant app sees itself (`verify-code`, `/auth/session`, `/me`). */
@Serializable
data class Tenant(
    val id: String,
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val phone: String,
    val email: String? = null,
    /** `el` | `en`; kept raw so a new server value does not break decoding. */
    val language: String,
) {
    val appLanguage: AppLanguage? get() = AppLanguage.fromCode(language)
}

@Serializable
data class SessionResponse(val tenant: Tenant, val expiresAt: Instant)

/** GET /api/tenant/config — public; read before sign-in for the forced update and the SMS switch. */
@Serializable
data class TenantConfig(
    /** Bumped by the server only on a change that breaks the tenant apps (TENANT_API_VERSION). */
    val apiVersion: Int,
    val serverVersion: String,
    /** False = the server cannot send codes; sign-in would answer 503. */
    val smsConfigured: Boolean = true,
    val turnstileSiteKey: String? = null,
    val ios: Ios? = null,
    val android: Android? = null,
) {
    @Serializable
    data class Ios(val minSupportedBuild: Int = 0, val appStoreUrl: String? = null)

    @Serializable
    data class Android(
        /** 0 = no minimum. */
        val minSupportedBuild: Int = 0,
        val playStoreUrl: String? = null,
    )

    fun requiresUpdate(build: Int): Boolean {
        val min = android?.minSupportedBuild ?: 0
        return min > 0 && build < min
    }
}

/** `POST /auth/request-code` → always the same body, whether or not the number is a tenant's. */
@Serializable
data class RequestCodeResponse(val message: String? = null, val expiresInSeconds: Int = 300)

@Serializable
internal data class RequestCodeRequest(val phone: String)

@Serializable
internal data class VerifyCodeRequest(val phone: String, val code: String)

@Serializable
internal data class LoginResponse(val tenant: Tenant, val message: String? = null, val tokens: SessionTokens)

@Serializable
internal data class RefreshRequest(val refreshToken: String)

@Serializable
internal data class RefreshResponse(val message: String? = null, val tokens: SessionTokens)

@Serializable
internal data class UpdateMeRequest(val language: String)
