// "Issues" (mock-up screen 7): the "Report an issue" button, then the tenant's issues in two
// groups — Open (received / in progress) and Resolved (resolved / closed) — each row with its
// category icon, summary, status pill, "Plumbing · reported dd/mm/yyyy", the urgent badge and the
// "New reply from your property manager" dot.

package one.lab5.kleidi.tenant.features.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.endpoints.issues
import one.lab5.kleidi.tenant.api.endpoints.leases
import one.lab5.kleidi.tenant.api.models.TenantIssue
import one.lab5.kleidi.tenant.api.models.TenantLease
import one.lab5.kleidi.tenant.core.IssueStatus
import one.lab5.kleidi.tenant.core.KleidiDateFormatter
import one.lab5.kleidi.tenant.core.LeaseFormatting
import one.lab5.kleidi.tenant.features.home.addressLabel
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.shell.errorMessageRes
import one.lab5.kleidi.tenant.ui.CardShape
import one.lab5.kleidi.tenant.ui.EmptyState
import one.lab5.kleidi.tenant.ui.Loadable
import one.lab5.kleidi.tenant.ui.LoadableView
import one.lab5.kleidi.tenant.ui.Pill
import one.lab5.kleidi.tenant.ui.PillTone
import one.lab5.kleidi.tenant.ui.PrimaryButton
import one.lab5.kleidi.tenant.ui.SectionTitle
import one.lab5.kleidi.tenant.ui.TenantTheme

/** The lease the "Report an issue" button is for (null = no lease yet) and every issue of the tenant. */
private class IssuesData(val lease: TenantLease?, val issues: List<TenantIssue>)

/**
 * [onReport] opens the report form for the lease; [onOpen] an issue's thread. Both replace this
 * screen while open, so the list reloads (seen state, new replies) when the tenant comes back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesScreen(onReport: (leaseId: String) -> Unit, onOpen: (issueId: String) -> Unit) {
    val app = LocalAppModel.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var state by remember { mutableStateOf<Loadable<IssuesData>>(Loadable.Loading) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() {
        state = Loadable.run {
            val lease = LeaseFormatting.primaryLease(app.client.leases()) { it.leaseStatus }
            IssuesData(lease, app.client.issues())
        }
    }
    LaunchedEffect(Unit) { if (state == Loadable.Loading) load() }

    LoadableView(
        state = state,
        retry = { state = Loadable.Loading; load() },
        errorTitle = stringResource(R.string.common_error_title),
        errorMessage = { resources.getString(errorMessageRes(it)) },
        retryLabel = stringResource(R.string.common_retry),
    ) { data ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    try { load() } finally { refreshing = false }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            val lease = data.lease
            if (lease == null && data.issues.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.home_no_lease_title),
                    message = stringResource(R.string.home_no_lease_message),
                    icon = Icons.Outlined.HomeWork,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Header(lease)
                        if (lease != null) PrimaryButton(stringResource(R.string.issues_report), onClick = { onReport(lease.id) })
                        if (data.issues.isEmpty()) {
                            EmptyState(
                                title = stringResource(R.string.issues_empty_title),
                                message = stringResource(R.string.issues_empty_message),
                                icon = Icons.Outlined.Build,
                                modifier = Modifier.height(320.dp),
                            )
                        } else {
                            val (open, over) = data.issues.partition { it.issueStatus.isOpen }
                            IssueGroup(stringResource(R.string.issues_open), open, onOpen)
                            IssueGroup(stringResource(R.string.issues_resolved), over, onOpen)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(lease: TenantLease?) {
    val language = LocalAppModel.current.language
    Column {
        Text(stringResource(R.string.issues_title), style = MaterialTheme.typography.headlineMedium, color = TenantTheme.colors.ink)
        lease?.property?.addressLabel(language)?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = TenantTheme.colors.secondaryText)
        }
    }
}

@Composable
private fun IssueGroup(title: String, issues: List<TenantIssue>, onOpen: (issueId: String) -> Unit) {
    if (issues.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title, modifier = Modifier.padding(top = 8.dp))
        issues.forEach { issue -> IssueRow(issue, onClick = { onOpen(issue.id) }) }
    }
}

/** One issue: icon tile, summary + status pill, "Category · reported dd/mm/yyyy", urgent / unread lines. */
@Composable
fun IssueRow(issue: TenantIssue, onClick: () -> Unit) {
    val c = TenantTheme.colors
    val status = issue.issueStatus
    val over = !status.isOpen
    val metaDate = when (status) {
        IssueStatus.Resolved -> stringResource(R.string.issues_resolved_on, KleidiDateFormatter.date(issue.resolvedAt ?: issue.lastActivityAt))
        IssueStatus.Closed -> stringResource(R.string.issues_closed_on, KleidiDateFormatter.date(issue.closedAt ?: issue.lastActivityAt))
        else -> stringResource(R.string.issues_reported_on, KleidiDateFormatter.date(issue.createdAt))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(c.ground)
            .border(1.dp, c.hairline, CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CategoryTile(issue, over)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    issue.summary.ifBlank { issue.issueCategory.label() },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (over) c.secondaryText else c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Pill(status.label(), tone = IssueLabels.statusTone(status))
            }
            Text("${issue.issueCategory.label()} · $metaDate", style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
            if (issue.isUrgent && !over) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = c.error, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.issues_urgent), style = MaterialTheme.typography.bodySmall, color = c.error, fontWeight = FontWeight.SemiBold)
                }
            }
            if (issue.tenantUnread) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(c.accent, CircleShape))
                    Text(stringResource(R.string.issues_new_reply), style = MaterialTheme.typography.bodySmall, color = c.accent, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** The 40 dp rounded tile with the category icon. */
@Composable
private fun CategoryTile(issue: TenantIssue, over: Boolean) {
    val c = TenantTheme.colors
    val (fill, tint) = when (IssueLabels.iconTone(issue.issueStatus, issue.isUrgent)) {
        PillTone.Error -> c.errorSoft to c.error
        PillTone.Neutral -> c.surface to c.secondaryText
        else -> c.accentSoft to c.accent
    }
    Box(Modifier.size(40.dp).background(fill, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(IssueLabels.categoryIcon(issue.issueCategory), contentDescription = null, tint = if (over) c.secondaryText else tint, modifier = Modifier.size(20.dp))
    }
}
