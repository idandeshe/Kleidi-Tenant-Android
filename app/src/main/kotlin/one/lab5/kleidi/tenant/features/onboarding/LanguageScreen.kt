// The first onboarding screen: Ελληνικά / English, pre-highlighted from the device locale.
// Shown before sign-in until a language was chosen once (AppModel.Phase.ChooseLanguage).

package one.lab5.kleidi.tenant.features.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import one.lab5.kleidi.tenant.core.AppLanguage
import one.lab5.kleidi.tenant.shell.BrandMark
import one.lab5.kleidi.tenant.shell.BrandMarkSize
import one.lab5.kleidi.tenant.shell.LanguagePreferences
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.ui.ChoiceButton
import one.lab5.kleidi.tenant.ui.PrimaryButton
import one.lab5.kleidi.tenant.ui.TenantTheme

@Composable
fun LanguageScreen() {
    val app = LocalAppModel.current
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(LanguagePreferences.deviceDefault()) }
    var saving by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BrandMark(BrandMarkSize.Large, Modifier.padding(bottom = 16.dp))
            // Both labels are endonyms: they read the same in either language on purpose.
            Text(stringResource(R.string.language_title), style = MaterialTheme.typography.headlineMedium, color = TenantTheme.colors.ink)
            Text(stringResource(R.string.language_subtitle), style = MaterialTheme.typography.bodyLarge, color = TenantTheme.colors.secondaryText)
            Spacer(Modifier.height(12.dp))
            ChoiceButton(stringResource(R.string.language_greek), selected = selected == AppLanguage.EL, onClick = { selected = AppLanguage.EL })
            ChoiceButton(stringResource(R.string.language_english), selected = selected == AppLanguage.EN, onClick = { selected = AppLanguage.EN })
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = stringResource(R.string.language_continue),
            loading = saving,
            onClick = {
                scope.launch {
                    saving = true
                    try {
                        app.chooseLanguage(selected)
                    } finally {
                        saving = false
                    }
                }
            },
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}
