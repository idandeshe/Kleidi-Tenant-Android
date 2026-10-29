// The proof of a bank transfer: a Camera / Gallery / Files chooser (the staff app's
// DocumentFilePicker) that hands back a file the server accepts. Camera shots and any photo that
// is not PNG / WebP (HEIC included) are decoded, turned upright per EXIF, downscaled so the longest
// side is at most 2000 px and re-encoded as JPEG 85 — which also drops the metadata (location
// included). PDFs go up as they are. Activity Result APIs only: no runtime permission is needed.

package one.lab5.kleidi.tenant.features.payments

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.endpoints.ProofUpload
import one.lab5.kleidi.tenant.core.WireDeclarationRules
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ProofSource { Camera, Gallery, Files }

/** Launches one of the three pickers; see [rememberProofPicker]. */
@Stable
class ProofPicker internal constructor(private val launchSource: (ProofSource) -> Unit) {
    fun launch(source: ProofSource) = launchSource(source)
}

/**
 * [onPick] gets a file the server will take; [onProblem] the string resource explaining why the
 * picked file cannot be used (unsupported type, too large, unreadable).
 */
@Composable
fun rememberProofPicker(onPick: (ProofUpload) -> Unit, onProblem: (Int) -> Unit): ProofPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun deliver(read: suspend () -> ProofImport.Outcome) {
        scope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) { read() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ProofImport.Outcome.Problem(R.string.proof_read_failed)
            }
            when (outcome) {
                is ProofImport.Outcome.Picked -> onPick(outcome.file)
                is ProofImport.Outcome.Problem -> onProblem(outcome.messageRes)
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri?.let(Uri::parse)
        cameraUri = null
        if (!saved || uri == null) return@rememberLauncherForActivityResult
        deliver {
            try {
                ProofImport.fromCamera(context, uri)
            } finally {
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        deliver { ProofImport.fromGallery(context, uri) }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        deliver { ProofImport.fromFile(context, uri) }
    }

    return remember {
        ProofPicker { source ->
            when (source) {
                ProofSource.Camera -> {
                    val uri = ProofImport.newCaptureUri(context)
                    cameraUri = uri.toString()
                    camera.launch(uri)
                }
                ProofSource.Gallery -> gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                ProofSource.Files -> files.launch(ProofImport.FILE_PICKER_TYPES)
            }
        }
    }
}

/** Reading and converting a picked file into a [ProofUpload]. */
object ProofImport {
    /** What the Files picker offers: PDFs and any image (non-PNG/WebP images are re-encoded as JPEG). */
    val FILE_PICKER_TYPES = arrayOf("application/pdf", "image/*")

    const val JPEG_QUALITY = 85
    const val MAX_SIDE_PX = 2000

    sealed interface Outcome {
        class Picked(val file: ProofUpload) : Outcome
        class Problem(val messageRes: Int) : Outcome
    }

    internal fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File.createTempFile("capture-", ".jpg", dir)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** "Proof 26-10-2026 14.30.jpg" — a file name for a camera / gallery image (no slashes or colons). */
    fun stampedName(base: String, extension: String, now: Instant = Instant.now()): String {
        val stamp = now.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH.mm"))
        return "$base $stamp.$extension"
    }

    internal fun fromCamera(context: Context, uri: Uri): Outcome =
        jpegFromUri(context, uri)?.let { finish(ProofUpload(it, stampedName("Proof", "jpg"), "image/jpeg")) }
            ?: Outcome.Problem(R.string.proof_read_failed)

    /** PNG / WebP as they are; anything else decodable (JPEG, HEIC…) becomes an upright, downscaled JPEG. */
    internal fun fromGallery(context: Context, uri: Uri): Outcome {
        val type = context.contentResolver.getType(uri)?.lowercase()
        val bytes = when (type) {
            "image/png" -> readAll(context, uri)?.let { ProofUpload(it, stampedName("Proof", "png"), type) }
            "image/webp" -> readAll(context, uri)?.let { ProofUpload(it, stampedName("Proof", "webp"), type) }
            else -> jpegFromUri(context, uri)?.let { ProofUpload(it, stampedName("Proof", "jpg"), "image/jpeg") }
        } ?: return Outcome.Problem(R.string.proof_read_failed)
        return finish(bytes)
    }

    /** A PDF or an accepted image as it is (size-checked before it is read); other images re-encoded as JPEG. */
    internal fun fromFile(context: Context, uri: Uri): Outcome {
        val (name, size) = nameAndSize(context.contentResolver, uri)
        val type = context.contentResolver.getType(uri)?.lowercase() ?: guessType(name)
        val isImage = type.startsWith("image/")
        if (!isImage && type != "application/pdf") return Outcome.Problem(R.string.wire_proof_hint)
        val keepAsIs = type in WireDeclarationRules.PROOF_MIME_TYPES
        if (keepAsIs && size != null && size > WireDeclarationRules.PROOF_MAX_BYTES) return Outcome.Problem(R.string.wire_proof_too_large)
        val upload = if (keepAsIs) {
            readAll(context, uri)?.let { ProofUpload(it, name, type) }
        } else {
            jpegFromUri(context, uri)?.let { ProofUpload(it, name.substringBeforeLast('.') + ".jpg", "image/jpeg") }
        } ?: return Outcome.Problem(R.string.proof_read_failed)
        return finish(upload)
    }

    /** The server's own checks, last: type and size of what will actually be sent. */
    private fun finish(upload: ProofUpload): Outcome = when (WireDeclarationRules.proofProblem(upload.mimeType, upload.bytes.size.toLong())) {
        WireDeclarationRules.ProofProblem.UnsupportedType -> Outcome.Problem(R.string.wire_proof_hint)
        WireDeclarationRules.ProofProblem.TooLarge -> Outcome.Problem(R.string.wire_proof_too_large)
        null -> Outcome.Picked(upload)
    }

    private fun guessType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        else -> "application/octet-stream"
    }

    private fun readAll(context: Context, uri: Uri): ByteArray? = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

    internal fun nameAndSize(resolver: ContentResolver, uri: Uri): Pair<String, Long?> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "proof"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { cursor.getString(it) }?.let { name = it }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let { size = cursor.getLong(it) }
            }
        }
        return name to size
    }

    /**
     * Decodes an image (sub-sampled so it never blows the heap), turns it upright per its EXIF
     * orientation, scales it so the longest side is at most [MAX_SIDE_PX] and encodes JPEG [JPEG_QUALITY].
     */
    internal fun jpegFromUri(context: Context, uri: Uri): ByteArray? {
        val bytes = readAll(context, uri) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight)) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = MAX_SIDE_PX.toFloat() / maxOf(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (degrees != 0f) postRotate(degrees)
        }
        val final = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        return ByteArrayOutputStream().use { out ->
            final.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    /** The power-of-two sub-sampling that keeps the decoded longest side at or above [MAX_SIDE_PX]. */
    internal fun sampleSize(longestSide: Int): Int {
        var sample = 1
        while (longestSide / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        return sample
    }
}
