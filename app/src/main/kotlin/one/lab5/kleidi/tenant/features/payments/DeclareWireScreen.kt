// "I paid by bank transfer" (web `DeclareWirePage`): pick the months, say how much and when,
// attach the bank confirmation (required) and send. The declaration goes to the property
// manager's review queue; the months show as "declared" until then.

package one.lab5.kleidi.tenant.features.payments

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.endpoints.DeclareWireInput
import one.lab5.kleidi.tenant.api.endpoints.ProofUpload
import one.lab5.kleidi.tenant.api.endpoints.declareWire
import one.lab5.kleidi.tenant.api.endpoints.rent
import one.lab5.kleidi.tenant.api.models.TenantRent
import one.lab5.kleidi.tenant.api.models.TenantRentMonth
import one.lab5.kleidi.tenant.core.KleidiDateFormatter
import one.lab5.kleidi.tenant.core.LeaseFormatting
import one.lab5.kleidi.tenant.core.RentFormatting
import one.lab5.kleidi.tenant.core.WireDeclarationRules
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.shell.errorMessageRes
import one.lab5.kleidi.tenant.ui.CardShape
import one.lab5.kleidi.tenant.ui.ErrorBanner
import one.lab5.kleidi.tenant.ui.InfoNote
import one.lab5.kleidi.tenant.ui.Loadable
import one.lab5.kleidi.tenant.ui.LoadableView
import one.lab5.kleidi.tenant.ui.PrimaryButton
import one.lab5.kleidi.tenant.ui.SecondaryButton
import one.lab5.kleidi.tenant.ui.TenantCard
import one.lab5.kleidi.tenant.ui.TenantTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * [onCancel] returns to where the tenant came from; after a successful declaration the done state
 * offers [onOpenPayments] and [onBackHome].
 */
@Composable
fun DeclareWireScreen(leaseId: String, onCancel: () -> Unit, onOpenPayments: () -> Unit, onBackHome: () -> Unit) {
    val app = LocalAppModel.current
    val resources = LocalResources.current
    var state by remember { mutableStateOf<Loadable<TenantRent>>(Loadable.Loading) }
    var done by rememberSaveable { mutableStateOf(false) }

    suspend fun load() {
        state = Loadable.run { app.client.rent(leaseId) }
    }
    LaunchedEffect(Unit) { if (state == Loadable.Loading) load() }
    BackHandler { if (done) onOpenPayments() else onCancel() }

    Scaffold(containerColor = TenantTheme.colors.ground) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (done) {
                DoneState(onOpenPayments, onBackHome)
            } else {
                Column(Modifier.fillMaxSize()) {
                    Header(onCancel)
                    LoadableView(
                        state = state,
                        retry = { state = Loadable.Loading; load() },
                        errorTitle = stringResource(R.string.common_error_title),
                        errorMessage = { resources.getString(errorMessageRes(it)) },
                        retryLabel = stringResource(R.string.common_retry),
                    ) { rent ->
                        DeclareForm(leaseId, rent, onCancel = onCancel, onDone = { done = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    val c = TenantTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.wire_cancel), tint = c.ink)
        }
        Text(stringResource(R.string.wire_title), style = MaterialTheme.typography.headlineSmall, color = c.ink)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeclareForm(leaseId: String, rent: TenantRent, onCancel: () -> Unit, onDone: () -> Unit) {
    val app = LocalAppModel.current
    val scope = rememberCoroutineScope()
    val language = app.language
    val c = TenantTheme.colors
    val declarable = remember(rent) { rent.declarableMonths }
    val expectedByPeriod = remember(declarable) { declarable.associate { it.period to it.expectedAmount } }

    var periods by rememberSaveable { mutableStateOf(listOf<String>()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var amountTouched by rememberSaveable { mutableStateOf(false) }
    var transferDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var reference by rememberSaveable { mutableStateOf("") }
    var proof by remember { mutableStateOf<ProofUpload?>(null) }
    var errorRes by rememberSaveable { mutableStateOf<Int?>(null) }
    var submitting by rememberSaveable { mutableStateOf(false) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var sourceMenuOpen by remember { mutableStateOf(false) }

    val transferDate = LocalDate.ofEpochDay(transferDay)
    val expected = WireDeclarationRules.expectedTotal(periods, expectedByPeriod)

    fun toggle(period: String) {
        periods = if (period in periods) periods - period else (periods + period).sorted()
        // Prefill the amount with the rent of the picked months until the tenant edits it.
        if (!amountTouched) amount = WireDeclarationRules.prefillAmount(WireDeclarationRules.expectedTotal(periods, expectedByPeriod))
    }

    val picker = rememberProofPicker(
        onPick = { errorRes = null; proof = it },
        onProblem = { proof = null; errorRes = it },
    )

    fun submit() {
        errorRes = null
        if (periods.isEmpty()) { errorRes = R.string.wire_months_required; return }
        val value = WireDeclarationRules.parseAmount(amount) ?: run { errorRes = R.string.wire_amount_required; return }
        val file = proof ?: run { errorRes = R.string.wire_proof_required; return }
        scope.launch {
            submitting = true
            try {
                app.client.declareWire(
                    leaseId,
                    DeclareWireInput(
                        periods = periods,
                        declaredAmount = value,
                        transferDate = WireDeclarationRules.transferDateField(transferDate.year, transferDate.monthValue, transferDate.dayOfMonth),
                        reference = reference.trim().takeIf { it.isNotEmpty() },
                    ),
                    file,
                )
                onDone()
            } catch (e: Throwable) {
                errorRes = RentLabels.declareErrorMessageRes(e)
            } finally {
                submitting = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.wire_intro), style = MaterialTheme.typography.bodyMedium, color = c.secondaryText)

            if (declarable.isEmpty()) {
                InfoNote(stringResource(R.string.rent_nothing_to_declare))
                SecondaryButton(stringResource(R.string.wire_cancel), onClick = onCancel)
                return@Column
            }

            TenantCard {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    errorRes?.let { ErrorBanner(stringResource(it)) }

                    // Months
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel(stringResource(R.string.wire_months_label))
                        Text(stringResource(R.string.wire_months_hint), style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
                        declarable.forEach { month ->
                            MonthChoice(month, rent.currencyCode, selected = month.period in periods, onToggle = { toggle(month.period) })
                        }
                    }

                    // Amount
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel(stringResource(R.string.wire_amount_label))
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amountTouched = true; amount = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            suffix = { LeaseFormatting.currencySymbol(rent.currencyCode)?.let { Text(it) } },
                        )
                        if (expected > 0) {
                            Text(
                                stringResource(R.string.wire_amount_hint, LeaseFormatting.money(expected, rent.currencyCode, language)),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.secondaryText,
                            )
                        }
                    }

                    // Transfer date
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel(stringResource(R.string.wire_date_label))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, c.hairline, RoundedCornerShape(4.dp))
                                .clickable { datePickerOpen = true }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(KleidiDateFormatter.date(transferDate.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC), style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = c.secondaryText)
                        }
                    }

                    // Reference
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel(stringResource(R.string.wire_reference_label))
                        OutlinedTextField(
                            value = reference,
                            onValueChange = { reference = it.take(WireDeclarationRules.REFERENCE_MAX_LENGTH) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.wire_reference_placeholder), color = c.secondaryText) },
                        )
                    }

                    // Proof
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel(stringResource(R.string.wire_proof_label))
                        Text(stringResource(R.string.wire_proof_hint), style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
                        Box {
                            SecondaryButton(
                                stringResource(if (proof == null) R.string.wire_proof_pick else R.string.wire_proof_change),
                                onClick = { sourceMenuOpen = true },
                                enabled = !submitting,
                            )
                            DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.proof_source_camera)) },
                                    leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                                    onClick = { sourceMenuOpen = false; picker.launch(ProofSource.Camera) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.proof_source_gallery)) },
                                    leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                                    onClick = { sourceMenuOpen = false; picker.launch(ProofSource.Gallery) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.proof_source_files)) },
                                    leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                                    onClick = { sourceMenuOpen = false; picker.launch(ProofSource.Files) },
                                )
                            }
                        }
                        proof?.let {
                            Text(
                                stringResource(R.string.proof_file_size_kb, it.fileName, maxOf(1, Math.round(it.bytes.size / 1024.0).toInt())),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.secondaryText,
                            )
                        }
                    }

                    PrimaryButton(
                        stringResource(if (submitting) R.string.wire_submitting else R.string.wire_submit),
                        onClick = ::submit,
                        loading = submitting,
                    )
                    SecondaryButton(stringResource(R.string.wire_cancel), onClick = onCancel, enabled = !submitting)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (datePickerOpen) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = transferDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc
                override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
            },
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { transferDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    datePickerOpen = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = TenantTheme.colors.ink)
}

/** One selectable month: checkbox, "October 2026", its expected rent. */
@Composable
private fun MonthChoice(month: TenantRentMonth, currencyCode: String?, selected: Boolean, onToggle: () -> Unit) {
    val c = TenantTheme.colors
    val language = LocalAppModel.current.language
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (selected) c.accentSoft else c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.hairline, CardShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Text(RentFormatting.period(month.period, language), style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
        month.expectedAmount?.let {
            Spacer(Modifier.width(8.dp))
            Text(LeaseFormatting.money(it, currencyCode, language), style = MaterialTheme.typography.bodyMedium, color = c.secondaryText, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

/** After a successful declaration: thanks, and the way to Payments or Home. */
@Composable
private fun DoneState(onOpenPayments: () -> Unit, onBackHome: () -> Unit) {
    val c = TenantTheme.colors
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 360.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(64.dp).clip(CardShape).background(c.successSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AccountBalance, contentDescription = null, tint = c.success, modifier = Modifier.size(30.dp))
            }
            Text(stringResource(R.string.wire_done_title), style = MaterialTheme.typography.titleLarge, color = c.ink, textAlign = TextAlign.Center)
            Text(stringResource(R.string.wire_done_body), style = MaterialTheme.typography.bodyMedium, color = c.secondaryText, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            PrimaryButton(stringResource(R.string.tab_payments), onClick = onOpenPayments)
            SecondaryButton(stringResource(R.string.wire_back_home), onClick = onBackHome)
        }
    }
}
