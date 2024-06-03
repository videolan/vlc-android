/*
 * ************************************************************************
 *  RemoteAccessShareActivity.kt
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

package org.videolan.vlc.webserver.gui.remoteaccess

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.gridlayout.widget.GridLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import org.videolan.resources.ACTION_START_SERVER
import org.videolan.resources.ACTION_STOP_SERVER
import org.videolan.resources.REMOTE_ACCESS_ONBOARDING
import org.videolan.tools.copy
import org.videolan.tools.dp
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.gui.BaseActivity
import org.videolan.vlc.util.UrlUtils
import org.videolan.vlc.util.share
import org.videolan.vlc.webserver.R
import org.videolan.vlc.webserver.RemoteAccessServer
import org.videolan.vlc.webserver.ServerStatus
import org.videolan.vlc.webserver.databinding.RemoteAccessShareActivityBinding
import org.videolan.vlc.webserver.gui.remoteaccess.adapters.ConnnectionAdapter


/**
 * Activity showing the different libraries used by VLC for Android and their licenses
 */
class RemoteAccessShareActivity : BaseActivity() {


    private lateinit var binding: RemoteAccessShareActivityBinding
    override fun getSnackAnchorView(overAudioPlayer: Boolean) = binding.root
    override val displayTitle = true
    private lateinit var connectionAdapter: ConnnectionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.remote_access_share_activity)
        val toolbar = findViewById<MaterialToolbar>(R.id.main_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_up)
        title = getString(R.string.remote_access)

        val remoteAccessServer = RemoteAccessServer.getInstance(applicationContext)
        remoteAccessServer.serverStatus.observe(this) { serverStatus ->
            binding.serverStatus.text = when (serverStatus) {
                ServerStatus.NOT_INIT -> getString(R.string.remote_access_notification_not_init)
                ServerStatus.STARTED -> getString(R.string.remote_access_active)
                ServerStatus.STOPPED -> getString(R.string.remote_access_notification_stopped)
                ServerStatus.CONNECTING -> getString(R.string.remote_access_notification_connecting)
                ServerStatus.ERROR -> getString(R.string.remote_access_notification_error)
                ServerStatus.STOPPING -> getString(R.string.remote_access_notification_stopping)
                else -> ""
            }

            arrayOf(binding.connectionTitle, binding.connectionList, binding.linksTitle, binding.remoteAccessQrCode, binding.linksGrid).forEach {
                if (serverStatus == ServerStatus.STARTED) it.setVisible() else it.setGone()
            }
            binding.statusButton.isEnabled = serverStatus in arrayOf(ServerStatus.STARTED, ServerStatus.STOPPED)
            binding.statusButton.text = getString(if (serverStatus == ServerStatus.STARTED) R.string.stop else R.string.start)
            binding.linksGrid.removeAllViews()
            remoteAccessServer.getServerAddresses().forEach { link ->
                val linkText = TextView(this)
                linkText.text = link
                val copyImageView = ImageView(this)
                copyImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_copy))
                copyImageView.setOnClickListener {
                    copy("VLC for Android Remote Access link", link)
                    Snackbar.make(window.decorView.findViewById(android.R.id.content), R.string.url_copied_to_clipboard, Snackbar.LENGTH_LONG).show()
                }
                val outValue = TypedValue()
                theme.resolveAttribute(R.attr.selectableItemBackgroundBorderless, outValue, true)
                copyImageView.setBackgroundResource(outValue.resourceId)
                copyImageView.setPadding(8.dp, 8.dp, 8.dp, 8.dp)

                val shareImageView = ImageView(this)
                shareImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_share))
                shareImageView.setOnClickListener {
                    share(getString(R.string.remote_access), link)
                }
                shareImageView.setBackgroundResource(outValue.resourceId)
                shareImageView.setPadding(8.dp, 8.dp, 8.dp, 8.dp)

                val qrImageView = ImageView(this)
                qrImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_qr_code))
                qrImageView.setBackgroundResource(outValue.resourceId)
                qrImageView.setPadding(8.dp, 8.dp, 8.dp, 8.dp)
                qrImageView.setOnClickListener {
                    val qrView = ImageView(this)
                    qrView.setPadding(8.dp,8.dp,8.dp,8.dp)
                    qrView.setImageBitmap(UrlUtils.generateQRCode(link, 512))
                    AlertDialog.Builder(this)
                            .setTitle(resources.getString(R.string.remote_access_notification, link))
                            .setView(qrView)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                }
                binding.linksGrid.addView(linkText)
                binding.linksGrid.addView(qrImageView)
                binding.linksGrid.addView(shareImageView)
                binding.linksGrid.addView(copyImageView)

                (qrImageView.layoutParams as GridLayout.LayoutParams).setGravity(Gravity.CENTER_VERTICAL)
                (copyImageView.layoutParams as GridLayout.LayoutParams).setGravity(Gravity.CENTER_VERTICAL)
                (shareImageView.layoutParams as GridLayout.LayoutParams).setGravity(Gravity.CENTER_VERTICAL)

                val layoutParams = linkText.layoutParams as GridLayout.LayoutParams
                layoutParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                layoutParams.height = 48.dp
                linkText.gravity = Gravity.CENTER_VERTICAL
                linkText.layoutParams = layoutParams
            }

        }
        binding.statusButton.setOnClickListener {
            val action = if (remoteAccessServer.serverStatus.value == ServerStatus.STARTED) ACTION_STOP_SERVER else ACTION_START_SERVER
            sendBroadcast(Intent(action).apply { `package` = packageName })
        }

        connectionAdapter = ConnnectionAdapter(layoutInflater, listOf())
        binding.connectionList.layoutManager = LinearLayoutManager(this)
        binding.connectionList.adapter = connectionAdapter

        remoteAccessServer.serverConnections.observe(this) {
            connectionAdapter.connections = it
            connectionAdapter.notifyDataSetChanged()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.remote_access_share, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.menu_remote_access_onboarding -> startActivity(Intent(Intent.ACTION_VIEW).apply { setClassName(this@RemoteAccessShareActivity, REMOTE_ACCESS_ONBOARDING) })

        }
        return super.onOptionsItemSelected(item)
    }

}
