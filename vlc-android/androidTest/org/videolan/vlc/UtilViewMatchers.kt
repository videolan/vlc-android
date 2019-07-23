package org.videolan.vlc

import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.internal.util.Checks
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher


class RecyclerViewMatcher(@IdRes private val recyclerViewId: Int) {
    var recyclerView: RecyclerView? = null

    fun atPosition(position: Int): Matcher<View> {
        return atPositionOnView(position, -1)
    }

    fun atPositionOnView(position: Int, targetViewId: Int): Matcher<View> {
        return object : TypeSafeMatcher<View>() {
            var childView: View? = null

            override fun describeTo(description: Description) {
                description.appendText("Recycler view doesn't have item at position $position")
            }

            override fun matchesSafely(view: View): Boolean {
                if (childView == null) {
                    recyclerView = view.rootView.findViewById(recyclerViewId) as RecyclerView
                    if (recyclerView!!.id == recyclerViewId) {
                        childView = recyclerView!!.findViewHolderForAdapterPosition(position)?.itemView
                    } else {
                        return false
                    }
                }

                return if (targetViewId == -1) {
                    view === childView
                } else {
                    view === childView?.findViewById<View>(targetViewId)
                }

            }
        }
    }
}

fun withRecyclerView(@IdRes recyclerViewId: Int) = RecyclerViewMatcher(recyclerViewId)


fun withBgColor(@ColorInt color: Int): Matcher<View> {
    Checks.checkNotNull(color)
    return object : BoundedMatcher<View, ViewGroup>(ViewGroup::class.java) {
        public override fun matchesSafely(vg: ViewGroup): Boolean {
            val colorDrawable = vg.background as ColorDrawable
            println("v- ${colorDrawable.color}")
            println("e - $color")
            return color == colorDrawable.color
        }

        override fun describeTo(description: Description) {
            description.appendText("with background color: $color")
        }
    }
}