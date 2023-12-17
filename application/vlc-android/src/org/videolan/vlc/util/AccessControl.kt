/*****************************************************************************
 * AccessControl.kt
 *
 * Copyright © 2021 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 */

package org.videolan.vlc.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.getPackageInfoCompat
import org.videolan.vlc.R
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

private const val TAG = "VLC/AccessControl"

data class CertInfo(val name: String, val keys: List<String>)
data class AuthEntry(val approved: Boolean, val callingPackage: String?, val desc: String)

object AccessControl {
    private val platformSignature: String?
    private val callingUidChecked = mutableMapOf<Int, AuthEntry>()
    private val certificateAllowList by lazy { runBlocking { loadAuthorizedKeys() } }

    init {
        platformSignature = getSignature(AppContextProvider.appContext, "android")
    }

    fun getKeysByPackage(packageName: String): List<String> {
        return certificateAllowList[packageName]?.keys ?: emptyList()
    }

    /**
     * Perform security checks to determine if the callingUid is authorized.
     * @param callingUid The calling application's user id.
     * @param clientPackageName The calling application's package name. If not specified, an attempt
     * will be made to resolve the package name from the package manager.
     */
    fun logCaller(callingUid: Int, clientPackageName: String? = null) {
        callingUidChecked[callingUid]?.let {
            return
        }
        val ctx = AppContextProvider.appContext
        val callingPackage = getCallingPackage(ctx, callingUid, clientPackageName)
        when {
            callingUid == Process.myUid() -> {
                Log.i(TAG, "Known access from self (${ctx.packageName}) to VLC")
                callingUidChecked[callingUid] = AuthEntry(true, ctx.packageName, "VLC UID")
                return
            }
            callingUid == Process.SYSTEM_UID -> {
                Log.i(TAG, "Known access from system to VLC")
                callingUidChecked[callingUid] = AuthEntry(true, "system", "System Process")
                return
            }
            callingPackage != null -> {
                val callingSignature = getSignature(ctx, callingPackage)
                if (callingSignature == platformSignature) {
                    Log.i(TAG, "Known access from Android platform ($callingPackage) to VLC")
                    callingUidChecked[callingUid] = AuthEntry(true, callingPackage, "Known Platform Signature")
                    return
                }
                val certs = certificateAllowList[callingPackage]
                certs?.keys?.forEach { key ->
                    if (callingSignature == key) {
                        Log.i(TAG, "Known access from ${certs.name} ($callingPackage) to VLC")
                        callingUidChecked[callingUid] = AuthEntry(true, callingPackage, "Known App Signature")
                        return
                    }
                }
                Log.i(TAG, "Unknown access from signature $callingSignature ($callingPackage) to VLC")
                callingUidChecked[callingUid] = AuthEntry(false, callingPackage, "Unknown Signature")
            }
        }
        Log.i(TAG, "Access history: $callingUidChecked")
    }

    fun getCallingPackage(ctx: Context, callingUid: Int, clientPackageName: String? = null): String? {
        val packages = ctx.packageManager.getPackagesForUid(callingUid) ?: return null
        val packageName = packages.firstOrNull()
        return if (clientPackageName == null || clientPackageName == packageName) {
            packageName
        } else {
            Log.i(TAG, "Unexpected package name mismatch between $clientPackageName and $packageName")
            null
        }
    }

    /**
     * Read authorized keys into memory. Keys are stored in a JSON data file.
     */
    private suspend fun loadAuthorizedKeys(): Map<String, CertInfo> {
        return withContext(Dispatchers.IO) {
            val certificateAllowList = mutableMapOf<String, CertInfo>()
            val jsonData = AppContextProvider.appResources.openRawResource(R.raw.authorized_keys).bufferedReader().use {
                it.readText()
            }
            val signatures = JSONArray(jsonData)
            for (i in 0 until signatures.length()) {
                val s = signatures[i] as JSONObject
                val keys = s.getJSONArray("keys")
                val keyList = arrayListOf<String>()
                for (j in 0 until keys.length()) {
                    val keyId = (keys[j] as JSONObject).getString("keyId")
                    keyList.add(keyId)
                }
                val name = s.getString("name")
                val packageName = s.getString("package")
                certificateAllowList[packageName] = CertInfo(name, keyList)
            }
            certificateAllowList
        }
    }

    @Suppress("deprecation")
    private fun getSignature(ctx: Context, callingPackage: String): String? {
        try {
            val packageInfo = ctx.packageManager.getPackageInfoCompat(callingPackage, PackageManager.GET_SIGNATURES)
            if (packageInfo.signatures != null && packageInfo.signatures.size == 1) {
                return genSigSha256(packageInfo.signatures[0].toByteArray())
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Calling package name not found: $callingPackage", e)
        }
        return null
    }

    private fun genSigSha256(certificate: ByteArray): String? {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(certificate)
            return md.digest().joinToString(":") { String.format("%02x", it) }
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "Message digest algorithm SHA-256 not found", e)
        }
        return null
    }
}
