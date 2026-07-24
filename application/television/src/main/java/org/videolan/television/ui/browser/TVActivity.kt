package org.videolan.television.ui.browser

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import org.videolan.resources.BROWSER_TYPE
import org.videolan.resources.HEADER_STREAM
import org.videolan.television.ui.DefaultTvActivity
import org.videolan.television.ui.compose.composable.screens.StreamScreen
import org.videolan.television.ui.compose.theme.VlcTVTheme

@AndroidEntryPoint
class TVActivity : DefaultTvActivity() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getLongExtra(BROWSER_TYPE, -1)
        if (type != HEADER_STREAM) {
            finish()
            return
        }

        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }
        setContent {
            VlcTVTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StreamScreen()
                }
            }
        }
    }
}