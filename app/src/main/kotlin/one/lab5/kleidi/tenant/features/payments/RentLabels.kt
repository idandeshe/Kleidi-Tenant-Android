// Localized wording of the rent months (web `RentMonthRow` / `PaymentsPage`): :core gives each
// known status a string-resource *name*; this maps the names to `R.string` ids, picks the pill
// tone, humanizes anything newer than this build, and turns the declaration endpoint's errors
// into copy.

package one.lab5.kleidi.tenant.features.payments

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.ApiError
import one.lab5.kleidi.tenant.api.models.TenantRentMonth
import one.lab5.kleidi.tenant.core.RentMonthStatus
import one.lab5.kleidi.tenant.core.WireDeclarationRules
import one.lab5.kleidi.tenant.core.humanize
import one.lab5.kleidi.tenant.shell.errorMessageRes
import one.lab5.kleidi.tenant.ui.PillTone

object RentLabels {
    val MONTH_STATUS: Map<String, Int> = mapOf(
        "rent_status_paid" to R.string.rent_status_paid,
        "rent_status_declared" to R.string.rent_status_declared,
        "rent_status_due" to R.string.rent_status_due,
        "rent_status_overdue" to R.string.rent_status_overdue,
        "rent_status_upcoming" to R.string.rent_status_upcoming,
        RentMonthStatus.CONFIRMED_BY_WIRE_KEY to R.string.rent_confirmed_by_wire,
    )

    /** The pill wording of a month: "Wire confirmed by property owner" or its status. */
    fun month(resources: Resources, status: RentMonthStatus, confirmedByWire: Boolean): String =
        RentMonthStatus.labelKey(status, confirmedByWire)?.let(MONTH_STATUS::get)?.let(resources::getString) ?: humanize(status.raw)

    /** Web MONTH_STYLE: paid green, declared blue, due amber, overdue red, upcoming (and unknown) grey. */
    fun monthTone(status: RentMonthStatus): PillTone = when (status) {
        RentMonthStatus.Paid -> PillTone.Success
        RentMonthStatus.Declared -> PillTone.Accent
        RentMonthStatus.Due -> PillTone.Warning
        RentMonthStatus.Overdue -> PillTone.Error
        else -> PillTone.Neutral
    }

    /** The copy for a failed `POST …/wire-declarations` (400 `{ error, field }`, 409, 413, 415, …). */
    fun declareErrorMessageRes(error: Throwable): Int {
        if (error !is ApiError.Http) return errorMessageRes(error)
        return when (error.status) {
            400 -> when (error.field) {
                WireDeclarationRules.FIELD_PERIODS -> R.string.wire_months_required
                WireDeclarationRules.FIELD_DECLARED_AMOUNT -> R.string.wire_amount_required
                WireDeclarationRules.FIELD_TRANSFER_DATE -> R.string.wire_date_required
                WireDeclarationRules.FIELD_PROOF -> R.string.wire_proof_required
                else -> R.string.error_generic
            }
            409 -> R.string.error_wire_conflict
            413 -> R.string.wire_proof_too_large
            415 -> R.string.wire_proof_hint
            else -> errorMessageRes(error)
        }
    }
}

@Composable @ReadOnlyComposable
fun TenantRentMonth.statusLabel(): String = RentLabels.month(LocalResources.current, monthStatus, confirmedByWire)
