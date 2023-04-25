/*
 * ************************************************************************
 *  WebserverShareActivity.kt
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

package org.videolan.vlc.webserver.gui.webserver

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import org.videolan.resources.ACTION_START_SERVER
import org.videolan.resources.ACTION_STOP_SERVER
import org.videolan.tools.copy
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.gui.BaseActivity
import org.videolan.vlc.util.UrlUtils
import org.videolan.vlc.util.share
import org.videolan.vlc.webserver.HttpSharingServer
import org.videolan.vlc.webserver.R
import org.videolan.vlc.webserver.ServerStatus
import org.videolan.vlc.webserver.databinding.WebserverShareActivityBinding
import org.videolan.vlc.webserver.gui.webserver.adapters.ConnnectionAdapter


/**
 * Activity showing the different libraries used by VLC for Android and their licenses
 */
class WebserverShareActivity : BaseActivity() {


    private lateinit var binding: WebserverShareActivityBinding
    override fun getSnackAnchorView(overAudioPlayer: Boolean) = binding.root
    override val displayTitle = true
    private lateinit var connectionAdapter: ConnnectionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.webserver_share_activity)
        val toolbar = findViewById<MaterialToolbar>(R.id.main_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_up)
        title = getString(R.string.web_server)

        val httpSharingServer = HttpSharingServer.getInstance(applicationContext)
        binding.webserverQrCode.setImageBitmap(UrlUtils.generateQRCode(httpSharingServer.serverInfo(), 512))
        httpSharingServer.serverStatus.observe(this) { serverStatus ->
            binding.serverStatus.text = when (serverStatus) {
                ServerStatus.NOT_INIT -> getString(R.string.web_server_notification_not_init)
                ServerStatus.STARTED -> getString(R.string.web_server_active)
                ServerStatus.STOPPED -> getString(R.string.web_server_notification_stopped)
                ServerStatus.CONNECTING -> getString(R.string.web_server_notification_connecting)
                ServerStatus.ERROR -> getString(R.string.web_server_notification_error)
                ServerStatus.STOPPING -> getString(R.string.web_server_notification_stopping)
                else -> ""
            }

            arrayOf(binding.connectionTitle, binding.connectionList, binding.linksTitle, binding.webserverQrCode, binding.link, binding.linkCopy).forEach {
                if (serverStatus == ServerStatus.STARTED) it.setVisible() else it.setGone()
            }
            binding.statusButton.isEnabled = serverStatus in arrayOf(ServerStatus.STARTED, ServerStatus.STOPPED)
            binding.statusButton.text = getString(if (serverStatus == ServerStatus.STARTED) R.string.stop else R.string.start)
            binding.link.text = httpSharingServer.serverInfo()

        }
        binding.statusButton.setOnClickListener {
            val action = if (httpSharingServer.serverStatus.value == ServerStatus.STARTED) ACTION_STOP_SERVER else ACTION_START_SERVER
            sendBroadcast(Intent(action))
        }
        binding.linkCopy.setOnClickListener {
            copy("Webserver", httpSharingServer.serverInfo())
            Snackbar.make(window.decorView.findViewById(android.R.id.content), R.string.url_copied_to_clipboard, Snackbar.LENGTH_LONG).show()
        }

        connectionAdapter = ConnnectionAdapter(layoutInflater, listOf())
        binding.connectionList.layoutManager = LinearLayoutManager(this)
        binding.connectionList.adapter = connectionAdapter

        httpSharingServer.serverConnections.observe(this) {
            connectionAdapter.connections = it
            connectionAdapter.notifyDataSetChanged()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.webserver_share, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.webserver_share -> share(getString(R.string.web_server), HttpSharingServer.getInstance(applicationContext).serverInfo())
        }
        return super.onOptionsItemSelected(item)
    }

}
