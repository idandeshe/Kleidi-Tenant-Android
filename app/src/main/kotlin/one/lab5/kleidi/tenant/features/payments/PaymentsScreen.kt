// Payments (web `PaymentsPage`): "I paid by bank transfer" (or nothing to declare) with the dashed
// "Pay online — COMING SOON" row, the "Rent" card listing the months, and "Your declarations".

package one.lab5.kleidi.tenant.features.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.endpoints.leases
import one.lab5.kleidi.tenant.api.endpoints.rent
import one.lab5.kleidi.tenant.api.endpoints.wireDeclarations
import one.lab5.kleidi.tenant.api.models.TenantLease
import one.lab5.kleidi.tenant.api.models.TenantRent
import one.lab5.kleidi.tenant.api.models.TenantWireDeclaration
import one.lab5.kleidi.tenant.core.LeaseFormatting
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.shell.errorMessageRes
import one.lab5.kleidi.tenant.ui.DashedCard
import one.lab5.kleidi.tenant.ui.EmptyState
import one.lab5.kleidi.tenant.ui.ErrorBanner
import one.lab5.kleidi.tenant.ui.Loadable
import one.lab5.kleidi.tenant.ui.LoadableView
import one.lab5.kleidi.tenant.ui.Pill
import one.lab5.kleidi.tenant.ui.PillTone
import one.lab5.kleidi.tenant.ui.PrimaryButton
import one.lab5.kleidi.tenant.ui.RowDivider
import one.lab5.kleidi.tenant.ui.TenantCard
import one.lab5.kleidi.tenant.ui.TenantTheme

/** What the screen shows for the lease it is about; null lease = "No lease yet". */
private class PaymentsData(val lease: TenantLease?, val rent: TenantRent?, val history: Loadable<List<TenantWireDeclaration>>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(onDeclare: (leaseId: String) -> Unit) {
    val app = LocalAppModel.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var state by remember { mutableStateOf<Loadable<PaymentsData>>(Loadable.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    var proofOf by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        state = Loadable.run {
            val lease = LeaseFormatting.primaryLease(app.client.leases()) { it.leaseStatus }
                ?: return@run PaymentsData(null, null, Loadable.Loaded(emptyList()))
            coroutineScope {
                val rent = async { app.client.rent(lease.id) }
                // The history is secondary: its failure shows inside its card, not instead of the screen.
                val history = async { Loadable.run { app.client.wireDeclarations(lease.id) } }
                PaymentsData(lease, rent.await(), history.await())
            }
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
            val rent = data.rent
            if (lease == null || rent == null) {
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
                        Text(stringResource(R.string.tab_payments), style = MaterialTheme.typography.headlineMedium, color = TenantTheme.colors.ink)
                        TenantCard { PaymentActions(rent, onDeclare = { onDeclare(lease.id) }, showHint = false) }
                        RentCard(rent)
                        HistoryCard(data.history, onViewProof = { proofOf = it })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    proofOf?.let { id -> ProofViewerDialog(id, onDismiss = { proofOf = null }) }
}

/**
 * The payment actions of Home and Payments: "I paid by bank transfer" when a month can be
 * declared (Home adds the hint under it), "Nothing to declare" otherwise, and the dashed
 * "Pay online — COMING SOON" row. [rent] null = the months are still loading.
 */
@Composable
fun PaymentActions(rent: TenantRent?, onDeclare: () -> Unit, showHint: Boolean) {
    val c = TenantTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            rent == null -> PrimaryButton(stringResource(R.string.home_paid_transfer), onClick = {}, enabled = false)
            rent.canDeclare -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Same wording on both screens (web `home.paidByTransfer` / `rent.declare`).
                PrimaryButton(stringResource(if (showHint) R.string.home_paid_transfer else R.string.rent_declare), onClick = onDeclare)
                if (showHint) {
                    Text(
                        stringResource(R.string.home_paid_transfer_soon),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.secondaryText,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            else -> Text(stringResource(R.string.rent_nothing_to_declare), style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
        }
        DashedCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_pay_online), style = MaterialTheme.typography.titleMedium, color = c.ink)
                Spacer(Modifier.width(10.dp))
                Pill(stringResource(R.string.home_coming_soon), tone = PillTone.Neutral, uppercase = true)
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.home_pay_online_note), style = MaterialTheme.typography.bodyMedium, color = c.secondaryText)
        }
    }
}

/** "Rent": every month of the lease, newest first. */
@Composable
private fun RentCard(rent: TenantRent) {
    TenantCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.rent_title), style = MaterialTheme.typography.titleMedium, color = TenantTheme.colors.ink)
        rent.months.forEachIndexed { index, month ->
            if (index > 0) RowDivider(inset = false)
            RentMonthRow(month, rent.currencyCode)
        }
    }
}

/** "Your declarations": the tenant's declarations on this lease, newest first. */
@Composable
private fun HistoryCard(history: Loadable<List<TenantWireDeclaration>>, onViewProof: (declarationId: String) -> Unit) {
    val c = TenantTheme.colors
    TenantCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.rent_history), style = MaterialTheme.typography.titleMedium, color = c.ink)
        when (history) {
            Loadable.Loading -> Spacer(Modifier.height(8.dp))
            is Loadable.Failed -> ErrorBanner(stringResource(errorMessageRes(history.error)), Modifier.padding(top = 8.dp))
            is Loadable.Loaded -> {
                if (history.value.isEmpty()) {
                    Text(stringResource(R.string.rent_no_history), style = MaterialTheme.typography.bodySmall, color = c.secondaryText, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                history.value.forEachIndexed { index, declaration ->
                    if (index > 0) RowDivider(inset = false)
                    DeclarationRow(declaration, onViewProof = { onViewProof(declaration.id) })
                }
            }
        }
    }
}
