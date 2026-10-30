// Localized wording of the tenant's issues: :core gives each known category / status a
// string-resource *name*; this maps the names to `R.string` ids, picks the pill tone and the
// category icon, humanizes anything newer than this build, and turns the report / reply
// endpoints' errors into copy.

package one.lab5.kleidi.tenant.features.issues

import android.content.res.Resources
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.ApiError
import one.lab5.kleidi.tenant.core.IssueCategory
import one.lab5.kleidi.tenant.core.IssueRules
import one.lab5.kleidi.tenant.core.IssueStatus
import one.lab5.kleidi.tenant.core.humanize
import one.lab5.kleidi.tenant.shell.errorMessageRes
import one.lab5.kleidi.tenant.ui.PillTone

object IssueLabels {
    val CATEGORY: Map<String, Int> = mapOf(
        "issue_category_plumbing" to R.string.issue_category_plumbing,
        "issue_category_electrical" to R.string.issue_category_electrical,
        "issue_category_heating_ac" to R.string.issue_category_heating_ac,
        "issue_category_doors_locks" to R.string.issue_category_doors_locks,
        "issue_category_building" to R.string.issue_category_building,
        "issue_category_other" to R.string.issue_category_other,
    )

    /** `new` reads "Received" to the tenant (the staff apps say "New"). */
    val STATUS: Map<String, Int> = mapOf(
        "issue_status_new" to R.string.issue_status_new,
        "issue_status_in_progress" to R.string.issue_status_in_progress,
        "issue_status_resolved" to R.string.issue_status_resolved,
        "issue_status_closed" to R.string.issue_status_closed,
    )

    fun category(resources: Resources, category: IssueCategory): String =
        category.labelKey?.let(CATEGORY::get)?.let(resources::getString) ?: humanize(category.raw)

    fun status(resources: Resources, status: IssueStatus): String =
        status.labelKey?.let(STATUS::get)?.let(resources::getString) ?: humanize(status.raw)

    /** Mock-up pills: Received blue, In progress amber, Resolved green, Closed (and unknown) grey. */
    fun statusTone(status: IssueStatus): PillTone = when (status) {
        IssueStatus.New -> PillTone.Accent
        IssueStatus.InProgress -> PillTone.Warning
        IssueStatus.Resolved -> PillTone.Success
        else -> PillTone.Neutral
    }

    /** The icon tile of a row / the category grid. */
    fun categoryIcon(category: IssueCategory): ImageVector = when (category) {
        IssueCategory.Plumbing -> Icons.Outlined.WaterDrop
        IssueCategory.Electrical -> Icons.Outlined.Bolt
        IssueCategory.HeatingAc -> Icons.Outlined.Thermostat
        IssueCategory.DoorsLocks -> Icons.Outlined.Lock
        IssueCategory.Building -> Icons.Outlined.Apartment
        else -> Icons.Outlined.HelpOutline
    }

    /** The tile behind a row's icon: red while urgent and open, blue while open, grey once over. */
    fun iconTone(status: IssueStatus, isUrgent: Boolean): PillTone = when {
        !status.isOpen -> PillTone.Neutral
        isUrgent -> PillTone.Error
        else -> PillTone.Accent
    }

    /** The copy for a failed `POST …/leases/:id/issues` (400 `{ error, field }`, 413, 415, …). */
    fun reportErrorMessageRes(error: Throwable): Int {
        if (error !is ApiError.Http) return errorMessageRes(error)
        return when (error.status) {
            400 -> when (error.field) {
                IssueRules.FIELD_CATEGORY -> R.string.report_category_required
                IssueRules.FIELD_DESCRIPTION -> R.string.report_description_required
                IssueRules.FIELD_PHOTO -> R.string.issue_photo_type
                else -> R.string.error_generic
            }
            413 -> R.string.issue_photo_too_large
            415 -> R.string.issue_photo_type
            else -> errorMessageRes(error)
        }
    }

    /** The copy for a failed `POST …/issues/:id/messages` (400, 409 replies closed, 413, 415, …). */
    fun replyErrorMessageRes(error: Throwable): Int {
        if (error !is ApiError.Http) return errorMessageRes(error)
        return when (error.status) {
            400 -> if (error.field == IssueRules.FIELD_PHOTO) R.string.issue_photo_type else R.string.issue_reply_required
            409 -> R.string.error_issue_reply_closed
            413 -> R.string.issue_photo_too_large
            415 -> R.string.issue_photo_type
            else -> errorMessageRes(error)
        }
    }

    /** The copy for a photo the picker handed back that the server would refuse. */
    fun photoProblemRes(problem: IssueRules.PhotoProblem): Int = when (problem) {
        IssueRules.PhotoProblem.UnsupportedType -> R.string.issue_photo_type
        IssueRules.PhotoProblem.TooLarge -> R.string.issue_photo_too_large
    }
}

@Composable @ReadOnlyComposable
fun IssueCategory.label(): String = IssueLabels.category(LocalResources.current, this)

@Composable @ReadOnlyComposable
fun IssueStatus.label(): String = IssueLabels.status(LocalResources.current, this)
