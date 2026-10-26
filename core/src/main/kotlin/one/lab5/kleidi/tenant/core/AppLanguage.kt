// The language of the app copy: chosen on the first onboarding screen, stored on the
// device and mirrored to the tenant record (`Tenant.language`, server/types/tenants.types.ts).

package one.lab5.kleidi.tenant.core

import java.util.Locale

enum class AppLanguage(val code: String) {
    EL("el"),
    EN("en");

    /** The locale for month names and number formatting. */
    val locale: Locale get() = Locale.forLanguageTag(code)

    companion object {
        /** The server's `'el' | 'en'`; null for anything else. */
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code?.lowercase() }

        /**
         * The pre-highlighted choice before the tenant picked one: English when the device
         * language is English (`en`, `en-GB`, …), Greek otherwise — most tenants are in Greece.
         */
        fun defaultFor(deviceLanguage: String?): AppLanguage {
            val primary = deviceLanguage?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')
            return if (primary == "en") EN else EL
        }
    }
}
