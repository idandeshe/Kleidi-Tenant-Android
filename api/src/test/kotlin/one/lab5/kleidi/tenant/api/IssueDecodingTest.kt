package one.lab5.kleidi.tenant.api

import one.lab5.kleidi.tenant.api.models.TenantIssueDetail
import one.lab5.kleidi.tenant.api.models.TenantIssueMessage
import one.lab5.kleidi.tenant.api.models.TenantIssuesResponse
import one.lab5.kleidi.tenant.core.IssueAuthor
import one.lab5.kleidi.tenant.core.IssueCategory
import one.lab5.kleidi.tenant.core.IssueMessageKind
import one.lab5.kleidi.tenant.core.IssueStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssueDecodingTest {
    @Test
    fun decodesTheListOpenFirst() {
        val issues = KleidiJson.decodeFromString(TenantIssuesResponse.serializer(), ISSUES_JSON).data
        assertEquals(3, issues.size)

        val tap = issues[0]
        assertEquals("i1", tap.id)
        assertEquals("l1", tap.leaseId)
        assertEquals(IssueCategory.Plumbing, tap.issueCategory)
        assertEquals(IssueStatus.InProgress, tap.issueStatus)
        assertTrue(tap.issueStatus.isOpen)
        assertEquals("Kitchen tap dripping", tap.summary)
        assertFalse(tap.isUrgent)
        assertEquals(listOf("d1", "d2"), tap.photoDocumentIds)
        assertTrue(tap.tenantUnread)
        assertTrue(tap.canReply)
        assertEquals(Instant.parse("2026-10-27T16:42:00Z"), tap.createdAt)
        assertNull(tap.resolvedAt)

        val power = issues[1]
        assertEquals(IssueStatus.New, power.issueStatus)
        assertTrue(power.isUrgent)
        assertTrue(power.photoDocumentIds.isEmpty())

        val lock = issues[2]
        assertEquals(IssueStatus.Resolved, lock.issueStatus)
        assertFalse(lock.issueStatus.isOpen)
        assertFalse(lock.canReply)
        assertEquals(Instant.parse("2026-09-12T09:00:00Z"), lock.resolvedAt)
        assertEquals(IssueCategory.DoorsLocks, lock.issueCategory)
    }

    @Test
    fun decodesTheDetailWithItsThread() {
        val detail = KleidiJson.decodeFromString(TenantIssueDetail.serializer(), ISSUE_DETAIL_JSON)
        assertEquals("i1", detail.id)
        assertEquals(IssueStatus.InProgress, detail.issueStatus)
        assertEquals("The kitchen tap has been dripping since Monday, even when fully closed. The cabinet underneath is getting damp.", detail.description)
        assertEquals(detail.id, detail.issue.id)
        assertEquals(detail.summary, detail.issue.summary)

        val (staff, status, tenant) = detail.messages
        assertEquals(IssueMessageKind.Text, staff.messageKind)
        assertEquals(IssueAuthor.Staff, staff.messageAuthor)
        assertFalse(staff.isFromTenant)
        assertEquals("Thanks. A plumber is booked for Thursday 09:00–11:00. Will someone be home?", staff.body)
        assertEquals(Instant.parse("2026-10-28T08:15:00Z"), staff.createdAt)

        assertTrue(status.isStatusEvent)
        assertEquals(IssueStatus.InProgress, status.newStatus)
        assertNull(status.body)

        assertTrue(tenant.isFromTenant)
        assertEquals(listOf("d3"), tenant.photoDocumentIds)
    }

    @Test
    fun aClosedStatusEventCarriesItsReason() {
        val message = KleidiJson.decodeFromString(
            TenantIssueMessage.serializer(),
            """{ "id": "m9", "kind": "status", "author": "staff", "body": null, "status": "closed", "closeReason": "Tenant's own appliance", "photoDocumentIds": [], "createdAt": "2026-10-29T10:00:00.000Z" }""",
        )
        assertEquals(IssueStatus.Closed, message.newStatus)
        assertEquals("Tenant's own appliance", message.closeReason)
    }

    @Test
    fun unknownCategoriesAndStatusesStillDecode() {
        val json = ISSUES_JSON.replace("\"category\": \"plumbing\"", "\"category\": \"garden\"").replace("\"status\": \"in_progress\"", "\"status\": \"escalated\"")
        val issue = KleidiJson.decodeFromString(TenantIssuesResponse.serializer(), json).data[0]
        assertEquals("garden", issue.category)
        assertNull(issue.issueCategory.labelKey)
        assertEquals("escalated", issue.status)
        assertNull(issue.issueStatus.labelKey)
        assertFalse(issue.issueStatus.isOpen)
    }

    @Test
    fun optionalFieldsDefaultWhenMissing() {
        val minimal = """{ "id": "i7", "leaseId": "l1", "category": "other", "summary": "x", "description": "x", "status": "new",
          "lastActivityAt": "2026-10-28T08:15:00.000Z", "createdAt": "2026-10-28T08:15:00.000Z" }"""
        val detail = KleidiJson.decodeFromString(TenantIssueDetail.serializer(), minimal)
        assertFalse(detail.isUrgent)
        assertTrue(detail.messages.isEmpty())
        assertTrue(detail.photoDocumentIds.isEmpty())
        assertNull(detail.closeReason)
    }
}

val ISSUES_JSON = """
{
  "data": [
    { "id": "i1", "leaseId": "l1", "category": "plumbing", "summary": "Kitchen tap dripping",
      "description": "The kitchen tap has been dripping since Monday, even when fully closed. The cabinet underneath is getting damp.",
      "isUrgent": false, "photoDocumentIds": ["d1", "d2"], "status": "in_progress", "closeReason": null, "resolvedAt": null, "closedAt": null,
      "lastActivityAt": "2026-10-28T08:31:00.000Z", "tenantUnread": true, "canReply": true, "createdAt": "2026-10-27T16:42:00.000Z", "futureField": 1 },
    { "id": "i2", "leaseId": "l1", "category": "electrical", "summary": "No power in the bedroom", "description": "No power in the bedroom since this morning.",
      "isUrgent": true, "photoDocumentIds": [], "status": "new", "closeReason": null, "resolvedAt": null, "closedAt": null,
      "lastActivityAt": "2026-10-30T06:40:00.000Z", "tenantUnread": false, "canReply": true, "createdAt": "2026-10-30T06:40:00.000Z" },
    { "id": "i3", "leaseId": "l1", "category": "doors_locks", "summary": "Front door lock sticking", "description": "Front door lock sticking.",
      "isUrgent": false, "photoDocumentIds": [], "status": "resolved", "closeReason": null, "resolvedAt": "2026-09-12T09:00:00.000Z", "closedAt": null,
      "lastActivityAt": "2026-09-12T09:00:00.000Z", "tenantUnread": false, "canReply": false, "createdAt": "2026-09-10T12:00:00.000Z" }
  ]
}
""".trimIndent()

val ISSUE_MESSAGE_JSON = """
{ "id": "m3", "kind": "text", "author": "tenant", "body": "Yes, I will be home all morning.", "status": null, "closeReason": null,
  "photoDocumentIds": ["d3"], "createdAt": "2026-10-28T08:31:00.000Z" }
""".trimIndent()

val ISSUE_DETAIL_JSON = """
{ "id": "i1", "leaseId": "l1", "category": "plumbing", "summary": "Kitchen tap dripping",
  "description": "The kitchen tap has been dripping since Monday, even when fully closed. The cabinet underneath is getting damp.",
  "isUrgent": false, "photoDocumentIds": ["d1", "d2"], "status": "in_progress", "closeReason": null, "resolvedAt": null, "closedAt": null,
  "lastActivityAt": "2026-10-28T08:31:00.000Z", "tenantUnread": false, "canReply": true, "createdAt": "2026-10-27T16:42:00.000Z",
  "messages": [
    { "id": "m1", "kind": "text", "author": "staff", "body": "Thanks. A plumber is booked for Thursday 09:00–11:00. Will someone be home?",
      "status": null, "closeReason": null, "photoDocumentIds": [], "createdAt": "2026-10-28T08:15:00.000Z" },
    { "id": "m2", "kind": "status", "author": "staff", "body": null, "status": "in_progress", "closeReason": null, "photoDocumentIds": [],
      "createdAt": "2026-10-28T08:16:00.000Z" },
    $ISSUE_MESSAGE_JSON
  ]
}
""".trimIndent()
