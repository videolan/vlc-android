package org.videolan.vlc.gui.view;

import android.content.Context;
import android.support.v7.preference.PreferenceViewHolder;
import android.support.v7.preference.TwoStatePreference;
import android.support.v7.widget.SwitchCompat;
import android.view.View;
import android.widget.CompoundButton;

public class ClickableSwitchPreference extends TwoStatePreference {

    private View switchView;
    private View.OnClickListener switchClickListener;

    public ClickableSwitchPreference(Context context) {
        super(context, null, android.support.v7.preference.R.attr.switchPreferenceCompatStyle, 0);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        switchView = holder.findViewById(android.support.v7.preference.R.id.switchWidget);
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