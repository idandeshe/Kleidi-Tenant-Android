package one.lab5.kleidi.tenant.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.core.PhoneNumber
import one.lab5.kleidi.tenant.shell.AppModel
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.shell.ServerEnvironment
import one.lab5.kleidi.tenant.shell.errorMessageRes
import one.lab5.kleidi.tenant.ui.ErrorBanner
import one.lab5.kleidi.tenant.ui.InfoNote
import one.lab5.kleidi.tenant.ui.PillTone
import one.lab5.kleidi.tenant.ui.PrimaryButton
import one.lab5.kleidi.tenant.ui.TenantTheme

/** Step 1: the mobile number (+30 by default). [onCodeSent] gets the E.164 number and the code's lifetime. */
@Composable
fun PhoneScreen(notice: AppModel.Notice?, onCodeSent: (phone: String, expiresInSeconds: Int) -> Unit) {
    val app = LocalAppModel.current
    val scope = rememberCoroutineScope()
    var countryCode by rememberSaveable { mutableStateOf("+" + PhoneNumber.DEFAULT_COUNTRY_CODE) }
    var national by rememberSaveable { mutableStateOf("") }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    var errorRes by rememberSaveable { mutableStateOf<Int?>(null) }
    val smsOff = app.config?.smsConfigured == false
    val canSubmit = !isSubmitting && national.isNotBlank() && !smsOff

    fun submit() {
        if (!canSubmit) return
        val phone = PhoneNumber.toE164(countryCode, national)
        if (phone == null) {
            errorRes = R.string.error_invalid_phone
            return
        }
        scope.launch {
            isSubmitting = true
            errorRes = null
            try {
                val expiresIn = app.requestCode(phone)
                onCodeSent(phone, expiresIn)
            } catch (e: Throwable) {
                errorRes = errorMessageRes(e)
            } finally {
                isSubmitting = false
            }
        }
    }

    Box(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Kleidi", style = MaterialTheme.typography.displaySmall, color = TenantTheme.colors.accent)
            Text(stringResource(R.string.phone_title), style = MaterialTheme.typography.headlineSmall, color = TenantTheme.colors.ink)
            Text(stringResource(R.string.phone_subtitle), style = MaterialTheme.typography.bodyLarge, color = TenantTheme.colors.secondaryText)

            errorRes?.let { ErrorBanner(stringResource(it)) }
                ?: notice?.let { InfoNote(stringResource(it.messageRes), tone = PillTone.Warning) }
            if (smsOff) InfoNote(stringResource(R.string.phone_sms_unavailable), tone = PillTone.Warning)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = countryCode,
                    onValueChange = { countryCode = it.take(5) },
                    label = { Text(stringResource(R.string.phone_country_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    modifier = Modifier.width(110.dp),
                )
                OutlinedTextField(
                    value = national,
                    onValueChange = { national = it.take(20) },
                    label = { Text(stringResource(R.string.phone_number)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.weight(1f).semantics { contentType = ContentType.PhoneNumber },
                )
            }
            PrimaryButton(stringResource(R.string.phone_send_code), onClick = ::submit, enabled = canSubmit, loading = isSubmitting)

            if (app.environment.kind != ServerEnvironment.Kind.Prod) {
                Text(
                    stringResource(R.string.phone_server, app.environment.baseUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = TenantTheme.colors.secondaryText,
                )
            }
        }
    }
}

val AppModel.Notice.messageRes: Int
    get() = when (this) {
        AppModel.Notice.SessionEnded -> R.string.notice_session_ended
        AppModel.Notice.NoLongerTenant -> R.string.notice_no_longer_tenant
        AppModel.Notice.Offline -> R.string.notice_offline
    }
