package one.lab5.kleidi.tenant.shell

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import one.lab5.kleidi.tenant.core.AppLanguage
import java.util.Locale

private val Context.languageStore: DataStore<Preferences> by preferencesDataStore(name = "kleidi.tenant.language")

/**
 * The language the tenant chose on the first screen (or later in Profile). Stored in DataStore
 * — null until chosen once, which is what keeps the language screen in front of sign-in — and
 * applied with `AppCompatDelegate.setApplicationLocales` so the whole app switches at once.
 */
class LanguagePreferences(private val context: Context) {
    private val key = stringPreferencesKey("language")

    /** null = never chosen. */
    val language: Flow<AppLanguage?> = context.languageStore.data.map { AppLanguage.fromCode(it[key]) }

    suspend fun current(): AppLanguage? = language.first()

    /** Stores the choice and switches the app's locale (recreating the activity when needed). */
    suspend fun choose(language: AppLanguage) {
        context.languageStore.edit { it[key] = language.code }
        apply(language)
    }

    companion object {
        /** The pre-highlighted choice on the first screen: from the device language. */
        fun deviceDefault(): AppLanguage = AppLanguage.defaultFor(Locale.getDefault().language)

        fun apply(language: AppLanguage) {
            val wanted = LocaleListCompat.forLanguageTags(language.code)
            if (AppCompatDelegate.getApplicationLocales() != wanted) AppCompatDelegate.setApplicationLocales(wanted)
        }
    }
}
