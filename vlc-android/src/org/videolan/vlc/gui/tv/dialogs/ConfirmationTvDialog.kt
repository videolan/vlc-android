package org.videolan.vlc.gui.tv.dialogs

import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import org.videolan.vlc.R
import org.videolan.vlc.gui.tv.dialogs.ConfirmationTvActivity.Companion.ACTION_ID_NEGATIVE
import org.videolan.vlc.gui.tv.dialogs.ConfirmationTvActivity.Companion.ACTION_ID_POSITIVE
import org.videolan.vlc.gui.tv.dialogs.ConfirmationTvActivity.Companion.CONFIRMATION_DIALOG_TEXT
import org.videolan.vlc.gui.tv.dialogs.ConfirmationTvActivity.Companion.CONFIRMATION_DIALOG_TITLE

class ConfirmationTvDialog : GuidedStepSupportFragment() {

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(arguments!!.getString(CONFIRMATION_DIALOG_TITLE),
                arguments!!.getString(CONFIRMATION_DIALOG_TEXT),
                "", null)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        var action = GuidedAction.Builder(requireActivity())
                .id(ACTION_ID_POSITIVE.toLong())
                .title(getString(R.string.yes)).build()
        actions.add(action)
        action = GuidedAction.Builder(requireActivity())
                .id(ACTION_ID_NEGATIVE.toLong())
                .title(getString(R.string.no)).build()
        actions.add(action)
    }

    override fun onGuidedActionClicked(action: GuidedAction?) {
        if (ACTION_ID_POSITIVE.toLong() == action!!.id) {
            requireActivity().setResult(ACTION_ID_POSITIVE)
        } else {
            requireActivity().setResult(ACTION_ID_NEGATIVE)
        }
        requireActivity().finish()
    }

    companion object {
        fun newInstance(title: String, text: String): ConfirmationTvDialog = ConfirmationTvDialog().also {
            val args = Bundle()
            args.putString(CONFIRMATION_DIALOG_TITLE, title)
            args.putString(CONFIRMATION_DIALOG_TEXT, text)
            it.arguments = args
        }
    }
}