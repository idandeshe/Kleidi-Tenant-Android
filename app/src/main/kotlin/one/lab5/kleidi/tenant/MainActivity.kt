package one.lab5.kleidi.tenant

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import one.lab5.kleidi.tenant.shell.AppModel
import one.lab5.kleidi.tenant.shell.LocalAppModel
import one.lab5.kleidi.tenant.shell.RootScreen
import one.lab5.kleidi.tenant.ui.TenantTheme

/**
 * An AppCompatActivity (not a plain ComponentActivity) so that
 * `AppCompatDelegate.setApplicationLocales` applies the chosen language in-app on every
 * Android version, recreating this activity; the [AppModel] survives the recreation.
 */
class MainActivity : AppCompatActivity() {
    private val app: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app.startOnce()

        setContent {
            TenantTheme {
                CompositionLocalProvider(LocalAppModel provides app) {
                    RootScreen()
                }
            }
        }
    }
}
