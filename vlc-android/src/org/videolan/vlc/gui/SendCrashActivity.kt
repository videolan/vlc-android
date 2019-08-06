/*
 * *************************************************************************
 *  FilePickerActivity.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider.getUriForFile
import androidx.core.text.HtmlCompat
import androidx.databinding.DataBindingUtil
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.databinding.SendCrashActivityBinding
import org.videolan.vlc.util.*
import java.io.File

const val SEND_CRASH_EMAIL = 1

class SendCrashActivity : AppCompatActivity() {

    private lateinit var binding: SendCrashActivityBinding
    private val path = VLCApplication.appContext.getExternalFilesDir(null)!!.absolutePath + "/last.crash"
    private val dbPath = VLCApplication.appContext.getExternalFilesDir(null)!!.absolutePath + "/" + AbstractMedialibrary.VLC_MEDIA_DB_NAME
    private val dbZipPath = VLCApplication.appContext.getExternalFilesDir(null)!!.absolutePath + "/" + "db.zip"

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, org.videolan.vlc.R.layout.send_crash_activity)
        binding.doNotSendCrashButton.setOnClickListener { close() }
        binding.sendCrashButton.setOnClickListener {
            runIO(Runnable {

                val stack = FileUtils.getStringFromFile(path)

                val emailIntent = Intent(Intent.ACTION_SEND)
                emailIntent.type = "message/rfc822"

                //get medialib db if needed
                if (binding.includeMedialibSwitch.isChecked) {
                    if (Permissions.canWriteStorage()) {
                        val db = File(getDir("db", Context.MODE_PRIVATE).toString() + AbstractMedialibrary.VLC_MEDIA_DB_NAME)

                        val dbFile = File(dbPath)
                        FileUtils.copyFile(db, dbFile)
                        FileUtils.zip(arrayOf(dbPath), dbZipPath)
                        FileUtils.deleteFile(dbFile)

                        val dbUri = getUriForFile(this, applicationContext.packageName + ".provider", File(dbZipPath))
                        emailIntent.putExtra(Intent.EXTRA_STREAM, dbUri)
                        emailIntent.type = "application/zip"
                    }
                }

                //body
                val body = "<p style=\"font-weight:bold;\">Here are my crash logs for VLC</strong></p><p style=3D\"color:#16171A;\"> [Please enter any useful information here]</p><p>${stack.replace("\n", "<br/>")}</p>"
                val htmlBody = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Html.fromHtml(body, HtmlCompat.FROM_HTML_MODE_LEGACY) else Html.fromHtml(body)


                emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf("videolan.android+androidcrash@gmail.com"))
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Crash logs for VLC")
                emailIntent.putExtra(Intent.EXTRA_TEXT, htmlBody)
                emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivityForResult(emailIntent, SEND_CRASH_EMAIL)

            })

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SEND_CRASH_EMAIL) {
            FileUtils.deleteFile(path)
            close()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun close() {
        if (binding.dontAskAgain.isChecked) Settings.getInstance(this).edit().putBoolean(CRASH_DONT_ASK_AGAIN, true).apply()

        finish()
    }
}
