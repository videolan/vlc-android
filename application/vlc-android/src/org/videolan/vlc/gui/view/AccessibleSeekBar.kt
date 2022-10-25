package org.videolan.vlc.gui.view

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.TalkbackUtil
import org.videolan.vlc.util.isTalkbackIsEnabled

class AccessibleSeekBar : AppCompatSeekBar {

    private val customAccessibilityDelegate = object : AccessibilityDelegateCompat() {
        var force = false
            set(value) {
                field = value
                if ((context as Activity).isTalkbackIsEnabled() && value) sendAccessibilityEventUnchecked(this@AccessibleSeekBar, AccessibilityEvent.obtain().apply { eventType = AccessibilityEvent.TYPE_VIEW_SELECTED })
            }
        var disabled = true


        /**
         * If the slider changes, it won't send the AccessibilityEvent TYPE_WINDOW_CONTENT_CHANGED
         * because it reads the percentages, so in that way it will read the sliderText.
         * On Android 10 and 9, the view got selected when it changes, so the TYPE_VIEW_SELECTED
         * event is controlled.
         *
         * @param host the view selected
         * @param event the accessibility event to send
         */
        override fun sendAccessibilityEventUnchecked(host: View, event: AccessibilityEvent) {
            if (disabled) {
                super.sendAccessibilityEventUnchecked(host, event)
                return
            }
            contentDescription = context.getString(R.string.talkback_out_of, TalkbackUtil.millisToString(context, progress.toLong()), TalkbackUtil.millisToString(context, max.toLong()) )
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    && event.eventType != AccessibilityEvent.TYPE_VIEW_SELECTED) {
                super.sendAccessibilityEventUnchecked(host, event)
            } else if (force) {
                super.sendAccessibilityEventUnchecked(host, event)
                force = false
            }
        }

    }

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initialize()
    }

    private fun initialize() {
        ViewCompat.setAccessibilityDelegate(this, customAccessibilityDelegate)
    }

    fun forceAccessibilityUpdate() {
        customAccessibilityDelegate.force = true
    }

    fun disableAccessibilityEvents() {
        customAccessibilityDelegate.disabled = true
    }

    fun enableAccessibilityEvents() {
        customAccessibilityDelegate.disabled = false
    }
}