// Mirrors server/types/tenantPortal.types.ts (TenantRentResponse, TenantRentMonth) and
// wireDeclarations.types.ts (TenantWireDeclarationResponse) — the tenant's rent months and
// their "I paid by bank transfer" declarations. Statuses stay open StringValues.
@file:UseSerializers(ApiInstantSerializer::class)

package one.lab5.kleidi.tenant.api.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import one.lab5.kleidi.tenant.api.ApiInstantSerializer
import one.lab5.kleidi.tenant.core.RentMonthStatus
import one.lab5.kleidi.tenant.core.WireDeclarationStatus
import java.time.Instant

/** The latest declaration covering a month (any status), so a rejection stays visible. */
@Serializable
data class TenantRentMonthDeclaration(
    val id: String,
    val status: String,
    val declaredAmount: Double,
    val transferDate: Instant,
    val rejectionNote: String? = null,
    val confirmedAt: Instant? = null,
) {
    val declarationStatus: WireDeclarationStatus get() = WireDeclarationStatus(status)
}

@Serializable
data class TenantRentMonth(
    /** `YYYY-MM`. */
    val period: String,
    /** UTC midnight of the day the rent is owed. */
    val dueDate: Instant,
    val expectedAmount: Double? = null,
    val status: String,
    val paidAt: Instant? = null,
    /** Paid through a confirmed declaration → "Wire confirmed by property owner". */
    val confirmedByWire: Boolean = false,
    val declaration: TenantRentMonthDeclaration? = null,
    /** The tenant may declare a bank transfer for this month. */
    val canDeclare: Boolean = false,
) {
    val monthStatus: RentMonthStatus get() = RentMonthStatus(status)

    /**
     * The red "Wire not confirmed" note (web `RentMonthRow`): the month is still owed and its
     * latest declaration was rejected.
     */
    val showsRejection: Boolean
        get() = monthStatus != RentMonthStatus.Paid && monthStatus != RentMonthStatus.Declared &&
            declaration?.declarationStatus == WireDeclarationStatus.Rejected
}

/** `GET /tenant/leases/:id/rent` — newest month first. */
@Serializable
data class TenantRent(
    val leaseId: String,
    val currencyCode: String? = null,
    /** The rent that applies today. */
    val monthlyRent: Double,
    val paymentDayOfMonth: Int? = null,
    val months: List<TenantRentMonth> = emptyList(),
) {
    /** This month's row, when the lease has reached it (the rent card's pill). */
    val newestMonth: TenantRentMonth? get() = months.firstOrNull()

    val canDeclare: Boolean get() = months.any { it.canDeclare }

    /** The months the tenant may still declare, newest first. */
    val declarableMonths: List<TenantRentMonth> get() = months.filter { it.canDeclare }
}

/** The tenant's own view of a declaration — no analysis, no staff ids. */
@Serializable
data class TenantWireDeclaration(
    val id: String,
    val leaseId: String,
    val periods: List<String>,
    val status: String,
    val declaredAmount: Double,
    val currencyCode: String? = null,
    val transferDate: Instant,
    val reference: String? = null,
    val rejectionNote: String? = null,
    val confirmedAt: Instant? = null,
    val createdAt: Instant,
) {
    val declarationStatus: WireDeclarationStatus get() = WireDeclarationStatus(status)
}

@Serializable
data class TenantWireDeclarationsResponse(val data: List<TenantWireDeclaration>)
