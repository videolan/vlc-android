package org.videolan.vlc.gui.dialogs;

import android.content.DialogInterface;
import android.support.v4.app.DialogFragment;

public class DismissDialogFragment extends DialogFragment {
    protected DialogInterface.OnDismissListener onDismissListener;

    public void setOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
        this.onDismissListener = onDismissListener;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissListener != null) {
            onDismissListener.onDismiss(dialog);
        }
    }
}
