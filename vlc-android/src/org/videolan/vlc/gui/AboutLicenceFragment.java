package org.videolan.vlc.gui;

import org.videolan.vlc.R;
import org.videolan.vlc.Util;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

public class AboutLicenceFragment extends Fragment {
    public final static String TAG = "VLC/AboutLicenceFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.about_licence, container, false);
        String revision = Util.readAsset(getActivity(), "revision.txt", "Unknown revision");
        WebView t = (WebView)v.findViewById(R.id.webview);
        t.loadData(Util.readAsset(getActivity(), "licence.htm", "").replace("!COMMITID!",revision), "text/html", "UTF8");
        return v;
    }
}