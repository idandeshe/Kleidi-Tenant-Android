// "View proof": downloads the proof with the Bearer token (a plain link would have no session) and
// shows it in a full-screen dialog — an image as it is, a PDF page by page through PdfRenderer.

package one.lab5.kleidi.tenant.features.payments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.endpoints.ProofFile
import one.lab5.kleidi.tenant.api.endpoints.wireProof
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.shell.errorMessageRes
import one.lab5.kleidi.tenant.ui.ErrorBanner
import one.lab5.kleidi.tenant.ui.Loadable
import one.lab5.kleidi.tenant.ui.TenantTheme
import java.io.File

@Composable
fun ProofViewerDialog(declarationId: String, onDismiss: () -> Unit) {
    val app = LocalAppModel.current
    val context = LocalContext.current
    var state by remember { mutableStateOf<Loadable<List<Bitmap>>>(Loadable.Loading) }

    LaunchedEffect(declarationId) {
        state = Loadable.run {
            val proof = app.client.wireProof(declarationId)
            withContext(Dispatchers.IO) { ProofRendering.pages(context, declarationId, proof) }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val c = TenantTheme.colors
        Column(Modifier.fillMaxSize().background(c.ground).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wire_proof_label), style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_close), tint = c.ink)
                }
            }
            when (val s = state) {
                Loadable.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
                is Loadable.Failed -> ErrorBanner(stringResource(errorMessageRes(s.error)), Modifier.padding(20.dp))
                is Loadable.Loaded -> LazyColumn(
                    Modifier.fillMaxSize().background(c.surface),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(s.value) { page ->
                        Image(page.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().background(c.ground), contentScale = ContentScale.FillWidth)
                    }
                }
            }
        }
    }
}

/** Turns the downloaded bytes into bitmaps to show: one for an image, one per page for a PDF. */
object ProofRendering {
    private const val MAX_PAGES = 30
    private const val PAGE_WIDTH_PX = 1400
    private const val MAX_IMAGE_SIDE_PX = 2500

    fun pages(context: Context, declarationId: String, proof: ProofFile): List<Bitmap> =
        if (proof.isPdf) pdfPages(context, declarationId, proof.bytes) else listOf(image(proof.bytes))

    private fun image(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE_PX) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalStateException("Not an image")
    }

    /** PdfRenderer needs a file descriptor: the bytes go to the cache (private, cleared with it). */
    private fun pdfPages(context: Context, declarationId: String, bytes: ByteArray): List<Bitmap> {
        val dir = File(context.cacheDir, "proofs").apply { mkdirs() }
        val file = File(dir, "$declarationId.pdf")
        file.writeBytes(bytes)
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    (0 until minOf(renderer.pageCount, MAX_PAGES)).map { index ->
                        renderer.openPage(index).use { page ->
                            val height = (PAGE_WIDTH_PX.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                            createBitmap(PAGE_WIDTH_PX, height).also { bitmap ->
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
    }
}
