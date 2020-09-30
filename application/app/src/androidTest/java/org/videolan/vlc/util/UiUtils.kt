/*
 * ************************************************************************
 *  UiUtils.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.util

import android.app.Activity
import android.os.SystemClock
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.util.HumanReadables
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.hamcrest.Matcher
import java.util.concurrent.TimeoutException

object UiUtils {
    /**
     * Perform action of waiting for a specific view id.
     * @param viewId The id of the view to wait for.
     * @param millis The timeout of until when to wait for.
     */
    fun waitId(viewId: Int, millis: Long): ViewAction? {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = ViewMatchers.isEnabled()

            override fun getDescription() = "wait for a specific view with id <$viewId> during $millis millis."

            override fun perform(uiController: UiController, view: View?) {
                uiController.loopMainThreadUntilIdle()
                val startTime = System.currentTimeMillis()
                val endTime = startTime + millis
                val viewMatcher: Matcher<View> = ViewMatchers.withId(viewId)
                val alphaViewMatcher = ViewMatchers.withAlpha(1F)
                do {
                    for (child in TreeIterables.breadthFirstViewTraversal(view)) { // found view with required ID
                        if (viewMatcher.matches(child)) {
                            if (alphaViewMatcher.matches(child))
                                return
                        }
                    }
                    uiController.loopMainThreadForAtLeast(50)
                } while (System.currentTimeMillis() < endTime)
                throw PerformException.Builder()
                        .withActionDescription(description)
                        .withViewDescription(HumanReadables.describe(view))
                        .withCause(TimeoutException())
                        .build()
            }
        }
    }

    /**
     * Stop the test until RecyclerView's data gets loaded.
     *
     * Passed [recyclerProvider] will be activated in UI thread, allowing you to retrieve the View.
     *
     * Workaround for https://issuetracker.google.com/issues/123653014
     */
    inline fun waitUntilLoaded(crossinline recyclerProvider: () -> RecyclerView) {
        Espresso.onIdle()

        lateinit var recycler: RecyclerView

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            recycler = recyclerProvider()
        }

        while (recycler.hasPendingAdapterUpdates()) {
            Thread.sleep(10)
        }
    }

    fun waitForActivity(activityType: Class<out Activity>, timeout: Int = 10): Boolean {
        val endTime = System.currentTimeMillis() + (timeout * 1000)

        do {
            val currentActivity = getActivityInstance()

            // THIS LINE IS MY ISSUE **********************************************
            if (currentActivity != null && activityType.isInstance(currentActivity::class.java))
                return true
            // ********************************************************************

            SystemClock.sleep(100)
        } while (System.currentTimeMillis() < endTime)
        return false
    }

    private fun getActivityInstance(): Activity? {
        val activity = arrayOfNulls<Activity>(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val currentActivity: Activity?
            val resumedActivities = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            if (resumedActivities.iterator().hasNext()) {
                currentActivity = resumedActivities.iterator().next() as Activity
                activity[0] = currentActivity
            }
        }
        return activity[0]
    }

    /**
     * Stop the test until RecyclerView's has children.
     *
     * Passed [recyclerProvider] will be activated in UI thread, allowing you to retrieve the View.
     *
     * Workaround for https://issuetracker.google.com/issues/123653014
     */
    inline fun waitUntilHasChildren(crossinline recyclerProvider: () -> RecyclerView) {
        Espresso.onIdle()

        lateinit var recycler: RecyclerView

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            recycler = recyclerProvider()
        }

        while (recycler.childCount == 0) {
            Thread.sleep(10)
        }
    }
}