/*
 * *************************************************************************
 *  FontCache.kt
 * **************************************************************************
 *  Copyright © 2026 VLC authors and VideoLAN
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.util

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.libvlc.LibVLC
import org.videolan.resources.VLCInstance
import org.videolan.tools.AppScope
import org.videolan.tools.KEY_FONT_CACHE_FAILURES
import org.videolan.tools.KEY_FONT_CACHE_FINGERPRINT
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.FONT_CONFIG_VERSION
import org.videolan.vlc.buildFontCacheIfSupported
import java.io.File
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "VLC/FontCache"
private const val SYSTEM_FONTS_DIR = "/system/fonts"
/** Give up after this many failed or interrupted builds, so that a device which can't store
 * the cache, or where the scan never gets to complete, doesn't delay every playback. */
private const val MAX_FAILURES = 3
/** Don't delay a playback longer than this. Past that delay, the playback starts anyway and
 * LibVLC scans the fonts itself, while the build goes on for the next playbacks. */
private const val MAX_WAIT = 5000L
/** Don't warn about the running build more often than this, which is how long a
 * [android.widget.Toast.LENGTH_LONG] toast stays on screen. */
private const val WAIT_NOTIFICATION_INTERVAL = 3500L

/**
 * Builds the fontconfig font cache ahead of playback.
 *
 * LibVLC scans every system font when it creates its first text renderer, which takes
 * seconds on the first run. [buildFontCacheIfSupported] does that scan beforehand and writes
 * the cache on disk (VLC3 only), so that playback only has to load it.
 *
 * The scan is done once per install: the result is remembered in the settings, along with
 * a fingerprint of the system fonts and of LibVLC's fontconfig configuration, so that a system
 * update or a configuration change triggers a new build.
 */
object FontCache {

    private var job: Deferred<Boolean>? = null
    /** The build a caller already waited [MAX_WAIT] for, so that the next ones don't wait again */
    @Volatile
    private var timedOutJob: Deferred<Boolean>? = null
    /** When a caller was last told about the running build, see [await] */
    private val lastWaitNotification = AtomicLong(-WAIT_NOTIFICATION_INTERVAL)

    /**
     * Start the build if it's needed, and return the running job.
     * @return the job to wait for, or null when the cache doesn't need to be built
     */
    @Synchronized
    private fun start(context: Context): Deferred<Boolean>? {
        job?.let { return it }
        val settings = Settings.getInstance(context)
        if (settings.getString(KEY_FONT_CACHE_FINGERPRINT, "") == fingerprint) {
            val failures = settings.getInt(KEY_FONT_CACHE_FAILURES, 0)
            // Already built, or built as many times as it's worth trying
            if (failures == 0 || failures >= MAX_FAILURES) return null
        }
        // The build can outlive the calling service, so it mustn't hold on to it
        val appContext = context.applicationContext
        return AppScope.async(Dispatchers.IO, CoroutineStart.LAZY) { build(appContext) }
                .also { job = it; it.start() }
    }

    /**
     * Build the font cache in the background. To be called once the app starts, so that the
     * scan runs while the user goes through the onboarding.
     */
    fun prepare(context: Context) {
        start(context)
    }

    /**
     * Wait for the font cache to be built, if a build is needed and still running, for at most
     * [MAX_WAIT]. Once a wait has timed out, the next calls don't wait for that build anymore.
     * @param onWait called before waiting, only when there is something to wait for, and at most
     * once per [WAIT_NOTIFICATION_INTERVAL], so that playbacks started in a row warn only once
     */
    suspend fun await(context: Context, onWait: (() -> Unit)? = null) {
        val pending = start(context) ?: return
        if (pending === timedOutJob) return
        if (onWait != null && !pending.isCompleted && shouldNotifyWait()) onWait()
        try {
            if (withTimeoutOrNull(MAX_WAIT) { pending.await() } == null) {
                timedOutJob = pending
                Log.w(TAG, "Font cache still building after ${MAX_WAIT}ms, not waiting for it anymore")
            }
        } catch (e: CancellationException) {
            // A cancelled caller mustn't go on, with the playback for instance. Otherwise, it's
            // the build that was cancelled, which mustn't prevent the playback.
            currentCoroutineContext().ensureActive()
            Log.e(TAG, "The font cache build was cancelled", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build the font cache", e)
        }
    }

    private fun shouldNotifyWait(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastWaitNotification.get()
        return now - last >= WAIT_NOTIFICATION_INTERVAL && lastWaitNotification.compareAndSet(last, now)
    }

    private fun build(context: Context): Boolean {
        val settings = Settings.getInstance(context)
        // A new fingerprint means new fonts to scan, so the previous failures don't count
        val previousFailures = if (settings.getString(KEY_FONT_CACHE_FINGERPRINT, "") == fingerprint)
            settings.getInt(KEY_FONT_CACHE_FAILURES, 0) else 0
        // Count this attempt as a failure until it succeeds: if the process gets killed during
        // the scan, by a user tired of waiting for instance, nothing could be stored afterwards
        // and every launch would scan again. Committed, so that it's on disk before the scan.
        val failures = previousFailures + 1
        settings.edit(commit = true) {
            putString(KEY_FONT_CACHE_FINGERPRINT, fingerprint)
            putInt(KEY_FONT_CACHE_FAILURES, failures)
        }
        val start = System.currentTimeMillis()
        // Creating the LibVLC instance sets HOME, which is where fontconfig stores its cache
        val cached = try {
            (VLCInstance.getInstance(context) as LibVLC).buildFontCacheIfSupported()
        } catch (e: Exception) {
            // Count it as a failed build, or a device where it always throws would retry forever
            Log.e(TAG, "Failed to build the font cache", e)
            false
        }
        Log.i(TAG, "Font cache built in ${System.currentTimeMillis() - start}ms, valid on disk: $cached")
        if (cached)
            settings.putSingle(KEY_FONT_CACHE_FAILURES, 0)
        else if (failures >= MAX_FAILURES)
            Log.w(TAG, "Font cache could not be stored after $failures tries, giving up")
        return cached
    }

    /**
     * Identify the system fonts currently installed. Fontconfig invalidates its cache when the
     * fonts directory changes, so a system update has to trigger a new build. A new fontconfig
     * configuration can add font directories, so it has to trigger a new build as well.
     *
     * Computed once, as it's checked on every playback and the system fonts can only change
     * with a reboot. It's first computed on a background thread, when the app starts.
     */
    private val fingerprint by lazy { "${Build.FINGERPRINT}|${File(SYSTEM_FONTS_DIR).lastModified()}|$FONT_CONFIG_VERSION" }
}
