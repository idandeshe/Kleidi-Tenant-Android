// Profile: who is signed in, the language picker (also PATCHes /tenant/me), sign out.

package one.lab5.kleidi.tenant.features.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhonelinkErase
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.models.Tenant
import one.lab5.kleidi.tenant.core.AppLanguage
import one.lab5.kleidi.tenant.core.PhoneNumber
import one.lab5.kleidi.tenant.shell.BrandMark
import one.lab5.kleidi.tenant.shell.BrandMarkSize
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.shell.ServerEnvironment
import one.lab5.kleidi.tenant.shell.errorMessageRes
import one.lab5.kleidi.tenant.ui.DetailCell
import one.lab5.kleidi.tenant.ui.ErrorBanner
import one.lab5.kleidi.tenant.ui.InfoNote
import one.lab5.kleidi.tenant.ui.ListRow
import one.lab5.kleidi.tenant.ui.PillTone
import one.lab5.kleidi.tenant.ui.RowDivider
import one.lab5.kleidi.tenant.ui.SectionTitle
import one.lab5.kleidi.tenant.ui.TenantCard
import one.lab5.kleidi.tenant.ui.TenantTheme

@Composable
fun ProfileScreen(tenant: Tenant) {
    val app = LocalAppModel.current
    val scope = rememberCoroutineScope()
    var busy by rememberSaveable { mutableStateOf(false) }
    var errorRes by rememberSaveable { mutableStateOf<Int?>(null) }
    var languageSyncFailed by rememberSaveable { mutableStateOf(false) }
    var confirmEverywhere by rememberSaveable { mutableStateOf(false) }
    val c = TenantTheme.colors

    fun run(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            errorRes = null
            try {
                block()
            } catch (e: Throwable) {
                errorRes = errorMessageRes(e)
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium, color = c.ink)

            errorRes?.let { ErrorBanner(stringResource(it)) }
            if (languageSyncFailed) InfoNote(stringResource(R.string.profile_language_sync_failed), tone = PillTone.Warning)

            TenantCard {
                Text(tenant.fullName, style = MaterialTheme.typography.titleLarge, color = c.ink)
                Spacer(Modifier.height(16.dp))
                DetailCell(stringResource(R.string.profile_phone), PhoneNumber.display(tenant.phone))
                Spacer(Modifier.height(12.dp))
                DetailCell(stringResource(R.string.profile_email), tenant.email?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_email_none))
            }

            Column {
                SectionTitle(stringResource(R.string.profile_language))
                TenantCard(contentPadding = PaddingValues(0.dp)) {
                    LanguageRow(stringResource(R.string.language_greek), selected = app.language == AppLanguage.EL, enabled = !busy) {
                        run {
                            languageSyncFailed = false
                            try {
                                app.setLanguage(AppLanguage.EL)
                            } catch (e: Throwable) {
                                languageSyncFailed = true
                            }
                        }
                    }
                    RowDivider()
                    LanguageRow(stringResource(R.string.language_english), selected = app.language == AppLanguage.EN, enabled = !busy) {
                        run {
                            languageSyncFailed = false
                            try {
                                app.setLanguage(AppLanguage.EN)
                            } catch (e: Throwable) {
                                languageSyncFailed = true
                            }
                        }
                    }
                }
            }

            TenantCard(contentPadding = PaddingValues(0.dp)) {
                ListRow(
                    stringResource(R.string.profile_sign_out),
                    onClick = { run { app.signOut() } },
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    chevron = false,
                )
                RowDivider()
                ListRow(
                    stringResource(R.string.profile_sign_out_everywhere),
                    onClick = { confirmEverywhere = true },
                    icon = Icons.Outlined.PhonelinkErase,
                    destructive = true,
                    chevron = false,
                )
            }

            BrandMark(BrandMarkSize.Small)
            Text(
                stringResource(R.string.profile_version, ServerEnvironment.appVersion, ServerEnvironment.clientBuild),
                style = MaterialTheme.typography.bodySmall,
                color = c.secondaryText,
            )
            if (app.environment.kind != ServerEnvironment.Kind.Prod) {
                Text(stringResource(R.string.profile_server, app.environment.baseUrl), style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
            }
        }
    }

    if (confirmEverywhere) {
        AlertDialog(
            onDismissRequest = { confirmEverywhere = false },
            title = { Text(stringResource(R.string.profile_sign_out_everywhere)) },
            text = { Text(stringResource(R.string.profile_sign_out_everywhere_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEverywhere = false
                    run { app.signOutEverywhere() }
                }) { Text(stringResource(R.string.profile_sign_out_everywhere), color = c.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEverywhere = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun LanguageRow(title: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = TenantTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = c.accent)
    }
}
