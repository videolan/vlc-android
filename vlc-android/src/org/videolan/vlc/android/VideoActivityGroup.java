package org.videolan.vlc.android;

import java.util.ArrayList;

import android.app.Activity;
import android.app.ActivityGroup;
import android.app.LocalActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;

public class VideoActivityGroup extends ActivityGroup {
    public final static String TAG = "VLC/VideoActivityGroup";

    private static VideoActivityGroup mInstance;
    private ArrayList<String> mHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mHistory = new ArrayList<String>();
        mInstance = this;

        // Load VideoListActivity by default
        Intent intent = new Intent(this, VideoListActivity.class);
        startChildAcitvity("VideoListActivity", intent);
    }

    public void startChildAcitvity(String id, Intent intent) {
        Window window = getLocalActivityManager().startActivity(
                id, intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        if (window != null) {
            mHistory.add(id);
            setContentView(window.getDecorView());
        }
    }

    public static VideoActivityGroup getInstance() {
        return mInstance;
    }

    @Override
    public void finishFromChild(Activity child) {
        LocalActivityManager manager = getLocalActivityManager();
        int index = mHistory.size() - 1;

        if (index > 0) {
            manager.destroyActivity(mHistory.get(index), true);
            mHistory.remove(index);
            index--;
            String id = mHistory.get(index);
            Activity activity = manager.getActivity(id);
            setContentView(activity.getWindow().getDecorView());
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        int index = mHistory.size() - 1;

        if (index > 0) {
            getCurrentActivity().finish();
            return;
        }
        super.onBackPressed();
    }

}
