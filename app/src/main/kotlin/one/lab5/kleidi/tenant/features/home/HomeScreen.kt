// "My lease": the rent card for this month (with the real status of the newest rent month),
// "I paid by bank transfer" / "Pay online — coming soon", the lease details grid and the
// "Report an issue" row.

package one.lab5.kleidi.tenant.features.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.endpoints.leases
import one.lab5.kleidi.tenant.api.endpoints.rent
import one.lab5.kleidi.tenant.api.models.Tenant
import one.lab5.kleidi.tenant.api.models.TenantLease
import one.lab5.kleidi.tenant.api.models.TenantRent
import one.lab5.kleidi.tenant.core.KleidiDateFormatter
import one.lab5.kleidi.tenant.core.LeaseFormatting
import one.lab5.kleidi.tenant.features.payments.PaymentActions
import one.lab5.kleidi.tenant.features.payments.RentLabels
import one.lab5.kleidi.tenant.features.payments.statusLabel
import one.lab5.kleidi.tenant.shell.BrandMark
import one.lab5.kleidi.tenant.shell.BrandMarkSize
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.shell.errorMessageRes
import one.lab5.kleidi.tenant.ui.DetailCell
import one.lab5.kleidi.tenant.ui.EmptyState
import one.lab5.kleidi.tenant.ui.InfoNote
import one.lab5.kleidi.tenant.ui.Loadable
import one.lab5.kleidi.tenant.ui.LoadableView
import one.lab5.kleidi.tenant.ui.Pill
import one.lab5.kleidi.tenant.ui.PillTone
import one.lab5.kleidi.tenant.ui.SectionTitle
import one.lab5.kleidi.tenant.ui.TenantCard
import one.lab5.kleidi.tenant.ui.TenantTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(tenant: Tenant, onReportIssue: (leaseId: String) -> Unit, onDeclare: (leaseId: String) -> Unit) {
    val app = LocalAppModel.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var state by remember { mutableStateOf<Loadable<List<TenantLease>>>(Loadable.Loading) }
    // The rent months per lease (the pill and the declare button); best effort — the lease shows without them.
    val rents = remember { mutableStateMapOf<String, TenantRent>() }
    var refreshing by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    suspend fun loadRent(leaseId: String) {
        runCatching { app.client.rent(leaseId) }.getOrNull()?.let { rents[leaseId] = it }
    }
    suspend fun load() {
        state = Loadable.run { app.client.leases() }
        rents.keys.toList().forEach { loadRent(it) }
    }
    LaunchedEffect(Unit) { if (state == Loadable.Loading) load() }

    LoadableView(
        state = state,
        retry = { state = Loadable.Loading; load() },
        errorTitle = stringResource(R.string.common_error_title),
        errorMessage = { resources.getString(errorMessageRes(it)) },
        retryLabel = stringResource(R.string.common_retry),
    ) { leases ->
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
            val lease = leases.firstOrNull { it.id == selectedId } ?: LeaseFormatting.primaryLease(leases) { it.leaseStatus }
            if (lease == null) {
                EmptyState(
                    title = stringResource(R.string.home_no_lease_title),
                    message = stringResource(R.string.home_no_lease_message),
                    icon = Icons.Outlined.HomeWork,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        LaunchedEffect(lease.id) { if (lease.id !in rents) loadRent(lease.id) }
                        val rent = rents[lease.id]
                        Header(tenant)
                        if (leases.size > 1) LeaseChooser(leases, lease, onSelect = { selectedId = it.id })
                        RentCard(lease, rent)
                        PaymentActions(rent, onDeclare = { onDeclare(lease.id) }, showHint = true)
                        LeaseDetails(lease)
                        ReportIssueRow(onClick = { onReportIssue(lease.id) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(tenant: Tenant) {
    Column {
        BrandMark(BrandMarkSize.Small, Modifier.padding(bottom = 12.dp))
        Text(stringResource(R.string.home_greeting, tenant.firstName), style = MaterialTheme.typography.bodyLarge, color = TenantTheme.colors.secondaryText)
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium, color = TenantTheme.colors.ink)
    }
}

/** Several leases (a tenant on more than one, or an old and a new one): pick which to look at. */
@Composable
private fun LeaseChooser(leases: List<TenantLease>, selected: TenantLease, onSelect: (TenantLease) -> Unit) {
    val language = LocalAppModel.current.language
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        leases.forEach { lease ->
            FilterChip(
                selected = lease.id == selected.id,
                onClick = { onSelect(lease) },
                label = { Text(lease.property.streetLine ?: lease.property.addressLabel(language).ifBlank { lease.leaseStatus.label() }) },
            )
        }
    }
}

/**
 * "€ 800 · Rent for October 2026", with the status pill of the newest rent month ("Paid",
 * "Wire confirmed by property owner", "Due"…) — or the due-day pill until the months are known.
 */
@Composable
private fun RentCard(lease: TenantLease, rent: TenantRent?) {
    val language = LocalAppModel.current.language
    val month = LeaseFormatting.rentMonth(LocalDate.now())
    val c = TenantTheme.colors
    TenantCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                LeaseFormatting.money(lease.monthlyRent, lease.currencyCode, language),
                style = MaterialTheme.typography.displaySmall,
                color = c.ink,
            )
            Spacer(Modifier.weight(1f))
            val thisMonth = rent?.newestMonth
            if (thisMonth != null) {
                Pill(thisMonth.statusLabel(), tone = RentLabels.monthTone(thisMonth.monthStatus), modifier = Modifier.widthIn(max = 180.dp), singleLine = false)
            } else {
                lease.paymentDayOfMonth?.let { day ->
                    Pill(stringResource(R.string.home_due_pill, LeaseFormatting.dayOfMonthLabel(day, language)))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.home_rent_for, KleidiDateFormatter.month(month, language)),
            style = MaterialTheme.typography.bodyLarge,
            color = c.secondaryText,
        )
        lease.upcomingRent?.let { next ->
            Spacer(Modifier.height(12.dp))
            InfoNote(
                stringResource(
                    R.string.home_upcoming_rent,
                    KleidiDateFormatter.date(next.effectiveFrom),
                    LeaseFormatting.money(next.monthlyRent, lease.currencyCode, language),
                ),
            )
        }
        if (lease.isPastEnd) {
            Spacer(Modifier.height(12.dp))
            InfoNote(stringResource(R.string.home_past_end), tone = PillTone.Warning)
        }
    }
}

/** "Your lease": two-column grid of the lease's details. */
@Composable
private fun LeaseDetails(lease: TenantLease) {
    val language = LocalAppModel.current.language
    val dash = notSet()
    Column {
        SectionTitle(stringResource(R.string.home_your_lease))
        TenantCard {
            DetailCell(stringResource(R.string.lease_address), lease.property.addressLabel(language).ifBlank { dash }, emphasis = true)
            Spacer(Modifier.height(16.dp))
            DetailGrid(
                listOf(
                    stringResource(R.string.lease_status) to lease.leaseStatus.label(),
                    stringResource(R.string.lease_rent) to LeaseFormatting.money(lease.monthlyRent, lease.currencyCode, language),
                    stringResource(R.string.lease_start) to KleidiDateFormatter.date(lease.startDate),
                    stringResource(R.string.lease_end) to (lease.endDate?.let { KleidiDateFormatter.date(it) } ?: stringResource(R.string.lease_end_open)),
                    stringResource(R.string.lease_deposit) to (LeaseFormatting.money(lease.depositAmount, lease.currencyCode, language).ifBlank { dash }),
                    stringResource(R.string.lease_payment_day) to (lease.paymentDayOfMonth?.let {
                        stringResource(R.string.lease_payment_day_value, LeaseFormatting.dayOfMonthLabel(it, language))
                    } ?: dash),
                    stringResource(R.string.lease_payment_method) to (lease.payment?.label() ?: dash),
                    stringResource(R.string.lease_notice_period) to (lease.noticePeriodMonths?.let { pluralStringResource(R.plurals.lease_notice_months, it, it) } ?: dash),
                    stringResource(R.string.lease_notice_deadline) to (lease.noticeDeadline?.let { KleidiDateFormatter.date(it) } ?: dash),
                ) + (lease.upcomingRent?.let {
                    listOf(stringResource(R.string.lease_upcoming_rent) to LeaseFormatting.money(it.monthlyRent, lease.currencyCode, language))
                } ?: emptyList()),
            )
        }
    }
}

@Composable
private fun DetailGrid(cells: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) -> DetailCell(label, value, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReportIssueRow(onClick: () -> Unit) {
    val c = TenantTheme.colors
    TenantCard(modifier = Modifier.clickable(onClick = onClick), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Build, contentDescription = null, tint = c.accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_report_issue_title), style = MaterialTheme.typography.bodyMedium, color = c.secondaryText)
                Text(stringResource(R.string.home_report_issue_action), style = MaterialTheme.typography.titleMedium, color = c.ink)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = null, tint = c.hairline, modifier = Modifier.width(14.dp))
        }
    }
}
