package org.videolan.vlc.addon

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.videolan.vlc.R

/**
 * Ecran d'accueil de l'addon : liste les contenus exposes par l'API
 * et lance la lecture au tap.
 */
class HomeActivity : AppCompatActivity() {

    private val contentApi by lazy { ContentApiProvider.get(this) }
    private lateinit var container: LinearLayout
    private var items: List<ContentItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        addTitle()
        setContentView(container)
        loadItems()
    }

    private fun addTitle() {
        val title = TextView(this).apply {
            text = getString(R.string.app_name_full)
            textSize = 20f
            setPadding(24, 24, 24, 24)
            gravity = Gravity.CENTER
        }
        container.addView(title)
    }

    private fun loadItems() {
        lifecycleScope.launch {
            items = contentApi.getItems()
            for (item in items) {
                val positionSec = item.position
                val label = item.title +
                        if (positionSec > 0) " · ${formatPosition(positionSec)}" else ""
                container.addView(TextView(this@HomeActivity).apply {
                    text = label
                    textSize = 16f
                    setPadding(24, 12, 24, 12)
                    setOnClickListener { play(item) }
                })
            }
        }
    }

    private fun play(item: ContentItem) {
        ContentPlayer.play(this, item)
    }

    private fun formatPosition(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%d:%02d".format(m, s)
    }
}