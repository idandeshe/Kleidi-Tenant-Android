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

        /**
         * The line under a month's pill that opens the declaration's proof (web `RentMonthRow`):
         * a month waiting for confirmation shows "View proof" (its declared date is already on
         * the right); a month paid through a confirmed wire, or one whose declaration was
         * rejected, shows "Declared dd/mm/yyyy · View proof". Nothing otherwise.
         */
        fun proofLine(status: RentMonthStatus, confirmedByWire: Boolean, declaration: WireDeclarationStatus?): RentProofLine = when {
            declaration == null -> RentProofLine.None
            status == Declared -> RentProofLine.ViewProof
            (status == Paid && confirmedByWire) || declaration == WireDeclarationStatus.Rejected -> RentProofLine.DeclaredAndViewProof
            else -> RentProofLine.None
        }
    }
}

/** What a month row says about its declaration's proof — see [RentMonthStatus.proofLine]. */
enum class RentProofLine { None, ViewProof, DeclaredAndViewProof }

/** `pending` | `confirmed` | `rejected` — read through the month's status, never shown on its own. */
@JvmInline
value class WireDeclarationStatus(override val raw: String) : StringValue {
    companion object {
        val Pending = WireDeclarationStatus("pending")
        val Confirmed = WireDeclarationStatus("confirmed")
        val Rejected = WireDeclarationStatus("rejected")
    }
}
