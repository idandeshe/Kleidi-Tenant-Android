package one.lab5.kleidi.tenant.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssueRulesTest {
    @Test
    fun photoRulesMirrorTheServer() {
        assertNull(IssueRules.photoProblem("image/jpeg", 1024))
        assertNull(IssueRules.photoProblem("image/PNG", IssueRules.PHOTO_MAX_BYTES))
        assertNull(IssueRules.photoProblem("image/webp", 10))
        assertEquals(IssueRules.PhotoProblem.TooLarge, IssueRules.photoProblem("image/jpeg", IssueRules.PHOTO_MAX_BYTES + 1))
        // Not a photo: PDFs are fine as a wire proof, never as an issue photo.
        assertEquals(IssueRules.PhotoProblem.UnsupportedType, IssueRules.photoProblem("application/pdf", 10))
        assertEquals(IssueRules.PhotoProblem.UnsupportedType, IssueRules.photoProblem("image/heic", 10))
        assertEquals(10L * 1024 * 1024, IssueRules.PHOTO_MAX_BYTES)
    }

    @Test
    fun photoLimits() {
        assertEquals(5, IssueRules.MAX_REPORT_PHOTOS)
        assertEquals(3, IssueRules.MAX_REPLY_PHOTOS)
        assertTrue(IssueRules.canAddPhoto(0, IssueRules.MAX_REPORT_PHOTOS))
        assertTrue(IssueRules.canAddPhoto(4, IssueRules.MAX_REPORT_PHOTOS))
        assertFalse(IssueRules.canAddPhoto(5, IssueRules.MAX_REPORT_PHOTOS))
        assertFalse(IssueRules.canAddPhoto(3, IssueRules.MAX_REPLY_PHOTOS))
    }

    @Test
    fun multipartFieldNamesMatchTheServer() {
        assertEquals("category", IssueRules.FIELD_CATEGORY)
        assertEquals("description", IssueRules.FIELD_DESCRIPTION)
        assertEquals("isUrgent", IssueRules.FIELD_IS_URGENT)
        assertEquals("body", IssueRules.FIELD_BODY)
        assertEquals("photo", IssueRules.FIELD_PHOTO)
        assertEquals("true", IssueRules.isUrgentField(true))
        assertEquals("false", IssueRules.isUrgentField(false))
    }

    @Test
    fun aReplyNeedsTextOrAPhoto() {
        assertFalse(IssueRules.canSendReply("", 0))
        assertFalse(IssueRules.canSendReply("   \n", 0))
        assertTrue(IssueRules.canSendReply("Yes, I will be home.", 0))
        assertTrue(IssueRules.canSendReply("", 1))
    }

    @Test
    fun theDescriptionIsTrimmedAndCapped() {
        assertNull(IssueRules.descriptionField("   "))
        assertEquals("Tap dripping", IssueRules.descriptionField("  Tap dripping \n"))
        assertEquals(IssueRules.DESCRIPTION_MAX_LENGTH, IssueRules.descriptionField("x".repeat(IssueRules.DESCRIPTION_MAX_LENGTH + 50))!!.length)
    }
}
