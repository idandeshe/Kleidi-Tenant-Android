package one.lab5.kleidi.tenant.features.issues

import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.ApiError
import one.lab5.kleidi.tenant.core.IssueCategory
import one.lab5.kleidi.tenant.core.IssueRules
import one.lab5.kleidi.tenant.core.IssueStatus
import one.lab5.kleidi.tenant.ui.PillTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM test (no Robolectric): resource ids are compile-time constants. */
class IssueLabelsTest {
    @Test
    fun everyKnownCategoryAndStatusHasAResource() {
        IssueCategory.ALL.forEach { assertTrue(it.raw, IssueLabels.CATEGORY.containsKey(it.labelKey)) }
        IssueStatus.ALL.forEach { assertTrue(it.raw, IssueLabels.STATUS.containsKey(it.labelKey)) }
        // The tenant reads `new` as "Received".
        assertEquals(R.string.issue_status_new, IssueLabels.STATUS[IssueStatus.New.labelKey])
    }

    @Test
    fun tonesFollowTheMockUp() {
        assertEquals(PillTone.Accent, IssueLabels.statusTone(IssueStatus.New))
        assertEquals(PillTone.Warning, IssueLabels.statusTone(IssueStatus.InProgress))
        assertEquals(PillTone.Success, IssueLabels.statusTone(IssueStatus.Resolved))
        assertEquals(PillTone.Neutral, IssueLabels.statusTone(IssueStatus.Closed))
        assertEquals(PillTone.Neutral, IssueLabels.statusTone(IssueStatus("escalated")))

        assertEquals(PillTone.Error, IssueLabels.iconTone(IssueStatus.New, isUrgent = true))
        assertEquals(PillTone.Accent, IssueLabels.iconTone(IssueStatus.InProgress, isUrgent = false))
        // Once over, urgency no longer colours the tile.
        assertEquals(PillTone.Neutral, IssueLabels.iconTone(IssueStatus.Resolved, isUrgent = true))
        assertEquals(PillTone.Neutral, IssueLabels.iconTone(IssueStatus.Closed, isUrgent = false))
    }

    @Test
    fun everyCategoryHasAnIconAndUnknownFallsBackToHelp() {
        val icons = IssueCategory.ALL.map { IssueLabels.categoryIcon(it) }
        assertEquals(IssueCategory.ALL.size, icons.toSet().size)
        assertEquals(IssueLabels.categoryIcon(IssueCategory.Other), IssueLabels.categoryIcon(IssueCategory("garden")))
    }

    @Test
    fun reportErrorsMapToTheirCopy() {
        assertEquals(R.string.report_category_required, IssueLabels.reportErrorMessageRes(ApiError.Http(400, "x", null, "category")))
        assertEquals(R.string.report_description_required, IssueLabels.reportErrorMessageRes(ApiError.Http(400, "x", null, "description")))
        assertEquals(R.string.issue_photo_type, IssueLabels.reportErrorMessageRes(ApiError.Http(400, "x", null, "photo")))
        assertEquals(R.string.issue_photo_too_large, IssueLabels.reportErrorMessageRes(ApiError.Http(413, "x", null, "photo")))
        assertEquals(R.string.issue_photo_type, IssueLabels.reportErrorMessageRes(ApiError.Http(415, "x", null, "photo")))
        assertEquals(R.string.error_rate_limited, IssueLabels.reportErrorMessageRes(ApiError.Http(429, "x", null, null)))
        assertEquals(R.string.error_generic, IssueLabels.reportErrorMessageRes(ApiError.Http(404, "x", null, null)))
        assertEquals(R.string.error_offline, IssueLabels.reportErrorMessageRes(ApiError.Transport("x")))
    }

    @Test
    fun replyErrorsMapToTheirCopy() {
        assertEquals(R.string.error_issue_reply_closed, IssueLabels.replyErrorMessageRes(ApiError.Http(409, "x", null, null)))
        assertEquals(R.string.issue_reply_required, IssueLabels.replyErrorMessageRes(ApiError.Http(400, "x", null, "body")))
        assertEquals(R.string.issue_photo_type, IssueLabels.replyErrorMessageRes(ApiError.Http(400, "x", null, "photo")))
        assertEquals(R.string.issue_photo_too_large, IssueLabels.replyErrorMessageRes(ApiError.Http(413, "x", null, "photo")))
        assertEquals(R.string.error_server, IssueLabels.replyErrorMessageRes(ApiError.Http(500, "x", null, null)))
    }

    @Test
    fun pickerProblemsMapToTheirCopy() {
        assertEquals(R.string.issue_photo_type, IssueLabels.photoProblemRes(IssueRules.PhotoProblem.UnsupportedType))
        assertEquals(R.string.issue_photo_too_large, IssueLabels.photoProblemRes(IssueRules.PhotoProblem.TooLarge))
    }
}
