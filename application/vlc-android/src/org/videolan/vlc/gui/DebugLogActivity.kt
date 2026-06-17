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
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.AndroidDevices
import org.videolan.vlc.DebugLogService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DebugLogBinding
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.share
import java.io.File

class DebugLogActivity : AppCompatActivity(), DebugLogService.Client.Callback {
    private lateinit var client: DebugLogService.Client
    private var logList: MutableList<String> = ArrayList()
    private lateinit var logAdapter: ArrayAdapter<String>
    private lateinit var binding: DebugLogBinding


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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(android.R.id.content)) { v, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
        binding = DataBindingUtil.setContentView(this, R.layout.debug_log)

        client = DebugLogService.Client(this, this)

        setOptionsButtonsEnabled(false)

        binding.startLog.setOnClickListener {
            if (client.isStarted())
                client.stop()
            else
                client.start()
        }
        binding.clearLog.setOnClickListener(clearClickListener)
        binding.saveToFile.setOnClickListener(saveClickListener)

        binding.copyToClipboard.setOnClickListener(copyClickListener)
        Log.d(TAG, "Entering DebugLogActivity")
    }

    override fun onDestroy() {
        client.release()
        super.onDestroy()
    }

    private fun setOptionsButtonsEnabled(enabled: Boolean) {
        binding.clearLog.isEnabled = enabled
        binding.copyToClipboard.isEnabled = enabled
        binding.saveToFile.isEnabled = enabled
    }

    override fun onStarted(logList: List<String>) {
        binding.startLog.text = getString(R.string.stop_logging)
        if (logList.isNotEmpty())
            setOptionsButtonsEnabled(true)
        this.logList = ArrayList(logList)
        logAdapter = ArrayAdapter(this, R.layout.debug_log_item, this.logList)
        binding.logList.adapter = logAdapter
        binding.logList.transcriptMode = ListView.TRANSCRIPT_MODE_NORMAL
        if (this.logList.size > 0)
            binding.logList.setSelection(this.logList.size - 1)
    }

    override fun onStopped() {
        binding.startLog.text = getString(R.string.start_logging)
    }

    override fun onLog(msg: String) {
        logList.add(msg)
        if (::logAdapter.isInitialized) logAdapter.notifyDataSetChanged()
        setOptionsButtonsEnabled(true)
    }

    override fun onSaved(success: Boolean, path: String) {
        if (success) {
            if (AndroidDevices.isAndroidTv)
            Snackbar.make(binding.logList, String.format(
                    getString(R.string.dump_logcat_success),
                    path), Snackbar.LENGTH_LONG).show()
            else UiTools.snackerConfirm(this, String.format(getString(R.string.dump_logcat_success), path), false, R.string.share) {
                share(File(path))
            }
        } else {
            UiTools.snacker(this, R.string.dump_logcat_failure)
        }
    }

    companion object {
        const val TAG = "VLC/DebugLogActivity"
    }
}
