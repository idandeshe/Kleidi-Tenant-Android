// Sign-in: phone → code. Holds which step is showing and the number a code was sent to.

package one.lab5.kleidi.tenant.features.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import one.lab5.kleidi.tenant.shell.AppModel

@Composable
fun AuthFlow(notice: AppModel.Notice?) {
    var phone by rememberSaveable { mutableStateOf<String?>(null) }
    var expiresInSeconds by rememberSaveable { mutableIntStateOf(300) }

    val sentTo = phone
    if (sentTo == null) {
        PhoneScreen(notice) { e164, expiresIn ->
            expiresInSeconds = expiresIn
            phone = e164
        }
    } else {
        CodeScreen(phone = sentTo, expiresInSeconds = expiresInSeconds, onChangeNumber = { phone = null })
    }
}
