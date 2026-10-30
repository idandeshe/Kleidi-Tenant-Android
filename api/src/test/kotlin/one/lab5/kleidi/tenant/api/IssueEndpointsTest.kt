// The multipart report and reply (part order, several photos), the list / detail / photo calls
// and the 409 of a reply to a resolved issue.

package one.lab5.kleidi.tenant.api

import kotlinx.coroutines.test.runTest
import one.lab5.kleidi.tenant.api.endpoints.PhotoUpload
import one.lab5.kleidi.tenant.api.endpoints.ReportIssueInput
import one.lab5.kleidi.tenant.api.endpoints.buildReplyForm
import one.lab5.kleidi.tenant.api.endpoints.buildReportIssueForm
import one.lab5.kleidi.tenant.api.endpoints.issue
import one.lab5.kleidi.tenant.api.endpoints.issuePhoto
import one.lab5.kleidi.tenant.api.endpoints.issues
import one.lab5.kleidi.tenant.api.endpoints.replyToIssue
import one.lab5.kleidi.tenant.api.endpoints.reportIssue
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IssueEndpointsTest {
    private val input = ReportIssueInput(category = "plumbing", description = "Kitchen tap dripping", isUrgent = true)
    private val photo1 = PhotoUpload(byteArrayOf(1, 2, 3), "Photo 1.jpg", "image/jpeg")
    private val photo2 = PhotoUpload(byteArrayOf(4, 5), "Photo 2.png", "image/png")

    private fun partNames(body: String) = Regex("name=\"([a-zA-Z]+)\"").findAll(body).map { it.groupValues[1] }.toList()

    @Test
    fun textPartsPrecedeEveryPhoto() {
        val form = buildReportIssueForm(input, listOf(photo1, photo2), MultipartForm("b"))
        val body = form.body.decodeToString(throwOnInvalidSequence = false)

        assertEquals("multipart/form-data; boundary=b", form.contentType)
        assertEquals(listOf("category", "description", "isUrgent", "photo", "photo"), partNames(body))
        assertTrue(body.contains("name=\"category\"\r\n\r\nplumbing\r\n"))
        assertTrue(body.contains("name=\"description\"\r\n\r\nKitchen tap dripping\r\n"))
        assertTrue(body.contains("name=\"isUrgent\"\r\n\r\ntrue\r\n"))
        assertTrue(body.contains("name=\"photo\"; filename=\"Photo 1.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n\u0001\u0002\u0003\r\n"))
        assertTrue(body.contains("name=\"photo\"; filename=\"Photo 2.png\"\r\nContent-Type: image/png\r\n\r\n\u0004\u0005\r\n--b--\r\n"))
    }

    @Test
    fun aReportWithoutPhotosHasOnlyTextParts() {
        val body = buildReportIssueForm(ReportIssueInput("other", "Noise", isUrgent = false), emptyList(), MultipartForm("b")).body.decodeToString()
        assertEquals(listOf("category", "description", "isUrgent"), partNames(body))
        assertTrue(body.contains("name=\"isUrgent\"\r\n\r\nfalse\r\n"))
    }

    @Test
    fun aReplyLeavesOutABlankBodyAndSendsUpToThreePhotos() {
        val withText = buildReplyForm(" Yes, all morning. ", listOf(photo1), MultipartForm("b")).body.decodeToString(throwOnInvalidSequence = false)
        assertEquals(listOf("body", "photo"), partNames(withText))
        assertTrue(withText.contains("name=\"body\"\r\n\r\nYes, all morning.\r\n"))

        val photosOnly = buildReplyForm("   ", listOf(photo1, photo2, photo1), MultipartForm("b")).body.decodeToString(throwOnInvalidSequence = false)
        assertEquals(listOf("photo", "photo", "photo"), partNames(photosOnly))
    }

    @Test
    fun reportIssuePostsMultipartWithBearerAndDecodesTheDetail() = runTest {
        val transport = ScriptedTransport { 201 to ISSUE_DETAIL_JSON }
        val created = signedInClient(transport).reportIssue("l1", input, listOf(photo1))

        assertEquals("i1", created.id)
        assertEquals(3, created.messages.size)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/tenant/leases/l1/issues", request.path)
        assertEquals("Bearer access-a", request.header("Authorization"))
        assertTrue(request.header("Content-Type")!!.startsWith("multipart/form-data; boundary=kleidi-"))
        assertEquals("tenant-android", request.header("x-client"))
    }

    @Test
    fun theListIsReadWithAnOptionalLeaseFilter() = runTest {
        val transport = ScriptedTransport { 200 to ISSUES_JSON }
        val client = signedInClient(transport)

        assertEquals(listOf("i1", "i2", "i3"), client.issues().map { it.id })
        assertEquals("/api/tenant/issues", transport.requests[0].path)
        assertTrue(transport.requests[0].url.endsWith("/api/tenant/issues"))

        client.issues(leaseId = "l1")
        assertTrue(transport.requests[1].url.endsWith("/api/tenant/issues?leaseId=l1"))
    }

    @Test
    fun theDetailIsRead() = runTest {
        val transport = ScriptedTransport { request ->
            when (request.path) {
                "/api/tenant/issues/i1" -> 200 to ISSUE_DETAIL_JSON
                else -> 404 to """{"error":"Not found"}"""
            }
        }
        val detail = signedInClient(transport).issue("i1")
        assertEquals("Kitchen tap dripping", detail.summary)
        assertEquals("GET", transport.requests.single().method)
    }

    @Test
    fun aReplyToAResolvedIssueIsA409() = runTest {
        val transport = ScriptedTransport { 409 to """{"error":"The issue is resolved; replies are closed"}""" }
        val error = assertFailsWith<ApiError.Http> { signedInClient(transport).replyToIssue("i1", "Still leaking", emptyList()) }
        assertEquals(409, error.status)
        assertEquals("/api/tenant/issues/i1/messages", transport.requests.single().path)
    }

    @Test
    fun aReplyDecodesTheCreatedMessage() = runTest {
        val transport = ScriptedTransport { 201 to ISSUE_MESSAGE_JSON }
        val message = signedInClient(transport).replyToIssue("i1", "Yes, I will be home all morning.", emptyList())
        assertEquals("m3", message.id)
        assertTrue(message.isFromTenant)
    }

    @Test
    fun thePhotoComesBackWithItsContentType() = runTest {
        val transport = object : HttpTransport {
            override suspend fun send(request: HttpRequest): HttpResponse {
                assertEquals("/api/tenant/issues/i1/photos/d1", request.url.removePrefix(BASE_URL))
                assertEquals("Bearer access-a", request.headers["Authorization"])
                return HttpResponse(200, byteArrayOf(-1, -40, -1), mapOf("content-type" to "image/jpeg"))
            }
        }
        val photo = signedInClient(transport).issuePhoto("i1", "d1")
        assertContentEquals(byteArrayOf(-1, -40, -1), photo.bytes)
        assertEquals("image/jpeg", photo.mimeType)
    }
}
