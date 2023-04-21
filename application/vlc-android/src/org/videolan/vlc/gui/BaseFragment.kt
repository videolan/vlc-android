package org.videolan.vlc.gui

import android.content.Intent
import android.content.res.TypedArray
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.TAG_ITEM
import org.videolan.tools.setGone
import org.videolan.vlc.R
import org.videolan.vlc.gui.browser.KEY_IN_MEDIALIB
import org.videolan.vlc.gui.browser.KEY_MEDIA
import org.videolan.vlc.gui.helpers.FloatingActionButtonBehavior
import org.videolan.vlc.gui.helpers.UiTools.isTablet
import org.videolan.vlc.gui.view.SwipeRefreshLayout

abstract class BaseFragment : Fragment(), ActionMode.Callback {
    var actionMode: ActionMode? = null
    var fabPlay: FloatingActionButton? = null
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    /*
     * Disable Swipe Refresh while scrolling horizontally
     */
    val swipeFilter = View.OnTouchListener { _, event ->
        swipeRefreshLayout.isEnabled = event.action == MotionEvent.ACTION_UP
        false
    }
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

    open val isMainNavigationPoint = true

    abstract fun getTitle(): String
    open fun onFabPlayClick(view: View) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<SwipeRefreshLayout>(R.id.swipeLayout)?.let {
            swipeRefreshLayout = it
            val a: TypedArray = requireActivity().obtainStyledAttributes(TypedValue().data, intArrayOf(R.attr.colorPrimary, R.attr.swipe_refresh_background))
            val color = a.getColor(0, 0)
            val bColor = a.getColor(1, Color.WHITE)
            a.recycle()
            it.setColorSchemeColors(color)
            it.setProgressBackgroundColorSchemeColor(bColor)
        }
        if (isMainNavigationPoint) manageFabNeverShow()
        updateFabPlayView()
    }

    fun manageFabNeverShow() {
        val fab = requireActivity().findViewById<FloatingActionButton?>(R.id.fab)
        ((fab?.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as? FloatingActionButtonBehavior)?.shouldNeverShow = !hasFAB() && requireActivity() is MainActivity
    }

    override fun onStart() {
        super.onStart()
        updateActionBar()
        setFabPlayVisibility(hasFAB())
        fabPlay?.setOnClickListener { v -> onFabPlayClick(v) }
    }

    private fun updateFabPlayView() {
        val visibility = fabPlay?.visibility
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        val fabLarge = requireActivity().findViewById<FloatingActionButton>(R.id.fab_large)
        fab.setGone()
        fabLarge.setGone()
        fabPlay = if (requireActivity().isTablet() && requireActivity() is MainActivity) fabLarge else fab
        visibility?.let { fabPlay?.visibility = it }
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
        if (parentFragment != null) return
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
            if (enable) show() else hide()
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

    fun browse(media: MediaWrapper, scanned: Boolean, next: Fragment, backstackName:String) {
        val ft = activity?.supportFragmentManager?.beginTransaction()
        next.arguments = bundleOf(KEY_MEDIA to media, KEY_IN_MEDIALIB to (scanned))
        ft?.replace(R.id.fragment_placeholder, next, media.location)
        ft?.addToBackStack(backstackName)
        ft?.commit()
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
}