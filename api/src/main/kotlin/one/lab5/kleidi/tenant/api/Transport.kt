package one.lab5.kleidi.tenant.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One HTTP exchange, independent of OkHttp so tests can script the server (see ScriptedTransport). */
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
)

class HttpResponse(val status: Int, val body: ByteArray, val headers: Map<String, String> = emptyMap())

fun interface HttpTransport {
    /** Throws an [IOException] when there is no HTTP answer at all. */
    suspend fun send(request: HttpRequest): HttpResponse
}

class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : HttpTransport {
    override suspend fun send(request: HttpRequest): HttpResponse {
        val contentType = request.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value
        val body = request.body?.toRequestBody(contentType?.toMediaType())
            ?: if (request.method == "POST" || request.method == "PUT" || request.method == "PATCH") ByteArray(0).toRequestBody() else null
        val okRequest = Request.Builder()
            .url(request.url.toHttpUrl())
            .headers(request.headers.toHeaders())
            .method(request.method, body)
            .build()
        val response = client.newCall(okRequest).await()
        return withContext(Dispatchers.IO) {
            response.use { r ->
                HttpResponse(r.code, r.body.bytes(), r.headers.toMap())
            }
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
    continuation.invokeOnCancellation { runCatching { cancel() } }
}
