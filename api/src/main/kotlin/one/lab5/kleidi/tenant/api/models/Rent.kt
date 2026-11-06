// Mirrors server/types/tenantPortal.types.ts (TenantRentResponse, TenantRentMonth) and
// wireDeclarations.types.ts (TenantWireDeclarationResponse) — the tenant's rent months, each with
// its latest "I paid by bank transfer" declaration. Statuses stay open StringValues.
@file:UseSerializers(ApiInstantSerializer::class)

package one.lab5.kleidi.tenant.api.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import one.lab5.kleidi.tenant.api.ApiInstantSerializer
import one.lab5.kleidi.tenant.core.RentMonthStatus
import one.lab5.kleidi.tenant.core.RentProofLine
import one.lab5.kleidi.tenant.core.WireDeclarationStatus
import java.time.Instant

/** The latest declaration covering a month (any status), so a rejection stays visible. */
@Serializable
data class TenantRentMonthDeclaration(
    val id: String,
    val status: String,
    val declaredAmount: Double,
    val transferDate: Instant,
    /** When the tenant submitted the declaration. */
    val declaredAt: Instant,
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
    /** First day of the Rental Month this row covers; null on older servers. */
    val rentalMonthStart: Instant? = null,
    /** Last day of the Rental Month this row covers; null on older servers. */
    val rentalMonthEnd: Instant? = null,
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

    /** The "View proof" line under the pill, when the month's declaration has a proof worth opening. */
    val proofLine: RentProofLine
        get() = RentMonthStatus.proofLine(monthStatus, confirmedByWire, declaration?.declarationStatus)
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
    /** The newest row of the rent view. */
    val newestMonth: TenantRentMonth? get() = months.firstOrNull()

    /**
     * This calendar month's row (the rent card's pill), by the server's UTC month. None before the
     * rent view reaches the month, and none in a lease's last weeks when the next Rental Month
     * starts after its end — the newest row is then an earlier month.
     */
    fun currentMonth(today: Instant = Instant.now()): TenantRentMonth? {
        val period = today.toString().take(7)
        return months.firstOrNull { it.period == period }
    }

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
