// Headers, Bearer, proactive and single-flight refresh, session end, 426 events, error bodies,
// the sign-in endpoints and logout — against a scripted server.

package one.lab5.kleidi.tenant.api

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import one.lab5.kleidi.tenant.api.endpoints.leases
import one.lab5.kleidi.tenant.api.endpoints.me
import one.lab5.kleidi.tenant.api.endpoints.updateLanguage
import one.lab5.kleidi.tenant.core.AppLanguage
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiClientTest {
    @Test
    fun requestCodeSendsClientHeadersWithoutAuth() = runTest {
        val transport = ScriptedTransport { 200 to """{"message":"If this number belongs to a tenant, a code was sent","expiresInSeconds":300}""" }
        val client = signedInClient(transport, tokens = null, clientBuild = 42)

        val response = client.requestCode("+306912345678")

        assertEquals(300, response.expiresInSeconds)
        val request = transport.requests.single()
        assertEquals("https://kleidi.test/api/tenant/auth/request-code", request.url)
        assertEquals("POST", request.method)
        assertEquals("tenant-android", request.header("x-client"))
        assertEquals("42", request.header("x-client-build"))
        assertNull(request.header("Authorization"))
        assertEquals("+306912345678", request.bodyJson?.get("phone")?.jsonPrimitive?.content)
    }

    @Test
    fun verifyCodeStoresTokensFromBody() = runTest {
        val transport = ScriptedTransport { 200 to """{"tenant":$TENANT_JSON,"message":"ok","tokens":${tokensJson("1")}}""" }
        val store = InMemoryTokenStore()
        val client = ApiClient(BASE_URL, 1, transport, store)

        val tenant = client.verifyCode("+306912345678", "123456")

        assertEquals("Maria Papadopoulou", tenant.fullName)
        assertEquals(AppLanguage.EL, tenant.appLanguage)
        assertNull(tenant.email)
        assertEquals("123456", transport.requests.single().bodyJson?.get("code")?.jsonPrimitive?.content)
        assertEquals("refresh-1", store.load()?.refreshToken)
        assertTrue(client.hasStoredSession)
    }

    @Test
    fun wrongCodeIsUnauthorizedWithItsCodeAndKeepsNoSession() = runTest {
        val transport = ScriptedTransport { 401 to """{"error":"The code is not correct","code":"INVALID_CODE"}""" }
        val client = signedInClient(transport, tokens = null)

        val error = assertFailsWith<ApiError.Unauthorized> { client.verifyCode("+306912345678", "000000") }

        assertEquals(TenantErrorCode.INVALID_CODE, error.code)
        assertEquals("The code is not correct", error.serverMessage)
        assertFalse(client.hasStoredSession)
    }

    @Test
    fun notATenantIsForbiddenWithItsCode() = runTest {
        val transport = ScriptedTransport { 403 to """{"error":"This number is not linked to a lease","code":"NOT_A_TENANT"}""" }
        val client = signedInClient(transport, tokens = null)

        val error = assertFailsWith<ApiError.Forbidden> { client.verifyCode("+306912345678", "123456") }

        assertEquals(TenantErrorCode.NOT_A_TENANT, error.code)
    }

    @Test
    fun smsNotConfiguredIsA503() = runTest {
        val transport = ScriptedTransport { 503 to """{"error":"SMS is not configured","code":"SMS_NOT_CONFIGURED"}""" }
        val client = signedInClient(transport, tokens = null)

        val error = assertFailsWith<ApiError.Http> { client.requestCode("+306912345678") }

        assertEquals(503, error.status)
        assertEquals(TenantErrorCode.SMS_NOT_CONFIGURED, error.code)
    }

    @Test
    fun badPhoneCarriesMessageAndField() = runTest {
        val transport = ScriptedTransport { 400 to """{"error":"Enter a valid phone number","field":"phone"}""" }
        val client = signedInClient(transport, tokens = null)

        val error = assertFailsWith<ApiError.Http> { client.requestCode("abc") }

        assertEquals(ApiError.Http(400, "Enter a valid phone number", null, "phone"), error)
    }

    @Test
    fun authenticatedRequestUsesBearer() = runTest {
        val transport = ScriptedTransport { 200 to """{"data":[$LEASE_JSON]}""" }
        val client = signedInClient(transport)

        val leases = client.leases()

        assertEquals("l1", leases.single().id)
        val request = transport.requests.single()
        assertEquals("https://kleidi.test/api/tenant/leases", request.url)
        assertEquals("Bearer access-a", request.header("Authorization"))
    }

    @Test
    fun notSignedInWithoutStoredTokens() = runTest {
        val transport = ScriptedTransport { 200 to "{}" }
        val client = signedInClient(transport, tokens = null)
        assertFailsWith<ApiError.NotSignedIn> { client.me() }
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun refreshesProactivelyBeforeExpiry() = runTest {
        val now = Instant.ofEpochSecond(1_800_000_000)
        val transport = ScriptedTransport { request ->
            if (request.path == "/api/tenant/auth/refresh") 200 to """{"message":"ok","tokens":${tokensJson("b")}}"""
            else 200 to TENANT_JSON
        }
        val store = InMemoryTokenStore(storedTokens("a", accessExpiresAt = now.plusSeconds(30)))
        val client = ApiClient(BASE_URL, 1, transport, store) { now }

        client.me()

        val refresh = transport.requestsTo("/api/tenant/auth/refresh").single()
        assertEquals("refresh-a", refresh.bodyJson?.get("refreshToken")?.jsonPrimitive?.content)
        assertNull(refresh.header("Authorization"))
        val call = transport.requestsTo("/api/tenant/me").single()
        assertEquals("Bearer access-b", call.header("Authorization"))
        assertEquals("refresh-b", store.load()?.refreshToken)
    }

    @Test
    fun concurrent401sShareOneRefresh() = runTest {
        val transport = ScriptedTransport { request ->
            when {
                request.path == "/api/tenant/auth/refresh" -> {
                    delay(50)
                    200 to """{"message":"ok","tokens":${tokensJson("b")}}"""
                }
                request.header("Authorization") == "Bearer access-a" -> 401 to """{"error":"Invalid or expired token"}"""
                else -> 200 to TENANT_JSON
            }
        }
        val client = signedInClient(transport)

        val results = (0 until 5).map { async { client.me() } }.awaitAll()

        results.forEach { assertEquals("t1", it.id) }
        assertEquals(1, transport.requestsTo("/api/tenant/auth/refresh").size)
    }

    @Test
    fun rejectedRefreshEndsSession() = runTest {
        val transport = ScriptedTransport { request ->
            if (request.path == "/api/tenant/auth/refresh") 401 to """{"error":"Token revoked","code":"TOKEN_REVOKED"}"""
            else 401 to """{"error":"Invalid or expired token"}"""
        }
        val store = InMemoryTokenStore(storedTokens("a"))
        val client = ApiClient(BASE_URL, 1, transport, store)
        val event = async(start = CoroutineStart.UNDISPATCHED) { client.events.first() }

        val error = assertFailsWith<ApiError.Unauthorized> { client.me() }

        assertEquals(ApiError.Unauthorized("TOKEN_REVOKED", "Token revoked"), error)
        assertEquals(ApiEvent.SessionEnded("TOKEN_REVOKED"), event.await())
        assertNull(store.load())
        assertFailsWith<ApiError.NotSignedIn> { client.me() }
    }

    @Test
    fun noLongerATenantEndsSession() = runTest {
        val transport = ScriptedTransport { 403 to """{"error":"Not a tenant","code":"NOT_A_TENANT"}""" }
        val store = InMemoryTokenStore(storedTokens("a"))
        val client = ApiClient(BASE_URL, 1, transport, store)
        val event = async(start = CoroutineStart.UNDISPATCHED) { client.events.first() }

        assertFailsWith<ApiError.Forbidden> { client.leases() }

        assertEquals(ApiEvent.SessionEnded("NOT_A_TENANT"), event.await())
        assertNull(store.load())
    }

    @Test
    fun serverErrorDuringRefreshKeepsSession() = runTest {
        val now = Instant.ofEpochSecond(1_800_000_000)
        val transport = ScriptedTransport { 503 to """{"error":"Service unavailable"}""" }
        val store = InMemoryTokenStore(storedTokens("a", accessExpiresAt = now))
        val client = ApiClient(BASE_URL, 1, transport, store) { now }

        val error = assertFailsWith<ApiError.Http> { client.me() }

        assertEquals(ApiError.Http(503, "Service unavailable", null, null), error)
        assertNotNull(store.load())
    }

    @Test
    fun updateRequiredIsAnEventAndAnError() = runTest {
        val transport = ScriptedTransport { 426 to """{"error":"App update required","code":"APP_UPDATE_REQUIRED","minSupportedBuild":7}""" }
        val client = signedInClient(transport, clientBuild = 3)
        val event = async(start = CoroutineStart.UNDISPATCHED) { client.events.first() }

        val error = assertFailsWith<ApiError.UpdateRequired> { client.me() }

        assertEquals(7, error.minSupportedBuild)
        assertEquals(ApiEvent.UpdateRequired(7), event.await())
    }

    @Test
    fun configIsPublicAndGatesOldBuilds() = runTest {
        val transport = ScriptedTransport {
            200 to """{"apiVersion":1,"serverVersion":"2.3.0","smsConfigured":false,"turnstileSiteKey":null,"ios":{"minSupportedBuild":0,"appStoreUrl":null},"android":{"minSupportedBuild":5,"playStoreUrl":"https://play.google.com/store/apps/details?id=one.lab5.kleidi.tenant"}}"""
        }
        val client = signedInClient(transport, tokens = null)

        val config = client.config()

        assertNull(transport.requests.single().header("Authorization"))
        assertFalse(config.smsConfigured)
        assertTrue(config.requiresUpdate(4))
        assertFalse(config.requiresUpdate(5))
        assertEquals("https://play.google.com/store/apps/details?id=one.lab5.kleidi.tenant", config.android?.playStoreUrl)
    }

    @Test
    fun undecodableBodyIsADecodingError() = runTest {
        val transport = ScriptedTransport { 200 to """{"apiVersion":"one"}""" }
        val client = signedInClient(transport)
        assertFailsWith<ApiError.Decoding> { client.config() }
    }

    @Test
    fun noAnswerIsATransportError() = runTest {
        val transport = ScriptedTransport { throw IOException("Unable to resolve host") }
        val client = signedInClient(transport)
        val error = assertFailsWith<ApiError.Transport> { client.me() }
        assertEquals("Unable to resolve host", error.detail)
    }

    @Test
    fun updateLanguagePatchesMe() = runTest {
        val transport = ScriptedTransport { 200 to TENANT_JSON.replace("\"el\"", "\"en\"") }
        val client = signedInClient(transport)

        val tenant = client.updateLanguage(AppLanguage.EN)

        assertEquals(AppLanguage.EN, tenant.appLanguage)
        val request = transport.requests.single()
        assertEquals("PATCH", request.method)
        assertEquals("/api/tenant/me", request.path)
        assertEquals("en", request.bodyJson?.get("language")?.jsonPrimitive?.content)
    }

    @Test
    fun logoutSendsRefreshTokenAndClearsEvenWhenOffline() = runTest {
        val transport = ScriptedTransport { throw IOException("offline") }
        val store = InMemoryTokenStore(storedTokens("a"))
        val client = ApiClient(BASE_URL, 1, transport, store)

        client.logout()

        val request = transport.requestsTo("/api/tenant/auth/logout").single()
        assertEquals("refresh-a", request.bodyJson?.get("refreshToken")?.jsonPrimitive?.content)
        assertNull(request.header("Authorization"))
        assertNull(store.load())
        assertFalse(client.hasStoredSession)
    }

    @Test
    fun logoutAllUsesBearerAndClears() = runTest {
        val transport = ScriptedTransport { 200 to """{"message":"ok","revokedCount":3}""" }
        val store = InMemoryTokenStore(storedTokens("a"))
        val client = ApiClient(BASE_URL, 1, transport, store)

        client.logoutAll()

        val request = transport.requestsTo("/api/tenant/auth/logout-all").single()
        assertEquals("Bearer access-a", request.header("Authorization"))
        assertNull(store.load())
    }
}
