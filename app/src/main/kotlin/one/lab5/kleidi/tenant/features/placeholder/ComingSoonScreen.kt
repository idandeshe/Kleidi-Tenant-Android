// Payments and Issues in phase 1: a placeholder until their release.

package one.lab5.kleidi.tenant.features.placeholder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.ui.EmptyState
import one.lab5.kleidi.tenant.ui.TenantTheme

@Composable
fun ComingSoonScreen(titleRes: Int, messageRes: Int) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium,
            color = TenantTheme.colors.ink,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        EmptyState(
            title = stringResource(R.string.placeholder_title),
            message = stringResource(messageRes),
            icon = Icons.Outlined.HourglassEmpty,
        )
    }
}
