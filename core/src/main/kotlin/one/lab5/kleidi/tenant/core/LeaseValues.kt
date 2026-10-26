// Server values of a tenant's lease (server/types/leases.types.ts, properties.types.ts) and the
// string-resource *names* of their labels. Core is pure Kotlin, so it cannot hold `R.string`
// ids: the app resolves `labelKey` to the resource of that name (`LeaseLabels` in :app) and
// falls back to the humanized raw value for a case this build does not know.

package one.lab5.kleidi.tenant.core

/** `upcoming` | `active` | `ended` | `terminated`. */
@JvmInline
value class LeaseStatus(override val raw: String) : StringValue {
    /** Name of the string resource with this status' label, or null for an unknown value. */
    val labelKey: String? get() = if (this in KNOWN) "lease_status_$raw" else null

    /** The lease the tenant is living (or about to live) in. */
    val isOpen: Boolean get() = this == Active || this == Upcoming

    companion object {
        val Upcoming = LeaseStatus("upcoming")
        val Active = LeaseStatus("active")
        val Ended = LeaseStatus("ended")
        val Terminated = LeaseStatus("terminated")
        private val KNOWN = setOf(Upcoming, Active, Ended, Terminated)
    }
}

/** `bank_transfer` | `cheques` | `cash` | `standing_order` | `other`. */
@JvmInline
value class PaymentMethod(override val raw: String) : StringValue {
    val labelKey: String? get() = if (this in KNOWN) "payment_method_$raw" else null

    companion object {
        val BankTransfer = PaymentMethod("bank_transfer")
        val Cheques = PaymentMethod("cheques")
        val Cash = PaymentMethod("cash")
        val StandingOrder = PaymentMethod("standing_order")
        val Other = PaymentMethod("other")
        private val KNOWN = setOf(BankTransfer, Cheques, Cash, StandingOrder, Other)
    }
}

/** `basement` | `semi_basement` | `ground` | `upper_ground` | `floor_<n>`. */
@JvmInline
value class FloorLevel(override val raw: String) : StringValue {
    /** `floor_3` → 3; null for the named levels. */
    val number: Int? get() = raw.removePrefix("floor_").takeIf { raw.startsWith("floor_") }?.toIntOrNull()

    /** Resource name for a named level (`floor_basement`…), null for numbered / unknown ones. */
    val labelKey: String? get() = if (this in NAMED) "floor_$raw" else null

    companion object {
        val Basement = FloorLevel("basement")
        val SemiBasement = FloorLevel("semi_basement")
        val Ground = FloorLevel("ground")
        val UpperGround = FloorLevel("upper_ground")
        private val NAMED = setOf(Basement, SemiBasement, Ground, UpperGround)
    }
}
