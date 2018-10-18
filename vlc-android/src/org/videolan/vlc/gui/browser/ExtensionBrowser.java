package org.videolan.vlc.gui.browser;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.extensions.ExtensionListing;
import org.videolan.vlc.extensions.ExtensionManagerService;
import org.videolan.vlc.extensions.Utils;
import org.videolan.vlc.extensions.api.VLCExtensionItem;
import org.videolan.vlc.gui.dialogs.ContextSheetKt;
import org.videolan.vlc.gui.dialogs.CtxActionReceiver;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;
import java.util.List;

public class ExtensionBrowser extends Fragment implements View.OnClickListener, androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener, CtxActionReceiver {

    public static final String TAG = "VLC/ExtensionBrowser";

    public static final String KEY_ITEMS_LIST = "key_items_list";
    public static final String KEY_SHOW_FAB = "key_fab";
    public static final String KEY_TITLE = "key_title";


    private static final int ACTION_HIDE_REFRESH = 42;
    private static final int ACTION_SHOW_REFRESH = 43;

    private static final int REFRESH_TIMEOUT = 5000;

    private String mTitle;
    private FloatingActionButton mAddDirectoryFAB;
    private ExtensionAdapter mAdapter;
    protected RecyclerView mRecyclerView;
    protected TextView mEmptyView;
    protected SwipeRefreshLayout mSwipeRefreshLayout;

    private ExtensionManagerService mExtensionManagerService;
    private boolean showSettings = false;
    private boolean mustBeTerminated = false;

    public void setExtensionService(ExtensionManagerService service) {
        mExtensionManagerService = service;
    }

    public ExtensionBrowser() {
        mAdapter = new ExtensionAdapter(this);
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (bundle == null) bundle = getArguments();
        if (bundle != null) {
            mTitle = bundle.getString(KEY_TITLE);
            showSettings = bundle.getBoolean(KEY_SHOW_FAB);
            final List<VLCExtensionItem> list = bundle.getParcelableArrayList(KEY_ITEMS_LIST);
            if (list != null) mAdapter.addAll(list);
        }
        setHasOptionsMenu(true);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        final View v = inflater.inflate(R.layout.directory_browser, container, false);
        mRecyclerView = v.findViewById(R.id.network_list);
        mEmptyView = v.findViewById(android.R.id.empty);
        mEmptyView.setText(R.string.extension_empty);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.setAdapter(mAdapter);
        registerForContextMenu(mRecyclerView);
        mSwipeRefreshLayout = v.findViewById(R.id.swipeLayout);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mustBeTerminated)
            getActivity().getSupportFragmentManager().beginTransaction().remove(this).commit();
        mustBeTerminated = true;
    }

    @Override
    public void onStart() {
        super.onStart();
        setTitle(mTitle);
        updateDisplay();
        if (showSettings) {
            if (mAddDirectoryFAB == null) mAddDirectoryFAB = getActivity().findViewById(R.id.fab);
            mAddDirectoryFAB.setImageResource(R.drawable.ic_fab_add);
            mAddDirectoryFAB.setVisibility(View.VISIBLE);
            mAddDirectoryFAB.setOnClickListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (showSettings) {
            mAddDirectoryFAB.setVisibility(View.GONE);
            mAddDirectoryFAB.setOnClickListener(null);
        }
    }

    private void setTitle(String title) {
        final AppCompatActivity activity = (AppCompatActivity)getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(title);
            getActivity().invalidateOptionsMenu();
        }
    }

    public void goBack() {
        final FragmentActivity activity = getActivity();
        if (activity != null && activity.getSupportFragmentManager().popBackStackImmediate()) getActivity().finish();
    }

    public void doRefresh(String title, List<VLCExtensionItem> items) {
        setTitle(title);
        mAdapter.addAll(items);
    }

    private void updateDisplay() {
        if (mAdapter.getItemCount() > 0) {
            mEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        } else {
            mEmptyView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
        }
    }

    public void browseItem(VLCExtensionItem item) {
        mExtensionManagerService.browse(item.stringId);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == mAddDirectoryFAB.getId()){
            final ExtensionListing extension = mExtensionManagerService.getCurrentExtension();
            if (extension == null) return;
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setComponent(extension.settingsActivity());
            startActivity(intent);
        }
    }

    @Override
    public void onRefresh() {
        mExtensionManagerService.refresh();
        mHandler.sendEmptyMessageDelayed(ACTION_HIDE_REFRESH, REFRESH_TIMEOUT);
    }

    public void openContextMenu(final int position) {
        ContextSheetKt.showContext(requireActivity(), this, position, mAdapter.getItem(position).title, Constants.CTX_PLAY_ALL|Constants.CTX_APPEND|Constants.CTX_PLAY_AS_AUDIO|Constants.CTX_ITEM_DL);
    }

    @Override
    public void onCtxAction(int position, int option) {
        switch (option) {
            case Constants.CTX_PLAY_ALL:
                final List<VLCExtensionItem> items = mAdapter.getAll();
                final List<MediaWrapper> medias = new ArrayList<>(items.size());
                for (VLCExtensionItem vlcItem : items) medias.add(Utils.mediawrapperFromExtension(vlcItem));
                MediaUtils.INSTANCE.openList(getActivity(), medias, position);
                break;
            case Constants.CTX_APPEND:
                MediaUtils.INSTANCE.appendMedia(getActivity(), Utils.mediawrapperFromExtension(mAdapter.getItem(position)));
                break;
            case Constants.CTX_PLAY_AS_AUDIO:
                final MediaWrapper mw = Utils.mediawrapperFromExtension(mAdapter.getItem(position));
                mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                MediaUtils.INSTANCE.openMedia(getActivity(), mw);
                break;
            case Constants.CTX_ITEM_DL:
                //TODO
                break;
        }
    }

    private Handler mHandler = new ExtensionBrowserHandler(this);

    private class ExtensionBrowserHandler extends WeakHandler<ExtensionBrowser> {

        ExtensionBrowserHandler(ExtensionBrowser owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ACTION_HIDE_REFRESH:
                    removeMessages(ACTION_SHOW_REFRESH);
                    getOwner().mSwipeRefreshLayout.setRefreshing(false);
                    break;
                case ACTION_SHOW_REFRESH:
                    removeMessages(ACTION_HIDE_REFRESH);
                    getOwner().mSwipeRefreshLayout.setRefreshing(true);
                    sendEmptyMessageDelayed(ACTION_HIDE_REFRESH, REFRESH_TIMEOUT);
                    break;
            }
        }
    }
}
