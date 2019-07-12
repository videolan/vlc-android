package org.videolan.vlc.gui.tv.dialogs

import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import org.videolan.vlc.gui.tv.browser.BaseTvActivity

class ConfirmationTvActivity : BaseTvActivity() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val title = intent.getStringExtra(CONFIRMATION_DIALOG_TITLE)
            val text = intent.getStringExtra(CONFIRMATION_DIALOG_TEXT)
            val fragment = ConfirmationTvDialog.newInstance(title, text)
            GuidedStepSupportFragment.addAsRoot(this, fragment, android.R.id.content)
        }
    }

    companion object {
        const val CONFIRMATION_DIALOG_TITLE = "confirmation_dialog_title"
        const val CONFIRMATION_DIALOG_TEXT = "confirmation_dialog_text"
        const val ACTION_ID_POSITIVE = 1
        const val ACTION_ID_NEGATIVE = ACTION_ID_POSITIVE + 1
    }

    override fun refresh() {}
}