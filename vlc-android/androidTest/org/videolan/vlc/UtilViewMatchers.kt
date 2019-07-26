package org.videolan.vlc

import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.matcher.BoundedMatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.vlc.gui.browser.BaseBrowserAdapter
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.hamcrest.BaseMatcher




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
    return object : BoundedMatcher<View, ViewGroup>(ViewGroup::class.java) {
        public override fun matchesSafely(vg: ViewGroup): Boolean {
            val colorDrawable = vg.background as ColorDrawable
            return color == colorDrawable.color
        }

        override fun describeTo(description: Description) {
            description.appendText("with background color: $color")
        }
    }
}

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class MediaRecyclerViewMatcher<VH : SelectorViewHolder<out ViewDataBinding>>(@IdRes private val recyclerViewId: Int) {
    var recyclerView: RecyclerView? = null

    fun atGivenType(mediaType: Int, matcher: Matcher<View>? = null): Matcher<View> {
        return object : TypeSafeMatcher<View>() {
            val mapVH: MutableMap<View, VH> = HashMap()

            override fun describeTo(description: Description) {
                description.appendText("with given media type: $mediaType")
            }

            override fun matchesSafely(view: View): Boolean {
                if (!fillMatchesIfRequired(mapVH, view.rootView) { vh ->
                            if (vh is BaseBrowserAdapter.MediaViewHolder) {
                                val item = (vh as BaseBrowserAdapter.MediaViewHolder).binding.item as? AbstractMediaWrapper
                                item?.type == mediaType
                            } else false
                        }) return false

                return mapVH[view]?.let {
                    scrollToShowItem(it.adapterPosition)
                    matcher?.matches(view) ?: true
                } ?: false
            }
        }
    }

    private fun fillMatchesIfRequired(mapVH: MutableMap<View, VH>, rootView: View, condition: ((VH) -> Boolean)): Boolean {
        if (recyclerView == null || mapVH.isEmpty()) {
            recyclerView = rootView.findViewById(recyclerViewId) as RecyclerView
            if (recyclerView!!.id == recyclerViewId) {
                val it = (0 until recyclerView!!.adapter!!.itemCount).iterator()
                while (it.hasNext()) {
                    val pos = it.nextInt()
                    val vh = try {
                        recyclerView!!.findViewHolderForAdapterPosition(pos) as VH
                    } catch (e: ClassCastException) {
                        null
                    } ?: continue
                    if (condition(vh)) {
                        mapVH[vh.itemView] = vh
                    }
                }
            } else return false
        }
        return true
    }

    fun scrollToShowItem(position: Int): Boolean {
        recyclerView?.scrollToPosition(position)
        return true
    }
}

class FirstViewMatcher : BaseMatcher<View>() {
    var matchedBefore = false

    override fun matches(view: Any): Boolean = !matchedBefore.also { matchedBefore = true }

    override fun describeTo(description: Description) {
        description.appendText(" is the first view that comes along ")
    }
}

fun firstView() = FirstViewMatcher()

fun sizeOfAtLeast(minSize: Int): Matcher<in View> {
    return object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("Recycler view has atleast $minSize items")
        }

        override fun matchesSafely(view: View): Boolean {
            return view is RecyclerView && (view.adapter?.itemCount ?: 0) >= minSize
        }
    }
}

fun withCount(matcher: Matcher<Int>): Matcher<in View> {
    return object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("Recycler view has count with ${matcher.describeTo(description)}")
        }

        override fun matchesSafely(view: View): Boolean {
            return view is RecyclerView && matcher.matches(view.adapter?.itemCount ?: 0)
        }
    }
}
