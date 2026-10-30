package one.lab5.kleidi.tenant.shell

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import one.lab5.kleidi.tenant.api.ApiClient
import one.lab5.kleidi.tenant.api.ApiError
import one.lab5.kleidi.tenant.api.ApiEvent
import one.lab5.kleidi.tenant.api.TenantErrorCode
import one.lab5.kleidi.tenant.api.endpoints.issues
import one.lab5.kleidi.tenant.api.endpoints.updateLanguage
import one.lab5.kleidi.tenant.api.models.Tenant
import one.lab5.kleidi.tenant.api.models.TenantConfig
import one.lab5.kleidi.tenant.api.models.TenantIssue
import one.lab5.kleidi.tenant.api.models.unreadCount
import one.lab5.kleidi.tenant.core.AppLanguage

/**
 * Owns the API client and the session lifecycle: the language choice, launch checks
 * (config → stored session → `/tenant/auth/session`), sign-in, sign-out, forced update,
 * reacting to [ApiEvent]s from any screen, and the Issues tab badge (unread replies from the
 * property manager). The counterpart of the tenant iOS `AppModel` + `AppNavigation`.
 */
class AppModel(application: Application) : AndroidViewModel(application) {
    /** Why the sign-in screen is showing a notice. */
    enum class Notice { SessionEnded, NoLongerTenant, Offline }

    sealed interface Phase {
        data object Launching : Phase
        /** First launch: the language has never been chosen. */
        data object ChooseLanguage : Phase
        data class SignedOut(val notice: Notice?) : Phase
        data class SignedIn(val tenant: Tenant) : Phase
        data class UpdateRequired(val storeUrl: String?) : Phase
    }

    var phase: Phase by mutableStateOf(Phase.Launching)
        private set

    /** The language in effect (device default until chosen). */
    var language: AppLanguage by mutableStateOf(LanguagePreferences.deviceDefault())
        private set

    val environment: ServerEnvironment = ServerEnvironment.current

    val client: ApiClient = ApiClient(
        baseUrl = environment.baseUrl,
        clientBuild = ServerEnvironment.clientBuild,
        tokenStore = AndroidTokenStore(application, environment.tokenAccount),
    )

    /** The last `GET /tenant/config`; null when the server could not be reached at launch. */
    var config: TenantConfig? by mutableStateOf(null)
        private set

    /**
     * Issues with a reply the tenant has not opened — the number on the Issues tab (0 hides it).
     * Set from whichever screen last loaded the list and by [refreshUnreadIssues] at launch / on resume.
     */
    var unreadIssueCount: Int by mutableStateOf(0)
        private set

    private val preferences = LanguagePreferences(application)
    private var eventsJob: Job? = null
    private var started = false

    val tenant: Tenant? get() = (phase as? Phase.SignedIn)?.tenant

    // Lifecycle

    /** Called once by the activity; later calls are ignored (the model survives recreation). */
    fun startOnce() {
        if (started) return
        started = true
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        listenToEvents()
        val chosen = preferences.current()
        if (chosen != null) {
            language = chosen
            LanguagePreferences.apply(chosen)
        }
        // Version gate first: an unsupported build must not try to sign in.
        runCatching { client.config() }.getOrNull()?.let { config ->
            this.config = config
            if (config.requiresUpdate(ServerEnvironment.clientBuild)) {
                phase = Phase.UpdateRequired(config.android?.playStoreUrl)
                return
            }
        }
        if (chosen == null) {
            phase = Phase.ChooseLanguage
            return
        }
        resolveSession()
    }

    /** Stored session → `/tenant/auth/session`; otherwise the sign-in screen. */
    private suspend fun resolveSession() {
        if (!client.hasStoredSession) {
            phase = Phase.SignedOut(null)
            return
        }
        phase = try {
            val session = client.session()
            Phase.SignedIn(session.tenant).also { syncLanguage(session.tenant) }
        } catch (e: ApiError.Transport) {
            // Offline at launch with a stored session: let the tenant retry rather than sign out.
            Phase.SignedOut(Notice.Offline)
        } catch (e: ApiError.UpdateRequired) {
            return
        } catch (e: ApiError) {
            Phase.SignedOut(null)
        }
    }

    /** The first screen's choice. Stores it, switches the locale, and moves on to sign-in. */
    suspend fun chooseLanguage(language: AppLanguage) {
        this.language = language
        preferences.choose(language)
        if (phase == Phase.ChooseLanguage) resolveSession()
    }

    /**
     * Profile → Language. Applies locally at once; a signed-in tenant's record is updated too
     * (`PATCH /tenant/me`). Throws when that update failed — the local change stays.
     */
    suspend fun setLanguage(language: AppLanguage) {
        this.language = language
        preferences.choose(language)
        val current = tenant ?: return
        if (current.appLanguage == language) return
        val updated = client.updateLanguage(language)
        phase = Phase.SignedIn(updated)
    }

    /** `POST /tenant/auth/request-code` — a 6-digit code to [phone] (E.164). */
    suspend fun requestCode(phone: String): Int = client.requestCode(phone).expiresInSeconds

    /** `POST /tenant/auth/verify-code` — signs in. */
    suspend fun signIn(phone: String, code: String) {
        val tenant = client.verifyCode(phone, code)
        phase = Phase.SignedIn(tenant)
        syncLanguage(tenant)
    }

    suspend fun signOut() {
        client.logout()
        endSession(Phase.SignedOut(null))
    }

    /** Revokes every session of this tenant (other phones and the web included). */
    suspend fun signOutEverywhere() {
        try {
            client.logoutAll()
        } finally {
            endSession(Phase.SignedOut(null))
        }
    }

    /** Leaves the signed-in state; the badge belonged to the tenant who just left. */
    private fun endSession(next: Phase) {
        unreadIssueCount = 0
        phase = next
    }

    /** The issues list was (re)loaded: the tab badge follows it. */
    fun issuesDidLoad(issues: List<TenantIssue>) {
        unreadIssueCount = issues.unreadCount
    }

    /** Re-reads the tenant's issues for the badge. Best effort: a failure leaves the count as it was. */
    suspend fun refreshUnreadIssues() {
        if (phase !is Phase.SignedIn) return
        runCatching { client.issues() }.onSuccess { issuesDidLoad(it) }
    }

    /** Re-reads the tenant from the server (after a profile change). */
    suspend fun reloadSession() {
        if (phase !is Phase.SignedIn) return
        val session = runCatching { client.session() }.getOrNull() ?: return
        phase = Phase.SignedIn(session.tenant)
    }

    /**
     * After sign-in: the phone's language wins over the record's, once. Best effort — the
     * server may not accept the update yet, and the app is usable either way.
     */
    private suspend fun syncLanguage(tenant: Tenant) {
        if (tenant.appLanguage == language) return
        val updated = runCatching { client.updateLanguage(language) }.getOrNull() ?: return
        if (phase is Phase.SignedIn) phase = Phase.SignedIn(updated)
    }

    // Events

    private fun listenToEvents() {
        eventsJob?.cancel()
        val events = client.events
        eventsJob = viewModelScope.launch {
            events.collect { handle(it) }
        }
    }

    private fun handle(event: ApiEvent) {
        when (event) {
            is ApiEvent.SessionEnded -> {
                val notice = if (event.reason == TenantErrorCode.NOT_A_TENANT) Notice.NoLongerTenant else Notice.SessionEnded
                if (phase is Phase.SignedIn) endSession(Phase.SignedOut(notice))
            }
            is ApiEvent.UpdateRequired -> phase = Phase.UpdateRequired(config?.android?.playStoreUrl)
        }
    }
}

/** The app model, for any screen: `val app = LocalAppModel.current; app.client.…`. */
val LocalAppModel = staticCompositionLocalOf<AppModel> { error("No AppModel provided") }
