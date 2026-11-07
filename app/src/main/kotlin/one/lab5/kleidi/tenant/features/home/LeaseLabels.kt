// Localized labels of the lease values. :core gives each known value a string-resource *name*
// (`labelKey`); this maps the names to `R.string` ids and humanizes anything newer than this build.

package one.lab5.kleidi.tenant.features.home

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.models.TenantLeaseProperty
import one.lab5.kleidi.tenant.core.AppLanguage
import one.lab5.kleidi.tenant.core.FloorLevel
import one.lab5.kleidi.tenant.core.LeaseFormatting
import one.lab5.kleidi.tenant.core.LeaseStatus
import one.lab5.kleidi.tenant.core.PaymentMethod
import one.lab5.kleidi.tenant.core.humanize

object LeaseLabels {
    val STATUS: Map<String, Int> = mapOf(
        "lease_status_upcoming" to R.string.lease_status_upcoming,
        "lease_status_active" to R.string.lease_status_active,
        "lease_status_ended" to R.string.lease_status_ended,
        "lease_status_terminated" to R.string.lease_status_terminated,
    )

    val PAYMENT_METHOD: Map<String, Int> = mapOf(
        "payment_method_bank_transfer" to R.string.payment_method_bank_transfer,
        "payment_method_cheques" to R.string.payment_method_cheques,
        "payment_method_cash" to R.string.payment_method_cash,
        "payment_method_standing_order" to R.string.payment_method_standing_order,
        "payment_method_other" to R.string.payment_method_other,
    )

    val FLOOR: Map<String, Int> = mapOf(
        "floor_basement" to R.string.floor_basement,
        "floor_semi_basement" to R.string.floor_semi_basement,
        "floor_ground" to R.string.floor_ground,
        "floor_upper_ground" to R.string.floor_upper_ground,
        "floor_doma" to R.string.floor_doma,
    )

    fun status(resources: Resources, status: LeaseStatus): String =
        status.labelKey?.let(STATUS::get)?.let(resources::getString) ?: humanize(status.raw)

    fun paymentMethod(resources: Resources, method: PaymentMethod): String =
        method.labelKey?.let(PAYMENT_METHOD::get)?.let(resources::getString) ?: humanize(method.raw)

    /** "3rd floor" / "3ος όροφος", "Ground floor", or the humanized raw value. */
    fun floor(resources: Resources, floor: FloorLevel, language: AppLanguage): String {
        floor.labelKey?.let(FLOOR::get)?.let { return resources.getString(it) }
        floor.number?.let { return resources.getString(R.string.floor_numbered, LeaseFormatting.dayOfMonthLabel(it, language)) }
        return humanize(floor.raw)
    }

    /** "Ermou 12, 3rd floor · 10563 Athens" — whichever parts the property has. */
    fun address(resources: Resources, property: TenantLeaseProperty, language: AppLanguage): String {
        val first = listOfNotNull(property.streetLine, property.floorLevel?.let { floor(resources, it, language) }).joinToString(", ")
        return listOf(first, property.cityLine ?: "").filter { it.isNotBlank() }.joinToString(" · ")
    }
}

@Composable @ReadOnlyComposable
fun LeaseStatus.label(): String = LeaseLabels.status(LocalResources.current, this)

@Composable @ReadOnlyComposable
fun PaymentMethod.label(): String = LeaseLabels.paymentMethod(LocalResources.current, this)

@Composable @ReadOnlyComposable
fun TenantLeaseProperty.addressLabel(language: AppLanguage): String = LeaseLabels.address(LocalResources.current, this, language)

@Composable @ReadOnlyComposable
fun notSet(): String = stringResource(R.string.lease_not_set)
