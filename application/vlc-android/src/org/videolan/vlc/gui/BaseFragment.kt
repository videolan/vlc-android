package org.videolan.vlc.gui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.resources.TAG_ITEM
import org.videolan.vlc.R
import org.videolan.vlc.gui.view.SwipeRefreshLayout

abstract class BaseFragment : Fragment(), ActionMode.Callback {
    var actionMode: ActionMode? = null
    var fabPlay: FloatingActionButton? = null
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    open val hasTabs = false

    private var refreshJob : Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    open val subTitle: String?
        get() = null

    val menu: Menu?
        get() = (activity as? AudioPlayerContainerActivity)?.menu

    abstract fun getTitle(): String
    open fun onFabPlayClick(view: View) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(!AndroidDevices.isAndroidTv)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<SwipeRefreshLayout>(R.id.swipeLayout)?.let {
            swipeRefreshLayout = it
            it.setColorSchemeResources(R.color.orange700)
        }
        if (hasFAB()) fabPlay = requireActivity().findViewById(R.id.fab)
    }

    override fun onStart() {
        super.onStart()
        updateActionBar()
        setFabPlayVisibility(hasFAB())
        fabPlay?.setOnClickListener { v -> onFabPlayClick(v) }
    }

    override fun onStop() {
        super.onStop()
        setFabPlayVisibility(false)
    }

    fun startActionMode() {
        val activity = activity as AppCompatActivity? ?: return
        actionMode = activity.startSupportActionMode(this)
        setFabPlayVisibility(false)
    }

    private fun updateActionBar() {
        val activity = activity as? AppCompatActivity ?: return
        activity.supportActionBar?.let {
            if (requireActivity() !is ContentActivity || (requireActivity() as ContentActivity).displayTitle) requireActivity().title = getTitle()
            it.subtitle = subTitle
            activity.invalidateOptionsMenu()
        }
        if (activity is ContentActivity) activity.setTabLayoutVisibility(hasTabs)
    }

    protected open fun hasFAB() = ::swipeRefreshLayout.isInitialized

    open fun setFabPlayVisibility(enable: Boolean) {
        fabPlay?.run {
            if (enable) show()
            else hide()
        }
    }

    protected fun showInfoDialog(item: MediaLibraryItem) {
        val i = Intent(activity, InfoActivity::class.java)
        i.putExtra(TAG_ITEM, item)
        startActivity(i)
    }

    protected fun setRefreshing(refreshing: Boolean, action: ((loading: Boolean) -> Unit)? = null) {
        refreshJob = lifecycleScope.launchWhenStarted {
            if (refreshing) delay(300L)
            swipeRefreshLayout.isRefreshing = refreshing
            (activity as? MainActivity)?.refreshing = refreshing
            action?.invoke(refreshing)
        }
    }

    fun stopActionMode() {
        actionMode?.let {
            it.finish()
            setFabPlayVisibility(true)
        }
    }

    fun invalidateActionMode() {
        actionMode?.invalidate()
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
}