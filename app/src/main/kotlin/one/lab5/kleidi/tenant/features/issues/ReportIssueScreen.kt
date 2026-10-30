// "Report an issue" (mock-up screen 6): the category grid, "Describe the problem", up to five
// photos (camera / gallery, re-encoded as upright JPEG by ProofImport), the urgent checkbox and
// "Send to property manager". On success the new issue's thread opens.

package one.lab5.kleidi.tenant.features.issues

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.endpoints.PhotoUpload
import one.lab5.kleidi.tenant.api.endpoints.ProofUpload
import one.lab5.kleidi.tenant.api.endpoints.ReportIssueInput
import one.lab5.kleidi.tenant.api.endpoints.reportIssue
import one.lab5.kleidi.tenant.core.IssueCategory
import one.lab5.kleidi.tenant.core.IssueRules
import one.lab5.kleidi.tenant.features.payments.ProofSource
import one.lab5.kleidi.tenant.features.payments.rememberProofPicker
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.ui.ErrorBanner
import one.lab5.kleidi.tenant.ui.PrimaryButton
import one.lab5.kleidi.tenant.ui.TenantTheme

/** A photo picked for upload, with the small bitmap the grid shows. */
class PickedPhoto(val upload: PhotoUpload, val thumbnail: Bitmap?)

/** [onReported] opens the thread of the new issue; [onCancel] goes back without reporting. */
@Composable
fun ReportIssueScreen(leaseId: String, onCancel: () -> Unit, onReported: (issueId: String) -> Unit) {
    val app = LocalAppModel.current
    val scope = rememberCoroutineScope()
    val c = TenantTheme.colors

    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    var isUrgent by rememberSaveable { mutableStateOf(false) }
    val photos = remember { mutableStateListOf<PickedPhoto>() }
    var errorRes by rememberSaveable { mutableStateOf<Int?>(null) }
    var submitting by rememberSaveable { mutableStateOf(false) }
    var sourceMenuOpen by remember { mutableStateOf(false) }

    val picker = rememberIssuePhotoPicker(
        onPick = { errorRes = null; photos += it },
        onProblem = { errorRes = it },
    )

    fun submit() {
        errorRes = null
        val picked = category ?: run { errorRes = R.string.report_category_required; return }
        val text = IssueRules.descriptionField(description) ?: run { errorRes = R.string.report_description_required; return }
        scope.launch {
            submitting = true
            try {
                val created = app.client.reportIssue(leaseId, ReportIssueInput(picked, text, isUrgent), photos.map { it.upload })
                onReported(created.id)
            } catch (e: Throwable) {
                errorRes = IssueLabels.reportErrorMessageRes(e)
            } finally {
                submitting = false
            }
        }
    }

    BackHandler(enabled = !submitting, onBack = onCancel)

    Scaffold(containerColor = c.ground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel, enabled = !submitting) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.report_back), tint = c.ink)
                }
                Text(stringResource(R.string.report_title), style = MaterialTheme.typography.headlineSmall, color = c.ink)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    errorRes?.let { ErrorBanner(stringResource(it)) }

                    // Category
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel(stringResource(R.string.report_category_label))
                        CategoryGrid(selected = category?.let(::IssueCategory), onSelect = { category = it.raw })
                    }

                    // Description
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel(stringResource(R.string.report_description_label))
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it.take(IssueRules.DESCRIPTION_MAX_LENGTH) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 8,
                            placeholder = { Text(stringResource(R.string.report_description_placeholder), color = c.secondaryText) },
                        )
                    }

                    // Photos
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FieldLabel(stringResource(R.string.report_photos_label))
                            Text(
                                stringResource(R.string.report_photos_hint, IssueRules.MAX_REPORT_PHOTOS),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.secondaryText,
                            )
                        }
                        PhotoGrid(
                            photos = photos,
                            canAdd = IssueRules.canAddPhoto(photos.size, IssueRules.MAX_REPORT_PHOTOS) && !submitting,
                            onAdd = { sourceMenuOpen = true },
                            onRemove = { photos.remove(it) },
                            sourceMenu = {
                                PhotoSourceMenu(expanded = sourceMenuOpen, onDismiss = { sourceMenuOpen = false }, onPick = { picker.launch(it) })
                            },
                        )
                    }

                    // Urgent
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, c.hairline, RoundedCornerShape(12.dp))
                            .clickable(enabled = !submitting) { isUrgent = !isUrgent }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isUrgent, onCheckedChange = { isUrgent = it }, enabled = !submitting)
                        Text(stringResource(R.string.report_urgent), style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.padding(end = 8.dp))
                    }

                    PrimaryButton(
                        stringResource(if (submitting) R.string.report_submitting else R.string.report_submit),
                        onClick = ::submit,
                        loading = submitting,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = TenantTheme.colors.ink)
}

/** Three columns of category tiles: icon over label, the picked one filled accent. */
@Composable
private fun CategoryGrid(selected: IssueCategory?, onSelect: (IssueCategory) -> Unit) {
    val c = TenantTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IssueCategory.ALL.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    val picked = category == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .height(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (picked) c.accentSoft else c.ground)
                            .border(if (picked) 2.dp else 1.dp, if (picked) c.accent else c.hairline, RoundedCornerShape(12.dp))
                            .clickable { onSelect(category) }
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(IssueLabels.categoryIcon(category), contentDescription = null, tint = if (picked) c.accent else c.secondaryText, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            category.label(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (picked) c.accent else c.ink,
                            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Four square tiles per row: the picked photos (tap the X to remove) and the dashed "add" tile. */
@Composable
fun PhotoGrid(
    photos: List<PickedPhoto>,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onRemove: (PickedPhoto) -> Unit,
    sourceMenu: @Composable () -> Unit,
) {
    val c = TenantTheme.colors
    val cells: List<PickedPhoto?> = photos + (if (canAdd) listOf<PickedPhoto?>(null) else emptyList())
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell ->
                    if (cell == null) {
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            AddPhotoTile(onClick = onAdd, modifier = Modifier.fillMaxSize())
                            sourceMenu()
                        }
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(c.surface)) {
                            cell.thumbnail?.let {
                                Image(it.asImageBitmap(), contentDescription = stringResource(R.string.issue_photo), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .background(c.ink.copy(alpha = 0.7f), CircleShape)
                                    .clickable { onRemove(cell) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.report_remove_photo), tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The dashed-border tile with a plus. */
@Composable
fun AddPhotoTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = TenantTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                val inset = 1.dp.toPx() / 2
                drawRoundRect(
                    color = c.secondaryText,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.report_add_photo), tint = c.secondaryText)
    }
}

/** Camera / Gallery — photos only (no Files: a PDF is not a photo). */
@Composable
fun PhotoSourceMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (ProofSource) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.proof_source_camera)) },
            leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
            onClick = { onDismiss(); onPick(ProofSource.Camera) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.proof_source_gallery)) },
            leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
            onClick = { onDismiss(); onPick(ProofSource.Gallery) },
        )
    }
}

/**
 * The proof picker (camera shots and gallery photos come back as upright JPEG / PNG / WebP) with
 * the issue photo limits applied on top: 10 MB, images only.
 */
@Composable
fun rememberIssuePhotoPicker(onPick: (PickedPhoto) -> Unit, onProblem: (Int) -> Unit) = rememberProofPicker(
    onPick = { proof ->
        when (val problem = IssueRules.photoProblem(proof.mimeType, proof.bytes.size.toLong())) {
            null -> onPick(PickedPhoto(proof.asPhotoUpload(), IssuePhotoDecoding.thumbnail(proof.bytes)))
            else -> onProblem(IssueLabels.photoProblemRes(problem))
        }
    },
    onProblem = onProblem,
)

private fun ProofUpload.asPhotoUpload() = PhotoUpload(bytes, fileName.replaceFirst("Proof", "Photo"), mimeType)
