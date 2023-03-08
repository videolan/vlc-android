/*****************************************************************************
 * DebugLogActivity.java
 *
 * Copyright © 2013-2015 VLC authors and VideoLAN
 * Copyright © 2013 Edward Wang
 *
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
 */
package org.videolan.vlc.gui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.AndroidDevices
import org.videolan.vlc.DebugLogService
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.share
import java.io.File

class DebugLogActivity : FragmentActivity(), DebugLogService.Client.Callback {
    private lateinit var client: DebugLogService.Client
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var copyButton: Button
    private lateinit var clearButton: Button
    private lateinit var saveButton: Button
    private lateinit var logView: ListView
    private var logList: MutableList<String> = ArrayList()
    private lateinit var logAdapter: ArrayAdapter<String>

    private val startClickListener = View.OnClickListener {
        startButton.isEnabled = false
        stopButton.isEnabled = false
        client.start()
    }

    private val stopClickListener = View.OnClickListener {
        startButton.isEnabled = false
        stopButton.isEnabled = false
        client.stop()
    }

    private val clearClickListener = View.OnClickListener {
        if (::client.isInitialized) client.clear()
        logList.clear()
        if (::logAdapter.isInitialized) logAdapter.notifyDataSetChanged()
        setOptionsButtonsEnabled(false)
    }

    private val saveClickListener = View.OnClickListener {
        if (AndroidUtil.isOOrLater && !Permissions.canWriteStorage())
            Permissions.askWriteStoragePermission(this@DebugLogActivity, false, Runnable { client.save() })
        else
            client.save()
    }

    private val copyClickListener = View.OnClickListener {
        val buffer = StringBuffer()
        for (line in logList)
            buffer.append(line).append("\n")

        val clipboard = applicationContext.getSystemService<ClipboardManager>()!!
        clipboard.setPrimaryClip(ClipData.newPlainText(null, buffer))

        UiTools.snacker(this, R.string.copied_to_clipboard)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.debug_log)

        startButton = findViewById(R.id.start_log)
        stopButton = findViewById(R.id.stop_log)
        logView = findViewById(R.id.log_list)
        copyButton = findViewById(R.id.copy_to_clipboard)
        clearButton = findViewById(R.id.clear_log)
        saveButton = findViewById(R.id.save_to_file)

        client = DebugLogService.Client(this, this)

        startButton.isEnabled = false
        stopButton.isEnabled = false
        setOptionsButtonsEnabled(false)

        startButton.setOnClickListener(startClickListener)
        stopButton.setOnClickListener(stopClickListener)
        clearButton.setOnClickListener(clearClickListener)
        saveButton.setOnClickListener(saveClickListener)

        copyButton.setOnClickListener(copyClickListener)
    }

    override fun onDestroy() {
        client.release()
        super.onDestroy()
    }

    private fun setOptionsButtonsEnabled(enabled: Boolean) {
        clearButton.isEnabled = enabled
        copyButton.isEnabled = enabled
        saveButton.isEnabled = enabled
    }

    override fun onStarted(logList: List<String>) {
        startButton.isEnabled = false
        stopButton.isEnabled = true
        if (logList.isNotEmpty())
            setOptionsButtonsEnabled(true)
        this.logList = ArrayList(logList)
        logAdapter = ArrayAdapter(this, R.layout.debug_log_item, this.logList)
        logView.adapter = logAdapter
        logView.transcriptMode = ListView.TRANSCRIPT_MODE_NORMAL
        if (this.logList.size > 0)
            logView.setSelection(this.logList.size - 1)
    }

    override fun onStopped() {
        startButton.isEnabled = true
        stopButton.isEnabled = false
    }

    override fun onLog(msg: String) {
        logList.add(msg)
        if (::logAdapter.isInitialized) logAdapter.notifyDataSetChanged()
        setOptionsButtonsEnabled(true)
    }

    override fun onSaved(success: Boolean, path: String) {
        if (success) {
            if (AndroidDevices.isAndroidTv)
            Snackbar.make(logView, String.format(
                    getString(R.string.dump_logcat_success),
                    path), Snackbar.LENGTH_LONG).show()
            else UiTools.snackerConfirm(this, String.format(getString(R.string.dump_logcat_success), path), false, R.string.share) {
                share(File(path))
            }
        } else {
            UiTools.snacker(window.decorView.findViewById(android.R.id.content), R.string.dump_logcat_failure)
        }
    }

    companion object {
        const val TAG = "VLC/DebugLogActivity"
    }
}
