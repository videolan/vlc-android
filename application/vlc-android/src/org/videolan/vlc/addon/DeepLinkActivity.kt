package org.videolan.vlc.addon

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Traite les deeplinks du type
 * `xplayon://play?id=<contentId>`.
 */
class DeepLinkActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contentId = intent?.data?.getQueryParameter("id")
        if (contentId.isNullOrEmpty()) {
            finish()
            return
        }
        play(contentId)
    }

    private fun play(contentId: String) {
        lifecycleScope.launch {
            val item = ContentApiProvider.get(this@DeepLinkActivity).getItem(contentId)
            if (item != null) ContentPlayer.play(this@DeepLinkActivity, item)
            finish()
        }
    }
}