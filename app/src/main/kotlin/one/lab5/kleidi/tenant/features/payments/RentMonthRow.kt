// One rent month as the tenant sees it (web `RentMonthRow`): period, expected amount, the status
// pill, "Paid / Declared / Due dd/mm/yyyy", the "(Declared dd/mm/yyyy ·) View proof" line of the
// month's declaration, and the red note (with "Declare again") when the last declaration on an
// owed month was rejected.

package one.lab5.kleidi.tenant.features.payments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.models.TenantRentMonth
import one.lab5.kleidi.tenant.core.KleidiDateFormatter
import one.lab5.kleidi.tenant.core.LeaseFormatting
import one.lab5.kleidi.tenant.core.RentFormatting
import one.lab5.kleidi.tenant.core.RentMonthStatus
import one.lab5.kleidi.tenant.core.RentProofLine
import one.lab5.kleidi.tenant.features.home.notSet
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.ui.InfoNote
import one.lab5.kleidi.tenant.ui.Pill
import one.lab5.kleidi.tenant.ui.PillTone
import one.lab5.kleidi.tenant.ui.TenantTheme

/**
 * [onViewProof] opens the proof of the month's declaration; [onDeclare] is the "Declare again"
 * of a rejected month (same destination as "I paid by bank transfer").
 */
@Composable
fun RentMonthRow(month: TenantRentMonth, currencyCode: String?, onViewProof: (declarationId: String) -> Unit, onDeclare: () -> Unit) {
    val language = LocalAppModel.current.language
    val c = TenantTheme.colors
    val declaration = month.declaration
    val dateLine = when {
        month.monthStatus == RentMonthStatus.Paid && month.paidAt != null -> stringResource(R.string.rent_paid_on, KleidiDateFormatter.date(month.paidAt))
        month.monthStatus == RentMonthStatus.Declared && declaration != null -> stringResource(R.string.rent_declared_on, KleidiDateFormatter.date(declaration.declaredAt))
        else -> stringResource(R.string.rent_due_on, KleidiDateFormatter.date(month.dueDate))
    }
    val proofLine = month.proofLine

    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                RentFormatting.period(month.period, language),
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                month.expectedAmount?.let { LeaseFormatting.money(it, currencyCode, language) } ?: notSet(),
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(month.statusLabel(), tone = RentLabels.monthTone(month.monthStatus), modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(12.dp))
            Text(dateLine, style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
        }
        if (declaration != null && proofLine != RentProofLine.None) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (proofLine == RentProofLine.DeclaredAndViewProof) {
                    Text(
                        stringResource(R.string.rent_declared_on, KleidiDateFormatter.date(declaration.declaredAt)) + " · ",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.secondaryText,
                    )
                }
                LinkText(stringResource(R.string.rent_view_proof), onClick = { onViewProof(declaration.id) })
                if (month.showsRejection && month.canDeclare) {
                    Text(" · ", style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
                    LinkText(stringResource(R.string.rent_declare_again), onClick = onDeclare)
                }
            }
        }
        if (month.showsRejection) RejectionNote(declaration?.rejectionNote)
    }
}

/** An inline accent link ("View proof", "Declare again"). */
@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = TenantTheme.colors.accent,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    )
}

/** "Wire not confirmed — please contact management" + "Note from management: …". */
@Composable
private fun RejectionNote(note: String?) {
    Spacer(Modifier.height(2.dp))
    val text = buildString {
        append(stringResource(R.string.rent_rejected))
        note?.takeIf { it.isNotBlank() }?.let { append('\n'); append(stringResource(R.string.rent_rejected_note, it)) }
    }
    InfoNote(text, tone = PillTone.Error)
}
