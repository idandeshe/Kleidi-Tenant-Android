// Maintenance issues (tenant app phase 3): GET /api/tenant/issues, POST /api/tenant/leases/:id/issues,
// GET /api/tenant/issues/:id, POST /api/tenant/issues/:id/messages, GET /api/tenant/issues/:id/photos/:documentId.

package one.lab5.kleidi.tenant.api.endpoints

import one.lab5.kleidi.tenant.api.ApiClient
import one.lab5.kleidi.tenant.api.ApiRequest
import one.lab5.kleidi.tenant.api.HttpMethod
import one.lab5.kleidi.tenant.api.MultipartForm
import one.lab5.kleidi.tenant.api.models.TenantIssue
import one.lab5.kleidi.tenant.api.models.TenantIssueDetail
import one.lab5.kleidi.tenant.api.models.TenantIssueMessage
import one.lab5.kleidi.tenant.api.models.TenantIssuesResponse
import one.lab5.kleidi.tenant.core.IssueRules

/** A photo as it goes up: already JPEG / PNG / WebP (the app re-encodes everything else). */
class PhotoUpload(val bytes: ByteArray, val fileName: String, val mimeType: String)

/** The text fields of `POST /tenant/leases/:id/issues`. */
class ReportIssueInput(
    /** A raw category value (`IssueCategory.raw`). */
    val category: String,
    val description: String,
    val isUrgent: Boolean,
)

/** The bytes of an issue photo, with the `Content-Type` the server sent. */
class IssuePhoto(val bytes: ByteArray, val mimeType: String)

/** `GET /tenant/issues[?leaseId]` — the tenant's own issues: open first, then by last activity. */
suspend fun ApiClient.issues(leaseId: String? = null): List<TenantIssue> {
    val query = leaseId?.let { listOf("leaseId" to it) } ?: emptyList()
    return send(ApiRequest(HttpMethod.GET, "/tenant/issues", query = query), TenantIssuesResponse.serializer()).data
}

/**
 * `POST /tenant/leases/:id/issues` — "Report an issue". Multipart: the text fields first
 * (`category`, `description`, `isUrgent`), then 0–5 `photo` files. 201 → the issue with its
 * (empty) thread; 400 `{ error, field }`, 404 (not the tenant's lease), 413 (a photo too large),
 * 415 (type), 429 (5 per 10 minutes).
 */
suspend fun ApiClient.reportIssue(leaseId: String, input: ReportIssueInput, photos: List<PhotoUpload>): TenantIssueDetail {
    val form = buildReportIssueForm(input, photos)
    return send(
        ApiRequest(HttpMethod.POST, "/tenant/leases/$leaseId/issues", body = form.body, contentType = form.contentType),
        TenantIssueDetail.serializer(),
    )
}

/** The multipart body of [reportIssue]; separate so its part order is unit-tested. */
fun buildReportIssueForm(input: ReportIssueInput, photos: List<PhotoUpload>, form: MultipartForm = MultipartForm()): MultipartForm {
    form.addField(IssueRules.FIELD_CATEGORY, input.category)
    form.addField(IssueRules.FIELD_DESCRIPTION, input.description)
    form.addField(IssueRules.FIELD_IS_URGENT, IssueRules.isUrgentField(input.isUrgent))
    photos.forEach { form.addFile(IssueRules.FIELD_PHOTO, it.fileName, it.mimeType, it.bytes) }
    return form
}

/** `GET /tenant/issues/:id` — one own issue with its thread; the server marks it seen. */
suspend fun ApiClient.issue(id: String): TenantIssueDetail =
    send(ApiRequest(HttpMethod.GET, "/tenant/issues/$id"), TenantIssueDetail.serializer())

/**
 * `POST /tenant/issues/:id/messages` — a tenant reply. Multipart: `body` (may be blank when a
 * photo is attached) then 0–3 `photo` files. 201 → the message; 409 when the issue is
 * resolved / closed (replies are over — report again).
 */
suspend fun ApiClient.replyToIssue(id: String, body: String, photos: List<PhotoUpload>): TenantIssueMessage {
    val form = buildReplyForm(body, photos)
    return send(
        ApiRequest(HttpMethod.POST, "/tenant/issues/$id/messages", body = form.body, contentType = form.contentType),
        TenantIssueMessage.serializer(),
    )
}

/** The multipart body of [replyToIssue]: the trimmed `body` (left out when blank), then the photos. */
fun buildReplyForm(body: String, photos: List<PhotoUpload>, form: MultipartForm = MultipartForm()): MultipartForm {
    body.trim().takeIf { it.isNotEmpty() }?.let { form.addField(IssueRules.FIELD_BODY, it) }
    photos.forEach { form.addFile(IssueRules.FIELD_PHOTO, it.fileName, it.mimeType, it.bytes) }
    return form
}

/** `GET /tenant/issues/:id/photos/:documentId` — the bytes of a photo on the report or a reply. */
suspend fun ApiClient.issuePhoto(issueId: String, documentId: String): IssuePhoto {
    val response = sendData(ApiRequest(HttpMethod.GET, "/tenant/issues/$issueId/photos/$documentId"))
    val type = response.headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value ?: "application/octet-stream"
    return IssuePhoto(response.body, type)
}
