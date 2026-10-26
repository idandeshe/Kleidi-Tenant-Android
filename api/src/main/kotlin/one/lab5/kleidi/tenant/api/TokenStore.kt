package one.lab5.kleidi.tenant.api

import one.lab5.kleidi.tenant.api.models.SessionTokens

/** Where the session tokens live between launches (the app: Android Keystore-encrypted). */
interface TokenStore {
    fun load(): SessionTokens?
    fun save(tokens: SessionTokens)
    fun clear()
}

/** For tests and previews. */
class InMemoryTokenStore(private var tokens: SessionTokens? = null) : TokenStore {
    @Synchronized override fun load(): SessionTokens? = tokens
    @Synchronized override fun save(tokens: SessionTokens) { this.tokens = tokens }
    @Synchronized override fun clear() { tokens = null }
}
