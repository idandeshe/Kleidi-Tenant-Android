package one.lab5.kleidi.tenant.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.features.auth.AuthFlow
import one.lab5.kleidi.tenant.features.onboarding.LanguageScreen
import one.lab5.kleidi.tenant.ui.EmptyState
import one.lab5.kleidi.tenant.ui.TenantTheme

/** Launch spinner, language choice, sign-in, the signed-in app, or the forced-update screen. */
@Composable
fun RootScreen() {
    val app = LocalAppModel.current
    when (val phase = app.phase) {
        AppModel.Phase.Launching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TenantTheme.colors.accent)
        }
        AppModel.Phase.ChooseLanguage -> LanguageScreen()
        is AppModel.Phase.SignedOut -> AuthFlow(phase.notice)
        is AppModel.Phase.SignedIn -> MainScaffold(phase.tenant)
        is AppModel.Phase.UpdateRequired -> UpdateRequiredScreen(phase.storeUrl)
    }
}

@Composable
fun UpdateRequiredScreen(storeUrl: String?) {
    val uri = LocalUriHandler.current
    EmptyState(
        title = stringResource(R.string.update_title),
        message = stringResource(R.string.update_message),
        icon = Icons.Outlined.SystemUpdate,
        actionLabel = if (storeUrl != null) stringResource(R.string.update_action) else null,
        onAction = storeUrl?.let { url -> { runCatching { uri.openUri(url) } } },
    )
}
