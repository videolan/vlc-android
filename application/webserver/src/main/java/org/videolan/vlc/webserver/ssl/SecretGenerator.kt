/*
MIT License: https://opensource.org/licenses/MIT
Copyright 2017 Diederik Hattingh
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package org.videolan.vlc.webserver.ssl

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import io.ktor.utils.io.core.toByteArray
import org.videolan.tools.ENCRYPTED_KEY_NAME
import org.videolan.tools.KEYSTORE_PASSWORD_IV
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.SecureRandom
import java.security.UnrecoverableEntryException
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import java.util.Calendar
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal


object SecretGenerator {

    private const val ANDROID_KEY_STORE_NAME = "AndroidKeyStore"
    private const val AES_MODE_M_OR_GREATER = "AES/GCM/NoPadding"
    private const val AES_MODE_LESS_THAN_M = "AES/ECB/PKCS7Padding"
    private const val KEY_ALIAS = "vlc-android"

    private const val RSA_ALGORITHM_NAME = "RSA"
    private const val RSA_MODE = "RSA/ECB/PKCS1Padding"
    private const val CIPHER_PROVIDER_NAME_ENCRYPTION_DECRYPTION_RSA = "AndroidOpenSSL"
    private const val CIPHER_PROVIDER_NAME_ENCRYPTION_DECRYPTION_AES = "BC"
    private val LOG_TAG = SecretGenerator::class.java.name
    private val s_keyInitLock = Any()

    @get:Throws(CertificateException::class, NoSuchAlgorithmException::class, IOException::class, KeyStoreException::class, UnrecoverableKeyException::class)
    private val secretKeyAPIMorGreater: Key
        get() {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_NAME)
            keyStore.load(null)
            return keyStore.getKey(KEY_ALIAS, null)
        }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Throws(KeyStoreException::class, CertificateException::class, NoSuchAlgorithmException::class, IOException::class, NoSuchProviderException::class, InvalidAlgorithmParameterException::class, UnrecoverableEntryException::class, NoSuchPaddingException::class, InvalidKeyException::class)
    private fun initKeys(context: Context) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_NAME)
        keyStore.load(null)
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            initValidKeys(context)
        } else {
            var keyValid = false
            try {
                val keyEntry = keyStore.getEntry(KEY_ALIAS, null)
                if (keyEntry is KeyStore.SecretKeyEntry &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    keyValid = true
                }
                if (keyEntry is KeyStore.PrivateKeyEntry && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    val secretKey = getSecretKeyFromSharedPreferences(context)
                    // When doing "Clear data" on Android 4.x it removes the shared preferences (where
                    // we have stored our encrypted secret key) but not the key entry. Check for existence
                    // of key here as well.
                    if (!TextUtils.isEmpty(secretKey)) {
                        keyValid = true
                    }
                }
            } catch (e: NullPointerException) {
                // Bad to catch null pointer exception, but looks like Android 4.4.x
                // pin switch to password Keystore bug.
                // https://issuetracker.google.com/issues/36983155
                Log.e(LOG_TAG, "Failed to get key store entry", e)
            } catch (e: UnrecoverableKeyException) {
                Log.e(LOG_TAG, "Failed to get key store entry", e)
            }
            if (!keyValid) {
                synchronized(s_keyInitLock) {

                    // System upgrade or something made key invalid
                    removeKeys(context, keyStore)
                    initValidKeys(context)
                }
            }
        }
    }

    @Throws(KeyStoreException::class)
    fun removeKeys(context: Context, keyStore: KeyStore) {
        keyStore.deleteEntry(KEY_ALIAS)
        removeSavedSharedPreferences(context)
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Throws(NoSuchAlgorithmException::class, NoSuchProviderException::class, InvalidAlgorithmParameterException::class, CertificateException::class, UnrecoverableEntryException::class, NoSuchPaddingException::class, KeyStoreException::class, InvalidKeyException::class, IOException::class)
    private fun initValidKeys(context: Context) {
        synchronized(s_keyInitLock) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                generateKeysForAPIMOrGreater()
            } else {
                generateKeysForAPILessThanM(context)
            }
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun removeSavedSharedPreferences(context: Context) {
        val clearedPreferencesSuccessfully: Boolean = Settings.getInstance(context).edit().remove(ENCRYPTED_KEY_NAME).commit()
        Log.d(LOG_TAG,"Cleared secret key shared preferences $clearedPreferencesSuccessfully")
    }

    @Suppress("DEPRECATION")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Throws(NoSuchProviderException::class, NoSuchAlgorithmException::class, InvalidAlgorithmParameterException::class, CertificateException::class, UnrecoverableEntryException::class, NoSuchPaddingException::class, KeyStoreException::class, InvalidKeyException::class, IOException::class)
    private fun generateKeysForAPILessThanM(context: Context) {
        // Generate a key pair for encryption
        val start = Calendar.getInstance()
        val end = Calendar.getInstance()
        end.add(Calendar.YEAR, 30)
        val spec: KeyPairGeneratorSpec = KeyPairGeneratorSpec.Builder(context)
                .setAlias(KEY_ALIAS)
                .setSubject(X500Principal("CN=$KEY_ALIAS"))
                .setSerialNumber(BigInteger.TEN)
                .setStartDate(start.time)
                .setEndDate(end.time)
                .build()
        val kpg = KeyPairGenerator.getInstance(RSA_ALGORITHM_NAME, ANDROID_KEY_STORE_NAME)
        kpg.initialize(spec)
        kpg.generateKeyPair()
        saveEncryptedKey(context)
    }

    @SuppressLint("ApplySharedPref")
    @Throws(CertificateException::class, NoSuchPaddingException::class, InvalidKeyException::class, NoSuchAlgorithmException::class, KeyStoreException::class, NoSuchProviderException::class, UnrecoverableEntryException::class, IOException::class)
    private fun saveEncryptedKey(context: Context) {
        var encryptedKeyBase64encoded: String? = Settings.getInstance(context).getString(ENCRYPTED_KEY_NAME, null)
        if (encryptedKeyBase64encoded == null) {
            val key = ByteArray(16)
            val secureRandom = SecureRandom()
            secureRandom.nextBytes(key)
            val encryptedKey = rsaEncryptKey(key)
            encryptedKeyBase64encoded = Base64.encodeToString(encryptedKey, Base64.DEFAULT)
            val edit: SharedPreferences.Editor = Settings.getInstance(context).edit()
            edit.putString(ENCRYPTED_KEY_NAME, encryptedKeyBase64encoded)
            val successfullyWroteKey: Boolean = edit.commit()
            if (!successfullyWroteKey) {
                Log.e(LOG_TAG, "Saved keys unsuccessfully")
                throw IOException("Could not save keys")
            }
        }
    }


    private fun getSecretKeyApiLessThanM(context: Context): Key {
        val encryptedKeyBase64Encoded = getSecretKeyFromSharedPreferences(context)
        if (TextUtils.isEmpty(encryptedKeyBase64Encoded)) {
            throw InvalidKeyException("Saved key missing from shared preferences")
        }
        val encryptedKey = Base64.decode(encryptedKeyBase64Encoded, Base64.DEFAULT)
        val key = rsaDecryptKey(encryptedKey)
        return SecretKeySpec(key, "AES")
    }

    private fun getSecretKeyFromSharedPreferences(context: Context): String? {
        return Settings.getInstance(context).getString(ENCRYPTED_KEY_NAME, null)
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Throws(NoSuchAlgorithmException::class, NoSuchProviderException::class, InvalidAlgorithmParameterException::class)
    private fun generateKeysForAPIMOrGreater() {
        val keyGenerator: KeyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE_NAME)
        keyGenerator.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE) // NOTE no Random IV. According to above this is less secure but acceptably so.
                        .setRandomizedEncryptionRequired(false)
                        .build())
        keyGenerator.generateKey()
    }


    /**
     * Encrypts data with the generated keys
     *
     * @param context the context used to retrieve the [SharedPreferences]
     * @param stringDataToEncrypt the data to encrypt
     * @return an encrypted string
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Throws(NoSuchPaddingException::class, NoSuchAlgorithmException::class, UnrecoverableEntryException::class, CertificateException::class, KeyStoreException::class, IOException::class, InvalidAlgorithmParameterException::class, InvalidKeyException::class, NoSuchProviderException::class, BadPaddingException::class, IllegalBlockSizeException::class)
    fun encryptData(context: Context, stringDataToEncrypt: String): String {
        initKeys(context)
        val cipher: Cipher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cipher = Cipher.getInstance(AES_MODE_M_OR_GREATER)
            val iv = generateRandomBytes()
            Settings.getInstance(context).putSingle(KEYSTORE_PASSWORD_IV, Base64.encodeToString(iv, Base64.DEFAULT))
            cipher.init(Cipher.ENCRYPT_MODE, secretKeyAPIMorGreater,
                    GCMParameterSpec(128, iv))
        } else {
            cipher = Cipher.getInstance(AES_MODE_LESS_THAN_M, CIPHER_PROVIDER_NAME_ENCRYPTION_DECRYPTION_AES)
            try {
                cipher.init(Cipher.ENCRYPT_MODE, getSecretKeyApiLessThanM(context))
            } catch (e: InvalidKeyException) {
                // Since the keys can become bad (perhaps because of lock screen change)
                // drop keys in this case.
                removeKeys(context)
                throw e
            } catch (e: IOException) {
                removeKeys(context)
                throw e
            } catch (e: IllegalArgumentException) {
                removeKeys(context)
                throw e
            }
        }
        val encodedBytes = cipher.doFinal(stringDataToEncrypt.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encodedBytes, Base64.DEFAULT)
    }

    /**
     * Decrypts data using the generated keys
     *
     * @param context the context used to retrieve the [SharedPreferences]
     * @param encryptedData the data to decrypt
     * @return the decrypted data
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Throws(NoSuchPaddingException::class, NoSuchAlgorithmException::class, UnrecoverableEntryException::class, CertificateException::class, KeyStoreException::class, IOException::class, InvalidAlgorithmParameterException::class, InvalidKeyException::class, NoSuchProviderException::class, BadPaddingException::class, IllegalBlockSizeException::class)
    fun decryptData(context: Context, encryptedData: String): String {
        initKeys(context)
        val encryptedDecodedData = Base64.decode(encryptedData, Base64.DEFAULT)
        val c: Cipher
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                c = Cipher.getInstance(AES_MODE_M_OR_GREATER)
                val iv = Base64.decode(Settings.getInstance(context).getString(KEYSTORE_PASSWORD_IV, ""), Base64.DEFAULT)
                c.init(Cipher.DECRYPT_MODE, secretKeyAPIMorGreater, GCMParameterSpec(128, iv))
            } else {
                c = Cipher.getInstance(AES_MODE_LESS_THAN_M, CIPHER_PROVIDER_NAME_ENCRYPTION_DECRYPTION_AES)
                c.init(Cipher.DECRYPT_MODE, getSecretKeyApiLessThanM(context))
            }
        } catch (e: InvalidKeyException) {
            // Since the keys can become bad (perhaps because of lock screen change)
            // drop keys in this case.
            removeKeys(context)
            throw e
        } catch (e: IOException) {
            removeKeys(context)
            throw e
        }
        val decodedBytes = c.doFinal(encryptedDecodedData)
        return String(decodedBytes, Charsets.UTF_8)
    }

    /**
     * Generates a random string between 32 and 48 chars
     *
     * @return a random string
     */
    fun generateRandomString(): String {
        val random = SecureRandom()
        val randomStringBuilder = StringBuilder()
        val randomLength = random.nextInt(16) + 32
        var tempChar: Char
        for (i in 0 until randomLength) {
            tempChar = Char(random.nextInt(95) + 33)
            randomStringBuilder.append(tempChar)
        }
        return randomStringBuilder.toString()
    }

    /**
     * Generates a random alphanumeric string
     *
     * @param length the string length
     * @return a string of random alphanumeric chars
     */
    fun generateRandomAlphanumericString(length:Int = -1): String {
        val random = SecureRandom()
        val randomStringBuilder = StringBuilder()
        val randomLength = if (length == -1) random.nextInt(16) + 32 else length
        val alphanumericChars = "abcdef0123456789"
        var tempChar: Char
        for (i in 0 until randomLength) {
            tempChar = alphanumericChars[random.nextInt(alphanumericChars.length)]
            randomStringBuilder.append(tempChar)
        }
        return randomStringBuilder.toString()
    }

    /**
     * Generates a byte array with random values
     *
     * @return a 12 long random bytes array
     */
    fun generateRandomBytes(): ByteArray {
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)
        return nonce
    }

    @Throws(KeyStoreException::class, CertificateException::class, NoSuchAlgorithmException::class, IOException::class, NoSuchProviderException::class, NoSuchPaddingException::class, UnrecoverableEntryException::class, InvalidKeyException::class)
    private fun rsaEncryptKey(secret: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_NAME)
        keyStore.load(null)
        val privateKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val inputCipher = Cipher.getInstance(RSA_MODE, CIPHER_PROVIDER_NAME_ENCRYPTION_DECRYPTION_RSA)
        inputCipher.init(Cipher.ENCRYPT_MODE, privateKeyEntry.certificate.publicKey)
        val outputStream = ByteArrayOutputStream()
        val cipherOutputStream = CipherOutputStream(outputStream, inputCipher)
        cipherOutputStream.write(secret)
        cipherOutputStream.close()
        return outputStream.toByteArray()
    }

    @Throws(KeyStoreException::class, CertificateException::class, NoSuchAlgorithmException::class, IOException::class, UnrecoverableEntryException::class, NoSuchProviderException::class, NoSuchPaddingException::class, InvalidKeyException::class)
    private fun rsaDecryptKey(encrypted: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_NAME)
        keyStore.load(null)
        val privateKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val output = Cipher.getInstance(RSA_MODE, CIPHER_PROVIDER_NAME_ENCRYPTION_DECRYPTION_RSA)
        output.init(Cipher.DECRYPT_MODE, privateKeyEntry.privateKey)
        val cipherInputStream = CipherInputStream(
                ByteArrayInputStream(encrypted), output)
        val values = ArrayList<Byte>()
        var nextByte: Int
        while (cipherInputStream.read().also { nextByte = it } != -1) {
            values.add(nextByte.toByte())
        }
        val decryptedKeyAsBytes = ByteArray(values.size)
        for (i in decryptedKeyAsBytes.indices) {
            decryptedKeyAsBytes[i] = values[i]
        }
        return decryptedKeyAsBytes
    }

    /**
     * Removes all the keys
     *
     * @param context the context used to retrieve the [SharedPreferences]
     */
    @Throws(KeyStoreException::class, CertificateException::class, NoSuchAlgorithmException::class, IOException::class)
    fun removeKeys(context: Context) {
        synchronized(s_keyInitLock) {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_NAME)
            keyStore.load(null)
            removeKeys(context, keyStore)
        }
    }
}