package org.videolan.vlc.gui.dialogs

import android.content.DialogInterface
import androidx.fragment.app.DialogFragment

class DismissDialogFragment : DialogFragment() {
    var onDismissListener: DialogInterface.OnDismissListener? = null


    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (onDismissListener != null) {
            onDismissListener!!.onDismiss(dialog)
        }
    }
}
