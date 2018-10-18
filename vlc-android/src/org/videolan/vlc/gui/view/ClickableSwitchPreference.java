package org.videolan.vlc.gui.view;

import android.content.Context;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.TwoStatePreference;
import androidx.appcompat.widget.SwitchCompat;
import android.view.View;
import android.widget.CompoundButton;

public class ClickableSwitchPreference extends TwoStatePreference {

    private View switchView;
    private View.OnClickListener switchClickListener;

    public ClickableSwitchPreference(Context context) {
        super(context, null, androidx.preference.R.attr.switchPreferenceCompatStyle, 0);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        switchView = holder.findViewById(androidx.preference.R.id.switchWidget);
        switchView.setOnClickListener(switchClickListener);

        //for some reason, it does not initialize itself;
        ((SwitchCompat) switchView).setChecked(isChecked());

        ((SwitchCompat)switchView).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

            }
        });
    }

    public void setOnSwitchClickListener(View.OnClickListener listener) {
        switchClickListener = listener;
    }

    @Override
    protected void onClick() {
        //Do not call super.onClick();
    }
}