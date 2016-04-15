package org.videolan.vlc.gui.preferences.hack;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.v14.preference.MultiSelectListPreference;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;

import java.util.HashSet;
import java.util.Set;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class MultiSelectListPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat
        implements DialogPreference.TargetFragment {
    private final Set<String> mNewValues = new HashSet<>();
    private boolean mPreferenceChanged;

    public MultiSelectListPreferenceDialogFragmentCompat() {
    }

    public static MultiSelectListPreferenceDialogFragmentCompat newInstance(String key) {
        MultiSelectListPreferenceDialogFragmentCompat fragment = new MultiSelectListPreferenceDialogFragmentCompat();
        Bundle b = new Bundle(1);
        b.putString("key", key);
        fragment.setArguments(b);
        return fragment;
    }

    private MultiSelectListPreference getListPreference() {
        return (MultiSelectListPreference)this.getPreference();
    }

   protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        final MultiSelectListPreference preference = getListPreference();
        if (preference.getEntries() != null && preference.getEntryValues() != null) {
            boolean[] checkedItems = getSelectedItems();
            builder.setMultiChoiceItems(preference.getEntries(), checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    mPreferenceChanged = true;
                    if (isChecked) {
                        mNewValues.add(preference.getEntryValues()[which].toString());
                    } else {
                        mNewValues.remove(preference.getEntryValues()[which].toString());
                    }
                }
            });
            this.mNewValues.clear();
            this.mNewValues.addAll(preference.getValues());
        } else {
            throw new IllegalStateException("MultiSelectListPreference requires an entries array and an entryValues array.");
        }
    }

    public void onDialogClosed(boolean positiveResult) {
        MultiSelectListPreference preference = getListPreference();
        if (positiveResult && mPreferenceChanged) {
            Set<String> values = mNewValues;
            if (preference.callChangeListener(values)) {
                preference.setValues(values);
            }
        }
        this.mPreferenceChanged = false;
    }

    private boolean[] getSelectedItems() {
        MultiSelectListPreference preference = getListPreference();
        CharSequence[] entries = preference.getEntryValues();
        Set<String> values = preference.getValues();
        boolean[] result = new boolean[entries.length];

        for (int i = 0; i < entries.length; i++) {
            result[i] = values.contains(entries[i].toString());
        }

        return result;
    }

    @Override
    public Preference findPreference(CharSequence charSequence) {
        return getPreference();
    }
}

