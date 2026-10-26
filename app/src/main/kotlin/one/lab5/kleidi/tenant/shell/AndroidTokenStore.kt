package one.lab5.kleidi.tenant.shell

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import one.lab5.kleidi.tenant.api.KleidiJson
import one.lab5.kleidi.tenant.api.TokenStore
import one.lab5.kleidi.tenant.api.models.SessionTokens
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The session tokens, AES-GCM-encrypted with a key that never leaves the Android
 * Keystore, in private SharedPreferences excluded from backups and device transfer
 * (res/xml/data_extraction_rules.xml) — the counterpart of the tenant iOS Keychain item.
 * One entry per server ([account] = the base URL).
 */
class AndroidTokenStore(context: Context, private val account: String) : TokenStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val prefKey = "session:" + account

    override fun load(): SessionTokens? {
        val stored = prefs.getString(prefKey, null) ?: return null
        return runCatching {
            val (iv, data) = stored.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            KleidiJson.decodeFromString(SessionTokens.serializer(), cipher.doFinal(data).decodeToString())
        }.getOrElse {
            // Unreadable (key reset, corrupted): treat as signed out.
            clear()
            null
        }
    }

    override fun save(tokens: SessionTokens) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(KleidiJson.encodeToString(SessionTokens.serializer(), tokens).encodeToByteArray())
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
        prefs.edit().putString(prefKey, encoded).commit()
    }

    override fun clear() {
        prefs.edit().remove(prefKey).commit()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "kleidi.tenant.session"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "kleidi.tenant.session.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
