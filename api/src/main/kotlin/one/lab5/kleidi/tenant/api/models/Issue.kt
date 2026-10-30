// Mirrors server/types/tenantIssues.types.ts (TenantIssueOwnResponse, TenantIssueOwnDetailResponse,
// TenantIssueOwnMessageResponse) — the tenant's own, narrow view of a maintenance issue: no staff
// identity, no internal notes. Category / status / kind / author stay open StringValues.
@file:UseSerializers(ApiInstantSerializer::class)

package one.lab5.kleidi.tenant.api.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import one.lab5.kleidi.tenant.api.ApiInstantSerializer
import one.lab5.kleidi.tenant.core.IssueAuthor
import one.lab5.kleidi.tenant.core.IssueCategory
import one.lab5.kleidi.tenant.core.IssueMessageKind
import one.lab5.kleidi.tenant.core.IssueStatus
import java.time.Instant

/** One tenant-visible event of the thread: a text message (tenant or staff) or a status change. */
@Serializable
data class TenantIssueMessage(
    val id: String,
    val kind: String,
    /** `tenant` | `staff` — staff is shown as "Property manager". */
    val author: String,
    val body: String? = null,
    /** For a status event: the status the issue moved to. */
    val status: String? = null,
    /** For a `closed` status event: the reason shown to the tenant. */
    val closeReason: String? = null,
    /** Tenant photos attached to a text message (0–3). */
    val photoDocumentIds: List<String> = emptyList(),
    val createdAt: Instant,
) {
    val messageKind: IssueMessageKind get() = IssueMessageKind(kind)
    val messageAuthor: IssueAuthor get() = IssueAuthor(author)
    val newStatus: IssueStatus? get() = status?.let(::IssueStatus)
    val isStatusEvent: Boolean get() = messageKind == IssueMessageKind.Status
    val isFromTenant: Boolean get() = messageAuthor == IssueAuthor.Tenant
}

/** An issue as the list shows it (`GET /tenant/issues`). */
@Serializable
data class TenantIssue(
    val id: String,
    val leaseId: String,
    val category: String,
    /** First line of the description, cut short by the server. */
    val summary: String,
    val description: String,
    val isUrgent: Boolean = false,
    val photoDocumentIds: List<String> = emptyList(),
    val status: String,
    val closeReason: String? = null,
    val resolvedAt: Instant? = null,
    val closedAt: Instant? = null,
    val lastActivityAt: Instant,
    /** Staff did something since the tenant last opened the issue (the "new reply" dot). */
    val tenantUnread: Boolean = false,
    /** The issue is open: the tenant may post. */
    val canReply: Boolean = false,
    val createdAt: Instant,
) {
    val issueCategory: IssueCategory get() = IssueCategory(category)
    val issueStatus: IssueStatus get() = IssueStatus(status)
}

/** `GET /tenant/issues/:id` and the 201 of a report: the issue plus its tenant-visible thread, oldest first. */
@Serializable
data class TenantIssueDetail(
    val id: String,
    val leaseId: String,
    val category: String,
    val summary: String,
    val description: String,
    val isUrgent: Boolean = false,
    val photoDocumentIds: List<String> = emptyList(),
    val status: String,
    val closeReason: String? = null,
    val resolvedAt: Instant? = null,
    val closedAt: Instant? = null,
    val lastActivityAt: Instant,
    val tenantUnread: Boolean = false,
    val canReply: Boolean = false,
    val createdAt: Instant,
    val messages: List<TenantIssueMessage> = emptyList(),
) {
    val issueCategory: IssueCategory get() = IssueCategory(category)
    val issueStatus: IssueStatus get() = IssueStatus(status)

    /** The same issue as a list entry (what the list and the detail header share). */
    val issue: TenantIssue
        get() = TenantIssue(
            id, leaseId, category, summary, description, isUrgent, photoDocumentIds, status, closeReason,
            resolvedAt, closedAt, lastActivityAt, tenantUnread, canReply, createdAt,
        )
}

@Serializable
data class TenantIssuesResponse(val data: List<TenantIssue>)

/** Issues with a staff reply the tenant has not opened yet — the count on the Issues tab. */
val List<TenantIssue>.unreadCount: Int get() = count { it.tenantUnread }
