package org.videolan.vlc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.hamcrest.Matcher


/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 - Nathan Barraille
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 *
 * An Espresso ViewAction that changes the orientation of the screen
 */
class OrientationChangeAction internal constructor(private val orientation: Int) : ViewAction {

    override fun getConstraints(): Matcher<View> {
        return isRoot()
    }

    override fun getDescription(): String {
        return "change orientation to $orientation"
    }

    override fun perform(uiController: UiController, view: View) {
        uiController.loopMainThreadUntilIdle()
        var activity = getActivity(view.context)
        if (activity == null && view is ViewGroup) {
            val c = view.childCount
            var i = 0
            while (i < c && activity == null) {
                activity = getActivity(view.getChildAt(i).context)
                ++i
            }
        }
        activity!!.requestedOrientation = orientation

        val resumedActivities = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        if (resumedActivities.isEmpty()) {
            throw RuntimeException("Could not change orientation")
        }
    }

    private fun getActivity(context: Context): Activity? {
        var context = context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }
}

class ForceClickAction : ViewAction {
    override fun getConstraints() = isEnabled()
    override fun getDescription() = "Force click a view even if 90% condition is not satisfied"

    override fun perform(uiController: UiController?, view: View?) {
        view?.performClick()
    }
}

fun orientationLandscape(): ViewAction {
    return OrientationChangeAction(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
}

fun orientationPortrait(): ViewAction {
    return OrientationChangeAction(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
}
