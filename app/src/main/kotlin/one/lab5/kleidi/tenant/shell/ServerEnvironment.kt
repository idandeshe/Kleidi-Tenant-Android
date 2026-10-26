package one.lab5.kleidi.tenant.shell

import one.lab5.kleidi.tenant.BuildConfig

/** Which Kleidi server this build talks to — from the product flavor (`BuildConfig.API_BASE_URL`). */
data class ServerEnvironment(val kind: Kind, val baseUrl: String) {
    enum class Kind { Dev, Stage, Prod }

    /** Token store key: one stored session per server. */
    val tokenAccount: String get() = baseUrl

    companion object {
        /** Build number sent as `x-client-build`; the server's TENANT_ANDROID_MIN_SUPPORTED_BUILD compares against it. */
        val clientBuild: Int get() = BuildConfig.VERSION_CODE
        val appVersion: String get() = BuildConfig.VERSION_NAME

        val current: ServerEnvironment
            get() = ServerEnvironment(
                kind = when (BuildConfig.ENVIRONMENT) {
                    "dev" -> Kind.Dev
                    "stage" -> Kind.Stage
                    else -> Kind.Prod
                },
                baseUrl = BuildConfig.API_BASE_URL,
            )
    }
}
