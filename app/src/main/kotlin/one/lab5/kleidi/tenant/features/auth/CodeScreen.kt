package one.lab5.kleidi.tenant.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.core.PhoneNumber
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.shell.errorMessageRes
import one.lab5.kleidi.tenant.ui.ErrorBanner
import one.lab5.kleidi.tenant.ui.InfoNote
import one.lab5.kleidi.tenant.ui.PillTone
import one.lab5.kleidi.tenant.ui.PrimaryButton
import one.lab5.kleidi.tenant.ui.TenantTheme

private const val CODE_LENGTH = 6
/** The server sends at most one code a minute to the same number (CODE_RESEND_INTERVAL_MS). */
private const val RESEND_INTERVAL_SECONDS = 60

/** Step 2: the 6-digit code. Verifies as soon as six digits are typed; "Send again" after a minute. */
@Composable
fun CodeScreen(phone: String, expiresInSeconds: Int, onChangeNumber: () -> Unit) {
    val app = LocalAppModel.current
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    var errorRes by rememberSaveable { mutableStateOf<Int?>(null) }
    var sentAgain by rememberSaveable { mutableStateOf(false) }
    var sentAtMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var expiresIn by rememberSaveable { mutableIntStateOf(expiresInSeconds) }
    var nowMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val focus = remember { FocusRequester() }

    // One ticking clock for both countdowns (resend cooldown, code expiry).
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val elapsed = ((nowMillis - sentAtMillis) / 1000).toInt().coerceAtLeast(0)
    val resendIn = (RESEND_INTERVAL_SECONDS - elapsed).coerceAtLeast(0)
    val remaining = (expiresIn - elapsed).coerceAtLeast(0)
    val expired = remaining == 0

    fun verify() {
        if (isSubmitting || code.length != CODE_LENGTH) return
        scope.launch {
            isSubmitting = true
            errorRes = null
            try {
                app.signIn(phone, code)
            } catch (e: Throwable) {
                errorRes = errorMessageRes(e)
                code = ""
            } finally {
                isSubmitting = false
            }
        }
    }

    fun sendAgain() {
        if (isSubmitting || resendIn > 0) return
        scope.launch {
            isSubmitting = true
            errorRes = null
            try {
                expiresIn = app.requestCode(phone)
                sentAtMillis = System.currentTimeMillis()
                nowMillis = sentAtMillis
                code = ""
                sentAgain = true
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
            Text(stringResource(R.string.code_title), style = MaterialTheme.typography.headlineMedium, color = TenantTheme.colors.ink)
            Text(
                stringResource(R.string.code_subtitle, PhoneNumber.display(phone)),
                style = MaterialTheme.typography.bodyLarge,
                color = TenantTheme.colors.secondaryText,
            )

            errorRes?.let { ErrorBanner(stringResource(it)) }
            if (errorRes == null && sentAgain) InfoNote(stringResource(R.string.code_sent_again), tone = PillTone.Success)

            OutlinedTextField(
                value = code,
                onValueChange = { typed ->
                    code = typed.filter { it.isDigit() }.take(CODE_LENGTH)
                    sentAgain = false
                    if (code.length == CODE_LENGTH) verify()
                },
                label = { Text(stringResource(R.string.code_field)) },
                singleLine = true,
                enabled = !isSubmitting,
                textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 8.sp, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { verify() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentType = ContentType.SmsOtpCode },
            )

            Text(
                if (expired) stringResource(R.string.code_expired)
                else stringResource(R.string.code_expires_in, "%d:%02d".format(remaining / 60, remaining % 60)),
                style = MaterialTheme.typography.bodySmall,
                color = if (expired) TenantTheme.colors.warning else TenantTheme.colors.secondaryText,
            )

            PrimaryButton(stringResource(R.string.code_verify), onClick = ::verify, enabled = code.length == CODE_LENGTH && !isSubmitting, loading = isSubmitting)

            TextButton(onClick = ::sendAgain, enabled = resendIn == 0 && !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (resendIn > 0) stringResource(R.string.code_send_again_in, resendIn) else stringResource(R.string.code_send_again),
                    color = if (resendIn > 0) TenantTheme.colors.secondaryText else TenantTheme.colors.accent,
                )
            }
            TextButton(onClick = onChangeNumber, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.code_change_number), color = TenantTheme.colors.secondaryText)
            }
        }
    }
}
