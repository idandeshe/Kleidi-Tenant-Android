// The small decisions of "Report an issue" and the issue thread, kept pure so they are
// unit-tested: what the server accepts as a photo (server/types/tenantIssues.types.ts
// TENANT_ISSUES_VALIDATION + TENANT_ISSUE_PHOTO_MIME_TYPES), how many photos a report / a reply
// may carry, the multipart field names (TENANT_ISSUE_FORM_FIELDS) and when a reply can be sent.

package one.lab5.kleidi.tenant.core

object IssueRules {
    const val PHOTO_MAX_BYTES: Long = 10L * 1024 * 1024
    const val MAX_REPORT_PHOTOS = 5
    const val MAX_REPLY_PHOTOS = 3
    const val DESCRIPTION_MAX_LENGTH = 2000
    const val MESSAGE_MAX_LENGTH = 2000

    /** Images only; the app re-encodes anything else (HEIC, camera shots) as JPEG first. */
    val PHOTO_MIME_TYPES: Set<String> = setOf("image/jpeg", "image/png", "image/webp")

    /** The multipart field names, in the order the server reads them (text first, then the `photo` parts). */
    const val FIELD_CATEGORY = "category"
    const val FIELD_DESCRIPTION = "description"
    const val FIELD_IS_URGENT = "isUrgent"
    const val FIELD_BODY = "body"
    const val FIELD_PHOTO = "photo"

    enum class PhotoProblem { UnsupportedType, TooLarge }

    /** Why a file cannot go up as a photo, or null when the server will take it. */
    fun photoProblem(mimeType: String, sizeBytes: Long): PhotoProblem? = when {
        mimeType.lowercase() !in PHOTO_MIME_TYPES -> PhotoProblem.UnsupportedType
        sizeBytes > PHOTO_MAX_BYTES -> PhotoProblem.TooLarge
        else -> null
    }

    /** The `isUrgent` multipart value: `true` / `false` (the server reads text, absent = false). */
    fun isUrgentField(isUrgent: Boolean): String = if (isUrgent) "true" else "false"

    /** Another photo may be added to a report ([MAX_REPORT_PHOTOS]) or a reply ([MAX_REPLY_PHOTOS]). */
    fun canAddPhoto(count: Int, max: Int): Boolean = count < max

    /** A reply needs a non-blank body or at least one photo. */
    fun canSendReply(body: String, photoCount: Int): Boolean = body.isNotBlank() || photoCount > 0

    /** The description the report sends: trimmed, non-empty, within the server's limit. */
    fun descriptionField(description: String): String? =
        description.trim().take(DESCRIPTION_MAX_LENGTH).takeIf { it.isNotEmpty() }
}
