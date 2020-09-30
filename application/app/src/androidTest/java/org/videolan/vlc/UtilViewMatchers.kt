package org.videolan.vlc

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.core.content.ContextCompat
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.ThreeStatesCheckbox

class RecyclerViewMatcher(@IdRes private val recyclerViewId: Int) {
    var recyclerView: RecyclerView? = null

    fun atPosition(position: Int): Matcher<View> {
        return atPositionOnView(position, -1)
    }

    fun atPositionOnView(position: Int, targetViewId: Int): Matcher<View> {
        return object : TypeSafeMatcher<View>() {
            var childView: View? = null
            var triedMatch = false

            override fun describeTo(description: Description) {
                description.appendText("Recycler view doesn't have item at position $position")
                if (targetViewId != -1) {
                    val idDescription = try {
                        childView?.resources?.getResourceName(targetViewId) ?: targetViewId.toString()
                    } catch (e: Resources.NotFoundException) {
                        targetViewId.toString()
                    }
                    description.appendText(" and with view $idDescription")
                }
            }

            override fun matchesSafely(view: View): Boolean {
                if (!triedMatch && childView == null) {
                    if (recyclerView == null) recyclerView = view.rootView.findViewById(recyclerViewId) as RecyclerView
                    triedMatch = true
                    recyclerView?.run {
                        if (id == recyclerViewId) {
                            scrollToPosition(position + 1)
                            childView = findViewHolderForAdapterPosition(position)?.itemView
                            true
                        } else null
                    } ?: return false
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
                            false
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

class TabsMatcher internal constructor(var position: Int) : ViewAction {

    override fun getConstraints(): Matcher<View> = isDisplayed()

    override fun getDescription(): String = "Click on tab"

    override fun perform(uiController: UiController?, view: View) {
        if (view is TabLayout) {
            val tabLayout: TabLayout = view as TabLayout
            val tab: TabLayout.Tab? = tabLayout.getTabAt(position)
            if (tab != null) {
                tab.select()
            }
        }
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
            description.appendText("Recycler view has count with $matcher")
        }

        override fun matchesSafely(view: View): Boolean {
            return view is RecyclerView && matcher.matches(view.adapter?.itemCount ?: 0)
        }
    }
}

fun withCheckBoxState(state: Matcher<Int>): Matcher<in View> {
    return object : TypeSafeMatcher<ThreeStatesCheckbox>() {
        override fun describeTo(description: Description) {
            description.appendText("checkbox with state $state")
        }

        override fun matchesSafely(item: ThreeStatesCheckbox?): Boolean = state.matches(item?.state)
    } as Matcher<in View>
}

/*
    Taken from https://gist.github.com/frankiesardo/7490059
 */
fun withBackground(resourceId: Int): Matcher<View> {
    return object : TypeSafeMatcher<View>() {

        public override fun matchesSafely(view: View): Boolean {
            return sameBitmap(view.context, view.background, resourceId)
        }

        override fun describeTo(description: Description) {
            description.appendText("has background resource $resourceId")
        }
    }
}

fun withCompoundDrawable(resourceId: Int): Matcher<View> {
    return object : BoundedMatcher<View, TextView>(TextView::class.java) {
        override fun describeTo(description: Description) {
            description.appendText("has compound drawable resource $resourceId")
        }

        public override fun matchesSafely(textView: TextView): Boolean {
            for (drawable in textView.compoundDrawables) {
                if (sameBitmap(textView.context, drawable, resourceId)) {
                    return true
                }
            }
            return false
        }
    }
}

fun withImageDrawable(resourceId: Int): Matcher<View> {
    return object : BoundedMatcher<View, ImageView>(ImageView::class.java) {
        override fun describeTo(description: Description) {
            description.appendText("has image drawable resource $resourceId")
        }

        public override fun matchesSafely(imageView: ImageView): Boolean {
            return sameBitmap(imageView.context, imageView.drawable, resourceId)
        }
    }
}

private fun sameBitmap(context: Context, drawable: Drawable?, @DrawableRes resourceId: Int): Boolean {
    var drawable = drawable
    var otherDrawable = ContextCompat.getDrawable(context, resourceId)
    if (drawable == null || otherDrawable == null) {
        return false
    }
    if (drawable is StateListDrawable && otherDrawable is StateListDrawable) {
        drawable = drawable.current
        otherDrawable = otherDrawable.current
    }
    if (drawable is BitmapDrawable) {
        val bitmap = drawable.bitmap
        val otherBitmap = (otherDrawable as BitmapDrawable).bitmap
        return bitmap.sameAs(otherBitmap)
    }
    return false
}

fun withActionIconDrawable(@DrawableRes resourceId: Int): Matcher<View> {
    return object : BoundedMatcher<View, ActionMenuItemView>(ActionMenuItemView::class.java) {
        override fun describeTo(description: Description) {
            description.appendText("has image drawable resource $resourceId")
        }

        public override fun matchesSafely(actionMenuItemView: ActionMenuItemView): Boolean {
            return sameBitmap(actionMenuItemView.context, actionMenuItemView.itemData.icon, resourceId)
        }
    }
}

fun withResName(resName: String): Matcher<View> {
    return object: TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("with res-name: $resName")
        }

        override fun matchesSafely(view: View): Boolean {
            val identifier = view.resources.getIdentifier(resName, "id", "android")
            return resName.isNotEmpty() && (view.id == identifier)
        }
    }
}
