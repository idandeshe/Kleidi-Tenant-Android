package one.lab5.kleidi.tenant

import android.app.Application

/**
 * Nothing to initialise yet: the in-app language is restored by AppCompat itself
 * (`autoStoreLocales` in the manifest) before the first activity, and the session
 * lives in [one.lab5.kleidi.tenant.shell.AppModel].
 */
class KleidiTenantApplication : Application()
