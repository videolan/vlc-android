package org.videolan.vlc.discourse

import android.content.Context
import android.os.Build
import android.provider.Settings as AndroidSettings
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.AlgorithmParameterSpec
import java.util.UUID
import javax.security.auth.x500.X500Principal
import org.videolan.tools.Settings

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val ALIAS_PREFIX = "osho_user_"
private const val LEGACY_USER_ID = "osho_api_user_id"

/** Durable anonymous identity for the Osho API. */
object OshoUserIdentity {
    private val lock = Any()

    fun ensure(context: Context): String = synchronized(lock) {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return@synchronized UUID.nameUUIDFromBytes(
                "${appContext.packageName}:${AndroidSettings.Secure.getString(appContext.contentResolver, AndroidSettings.Secure.ANDROID_ID)}"
                    .toByteArray()
            ).toString()
        }

        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val storedAlias = keyStore.aliases().let { aliases ->
            var result: String? = null
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (alias.startsWith(ALIAS_PREFIX)) {
                    result = alias
                    break
                }
            }
            result
        }
        val userId = storedAlias?.removePrefix(ALIAS_PREFIX)?.takeIf(::isUuid)
            ?: Settings.getInstance(appContext).getString(LEGACY_USER_ID, null)?.takeIf(::isUuid)
            ?: UUID.randomUUID().toString()
        val alias = ALIAS_PREFIX + userId
        if (storedAlias != alias || !keyStore.containsAlias(alias)) createKey(appContext, alias)
        Settings.getInstance(appContext).edit().putString(LEGACY_USER_ID, userId).apply()
        userId
    }

    private fun createKey(context: Context, alias: String) {
        val generator = KeyPairGenerator.getInstance("RSA", KEYSTORE_PROVIDER)
        generator.initialize(keySpec(context, alias))
        generator.generateKeyPair()
    }

    @Suppress("DEPRECATION")
    private fun keySpec(context: Context, alias: String): AlgorithmParameterSpec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        android.security.keystore.KeyGenParameterSpec.Builder(
            alias,
            android.security.keystore.KeyProperties.PURPOSE_SIGN or android.security.keystore.KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .build()
    } else {
        android.security.KeyPairGeneratorSpec.Builder(context)
            .setAlias(alias)
            .setSubject(X500Principal("CN=$alias"))
            .setSerialNumber(BigInteger.ONE)
            .setStartDate(java.util.Date())
            .setEndDate(java.util.Date(System.currentTimeMillis() + 100L * 365 * 24 * 60 * 60 * 1000))
            .build()
    }

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess
}
