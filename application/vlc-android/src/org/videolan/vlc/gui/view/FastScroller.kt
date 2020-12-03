/*****************************************************************************
 * FastScroller.kt
 * **************************************************************************
 * Copyright © 2016-2019 VLC authors and VideoLAN
 * Inspired by Mark Allison blog post:
 * https://blog.stylingandroid.com/recyclerview-fastscroll-part-1/
 * https://blog.stylingandroid.com/recyclerview-fastscroll-part-2/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 */

package org.videolan.vlc.gui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.resources.util.HeadersIndex
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.tools.WeakHandler
import org.videolan.tools.safeOffer
import org.videolan.vlc.util.scope
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

private const val TAG = "FastScroller"
private const val HANDLE_ANIMATION_DURATION = 100
private const val HANDLE_HIDE_DELAY = 1000
private const val SCROLLER_HIDE_DELAY = 3000

private const val HIDE_HANDLE = 0
private const val HIDE_SCROLLER = 1
private const val SHOW_SCROLLER = 2

private const val ITEM_THRESHOLD = 25

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class FastScroller : LinearLayout, Observer<HeadersIndex> {

    private var currentHeight: Int = 0
    private var itemCount: Int = 0
    private var fastScrolling: Boolean = false
    private var showBubble: Boolean = false
    private var currentPosition: Int = 0

    private var currentAnimator: AnimatorSet? = null
    private val scrollListener = ScrollListener()
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var provider: MedialibraryProvider<out MediaLibraryItem>
    private lateinit var handle: ImageView
    private lateinit var bubble: TextView
    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var appbarLayout: AppBarLayout
    private lateinit var floatingActionButton: FloatingActionButton
    private var lastPosition = 0f
    private var appbarLayoutExpanded = true
    private val isAnimating = AtomicBoolean(false)
    private var timesScrollingDown = 0
    private var timesScrollingUp = 0
    private var lastVerticalOffset: Int = 0
    private var tryCollapseAppbarOnNextScroll = false
    private var tryExpandAppbarOnNextScroll = false

    private val handler = object : WeakHandler<FastScroller>(this) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HIDE_HANDLE -> hideBubble()
                HIDE_SCROLLER -> this@FastScroller.visibility = View.INVISIBLE
                SHOW_SCROLLER -> {
                    if (itemCount < ITEM_THRESHOLD) {
                        return
                    }
                    this@FastScroller.visibility = View.VISIBLE
                    this.removeMessages(HIDE_SCROLLER)
                    this.sendEmptyMessageDelayed(HIDE_SCROLLER, SCROLLER_HIDE_DELAY.toLong())
                }
            }
        }
    }

    interface SeparatedAdapter {
        fun hasSections(): Boolean
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(context)
    }


    private fun initialize(context: Context) {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.fastscroller, this)
        handle = findViewById(R.id.fastscroller_handle)
        bubble = findViewById(R.id.fastscroller_bubble)


    }

    /**
     * Attaches the FastScroller to an [appBarLayout] and a [coordinatorLayout]
     */
    fun attachToCoordinator(appBarLayout: AppBarLayout, coordinatorLayout: CoordinatorLayout, floatingActionButton: FloatingActionButton) {
        this.coordinatorLayout = coordinatorLayout
        appbarLayout = appBarLayout
        this.floatingActionButton = floatingActionButton
        appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            layoutParams.height = coordinatorLayout.height - (appBarLayout.height + appBarLayout.top)
            invalidate()
            appbarLayoutExpanded = appBarLayout.top > -appBarLayout.height
            if (appBarLayout.height == -appBarLayout.top || appBarLayout.top == 0) {
                isAnimating.set(false)
            }
            lastVerticalOffset = verticalOffset
        })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        currentHeight = h
        if (::recyclerView.isInitialized) updatePositions()
    }

    /**
     * Shows the bubble containing the section letter
     */
    private fun showBubble() {
        if (itemCount < ITEM_THRESHOLD) {
            return
        }
        val animatorSet = AnimatorSet()
        bubble.pivotX = bubble.width.toFloat()
        bubble.pivotY = bubble.height.toFloat()
        scrollListener.onScrolled(recyclerView, 0, 0)
        bubble.visibility = View.VISIBLE
        val growerX = ObjectAnimator.ofFloat(bubble, SCALE_X, 0f, 1f).setDuration(HANDLE_ANIMATION_DURATION.toLong())
        val growerY = ObjectAnimator.ofFloat(bubble, SCALE_Y, 0f, 1f).setDuration(HANDLE_ANIMATION_DURATION.toLong())
        val alpha = ObjectAnimator.ofFloat(bubble, ALPHA, 0f, 1f).setDuration(HANDLE_ANIMATION_DURATION.toLong())
        animatorSet.playTogether(growerX, growerY, alpha)
        animatorSet.start()
    }

    /**
     * Hides the bubble containing the section letter
     */
    private fun hideBubble() {
        currentAnimator = AnimatorSet()
        bubble.pivotX = bubble.width.toFloat()
        bubble.pivotY = bubble.height.toFloat()
        val shrinkerX = ObjectAnimator.ofFloat(bubble, SCALE_X, 1f, 0f).setDuration(HANDLE_ANIMATION_DURATION.toLong())
        val shrinkerY = ObjectAnimator.ofFloat(bubble, SCALE_Y, 1f, 0f).setDuration(HANDLE_ANIMATION_DURATION.toLong())
        val alpha = ObjectAnimator.ofFloat(bubble, ALPHA, 1f, 0f).setDuration(HANDLE_ANIMATION_DURATION.toLong())
        currentAnimator?.playTogether(shrinkerX, shrinkerY, alpha)
        currentAnimator?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                bubble.visibility = View.GONE
                currentAnimator = null
                handler.sendEmptyMessageDelayed(HIDE_SCROLLER, SCROLLER_HIDE_DELAY.toLong())
            }

            override fun onAnimationCancel(animation: Animator) {
                super.onAnimationCancel(animation)
                bubble.visibility = View.INVISIBLE
                currentAnimator = null
                handler.sendEmptyMessageDelayed(HIDE_SCROLLER, SCROLLER_HIDE_DELAY.toLong())
            }
        })
        currentAnimator?.start()
    }

    /**
     * Sets the position of the [handle]
     */
    private fun setPosition(y: Float) {
        val position = y / currentHeight
        val handleHeight = handle.height
        handle.y = getValueInRange(0, currentHeight - handleHeight, ((currentHeight - handleHeight) * position).toInt()).toFloat()
        val bubbleHeight = bubble.height
        bubble.y = getValueInRange(0, currentHeight - bubbleHeight, ((currentHeight - bubbleHeight) * position).toInt() - handleHeight).toFloat()
    }

    /**
     * Sets the [recyclerView] it will be attached to
     */
    fun setRecyclerView(recyclerView: RecyclerView, provider: MedialibraryProvider<out MediaLibraryItem>) {
        this.recyclerView = recyclerView
        this.layoutManager = recyclerView.layoutManager as LinearLayoutManager
        this.recyclerView.removeOnScrollListener(scrollListener)
        visibility = View.INVISIBLE
        itemCount = recyclerView.adapter!!.itemCount
        if (this::provider.isInitialized) this.provider.liveHeaders.removeObserver(this)
        this.provider = provider
        provider.liveHeaders.observeForever(this)
        recyclerView.addOnScrollListener(scrollListener)
        showBubble = (recyclerView.adapter as SeparatedAdapter).hasSections()
    }

    /**
     * [handle] drag and drop
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            fastScrolling = true
            currentPosition = -1
            if (currentAnimator != null)
                currentAnimator?.cancel()
            handler.removeMessages(HIDE_SCROLLER)
            handler.removeMessages(HIDE_HANDLE)
            if (showBubble && bubble.visibility == View.GONE)
                showBubble()
            setRecyclerViewPosition(event.y)
            return true
        } else if (event.action == MotionEvent.ACTION_UP) {
            fastScrolling = false
            handler.sendEmptyMessageDelayed(HIDE_HANDLE, HANDLE_HIDE_DELAY.toLong())
            handler.sendEmptyMessageDelayed(HIDE_SCROLLER, SCROLLER_HIDE_DELAY.toLong())
            if (event.y / currentHeight.toFloat() > 0.99f) {
                recyclerView.smoothScrollToPosition(itemCount)
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    /**
     * Scrolls in the [recyclerView]
     */
    private fun setRecyclerViewPosition(y: Float) {
        if (this::recyclerView.isInitialized) {
            val proportion: Float = y / currentHeight.toFloat()

            val targetPos = getValueInRange(0, itemCount, Math.round(proportion * itemCount.toFloat()))
            if (targetPos == currentPosition) {
                return
            }


            //Determine if we need to expand / collapse the [appBarLayout]

            //We avoid updating this when animation is running to avoid in/out graphical issue
            if (!isAnimating.get()) {
                if (lastPosition < y) {
                    timesScrollingUp = 0
                    timesScrollingDown++
                } else if (lastPosition > y) {
                    timesScrollingUp++
                    timesScrollingDown = 0
                }
            }

            currentPosition = targetPos
            recyclerView.scrollToPosition(targetPos)

            if (!isAnimating.get() && appbarLayoutExpanded && timesScrollingDown > 3) {
                tryCollapseAppbarOnNextScroll = true
                appbarLayoutExpanded = false
                isAnimating.set(true)
                timesScrollingUp = 0
                timesScrollingDown = 0
            } else if (!isAnimating.get() && !appbarLayoutExpanded && timesScrollingUp > 3) {
                tryExpandAppbarOnNextScroll = true
                appbarLayoutExpanded = true
                isAnimating.set(true)
                timesScrollingUp = 0
                timesScrollingDown = 0
            }
            lastPosition = y


        }
    }

    private fun getValueInRange(min: Int, max: Int, value: Int): Int {
        return min(max(min, value), max)
    }


    private inner class ScrollListener : RecyclerView.OnScrollListener() {

        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            updatePositions()
            //launch the collapse / expand animations if needed
            appbarLayout.totalScrollRange.let {

                if (tryCollapseAppbarOnNextScroll && lastVerticalOffset != -it) {
                    if (!isAnimating.get()) {
                        appbarLayout.setExpanded(false)
                        floatingActionButton.hide()
                        isAnimating.set(true)
                    }
                    tryCollapseAppbarOnNextScroll = false
                }

                if (tryExpandAppbarOnNextScroll && lastVerticalOffset == -it) {
                    if (!isAnimating.get()) {
                        appbarLayout.setExpanded(true)
                        floatingActionButton.show()
                        isAnimating.set(true)
                    }
                    tryExpandAppbarOnNextScroll = false
                }
            }
        }
    }

    private val actor = scope.actor<Unit>(capacity = Channel.CONFLATED) {
        for (evt in channel) if (fastScrolling) {
            //ItemDecoration has to be taken into account so we add 1 for the sticky header
            val position = layoutManager.findFirstVisibleItemPosition() + 1
            if (BuildConfig.DEBUG) Log.d(TAG, "findFirstVisibleItemPosition $position")
            val pos = provider.getPositionForSection(position)
            val sectionforPosition = provider.getSectionforPosition(pos)
            if (sectionforPosition.isNotEmpty()) bubble.text = " $sectionforPosition "
            delay(100L)
        }
    }

    /**
     * Updates the position of the bubble and refresh the letter
     */
    private fun updatePositions() {
        val verticalScrollOffset = recyclerView.computeVerticalScrollOffset()
        val recyclerviewTotalHeight = recyclerView.computeVerticalScrollRange() - recyclerView.computeVerticalScrollExtent()
        val proportion = if (recyclerviewTotalHeight == 0) 0f else verticalScrollOffset / recyclerviewTotalHeight.toFloat()
        setPosition(currentHeight * proportion)
        if (visibility == View.INVISIBLE) handler.sendEmptyMessage(SHOW_SCROLLER)
        actor.safeOffer(Unit)
    }

    override fun onChanged(t: HeadersIndex?) {
        actor.safeOffer(Unit)
    }
}
