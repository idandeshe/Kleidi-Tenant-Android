// Photos of an issue: a thumbnail that downloads its photo with the Bearer token (a plain URL
// would have no session) and a full-screen viewer dialog. Downloaded photos are kept in a small
// per-issue cache so the thread does not fetch the same picture twice.

package one.lab5.kleidi.tenant.features.issues

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.ApiClient
import one.lab5.kleidi.tenant.api.endpoints.issuePhoto
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.shell.errorMessageRes
import one.lab5.kleidi.tenant.ui.ErrorBanner
import one.lab5.kleidi.tenant.ui.Loadable
import one.lab5.kleidi.tenant.ui.TenantTheme

/** Downloaded photos of one issue, by document id; lives as long as the detail screen. */
@Stable
class IssuePhotoCache(private val client: ApiClient, private val issueId: String) {
    private val photos = mutableStateMapOf<String, Loadable<Bitmap>>()

    fun state(documentId: String): Loadable<Bitmap> = photos[documentId] ?: Loadable.Loading

    suspend fun load(documentId: String) {
        if (photos[documentId] is Loadable.Loaded) return
        photos[documentId] = Loadable.Loading
        photos[documentId] = Loadable.run {
            val photo = client.issuePhoto(issueId, documentId)
            withContext(Dispatchers.IO) { IssuePhotoDecoding.bitmap(photo.bytes) }
        }
    }
}

@Composable
fun rememberIssuePhotoCache(issueId: String): IssuePhotoCache {
    val client = LocalAppModel.current.client
    return remember(issueId) { IssuePhotoCache(client, issueId) }
}

/** A square thumbnail of one photo; a grey box while it loads, a broken-image icon when it cannot. */
@Composable
fun IssuePhotoThumbnail(cache: IssuePhotoCache, documentId: String, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = TenantTheme.colors
    LaunchedEffect(documentId) { cache.load(documentId) }
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(c.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = cache.state(documentId)) {
            Loadable.Loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.secondaryText)
            is Loadable.Failed -> Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = c.secondaryText)
            is Loadable.Loaded -> Image(
                state.value.asImageBitmap(),
                contentDescription = stringResource(R.string.issue_photo),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** The photo full-screen, scrollable when taller than the screen. */
@Composable
fun IssuePhotoViewerDialog(cache: IssuePhotoCache, documentId: String, onDismiss: () -> Unit) {
    LaunchedEffect(documentId) { cache.load(documentId) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val c = TenantTheme.colors
        Column(Modifier.fillMaxSize().background(c.ground).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.issue_photo), style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_close), tint = c.ink)
                }
            }
            when (val s = cache.state(documentId)) {
                Loadable.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
                is Loadable.Failed -> ErrorBanner(stringResource(errorMessageRes(s.error)), Modifier.padding(20.dp))
                is Loadable.Loaded -> Box(Modifier.fillMaxSize().background(c.surface).verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                    Image(s.value.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                }
            }
        }
    }
}

object IssuePhotoDecoding {
    private const val MAX_SIDE_PX = 2500

    /** Decodes the bytes sub-sampled so the longest side is at most ~[MAX_SIDE_PX] (never blows the heap). */
    fun bitmap(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalStateException("Not an image")
    }

    /** A small bitmap of a photo about to be uploaded (the grids of the report form and the composer). */
    fun thumbnail(bytes: ByteArray, maxSidePx: Int = 320): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
