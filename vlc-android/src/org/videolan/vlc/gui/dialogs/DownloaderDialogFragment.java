package org.videolan.vlc.gui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.videolan.vlc.R;

public class DownloaderDialogFragment extends DialogFragment implements View.OnClickListener {

    private static final int DOWNLOAD_PROGRESS = 40;
    private static final int DOWNLOAD_INDETERMINATE = 41;

    public static final String KEY_URL = "url";
    public static final String KEY_TITLE = "title";

    ProgressBar mProgress;
    TextView mText;
    Button mCancelButton;

    private  String mUrl, mtitle;

    public DownloaderDialogFragment() {
        startDownload();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        return new AppCompatDialog(getActivity(), getTheme());

    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        mtitle = args.getString(KEY_TITLE);
        mUrl = args.getString(KEY_URL);
        View view = inflater.inflate(R.layout.dialog_download, container);

        mProgress = (ProgressBar) view.findViewById(R.id.download_progress);
        mText = (TextView) view.findViewById(R.id.download_text);
        mCancelButton = (Button) view.findViewById(R.id.download_cancel);
        mCancelButton.setOnClickListener(this);

        mText.setText("Downloading " + mtitle);
        return view;
    }

    private void startDownload() {
        //TODO
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DOWNLOAD_PROGRESS:
                    mProgress.setProgress(msg.arg1);
                    break;
                case DOWNLOAD_INDETERMINATE:
                    mProgress.setIndeterminate(true);
                    break;
            }
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.download_cancel:
                //TODO
        }
    }
}
