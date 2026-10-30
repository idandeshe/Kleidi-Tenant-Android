// Server values of a tenant's maintenance issues (server/types/tenantIssues.types.ts
// TenantIssueCategory, TenantIssueStatus, TenantIssueMessageKind, TenantIssueActorKind) and the
// string-resource *names* of their labels — same pattern as LeaseValues / RentValues: open value
// classes, so a value this build does not know still decodes and is shown humanized.

package one.lab5.kleidi.tenant.core

/** `plumbing` | `electrical` | `heating_ac` | `doors_locks` | `building` | `other`. */
@JvmInline
value class IssueCategory(override val raw: String) : StringValue {
    /** Name of the string resource with this category's wording, or null for an unknown value. */
    val labelKey: String? get() = if (this in ALL) "issue_category_$raw" else null

    companion object {
        val Plumbing = IssueCategory("plumbing")
        val Electrical = IssueCategory("electrical")
        val HeatingAc = IssueCategory("heating_ac")
        val DoorsLocks = IssueCategory("doors_locks")
        val Building = IssueCategory("building")
        val Other = IssueCategory("other")

        /** The categories the report form offers, in the order of the mock-up grid. */
        val ALL: List<IssueCategory> = listOf(Plumbing, Electrical, HeatingAc, DoorsLocks, Building, Other)
    }
}

/**
 * `new` | `in_progress` | `resolved` | `closed`. The tenant reads `new` as "Received"; `closed`
 * is "ended without a fix" and carries a close reason.
 */
@JvmInline
value class IssueStatus(override val raw: String) : StringValue {
    /** Name of the string resource with this status' wording, or null for an unknown value. */
    val labelKey: String? get() = if (this in ALL) "issue_status_$raw" else null

    /** The thread is live: the tenant may post, staff still owe an outcome ("Open" list group). */
    val isOpen: Boolean get() = this == New || this == InProgress

    companion object {
        val New = IssueStatus("new")
        val InProgress = IssueStatus("in_progress")
        val Resolved = IssueStatus("resolved")
        val Closed = IssueStatus("closed")
        val ALL: List<IssueStatus> = listOf(New, InProgress, Resolved, Closed)
    }
}

/** `text` (a message) | `status` (a status change shown centred in the thread). */
@JvmInline
value class IssueMessageKind(override val raw: String) : StringValue {
    companion object {
        val Text = IssueMessageKind("text")
        val Status = IssueMessageKind("status")
    }
}

/** `tenant` | `staff` — staff is shown as "Property manager", never a name. */
@JvmInline
value class IssueAuthor(override val raw: String) : StringValue {
    companion object {
        val Tenant = IssueAuthor("tenant")
        val Staff = IssueAuthor("staff")
    }
}
