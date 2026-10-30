package one.lab5.kleidi.tenant.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssueValuesTest {
    @Test
    fun categoryLabelKeysAndOrder() {
        assertEquals(
            listOf("plumbing", "electrical", "heating_ac", "doors_locks", "building", "other"),
            IssueCategory.ALL.map { it.raw },
        )
        assertEquals("issue_category_plumbing", IssueCategory.Plumbing.labelKey)
        assertEquals("issue_category_heating_ac", IssueCategory.HeatingAc.labelKey)
        assertEquals("issue_category_doors_locks", IssueCategory.DoorsLocks.labelKey)
        assertNull(IssueCategory("garden").labelKey)
        assertEquals("Garden", humanize(IssueCategory("garden").raw))
    }

    @Test
    fun statusLabelKeys() {
        assertEquals("issue_status_new", IssueStatus.New.labelKey)
        assertEquals("issue_status_in_progress", IssueStatus.InProgress.labelKey)
        assertEquals("issue_status_resolved", IssueStatus.Resolved.labelKey)
        assertEquals("issue_status_closed", IssueStatus.Closed.labelKey)
        assertNull(IssueStatus("escalated").labelKey)
        assertEquals("Escalated", humanize(IssueStatus("escalated").raw))
    }

    @Test
    fun openStatusesAreNewAndInProgress() {
        assertTrue(IssueStatus.New.isOpen)
        assertTrue(IssueStatus.InProgress.isOpen)
        assertFalse(IssueStatus.Resolved.isOpen)
        assertFalse(IssueStatus.Closed.isOpen)
        // An unknown status is not treated as open: the tenant cannot post into a thread this build does not understand.
        assertFalse(IssueStatus("escalated").isOpen)
    }

    @Test
    fun messageKindsAndAuthors() {
        assertEquals(IssueMessageKind.Status, IssueMessageKind("status"))
        assertEquals(IssueMessageKind.Text, IssueMessageKind("text"))
        assertEquals(IssueAuthor.Staff, IssueAuthor("staff"))
        assertEquals(IssueAuthor.Tenant, IssueAuthor("tenant"))
    }
}
