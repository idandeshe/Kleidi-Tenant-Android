package one.lab5.kleidi.tenant.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import one.lab5.kleidi.tenant.api.models.LoginResponse
import one.lab5.kleidi.tenant.api.models.RefreshRequest
import one.lab5.kleidi.tenant.api.models.RefreshResponse
import one.lab5.kleidi.tenant.api.models.RequestCodeRequest
import one.lab5.kleidi.tenant.api.models.RequestCodeResponse
import one.lab5.kleidi.tenant.api.models.SessionResponse
import one.lab5.kleidi.tenant.api.models.SessionTokens
import one.lab5.kleidi.tenant.api.models.Tenant
import one.lab5.kleidi.tenant.api.models.TenantConfig
import one.lab5.kleidi.tenant.api.models.VerifyCodeRequest
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** Things the app shell reacts to, wherever in the app the triggering request was made. */
sealed interface ApiEvent {
    /** The session is gone (refresh rejected, revoked, no longer a tenant). Show the sign-in screen. */
    data class SessionEnded(val reason: String?) : ApiEvent

    /** 426 from the server. Show the forced-update screen. */
    data class UpdateRequired(val minSupportedBuild: Int?) : ApiEvent
}

enum class HttpMethod { GET, POST, PATCH, PUT, DELETE }

class ApiRequest(
    val method: HttpMethod = HttpMethod.GET,
    /** Path under `/api`, e.g. `/tenant/leases/123`. Segments are percent-encoded by the client. */
    val path: String,
    val query: List<Pair<String, String>> = emptyList(),
    val body: ByteArray? = null,
    /** Defaults to `application/json` when there is a body. */
    val contentType: String? = null,
    /** Sends `Authorization: Bearer` and refreshes the session when needed. */
    val requiresAuth: Boolean = true,
)

/**
 * The Kleidi tenant API as the native app sees it (server repo: docs/tenant-app.md):
 * `x-client: tenant-android` + `x-client-build` on every request, `Authorization: Bearer`
 * for the 15-minute access token, and the refresh token sent in the body of
 * `/tenant/auth/refresh` and `/tenant/auth/logout`. No cookies, no CSRF.
 */
class ApiClient(
    val baseUrl: String,
    val clientBuild: Int,
    private val transport: HttpTransport = OkHttpTransport(),
    private val tokenStore: TokenStore,
    private val now: () -> Instant = Instant::now,
) {
    private val eventSink = MutableSharedFlow<ApiEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ApiEvent> = eventSink.asSharedFlow()

    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var tokens: SessionTokens? = runCatching { tokenStore.load() }.getOrNull()
    private var refreshJob: Deferred<SessionTokens>? = null

    /** A session was stored by an earlier launch (it may still have expired server-side). */
    val hasStoredSession: Boolean get() = tokens != null

    // Sending

    suspend fun <T> send(request: ApiRequest, deserializer: DeserializationStrategy<T>): T {
        val response = sendData(request)
        return try {
            KleidiJson.decodeFromString(deserializer, response.body.decodeToString())
        } catch (e: SerializationException) {
            throw ApiError.Decoding(e.message ?: e.toString())
        } catch (e: IllegalArgumentException) {
            throw ApiError.Decoding(e.message ?: e.toString())
        }
    }

    suspend fun sendData(request: ApiRequest): HttpResponse = sendData(request, isRetry = false)

    private suspend fun sendData(request: ApiRequest, isRetry: Boolean): HttpResponse {
        val accessToken = if (request.requiresAuth) validAccessToken() else null
        val response = perform(request, accessToken)

        return when {
            response.status in 200..299 -> response
            response.status == 401 && request.requiresAuth && !isRetry -> {
                // The access token was rejected before its expiry (server restart with a new
                // secret, clock skew…). One refresh, one retry — as the web client does.
                refreshSession()
                sendData(request, isRetry = true)
            }
            response.status == 401 -> {
                val body = decodeError(response.body)
                if (request.requiresAuth) endSession(body?.code)
                throw ApiError.Unauthorized(body?.code, body?.text ?: "Authentication required")
            }
            response.status == 403 -> {
                val body = decodeError(response.body)
                // A signed-in tenant that is no longer eligible: the session is over.
                if (request.requiresAuth && body?.code == TenantErrorCode.NOT_A_TENANT) endSession(body.code)
                throw ApiError.Forbidden(body?.code, body?.text ?: "Forbidden")
            }
            response.status == 426 -> {
                val min = decodeError(response.body)?.minSupportedBuild
                eventSink.tryEmit(ApiEvent.UpdateRequired(min))
                throw ApiError.UpdateRequired(min)
            }
            else -> throw httpError(response)
        }
    }

    private suspend fun perform(request: ApiRequest, accessToken: String?): HttpResponse {
        val url = "${baseUrl.trimEnd('/')}/api".toHttpUrl().newBuilder().apply {
            val path = request.path.trim('/')
            if (path.isNotEmpty()) addPathSegments(path)
            request.query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build().toString()

        val headers = buildMap {
            put("x-client", CLIENT)
            put("x-client-build", clientBuild.toString())
            put("Accept", "application/json")
            if (accessToken != null) put("Authorization", "Bearer $accessToken")
            if (request.body != null) put("Content-Type", request.contentType ?: "application/json")
        }

        return try {
            transport.send(HttpRequest(request.method.name, url, headers, request.body))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            throw e
        } catch (e: IOException) {
            throw ApiError.Transport(e.message ?: e.toString())
        }
    }

    // Session

    private suspend fun validAccessToken(): String {
        val current = tokens ?: throw ApiError.NotSignedIn
        if (Duration.between(now(), current.accessTokenExpiresAt) > REFRESH_LEEWAY) return current.accessToken
        return refreshSession().accessToken
    }

    /**
     * Single flight: concurrent callers share one `/tenant/auth/refresh`, because the server
     * rotates the refresh token and a second use of the old one is rejected (`TOKEN_REVOKED`).
     */
    suspend fun refreshSession(): SessionTokens {
        val job = lock.withLock {
            refreshJob ?: run {
                val current = tokens ?: throw ApiError.NotSignedIn
                scope.async {
                    try {
                        performRefresh(current.refreshToken)
                    } finally {
                        lock.withLock { refreshJob = null }
                    }
                }.also { refreshJob = it }
            }
        }
        return job.await()
    }

    private suspend fun performRefresh(refreshToken: String): SessionTokens {
        val body = KleidiJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken)).encodeToByteArray()
        val response = perform(ApiRequest(HttpMethod.POST, "/tenant/auth/refresh", body = body, requiresAuth = false), null)

        return when (response.status) {
            in 200..299 -> {
                val decoded = try {
                    KleidiJson.decodeFromString(RefreshResponse.serializer(), response.body.decodeToString())
                } catch (e: SerializationException) {
                    throw ApiError.Decoding(e.message ?: e.toString())
                }
                store(decoded.tokens)
                decoded.tokens
            }
            426 -> {
                val min = decodeError(response.body)?.minSupportedBuild
                eventSink.tryEmit(ApiEvent.UpdateRequired(min))
                throw ApiError.UpdateRequired(min)
            }
            400, 401, 403 -> {
                // The refresh token itself was refused: expired, revoked, or the tenant is no longer eligible.
                val error = decodeError(response.body)
                endSession(error?.code)
                throw ApiError.Unauthorized(error?.code, error?.text ?: "Session expired")
            }
            // 429 / 5xx: keep the session, the next request will try again.
            else -> throw httpError(response)
        }
    }

    private fun store(newTokens: SessionTokens) {
        tokens = newTokens
        runCatching { tokenStore.save(newTokens) }
    }

    private fun endSession(reason: String?) {
        if (tokens == null) return
        tokens = null
        runCatching { tokenStore.clear() }
        eventSink.tryEmit(ApiEvent.SessionEnded(reason))
    }

    private fun clearLocal() {
        tokens = null
        runCatching { tokenStore.clear() }
    }

    // Auth endpoints

    /** `GET /tenant/config` — public; checked before sign-in for the forced update and the SMS switch. */
    suspend fun config(): TenantConfig =
        send(ApiRequest(HttpMethod.GET, "/tenant/config", requiresAuth = false), TenantConfig.serializer())

    /**
     * `POST /tenant/auth/request-code { phone }` — the answer is the same whether or not the number
     * belongs to a tenant. 400 `{ error, field }` for a malformed phone, 503 when SMS is not configured.
     */
    suspend fun requestCode(phone: String): RequestCodeResponse {
        val body = KleidiJson.encodeToString(RequestCodeRequest.serializer(), RequestCodeRequest(phone)).encodeToByteArray()
        return send(ApiRequest(HttpMethod.POST, "/tenant/auth/request-code", body = body, requiresAuth = false), RequestCodeResponse.serializer())
    }

    /**
     * `POST /tenant/auth/verify-code { phone, code }` — the tokens come back in the body.
     * 401 `INVALID_CODE` / `CODE_EXPIRED` / `TOO_MANY_ATTEMPTS`, 403 `NOT_A_TENANT`.
     */
    suspend fun verifyCode(phone: String, code: String): Tenant {
        val body = KleidiJson.encodeToString(VerifyCodeRequest.serializer(), VerifyCodeRequest(phone, code)).encodeToByteArray()
        val response = send(ApiRequest(HttpMethod.POST, "/tenant/auth/verify-code", body = body, requiresAuth = false), LoginResponse.serializer())
        store(response.tokens)
        return response.tenant
    }

    suspend fun session(): SessionResponse =
        send(ApiRequest(HttpMethod.GET, "/tenant/auth/session"), SessionResponse.serializer())

    /** Revokes this device's refresh token. Local sign-out happens even when offline. */
    suspend fun logout() {
        tokens?.refreshToken?.let { refreshToken ->
            val body = KleidiJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken)).encodeToByteArray()
            runCatching { perform(ApiRequest(HttpMethod.POST, "/tenant/auth/logout", body = body, requiresAuth = false), null) }
        }
        clearLocal()
    }

    /** Revokes every session of this tenant — other phones and the web included. */
    suspend fun logoutAll() {
        try {
            sendData(ApiRequest(HttpMethod.POST, "/tenant/auth/logout-all", body = "{}".encodeToByteArray()))
        } finally {
            clearLocal()
        }
    }

    // Helpers

    private fun decodeError(body: ByteArray): ServerErrorBody? =
        runCatching { KleidiJson.decodeFromString(ServerErrorBody.serializer(), body.decodeToString()) }.getOrNull()

    private fun httpError(response: HttpResponse): ApiError {
        val body = decodeError(response.body)
        return ApiError.Http(response.status, body?.text ?: reasonPhrase(response.status), body?.code, body?.field)
    }

    companion object {
        /** `x-client` of this app (server/types/auth.types.ts AuthClient). */
        const val CLIENT = "tenant-android"

        /** Refresh this long before the access token expires instead of waiting for a 401. */
        val REFRESH_LEEWAY: Duration = Duration.ofSeconds(60)

        private fun reasonPhrase(status: Int): String = when (status) {
            400 -> "Bad request"
            404 -> "Not found"
            409 -> "Conflict"
            429 -> "Too many requests"
            503 -> "Service unavailable"
            in 500..599 -> "Server error"
            else -> "HTTP $status"
        }
    }
}
