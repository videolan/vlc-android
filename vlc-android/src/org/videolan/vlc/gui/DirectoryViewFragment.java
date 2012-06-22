package org.videolan.vlc.gui;

import java.io.IOException;
import java.util.ArrayList;

import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.LibVLC;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.gui.audio.AudioDirectoryAdapter;
import org.videolan.vlc.gui.audio.AudioPlayerActivity;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.interfaces.ISortable;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;

public class DirectoryViewFragment extends SherlockListFragment implements ISortable {
    public final static String TAG = "VLC/DirectoryViewFragment";

    private AudioDirectoryAdapter mDirectoryAdapter;
    private Context mContext;

    public DirectoryViewFragment(Context context) {
        mContext = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDirectoryAdapter = new AudioDirectoryAdapter(mContext);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.directory_view, container, false);
        setListAdapter(mDirectoryAdapter);

        return v;
    }

    @Override
    public void onListItemClick(ListView l, View v, int p, long id) {
        Boolean success = mDirectoryAdapter.browse(p);
        if(!success) { /* Clicked on a media file */
            AudioServiceController audioController = AudioServiceController.getInstance();
            String mediaFile = mDirectoryAdapter.getMediaLocation(p);

            try {
                if(!LibVLC.getExistingInstance().hasVideoTrack(mediaFile)) {
                    ArrayList<String> arrayList = new ArrayList<String>();
                    arrayList.add(mDirectoryAdapter.getMediaLocation(p));
                    audioController.load(arrayList, 0);
                    Intent intent = new Intent(getActivity(), AudioPlayerActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                } else {
                    audioController.stop();
                    Intent intent = new Intent(getActivity(), VideoPlayerActivity.class);
                    intent.putExtra("itemLocation", mediaFile);
                    startActivity(intent);
                }
            } catch (IOException e) {
                /* disk error maybe? */
            }
        }
    }

    @Override
    public void sortBy(int sortby) {
        // TODO
        Util.toaster(getActivity(), R.string.notavailable);
    }

}
