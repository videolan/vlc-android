package org.videolan.vlc.gui.browser

import android.content.Intent
import android.os.Bundle
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.CTX_APPEND
import org.videolan.resources.CTX_ITEM_DL
import org.videolan.resources.CTX_PLAY_ALL
import org.videolan.resources.CTX_PLAY_AS_AUDIO
import org.videolan.tools.WeakHandler
import org.videolan.vlc.R
import org.videolan.vlc.extensions.ExtensionManagerService
import org.videolan.vlc.extensions.Utils
import org.videolan.vlc.extensions.api.VLCExtensionItem
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.view.SwipeRefreshLayout
import org.videolan.vlc.media.MediaUtils
import java.util.*

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class ExtensionBrowser : Fragment(), View.OnClickListener, androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener, CtxActionReceiver {

    private var mTitle: String? = null
    private var mAddDirectoryFAB: FloatingActionButton? = null
    private val mAdapter: ExtensionAdapter = ExtensionAdapter(this)
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mEmptyView: TextView
    private lateinit var mSwipeRefreshLayout: SwipeRefreshLayout

    private var mExtensionManagerService: ExtensionManagerService? = null
    private var showSettings = false
    private var mustBeTerminated = false

    private val mHandler = ExtensionBrowserHandler(this)

    fun setExtensionService(service: ExtensionManagerService) {
        mExtensionManagerService = service
    }

    override fun onCreate(bundle: Bundle?) {
        var bundle = bundle
        super.onCreate(bundle)
        if (bundle == null) bundle = arguments
        if (bundle != null) {
            mTitle = bundle.getString(KEY_TITLE)
            showSettings = bundle.getBoolean(KEY_SHOW_FAB)
            val list = bundle.getParcelableArrayList<VLCExtensionItem>(KEY_ITEMS_LIST)
            if (list != null) mAdapter.addAll(list)
        }
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.directory_browser, container, false)
        mRecyclerView = v.findViewById(R.id.network_list)
        mEmptyView = v.findViewById(android.R.id.empty)
        mEmptyView.setText(R.string.extension_empty)
        mRecyclerView.layoutManager = LinearLayoutManager(activity)
        mRecyclerView.adapter = mAdapter
        registerForContextMenu(mRecyclerView)
        mSwipeRefreshLayout = v.findViewById(R.id.swipeLayout)
        mSwipeRefreshLayout.setOnRefreshListener(this)
        return v
    }

    override fun onResume() {
        super.onResume()
        if (mustBeTerminated)
            activity!!.supportFragmentManager.beginTransaction().remove(this).commit()
        mustBeTerminated = true
    }

    override fun onStart() {
        super.onStart()
        setTitle(mTitle)
        updateDisplay()
        if (showSettings) {
            if (mAddDirectoryFAB == null) mAddDirectoryFAB = activity!!.findViewById(R.id.fab)
            mAddDirectoryFAB!!.setImageResource(R.drawable.ic_fab_add)
            mAddDirectoryFAB!!.show()
            mAddDirectoryFAB!!.setOnClickListener(this)
        }
    }

    override fun onStop() {
        super.onStop()
        if (showSettings) {
            mAddDirectoryFAB!!.hide()
            mAddDirectoryFAB!!.setOnClickListener(null)
        }
    }

    private fun setTitle(title: String?) {
        val activity = activity as AppCompatActivity?
        if (activity != null && activity.supportActionBar != null) {
            activity.supportActionBar!!.title = title
            getActivity()!!.invalidateOptionsMenu()
        }
    }

    fun goBack() {
        val activity = activity
        if (activity != null && activity.supportFragmentManager.popBackStackImmediate()) getActivity()!!.finish()
    }

    fun doRefresh(title: String, items: List<VLCExtensionItem>) {
        setTitle(title)
        mAdapter.addAll(items)
    }

    private fun updateDisplay() {
        if (mAdapter.itemCount > 0) {
            mEmptyView.visibility = View.GONE
            mRecyclerView.visibility = View.VISIBLE
        } else {
            mEmptyView.visibility = View.VISIBLE
            mRecyclerView.visibility = View.GONE
        }
    }

    fun browseItem(item: VLCExtensionItem) {
        mExtensionManagerService!!.browse(item.stringId)
    }

    override fun onClick(v: View) {
        if (v.id == mAddDirectoryFAB!!.id) {
            val extension = mExtensionManagerService!!.currentExtension ?: return
            val intent = Intent(Intent.ACTION_VIEW)
            intent.component = extension.settingsActivity()
            startActivity(intent)
        }
    }

    override fun onRefresh() {
        mExtensionManagerService!!.refresh()
        mHandler.sendEmptyMessageDelayed(ACTION_HIDE_REFRESH, REFRESH_TIMEOUT.toLong())
    }

    fun openContextMenu(position: Int) {
        showContext(requireActivity(), this, position, mAdapter.getItem(position).title, CTX_PLAY_ALL or CTX_APPEND or CTX_PLAY_AS_AUDIO or CTX_ITEM_DL)
    }

    override fun onCtxAction(position: Int, option: Long) {
        when (option) {
            CTX_PLAY_ALL -> {
                val items = mAdapter.all
                val medias = ArrayList<MediaWrapper>(items.size)
                for (vlcItem in items) medias.add(Utils.mediawrapperFromExtension(vlcItem))
                MediaUtils.openList(activity, medias, position)
            }
            CTX_APPEND -> MediaUtils.appendMedia(activity, Utils.mediawrapperFromExtension(mAdapter.getItem(position)))
            CTX_PLAY_AS_AUDIO -> {
                val mw = Utils.mediawrapperFromExtension(mAdapter.getItem(position))
                mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                MediaUtils.openMedia(activity, mw)
            }
            CTX_ITEM_DL -> {
            }
        }//TODO
    }

    private inner class ExtensionBrowserHandler(owner: ExtensionBrowser) : WeakHandler<ExtensionBrowser>(owner) {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                ACTION_HIDE_REFRESH -> {
                    removeMessages(ACTION_SHOW_REFRESH)
                    owner?.mSwipeRefreshLayout?.isRefreshing = false
                }
                ACTION_SHOW_REFRESH -> {
                    removeMessages(ACTION_HIDE_REFRESH)
                    owner?.mSwipeRefreshLayout?.isRefreshing = true
                    sendEmptyMessageDelayed(ACTION_HIDE_REFRESH, REFRESH_TIMEOUT.toLong())
                }
            }
        }
    }

    companion object {

        const val TAG = "VLC/ExtensionBrowser"

        const val KEY_ITEMS_LIST = "key_items_list"
        const val KEY_SHOW_FAB = "key_fab"
        const val KEY_TITLE = "key_title"


        private const val ACTION_HIDE_REFRESH = 42
        private const val ACTION_SHOW_REFRESH = 43

        private const val REFRESH_TIMEOUT = 5000
    }
}
