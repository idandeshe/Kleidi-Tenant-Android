// Shared test helpers — the scripted fake server of the staff apps' API tests.

package one.lab5.kleidi.tenant.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import one.lab5.kleidi.tenant.api.models.SessionTokens
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/** A fake server: routes each request to a handler and records what was sent. */
class ScriptedTransport(private val handler: suspend (HttpRequest) -> Pair<Int, String>) : HttpTransport {
    val requests = CopyOnWriteArrayList<HttpRequest>()

    fun requestsTo(path: String): List<HttpRequest> = requests.filter { it.path == path }

    override suspend fun send(request: HttpRequest): HttpResponse {
        requests += request
        val (status, body) = handler(request)
        return HttpResponse(status, body.encodeToByteArray())
    }
}

val HttpRequest.path: String get() = url.toHttpUrl().encodedPath
fun HttpRequest.header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
val HttpRequest.bodyJson: JsonObject? get() = body?.let { Json.parseToJsonElement(it.decodeToString()).jsonObject }

fun tokensJson(suffix: String, accessExpires: String = "2099-01-01T00:00:00.000Z") =
    """{"accessToken":"access-$suffix","accessTokenExpiresAt":"$accessExpires","refreshToken":"refresh-$suffix","refreshTokenExpiresAt":"2099-02-01T00:00:00.000Z"}"""

const val TENANT_JSON =
    """{"id":"t1","firstName":"Maria","lastName":"Papadopoulou","fullName":"Maria Papadopoulou","phone":"+306912345678","email":null,"language":"el"}"""

val LEASE_JSON = """
{
  "id": "l1",
  "property": { "id": "p1", "street": "Ermou", "unitNumber": "12", "floor": "floor_3", "postalCode": "10563", "cityName": "Athens" },
  "status": "active",
  "startDate": "2026-03-01T00:00:00.000Z",
  "endDate": "2029-02-28T00:00:00.000Z",
  "isPastEnd": false,
  "monthlyRent": 800,
  "upcomingRent": { "monthlyRent": 850, "effectiveFrom": "2027-03-01T00:00:00.000Z" },
  "currencyCode": "EUR",
  "depositAmount": 1600,
  "paymentDayOfMonth": 5,
  "paymentMethod": "bank_transfer",
  "noticePeriodMonths": 2,
  "noticeDeadline": "2028-12-31T00:00:00.000Z",
  "someFutureField": { "nested": true }
}
""".trimIndent()

val FAR_FUTURE: Instant = Instant.parse("2099-01-01T00:00:00Z")

fun storedTokens(suffix: String, accessExpiresAt: Instant = FAR_FUTURE) = SessionTokens(
    accessToken = "access-$suffix",
    accessTokenExpiresAt = accessExpiresAt,
    refreshToken = "refresh-$suffix",
    refreshTokenExpiresAt = FAR_FUTURE.plusSeconds(86_400),
)

const val BASE_URL = "https://kleidi.test"

/** A client signed in with `storedTokens("a")` unless [tokens] says otherwise. */
fun signedInClient(
    transport: HttpTransport,
    tokens: SessionTokens? = storedTokens("a"),
    clientBuild: Int = 1,
    now: () -> Instant = Instant::now,
) = ApiClient(BASE_URL, clientBuild, transport, InMemoryTokenStore(tokens), now)
