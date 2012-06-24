package org.videolan.vlc.gui;

import org.videolan.vlc.R;
import org.videolan.vlc.Util;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class AboutMainFragment extends Fragment {
    public final static String TAG = "VLC/AboutMainFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.about_main, container, false);
        TextView link = (TextView) v.findViewById(R.id.main_link);
        link.setText(Html.fromHtml(this.getString(R.string.about_link)));

        String builddate = Util.readAsset(getActivity(), "builddate.txt", "Unknown");
        String builder = Util.readAsset(getActivity(), "builder.txt", "unknown");

        TextView compiled = (TextView) v.findViewById(R.id.main_compiled);
        compiled.setText(builder + " (" + builddate + ")");

        return v;
    }
}
