/*
 * ************************************************************************
 *  RemoteAccessOTP.kt
 * *************************************************************************
 * Copyright © 2023 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.webserver

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import org.videolan.vlc.gui.helpers.NotificationHelper
import org.videolan.vlc.gui.helpers.REMOTE_ACCESS_CODE_ID
import org.videolan.vlc.webserver.ssl.SecretGenerator
import org.videolan.vlc.webserver.utils.CypherUtils
import java.security.SecureRandom


object RemoteAccessOTP {

    private val codes = ArrayList<OTPCode>()

    /**
     * generate an [OTPCode] and store it in memory for later use
     *
     * @return the generate [OTPCode]
     */
    private fun generateOTPCode(): OTPCode {
        val code = generateCode()
        val otpCode = OTPCode(code, SecretGenerator.generateRandomString(), System.currentTimeMillis() + 60000)
        codes.add(otpCode)
        return otpCode
    }

    fun generateCode(): String = (SecureRandom().nextInt(8999) + 1000).toString()

    /**
     * Verify if the code is valid by using the challenge
     *
     * @param appContext the app context used to cancel the notification
     * @param saltedCode the sha256 of the code + challenge to verify
     * @return true if the code is valid
     */
    fun verifyCode(appContext: Context, saltedCode: String): Boolean {
        codes.forEach {
            if (CypherUtils.hash(it.code + it.challenge) == saltedCode && System.currentTimeMillis() < it.expiration) {
                with(NotificationManagerCompat.from(appContext)) {
                    cancel(REMOTE_ACCESS_CODE_ID)
                }
                codes.remove(it)
                return true
            }
        }
        return false
    }

    /**
     * Get the first code that is still valid
     *
     * @param appContext the app context used to manage the notification
     * @return the first valid code or a new one if none is found
     */
    fun getFirstValidCode(appContext: Context): OTPCode {
        val toRemove = ArrayList<Int>()
        codes.forEachIndexed() { index, code ->
            if (System.currentTimeMillis() < code.expiration) {
                return code
            } else toRemove.add(index)
        }
        toRemove.sortDescending()
        toRemove.forEach {
            codes.removeAt(it)
        }
        val code = generateOTPCode()
        val notification = NotificationHelper.createRemoteAccessOtpNotification(appContext, code.code)
        with(NotificationManagerCompat.from(appContext)) {
            // notificationId is a unique int for each notification that you must define
            notify(REMOTE_ACCESS_CODE_ID, notification)
        }
        return code
    }

    /**
     * Remove the code corresponding to the challenge
     *
     * @param challenge
     */
    fun removeCodeWithChallenge(challenge: String) {
        codes.remove(codes.find { challenge == it.challenge })
    }
}

data class OTPCode(val code: String, val challenge: String, val expiration: Long)