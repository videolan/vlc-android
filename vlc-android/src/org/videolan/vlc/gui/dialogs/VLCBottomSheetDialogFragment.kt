package org.videolan.vlc.gui.dialogs

import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.vlc.R
import org.videolan.vlc.util.AppScope
import org.videolan.vlc.util.Settings

abstract class VLCBottomSheetDialogFragment : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Settings.showTvUi) {
            requireActivity().setTheme(R.style.Theme_VLC_Black)
        }
        super.onCreate(savedInstanceState)
    }

    var onDismissListener: DialogInterface.OnDismissListener? = null

    fun inflate(inflater: LayoutInflater, container: ViewGroup?, @LayoutRes layout: Int): View? {
//        if (Settings.showTvUi) {
//
//            val contextThemeWrapper = ContextThemeWrapper(activity, R.style.Theme_VLC_Black) // your app theme here
//            return inflater.cloneInContext(contextThemeWrapper).inflate(layout, container, true)
//        }
        return inflater.inflate(layout, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        AppScope.launch(Dispatchers.Main) {
            dialog?.window?.setLayout(resources.getDimensionPixelSize(R.dimen.default_context_width), ViewGroup.LayoutParams.MATCH_PARENT)
            dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                val bsb = BottomSheetBehavior.from(it)
                if (bsb.state == BottomSheetBehavior.STATE_COLLAPSED) bsb.state = getDefaultState()
            }
            dialog?.findViewById<View>(R.id.touch_outside)?.isFocusable = false
            dialog?.findViewById<View>(R.id.touch_outside)?.isFocusableInTouchMode = false
        }


    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.onDismiss(dialog)
    }

    override fun onResume() {
        super.onResume()

        initialFocusedView().isFocusable = true
        initialFocusedView().isFocusableInTouchMode = true

        initialFocusedView().requestFocus()
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        if (!needToManageOrientation()) {
            super.onConfigurationChanged(newConfig)
            return
        }
        val fragmentManager = fragmentManager
        if (fragmentManager != null) {
            dismiss()
        }
        super.onConfigurationChanged(newConfig)
        if (fragmentManager != null) {
            show(fragmentManager, tag)
        }
    }

    /**
     * Default state for the [BottomSheetBehavior]
     * Should be one of [BottomSheetBehavior.STATE_EXPANDED],[BottomSheetBehavior.STATE_COLLAPSED],[BottomSheetBehavior.STATE_HALF_EXPANDED]
     */
    abstract fun getDefaultState(): Int

    /**
     * Sends true if the fragments needs to be re-created when Activity is not recreated onConfigurationChanged
     */
    abstract fun needToManageOrientation(): Boolean

    /**
     * The initial view to be focused to avoid BottomSheetDialogFragment to steal it
     * Both fields [isFocusable] and [isFocusableInTouchMode] will be set to true
     */
    abstract fun initialFocusedView(): View


}