// One issue (mock-up screen 8): header with the summary, "Category · reported dd/mm/yyyy" and the
// status pill; the report card (description + photo thumbnails); the thread — tenant bubbles on
// the right in accent, staff bubbles on the left labelled "Property manager", status changes
// centred; and the composer (photo ≤ 3 + text + send) while the issue is open, else the note that
// replies are closed.

package one.lab5.kleidi.tenant.features.issues

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.endpoints.issue
import one.lab5.kleidi.tenant.api.endpoints.replyToIssue
import one.lab5.kleidi.tenant.api.models.TenantIssueDetail
import one.lab5.kleidi.tenant.api.models.TenantIssueMessage
import one.lab5.kleidi.tenant.core.IssueRules
import one.lab5.kleidi.tenant.core.IssueStatus
import one.lab5.kleidi.tenant.core.KleidiDateFormatter
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.shell.errorMessageRes
import one.lab5.kleidi.tenant.ui.CardShape
import one.lab5.kleidi.tenant.ui.ErrorBanner
import one.lab5.kleidi.tenant.ui.Loadable
import one.lab5.kleidi.tenant.ui.LoadableView
import one.lab5.kleidi.tenant.ui.Pill
import one.lab5.kleidi.tenant.ui.PillTone
import one.lab5.kleidi.tenant.ui.TenantCard
import one.lab5.kleidi.tenant.ui.TenantTheme

private val BubbleMaxWidth = 300.dp
private val ThumbSize = 64.dp

/** [onReportAgain] is the "Report it again" of a resolved / closed issue (same lease). */
@Composable
fun IssueDetailScreen(issueId: String, onBack: () -> Unit, onReportAgain: (leaseId: String) -> Unit) {
    val app = LocalAppModel.current
    val resources = LocalResources.current
    val c = TenantTheme.colors
    var state by remember { mutableStateOf<Loadable<TenantIssueDetail>>(Loadable.Loading) }
    val cache = rememberIssuePhotoCache(issueId)
    var viewing by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        state = Loadable.run { app.client.issue(issueId) }
    }
    LaunchedEffect(issueId) { if (state == Loadable.Loading) load() }
    BackHandler(onBack = onBack)

    val detail = state.valueOrNull
    Scaffold(
        containerColor = c.ground,
        topBar = { Header(detail, onBack) },
        bottomBar = {
            if (detail != null) {
                Box(Modifier.imePadding()) {
                    if (detail.canReply) {
                        Composer(
                            issueId = issueId,
                            onSent = { message ->
                                // Re-read the thread (seen state, any staff reply meanwhile); fall back to appending.
                                state = Loadable.run { app.client.issue(issueId) }.let { reloaded ->
                                    if (reloaded is Loadable.Loaded) reloaded else Loadable.Loaded(detail.copy(messages = detail.messages + message))
                                }
                            },
                        )
                    } else {
                        ClosedNote(onReportAgain = { onReportAgain(detail.leaseId) })
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LoadableView(
                state = state,
                retry = { state = Loadable.Loading; load() },
                errorTitle = stringResource(R.string.common_error_title),
                errorMessage = { resources.getString(errorMessageRes(it)) },
                retryLabel = stringResource(R.string.common_retry),
            ) { loaded ->
                Thread(loaded, cache, onViewPhoto = { viewing = it })
            }
        }
    }

    viewing?.let { documentId -> IssuePhotoViewerDialog(cache, documentId, onDismiss = { viewing = null }) }
}

@Composable
private fun Header(detail: TenantIssueDetail?, onBack: () -> Unit) {
    val c = TenantTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.report_back), tint = c.ink)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    detail?.summary?.ifBlank { null } ?: stringResource(R.string.issue_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail != null) {
                    Text(
                        "${detail.issueCategory.label()} · ${stringResource(R.string.issues_reported_on, KleidiDateFormatter.date(detail.createdAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.secondaryText,
                    )
                }
            }
            if (detail != null) {
                Spacer(Modifier.width(8.dp))
                Pill(detail.issueStatus.label(), tone = IssueLabels.statusTone(detail.issueStatus))
            }
        }
        HorizontalDivider(color = c.hairline)
    }
}

/** The report card, "Received · …", then every tenant-visible message and status change. */
@Composable
private fun Thread(detail: TenantIssueDetail, cache: IssuePhotoCache, onViewPhoto: (documentId: String) -> Unit) {
    val c = TenantTheme.colors
    val listState = rememberLazyListState()
    LaunchedEffect(detail.messages.size) {
        if (detail.messages.isNotEmpty()) listState.animateScrollToItem(detail.messages.size + 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutcomeLine(detail)
                TenantCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(detail.description, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    if (detail.photoDocumentIds.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        PhotoStrip(detail.photoDocumentIds, cache, onViewPhoto)
                    }
                }
                CentredLine(stringResource(R.string.issue_received_at, KleidiDateFormatter.dateTime(detail.createdAt)), c.secondaryText)
            }
        }
        items(detail.messages, key = { it.id }) { message ->
            Box(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                if (message.isStatusEvent) StatusEvent(message) else Bubble(message, cache, onViewPhoto)
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

/** "Resolved on dd/mm/yyyy" / "Closed by management · reason" under the header, once the issue is over. */
@Composable
private fun OutcomeLine(detail: TenantIssueDetail) {
    val c = TenantTheme.colors
    when (detail.issueStatus) {
        IssueStatus.Resolved -> Text(
            stringResource(R.string.issue_resolved_on, KleidiDateFormatter.date(detail.resolvedAt ?: detail.lastActivityAt)),
            style = MaterialTheme.typography.bodySmall, color = c.success, fontWeight = FontWeight.SemiBold,
        )
        IssueStatus.Closed -> Text(
            stringResource(R.string.issue_closed_by_management, detail.closeReason?.takeIf { it.isNotBlank() } ?: IssueStatus.Closed.label()),
            style = MaterialTheme.typography.bodySmall, color = c.secondaryText, fontWeight = FontWeight.SemiBold,
        )
        else -> Unit
    }
}

@Composable
private fun CentredLine(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
}

/** A status change: "Status changed to In progress · dd/mm/yyyy", or "Closed by management · reason". */
@Composable
private fun StatusEvent(message: TenantIssueMessage) {
    val c = TenantTheme.colors
    val status = message.newStatus
    val color = when (status?.let(IssueLabels::statusTone)) {
        PillTone.Warning -> c.warning
        PillTone.Success -> c.success
        PillTone.Accent -> c.accent
        else -> c.secondaryText
    }
    val text = if (status == IssueStatus.Closed) {
        stringResource(R.string.issue_closed_by_management, message.closeReason?.takeIf { it.isNotBlank() } ?: KleidiDateFormatter.date(message.createdAt))
    } else {
        stringResource(R.string.issue_status_changed, status?.label() ?: message.body.orEmpty(), KleidiDateFormatter.date(message.createdAt))
    }
    CentredLine(text, color)
}

/** A tenant message on the right in accent, a staff message on the left on white. */
@Composable
private fun Bubble(message: TenantIssueMessage, cache: IssuePhotoCache, onViewPhoto: (documentId: String) -> Unit) {
    val c = TenantTheme.colors
    val mine = message.isFromTenant
    val shape = if (mine) RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp) else RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            Modifier
                .widthIn(max = BubbleMaxWidth)
                .clip(shape)
                .background(if (mine) c.accent else c.ground)
                .then(if (mine) Modifier else Modifier.border(1.dp, c.hairline, shape))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            message.body?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = if (mine) Color.White else c.ink)
            }
            if (message.photoDocumentIds.isNotEmpty()) PhotoStrip(message.photoDocumentIds, cache, onViewPhoto)
        }
        Text(
            "${stringResource(if (mine) R.string.issue_author_you else R.string.issue_author_staff)} · ${KleidiDateFormatter.dateTime(message.createdAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = c.secondaryText,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun PhotoStrip(documentIds: List<String>, cache: IssuePhotoCache, onViewPhoto: (documentId: String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        documentIds.forEach { id -> IssuePhotoThumbnail(cache, id, ThumbSize, onClick = { onViewPhoto(id) }) }
    }
}

/** Photo (≤ 3) · text · send. The picked photos show in a strip above until sent. */
@Composable
private fun Composer(issueId: String, onSent: suspend (TenantIssueMessage) -> Unit) {
    val app = LocalAppModel.current
    val scope = rememberCoroutineScope()
    val c = TenantTheme.colors
    var body by rememberSaveable { mutableStateOf("") }
    val photos = remember { mutableStateListOf<PickedPhoto>() }
    var sending by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var sourceMenuOpen by remember { mutableStateOf(false) }
    val picker = rememberIssuePhotoPicker(
        onPick = { errorRes = null; photos += it },
        onProblem = { errorRes = it },
    )
    val canSend = IssueRules.canSendReply(body, photos.size) && !sending
    val canAddPhoto = IssueRules.canAddPhoto(photos.size, IssueRules.MAX_REPLY_PHOTOS) && !sending

    fun send() {
        if (!canSend) return
        errorRes = null
        scope.launch {
            sending = true
            try {
                val created = app.client.replyToIssue(issueId, body.trim().take(IssueRules.MESSAGE_MAX_LENGTH), photos.map { it.upload })
                body = ""
                photos.clear()
                onSent(created)
            } catch (e: Throwable) {
                errorRes = IssueLabels.replyErrorMessageRes(e)
            } finally {
                sending = false
            }
        }
    }

    Column(Modifier.fillMaxWidth().background(c.ground)) {
        HorizontalDivider(color = c.hairline)
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            errorRes?.let { ErrorBanner(stringResource(it)) }
            if (photos.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEach { photo ->
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(c.surface)) {
                            photo.thumbnail?.let {
                                Image(it.asImageBitmap(), contentDescription = stringResource(R.string.issue_photo), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp).background(c.ink.copy(alpha = 0.7f), CircleShape).clickable(enabled = !sending) { photos.remove(photo) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.report_remove_photo), tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    IconButton(
                        onClick = { sourceMenuOpen = true },
                        enabled = canAddPhoto,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, c.hairline, RoundedCornerShape(12.dp)),
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = stringResource(R.string.issue_add_photo), tint = if (canAddPhoto) c.ink else c.secondaryText)
                    }
                    PhotoSourceMenu(expanded = sourceMenuOpen, onDismiss = { sourceMenuOpen = false }, onPick = { picker.launch(it) })
                }
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(IssueRules.MESSAGE_MAX_LENGTH) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    enabled = !sending,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(stringResource(R.string.issue_message_placeholder), color = c.secondaryText) },
                )
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (canSend) c.accent else c.surface)
                        .clickable(enabled = canSend, onClick = ::send),
                    contentAlignment = Alignment.Center,
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.secondaryText)
                    else Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.issue_send), tint = if (canSend) Color.White else c.secondaryText, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Replies are over: "Once the issue is resolved, replies close. Still a problem? Report it again." */
@Composable
private fun ClosedNote(onReportAgain: () -> Unit) {
    val c = TenantTheme.colors
    Column(Modifier.fillMaxWidth().background(c.ground)) {
        HorizontalDivider(color = c.hairline)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.issue_replies_closed), style = MaterialTheme.typography.bodySmall, color = c.secondaryText, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.issue_report_again),
                style = MaterialTheme.typography.bodySmall,
                color = c.accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CardShape).clickable(onClick = onReportAgain).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
