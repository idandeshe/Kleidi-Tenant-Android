// Mirrors server/types/tenantPortal.types.ts (TenantLeaseResponse) — the tenant's own, narrow
// view of a lease. A field is added here only when the server adds it there on purpose.
@file:UseSerializers(ApiInstantSerializer::class)

package one.lab5.kleidi.tenant.api.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import one.lab5.kleidi.tenant.api.ApiInstantSerializer
import one.lab5.kleidi.tenant.core.FloorLevel
import one.lab5.kleidi.tenant.core.LeaseStatus
import one.lab5.kleidi.tenant.core.PaymentMethod
import java.time.Instant

@Serializable
data class TenantLeaseProperty(
    val id: String,
    val street: String? = null,
    val unitNumber: String? = null,
    val floor: String? = null,
    val postalCode: String? = null,
    val cityName: String? = null,
) {
    val floorLevel: FloorLevel? get() = floor?.let(::FloorLevel)

    /** "Ermou 12" — street and number; null when the property has no street. */
    val streetLine: String? get() = listOfNotNull(street?.takeIf { it.isNotBlank() }, unitNumber?.takeIf { it.isNotBlank() })
        .joinToString(" ").takeIf { it.isNotBlank() }

    /** "105 63 Athens" — postal code and city; null when both are missing. */
    val cityLine: String? get() = listOfNotNull(postalCode?.takeIf { it.isNotBlank() }, cityName?.takeIf { it.isNotBlank() })
        .joinToString(" ").takeIf { it.isNotBlank() }
}

@Serializable
data class UpcomingRent(val monthlyRent: Double, val effectiveFrom: Instant)

@Serializable
data class TenantLease(
    val id: String,
    val property: TenantLeaseProperty,
    val status: String,
    val startDate: Instant,
    val endDate: Instant? = null,
    /** The end date has passed while the lease is still open. */
    val isPastEnd: Boolean = false,
    /** The rent that applies today. */
    val monthlyRent: Double,
    val upcomingRent: UpcomingRent? = null,
    val currencyCode: String? = null,
    val depositAmount: Double? = null,
    val paymentDayOfMonth: Int? = null,
    val paymentMethod: String? = null,
    val noticePeriodMonths: Int? = null,
    /** Last day the tenant can give notice; null without an end date + notice period. */
    val noticeDeadline: Instant? = null,
) {
    val leaseStatus: LeaseStatus get() = LeaseStatus(status)
    val payment: PaymentMethod? get() = paymentMethod?.let(::PaymentMethod)
}

@Serializable
data class TenantLeasesResponse(val data: List<TenantLease>)
