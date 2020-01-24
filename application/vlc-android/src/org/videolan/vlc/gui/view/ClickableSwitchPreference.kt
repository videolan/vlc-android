package org.videolan.vlc.gui.view

import android.content.Context
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceViewHolder
import androidx.preference.TwoStatePreference

class ClickableSwitchPreference(context: Context) : TwoStatePreference(context, null, androidx.preference.R.attr.switchPreferenceCompatStyle, 0) {

    private var switchView: View? = null
    private var switchClickListener: View.OnClickListener? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        switchView = holder.findViewById(androidx.preference.R.id.switchWidget)
        switchView!!.setOnClickListener(switchClickListener)

        //for some reason, it does not initialize itself;
        (switchView as SwitchCompat).isChecked = isChecked

        (switchView as SwitchCompat).setOnCheckedChangeListener { _, _ -> }
    }

    fun setOnSwitchClickListener(listener: View.OnClickListener) {
        switchClickListener = listener
    }

    override fun onClick() {
        //Do not call super.onClick();
    }
}