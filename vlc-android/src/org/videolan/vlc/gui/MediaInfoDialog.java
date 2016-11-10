package org.videolan.vlc.gui;


import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.design.widget.FloatingActionButton;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.Extensions;
import org.videolan.libvlc.util.VLCUtil;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.BitmapUtil;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.video.MediaInfoAdapter;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import java.io.File;
import java.nio.ByteBuffer;

public class MediaInfoDialog extends BottomSheetDialogFragment {

    public final static String TAG = "VLC/MediaInfoDialog";

    public final static String ITEM_KEY = "key_item";

    private MediaWrapper mItem;
    private TextView mTitle;
    private ListView mTracksList;
    private TextView mLengthView;
    private TextView mSizeView;
    private TextView mPathView;
    private View mProgress;
    private FloatingActionButton mPlayButton;
    private ImageView mSubtitles;
    private Media mMedia;
    private MediaInfoAdapter mAdapter;
    private MediaInfoDialog.LoadImageTask mLoadImageTask = null;
    private final static int NEW_TEXT = 1;
    private final static int EXIT = 2;
    private final static int SHOW_SUBTITLES = 3;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mItem = (MediaWrapper) (savedInstanceState != null ?
                savedInstanceState.getParcelable(ITEM_KEY) :
                getArguments().getParcelable(ITEM_KEY));
    }

    @Override
    public void setupDialog(Dialog dialog, int style) {
        super.setupDialog(dialog, style);
        View contentView = View.inflate(getContext(), R.layout.media_info, null);
        dialog.setContentView(contentView);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View v = inflater.inflate(R.layout.media_info, container, false);

        mTitle = (TextView) v.findViewById(R.id.media_title);
        mLengthView = (TextView) v.findViewById(R.id.length);
        mTracksList = (ListView) v.findViewById(android.R.id.list);
        mSizeView = (TextView) v.findViewById(R.id.size_value);
        mPathView = (TextView) v.findViewById(R.id.info_path);
        mPlayButton = (FloatingActionButton) v.findViewById(R.id.play);
        mSubtitles = (ImageView) v.findViewById(R.id.info_subtitles);
        mProgress = v.findViewById(R.id.image_progress);
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaUtils.openMedia(getContext(), mItem);
            }
        });

        mAdapter = new MediaInfoAdapter(getActivity());
        mTracksList.setAdapter(mAdapter);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Shouldn't happen, maybe user opened it faster than Media Library could index it
        if (mItem == null)
            return;

        mTitle.setText(mItem.getTitle());
        mCheckFileTask = (MediaInfoDialog.CheckFileTask) new MediaInfoDialog.CheckFileTask().execute();
        mLoadImageTask = (MediaInfoDialog.LoadImageTask) new MediaInfoDialog.LoadImageTask().execute();
        mLengthView.setText(mItem.getLength() > 0l ? Strings.millisToString(mItem.getLength()) : "");
        mPathView.setText(Uri.decode(mItem.getUri().getPath()));
    }

    public void onStop() {
        super.onStop();
        if (mCheckFileTask != null && !mCheckFileTask.isCancelled())
            mCheckFileTask.cancel(true);
        if (mLoadImageTask != null && !mLoadImageTask.isCancelled())
            mLoadImageTask.cancel(true);
        if (mMedia != null)
            mMedia.release();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ITEM_KEY, mItem);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler = null;
    }

    MediaInfoDialog.CheckFileTask mCheckFileTask = null;
    private class CheckFileTask extends AsyncTask<Void, Void, File> {

        private void checkSubtitles(File itemFile) {
            String extension, filename, videoName = Uri.decode(itemFile.getName()), parentPath = Uri.decode(itemFile.getParent());
            videoName = videoName.substring(0, videoName.lastIndexOf('.'));
            String[] subFolders = {"/Subtitles", "/subtitles", "/Subs", "/subs"};
            String[] files = itemFile.getParentFile().list();
            int filesLength = files == null ? 0 : files.length;
            for (int i = 0 ; i < subFolders.length ; ++i){
                File subFolder = new File(parentPath+subFolders[i]);
                if (!subFolder.exists())
                    continue;
                String[] subFiles = subFolder.list();
                int subFilesLength = 0;
                String[] newFiles = new String[0];
                if (subFiles != null) {
                    subFilesLength = subFiles.length;
                    newFiles = new String[filesLength+subFilesLength];
                    System.arraycopy(subFiles, 0, newFiles, 0, subFilesLength);
                }
                if (files != null)
                    System.arraycopy(files, 0, newFiles, subFilesLength, filesLength);
                files = newFiles;
                filesLength = files.length;
            }
            for (int i = 0; i<filesLength ; ++i){
                filename = Uri.decode(files[i]);
                int index = filename.lastIndexOf('.');
                if (index <= 0)
                    continue;
                extension = filename.substring(index);
                if (!Extensions.SUBTITLES.contains(extension))
                    continue;

                if (mHandler == null || isCancelled())
                    return;
                if (filename.startsWith(videoName)) {
                    mHandler.obtainMessage(SHOW_SUBTITLES).sendToTarget();
                    return;
                }
            }
        }

        @Override
        protected File doInBackground(Void... params) {
            File itemFile = new File(Uri.decode(mItem.getLocation().substring(5)));

            if (mItem.getType() == MediaWrapper.TYPE_VIDEO)
                checkSubtitles(itemFile);
            return itemFile;
        }

        @Override
        protected void onPostExecute(File file) {
            mSizeView.setText(Strings.readableFileSize(file.length()));
            mCheckFileTask = null;
        }

        @Override
        protected void onCancelled() {
            mCheckFileTask = null;
        }
    }

    private class LoadImageTask extends AsyncTask<Void, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Void... params) {

            final LibVLC libVlc = VLCInstance.get();
            if (libVlc == null)
                return null;
            int videoHeight = mItem.getHeight();
            int videoWidth = mItem.getWidth();
            if (videoWidth <= 0 || videoHeight <= 0) {
                //FIXME : find a better way to display media info without video size
                videoWidth = 16;
                videoHeight = 9;
            }

            if (isCancelled())
                return null;

            mMedia = new Media(libVlc, mItem.getUri());
            mMedia.parse();

            if (mHandler != null)
                mHandler.sendEmptyMessage(NEW_TEXT);

            DisplayMetrics screen = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(screen);
            int width, height;
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                width = Math.min(screen.widthPixels, screen.heightPixels);
            } else {
                width = screen.widthPixels /2 ;
            }
            height = width * videoHeight/videoWidth;

            Bitmap image = null;
            if (!isCancelled()) {
                if (mItem.getType() == MediaWrapper.TYPE_VIDEO) {
                    // Get the thumbnail.
                    image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                    byte[] b = VLCUtil.getThumbnail(mMedia, width, height);

                    if (b == null || isCancelled()) // We were not able to create a thumbnail for this item.
                        return null;
                    image.copyPixelsFromBuffer(ByteBuffer.wrap(b));
                    image = BitmapUtil.cropBorders(image, width, height);
                } else if (mItem.getType() == MediaWrapper.TYPE_AUDIO)
                    image = AudioUtil.getCover(getActivity(), mItem, width);
            }
            return image;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            mProgress.setVisibility(View.GONE);
            ImageView imageView = (ImageView) getView().findViewById(R.id.image);
            imageView.setImageBitmap(bitmap);
            if (bitmap == null) {
                RelativeLayout layout = (RelativeLayout) getView().findViewById(R.id.image_layout);
                if (layout != null) {
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) layout.getLayoutParams();
                    lp.weight = 0f;
                    layout.setLayoutParams(lp);
                }
                return;
            }
            ViewGroup.LayoutParams lp = imageView.getLayoutParams();
            lp.height = bitmap.getHeight();
            lp.width = bitmap.getWidth();
            imageView.setLayoutParams(lp);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mLoadImageTask = null;
        }

        @Override
        protected void onCancelled() {
            mLoadImageTask = null;
        }
    }

    private void updateText() {
        boolean hasSubs = false;
        if (mMedia == null)
            return;
        final int trackCount = mMedia.getTrackCount();
        for (int i = 0; i < trackCount; ++i) {
            final Media.Track track = mMedia.getTrack(i);
            if (track.type == Media.Track.Type.Text)
                hasSubs = true;
            mAdapter.add(track);
        }

        if (hasSubs && mHandler != null)
            mHandler.obtainMessage(SHOW_SUBTITLES).sendToTarget();
    }

    private Handler mHandler = new MediaInfoDialog.MediaInfoHandler(this);

    private static class MediaInfoHandler extends WeakHandler<MediaInfoDialog> {
        public MediaInfoHandler(MediaInfoDialog owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            final MediaInfoDialog fragment = getOwner();
            if(fragment == null) return;

            switch (msg.what) {
                case NEW_TEXT:
                    fragment.updateText();
                    break;
                case EXIT:
                    fragment.getActivity().setResult(PreferencesActivity.RESULT_RESCAN);
                    fragment.getActivity().finish();
                    break;
                case SHOW_SUBTITLES:
                    fragment.mSubtitles.setVisibility(View.VISIBLE);
                    break;
            }
        }
    }
}
