// Server values of the tenant's rent months and bank-transfer declarations
// (server/types/tenantPortal.types.ts TenantRentMonthStatus, wireDeclarations.types.ts
// WireDeclarationStatus) and the string-resource *names* of their labels — same pattern as
// LeaseValues: open value classes, so a status this build does not know still decodes.

package one.lab5.kleidi.tenant.core

/** `paid` | `declared` | `due` | `overdue` | `upcoming`. */
@JvmInline
value class RentMonthStatus(override val raw: String) : StringValue {
    /** Name of the string resource with this status' wording, or null for an unknown value. */
    val labelKey: String? get() = if (this in KNOWN) "rent_status_$raw" else null

    companion object {
        val Paid = RentMonthStatus("paid")
        val Declared = RentMonthStatus("declared")
        val Due = RentMonthStatus("due")
        val Overdue = RentMonthStatus("overdue")
        val Upcoming = RentMonthStatus("upcoming")
        private val KNOWN = setOf(Paid, Declared, Due, Overdue, Upcoming)

        /** Resource name of "Wire confirmed by property owner" (a paid month settled through a declaration). */
        const val CONFIRMED_BY_WIRE_KEY = "rent_confirmed_by_wire"

        /**
         * The pill wording of a month (web `rentMonthStatusLabel`): a paid month that was settled
         * through a confirmed declaration reads "Wire confirmed by property owner", every other
         * month reads its status. Null for a status this build does not know (humanize the raw).
         */
        fun labelKey(status: RentMonthStatus, confirmedByWire: Boolean): String? =
            if (status == Paid && confirmedByWire) CONFIRMED_BY_WIRE_KEY else status.labelKey
    }
}

/** `pending` | `confirmed` | `rejected`. */
@JvmInline
value class WireDeclarationStatus(override val raw: String) : StringValue {
    val labelKey: String? get() = if (this in KNOWN) "rent_declaration_status_$raw" else null

    companion object {
        val Pending = WireDeclarationStatus("pending")
        val Confirmed = WireDeclarationStatus("confirmed")
        val Rejected = WireDeclarationStatus("rejected")
        private val KNOWN = setOf(Pending, Confirmed, Rejected)
    }
}
