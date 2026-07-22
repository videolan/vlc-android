package org.videolan.television.ui.browser

import android.os.Bundle
import androidx.fragment.app.Fragment
import org.videolan.resources.BROWSER_TYPE
import org.videolan.resources.HEADER_STREAM
import org.videolan.television.R
import org.videolan.vlc.gui.network.MRLPanelFragment

class TVActivity : BaseTvActivity() {

    private lateinit var fragment: Fragment

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tv_vertical_grid)
        if (savedInstanceState == null) {
            val type = intent.getLongExtra(BROWSER_TYPE, -1)
            if (type == HEADER_STREAM) {
                fragment = MRLPanelFragment()
            } else {
                finish()
                return
            }
            supportFragmentManager.beginTransaction()
                    .add(R.id.tv_fragment_placeholder, fragment)
                    .commit()
        }
    }

    override fun refresh() { }
}