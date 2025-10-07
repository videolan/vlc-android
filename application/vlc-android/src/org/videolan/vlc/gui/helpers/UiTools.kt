/*
 * *************************************************************************
 *  Util.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
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

package org.videolan.vlc.gui.helpers

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.media.MediaRouter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RSInvalidStateException
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.text.TextUtils
import android.view.DragEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ActionMode
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.MenuItemCompat
import androidx.core.view.WindowCompat
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.Rotation
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import nl.dionsegijn.konfetti.xml.KonfettiView
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.ACTION_DISCOVER_DEVICE
import org.videolan.resources.AppContextProvider
import org.videolan.resources.CATEGORY_ALBUMS
import org.videolan.resources.CATEGORY_ARTISTS
import org.videolan.resources.CATEGORY_GENRES
import org.videolan.resources.CATEGORY_NOW_PLAYING
import org.videolan.resources.CATEGORY_NOW_PLAYING_PIP
import org.videolan.resources.CATEGORY_SONGS
import org.videolan.resources.EXTRA_PATH
import org.videolan.resources.HEADER_ADD_STREAM
import org.videolan.resources.HEADER_DIRECTORIES
import org.videolan.resources.HEADER_MOVIES
import org.videolan.resources.HEADER_NETWORK
import org.videolan.resources.HEADER_PERMISSION
import org.videolan.resources.HEADER_PLAYLISTS
import org.videolan.resources.HEADER_SERVER
import org.videolan.resources.HEADER_STREAM
import org.videolan.resources.HEADER_TV_SHOW
import org.videolan.resources.HEADER_VIDEO
import org.videolan.resources.ID_ABOUT_TV
import org.videolan.resources.ID_REMOTE_ACCESS
import org.videolan.resources.ID_SETTINGS
import org.videolan.resources.ID_SPONSOR
import org.videolan.resources.TAG_ITEM
import org.videolan.resources.TV_CONFIRMATION_ACTIVITY
import org.videolan.resources.util.launchForeground
import org.videolan.tools.BitmapCache
import org.videolan.tools.KEY_APP_THEME
import org.videolan.tools.KEY_INCLUDE_MISSING
import org.videolan.tools.KEY_INCOGNITO
import org.videolan.tools.KEY_INCOGNITO_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE
import org.videolan.tools.KEY_INCOGNITO_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE
import org.videolan.tools.MultiSelectHelper
import org.videolan.tools.Settings
import org.videolan.tools.dp
import org.videolan.tools.isStarted
import org.videolan.tools.putSingle
import org.videolan.tools.setGone
import org.videolan.vlc.BuildConfig.VLC_VERSION_NAME
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.R
import org.videolan.vlc.StartActivity
import org.videolan.vlc.VlcMigrationHelper
import org.videolan.vlc.gui.AuthorsActivity
import org.videolan.vlc.gui.BaseActivity
import org.videolan.vlc.gui.FeedbackActivity
import org.videolan.vlc.gui.InfoActivity
import org.videolan.vlc.gui.LibrariesActivity
import org.videolan.vlc.gui.LibraryWithLicense
import org.videolan.vlc.gui.browser.MediaBrowserFragment
import org.videolan.vlc.gui.dialogs.AboutVersionDialog
import org.videolan.vlc.gui.dialogs.AddToGroupDialog
import org.videolan.vlc.gui.dialogs.LicenseDialog
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.VideoTracksDialog
import org.videolan.vlc.gui.helpers.BitmapUtil.vectorToBitmap
import org.videolan.vlc.gui.helpers.hf.PinCodeDelegate
import org.videolan.vlc.gui.helpers.hf.checkPIN
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.getAll
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.LifecycleAwareScheduler
import org.videolan.vlc.util.SchedulerCallback
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.util.openLinkIfPossible
import org.videolan.vlc.util.trackNumberText
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.math.min

object UiTools {
    private var logoAnimationRunning: Boolean = false
    var currentNightMode: Int = 0
    private const val TAG = "VLC/UiTools"
    private var DEFAULT_COVER_VIDEO_DRAWABLE: BitmapDrawable? = null
    private var DEFAULT_COVER_AUDIO_DRAWABLE: BitmapDrawable? = null
    private var DEFAULT_COVER_AUDIO_AUTO_DRAWABLE: BitmapDrawable? = null
    private var DEFAULT_COVER_ALBUM_DRAWABLE: BitmapDrawable? = null
    private var DEFAULT_COVER_ARTIST_DRAWABLE: BitmapDrawable? = null
    private var DEFAULT_COVER_PLAYLIST_DRAWABLE: BitmapDrawable? = null
    private var DEFAULT_COVER_GENRE_DRAWABLE: BitmapDrawable? = null
    private var DEFAULT_COVER_MOVIE_DRAWABLE: BitmapDrawable? = null
    private var DEFAULT_COVER_TVSHOW_DRAWABLE: BitmapDrawable? = null
    private var DEFAULT_COVER_FOLDER_DRAWABLE: BitmapDrawable? = null

    private var DEFAULT_COVER_VIDEO_DRAWABLE_BIG: BitmapDrawable? = null
    private var DEFAULT_COVER_AUDIO_DRAWABLE_BIG: BitmapDrawable? = null
    private var DEFAULT_COVER_ALBUM_DRAWABLE_BIG: BitmapDrawable? = null
    private var DEFAULT_COVER_ARTIST_DRAWABLE_BIG: BitmapDrawable? = null
    private var DEFAULT_COVER_PLAYLIST_DRAWABLE_BIG: BitmapDrawable? = null
    private var DEFAULT_COVER_GENRE_DRAWABLE_BIG: BitmapDrawable? = null
    private var DEFAULT_COVER_MOVIE_DRAWABLE_BIG: BitmapDrawable? = null
    private var DEFAULT_COVER_TVSHOW_DRAWABLE_BIG: BitmapDrawable? = null
    private var DEFAULT_COVER_FOLDER_DRAWABLE_BIG: BitmapDrawable? = null

    private val sHandler = Handler(Looper.getMainLooper())
    private const val DELETE_DURATION = 3000

    fun getDefaultVideoDrawable(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_VIDEO_DRAWABLE == null) {
            DEFAULT_COVER_VIDEO_DRAWABLE = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_no_thumbnail_1610))
        }
        return DEFAULT_COVER_VIDEO_DRAWABLE!!
    }

    fun getDefaultAudioDrawable(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_AUDIO_DRAWABLE == null) {
            DEFAULT_COVER_AUDIO_DRAWABLE = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_no_song))
        }
        return DEFAULT_COVER_AUDIO_DRAWABLE!!
    }

    fun getDefaultAudioAutoDrawable(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_AUDIO_AUTO_DRAWABLE == null) {
            DEFAULT_COVER_AUDIO_AUTO_DRAWABLE = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_auto_nothumb))
        }
        return DEFAULT_COVER_AUDIO_AUTO_DRAWABLE!!
    }

    fun getDefaultFolderDrawable(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_FOLDER_DRAWABLE == null) {
            DEFAULT_COVER_FOLDER_DRAWABLE = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_folder))
        }
        return DEFAULT_COVER_FOLDER_DRAWABLE!!
    }

    fun getDefaultAlbumDrawable(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_ALBUM_DRAWABLE == null) {
            DEFAULT_COVER_ALBUM_DRAWABLE = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_no_album))
        }
        return DEFAULT_COVER_ALBUM_DRAWABLE!!
    }

    fun getDefaultArtistDrawable(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_ARTIST_DRAWABLE == null) {
            DEFAULT_COVER_ARTIST_DRAWABLE = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_no_artist))
        }
        return DEFAULT_COVER_ARTIST_DRAWABLE!!
    }

    fun getDefaultPlaylistDrawable(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_PLAYLIST_DRAWABLE == null) {
            DEFAULT_COVER_PLAYLIST_DRAWABLE = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_playlist))
        }
        return DEFAULT_COVER_PLAYLIST_DRAWABLE!!
    }

    fun getDefaultGenreDrawable(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_GENRE_DRAWABLE == null) {
            DEFAULT_COVER_GENRE_DRAWABLE = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_genre))
        }
        return DEFAULT_COVER_GENRE_DRAWABLE!!
    }

    fun getDefaultMovieDrawable(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_MOVIE_DRAWABLE == null) {
            DEFAULT_COVER_MOVIE_DRAWABLE = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_browser_movie))
        }
        return DEFAULT_COVER_MOVIE_DRAWABLE!!
    }

    fun getDefaultTvshowDrawable(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_TVSHOW_DRAWABLE == null) {
            DEFAULT_COVER_TVSHOW_DRAWABLE = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_browser_tvshow))
        }
        return DEFAULT_COVER_TVSHOW_DRAWABLE!!
    }

    fun getDefaultVideoDrawableBig(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_VIDEO_DRAWABLE_BIG == null) {
            DEFAULT_COVER_VIDEO_DRAWABLE_BIG = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_video_big))
        }
        return DEFAULT_COVER_VIDEO_DRAWABLE_BIG!!
    }

    fun getDefaultAudioDrawableBig(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_AUDIO_DRAWABLE_BIG == null) {
            DEFAULT_COVER_AUDIO_DRAWABLE_BIG = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_song_big))
        }
        return DEFAULT_COVER_AUDIO_DRAWABLE_BIG!!
    }

    fun getDefaultAlbumDrawableBig(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_ALBUM_DRAWABLE_BIG == null) {
            DEFAULT_COVER_ALBUM_DRAWABLE_BIG = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_album_big))
        }
        return DEFAULT_COVER_ALBUM_DRAWABLE_BIG!!
    }

    fun getDefaultArtistDrawableBig(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_ARTIST_DRAWABLE_BIG == null) {
            DEFAULT_COVER_ARTIST_DRAWABLE_BIG = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_artist_big))
        }
        return DEFAULT_COVER_ARTIST_DRAWABLE_BIG!!
    }

    fun getDefaultPlaylistDrawableBig(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_PLAYLIST_DRAWABLE_BIG == null) {
            DEFAULT_COVER_PLAYLIST_DRAWABLE_BIG = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_playlist_big))
        }
        return DEFAULT_COVER_PLAYLIST_DRAWABLE_BIG!!
    }

    fun getDefaultGenreDrawableBig(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_GENRE_DRAWABLE_BIG == null) {
            DEFAULT_COVER_GENRE_DRAWABLE_BIG = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_genre_big))
        }
        return DEFAULT_COVER_GENRE_DRAWABLE_BIG!!
    }

    fun getDefaultMovieDrawableBig(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_MOVIE_DRAWABLE_BIG == null) {
            DEFAULT_COVER_MOVIE_DRAWABLE_BIG = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_browser_movie_big))
        }
        return DEFAULT_COVER_MOVIE_DRAWABLE_BIG!!
    }

    fun getDefaultTvshowDrawableBig(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_TVSHOW_DRAWABLE_BIG == null) {
            DEFAULT_COVER_TVSHOW_DRAWABLE_BIG = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_browser_tvshow_big))
        }
        return DEFAULT_COVER_TVSHOW_DRAWABLE_BIG!!
    }

    fun getDefaultFolderDrawableBig(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_FOLDER_DRAWABLE_BIG == null) {
            DEFAULT_COVER_FOLDER_DRAWABLE_BIG = BitmapDrawable(context.resources, getBitmapFromDrawable(context, R.drawable.ic_folder_big))
        }
        return DEFAULT_COVER_FOLDER_DRAWABLE_BIG!!
    }

    private fun getSnackAnchorView(activity: Activity, overAudioPlayer: Boolean = false) =
            if (activity is BaseActivity && activity.getSnackAnchorView(overAudioPlayer) != null) activity.getSnackAnchorView(overAudioPlayer) else activity.findViewById(android.R.id.content)

    /**
     * Print an on-screen message to alert the user
     */
    fun snacker(activity: Activity, stringId: Int, overAudioPlayer: Boolean = false) {
        val view = getSnackAnchorView(activity, overAudioPlayer) ?: return
        val snack = Snackbar.make(view, stringId, Snackbar.LENGTH_SHORT)
        if (overAudioPlayer) snack.setAnchorView(R.id.audio_play_progress)
        snack.show()
    }

    /**
     * Print an on-screen message to alert the user
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun snacker(activity:Activity, message: String) {
        val view = getSnackAnchorView(activity) ?: return
        val snack = Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
        if (VlcMigrationHelper.isLolliPopOrLater)
            snack.view.elevation = view.resources.getDimensionPixelSize(R.dimen.audio_player_elevation).toFloat()
        snack.show()
    }

    /**
     * Print an on-screen message to alert the user, with undo action
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun snackerConfirm(activity: Activity, message: String, overAudioPlayer: Boolean = false, @StringRes confirmMessage:Int = R.string.ok, forcedView: View? = null, indefinite:Boolean = false, action: () -> Unit) {
        val view = forcedView ?: (getSnackAnchorView(activity, overAudioPlayer) ?: return)
        val snack = Snackbar.make(view, message, if (indefinite) Snackbar.LENGTH_INDEFINITE else Snackbar.LENGTH_LONG)
                .setAction(confirmMessage) { action.invoke() }
        if (overAudioPlayer) snack.setAnchorView(R.id.time)
        if (VlcMigrationHelper.isLolliPopOrLater)
            snack.view.elevation = view.resources.getDimensionPixelSize(R.dimen.audio_player_elevation).toFloat()
        snack.show()
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun CoroutineScope.snackerConfirm(activity:Activity, message: String, action: suspend() -> Unit) {
        val view = getSnackAnchorView(activity) ?: return
        val snack = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                .setAction(R.string.ok) { launch { action.invoke() } }
        if (VlcMigrationHelper.isLolliPopOrLater)
            snack.view.elevation = view.resources.getDimensionPixelSize(R.dimen.audio_player_elevation).toFloat()
        snack.show()
    }


    /**
     * Print an on-screen message to alert the user, with undo action
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun snackerWithCancel(activity: Activity, message: String, overAudioPlayer: Boolean = false, action: () -> Unit, cancelAction: () -> Unit) {
        val view = getSnackAnchorView(activity, overAudioPlayer) ?: return
        @SuppressLint("WrongConstant") val snack = Snackbar.make(view, message, DELETE_DURATION)
                .setAction(R.string.cancel) {
                    sHandler.removeCallbacks(action)
                    cancelAction.invoke()
                }
        if (VlcMigrationHelper.isLolliPopOrLater)
            snack.view.elevation = view.resources.getDimensionPixelSize(R.dimen.audio_player_elevation).toFloat()
        if (overAudioPlayer) snack.setAnchorView(R.id.time)
        snack.show()
        sHandler.postDelayed(action, DELETE_DURATION.toLong())
    }

    fun snackerMessageInfinite(activity:Activity, message: String):Snackbar? {
        val view = getSnackAnchorView(activity) ?: return null
        return Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE)
    }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun snackerMissing(activity: FragmentActivity) {
        val view = getSnackAnchorView(activity) ?: return
        val snack = Snackbar.make(view, activity.getString(R.string.missing_media_snack), Snackbar.LENGTH_LONG)
                .setAction(R.string.ok) {
                    activity.lifecycleScope.launch {
                        PreferencesActivity.launchWithPref(activity, KEY_INCLUDE_MISSING)
                    }
                }
        if (VlcMigrationHelper.isLolliPopOrLater)
            snack.view.elevation = view.resources.getDimensionPixelSize(R.dimen.audio_player_elevation).toFloat()
        snack.show()
    }

    /**
     * Get a resource id from an attribute id.
     *
     * @param context
     * @param attrId
     * @return the resource id
     */
    fun getResourceFromAttribute(context: Context, attrId: Int): Int {
        val a = context.theme.obtainStyledAttributes(intArrayOf(attrId))
        val resId = a.getResourceId(0, 0)
        a.recycle()
        return resId
    }

    /**
     * Get a color id from an attribute id.
     *
     * @param context
     * @param attrId
     * @return the color id
     */
    fun getColorFromAttribute(context: Context, attrId: Int): Int {
        return ContextCompat.getColor(context, getResourceFromAttribute(context, attrId))
    }

    fun setViewOnClickListener(v: View?, ocl: View.OnClickListener?) {
        v?.setOnClickListener(ocl)
    }

    /**
     * Fill the about main view for mobile and TV
     */
    fun fillAboutView(activity: FragmentActivity, v: View) {
        val builddate = v.context.getString(R.string.build_time)
        val appVersion = v.findViewById<TextView>(R.id.app_version)
        appVersion.text = VLC_VERSION_NAME
        val appVersionDate = v.findViewById<TextView>(R.id.app_version_date)
        appVersionDate.text = builddate
        v.findViewById<View>(R.id.sliding_tabs).setGone()

        val logo = v.findViewById<ImageView>(R.id.logo)
        val konfettiView = v.findViewById<KonfettiView>(R.id.konfetti)
        logo.setOnClickListener {
            if (logoAnimationRunning) return@setOnClickListener
            logoAnimationRunning = true
            val iter = SecureRandom().nextInt(4) + 1
            logo.animate().rotationBy(360F * iter).translationY(-24.dp.toFloat()).setDuration(600 + (100L * iter)).setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                logo.animate().translationY(0F).setStartDelay(75).setDuration(300).setInterpolator(OvershootInterpolator(3.0F)).withEndAction {
                    logoAnimationRunning = false
                }
                val partyRight = Party(
                    colors = listOf(ContextCompat.getColor(activity, R.color.orange200), ContextCompat.getColor(activity, R.color.orange800), ContextCompat.getColor(activity, R.color.orange500)),
                    angle = 0,
                    spread = 60,
                    speed = 3f,
                    maxSpeed = 18f,
                    fadeOutEnabled = true,
                    timeToLive = 2000L,
                    rotation = Rotation(enabled = false),
                    shapes = listOf(Shape.Circle),
                    size = listOf(Size(4), Size(3), Size(2)),
                    delay = 275,
                    position = Position.Absolute(logo.x + logo.width - 12.dp, logo.y + logo.height - 24.dp).between(Position.Absolute(logo.x + logo.width - 12.dp, logo.y + logo.height + 24.dp)),
                    emitter = Emitter(duration = 200, TimeUnit.MILLISECONDS).max(iter*30)
                )
                konfettiView.start(partyRight)
                val partyLeft = Party(
                    colors = listOf(ContextCompat.getColor(activity, R.color.orange200), ContextCompat.getColor(activity, R.color.orange800), ContextCompat.getColor(activity, R.color.orange500)),
                    angle = 180,
                    spread = 60,
                    speed = 3f,
                    maxSpeed = 18f,
                    fadeOutEnabled = true,
                    timeToLive = 2000L,
                    rotation = Rotation(enabled = false),
                    shapes = listOf(Shape.Circle),
                    size = listOf(Size(4), Size(3), Size(2)),
                    delay = 275,
                    position = Position.Absolute(logo.x + 12.dp, logo.y + logo.height - 24.dp).between(Position.Absolute(logo.x + 12.dp, logo.y + logo.height + 24.dp)),
                    emitter = Emitter(duration = 200, TimeUnit.MILLISECONDS).max(iter*30)
                )
                konfettiView.start(partyLeft)
            }
        }

        v.findViewById<View>(R.id.version_card).setOnClickListener {
            AboutVersionDialog.newInstance().show(activity.supportFragmentManager, "AboutVersionDialog")
        }
        v.findViewById<View>(R.id.about_website_container).setOnClickListener {
            activity.openLinkIfPossible("https://www.videolan.org/vlc/")
        }
        v.findViewById<View>(R.id.about_report_container).setOnClickListener {
            activity.startActivity(Intent(activity, FeedbackActivity::class.java))
        }
        v.findViewById<View>(R.id.about_sources_container).setOnClickListener {
            activity.openLinkIfPossible("https://code.videolan.org/videolan/vlc-android")
        }

        v.findViewById<View>(R.id.about_authors_container).setOnClickListener {
            activity.startActivity(Intent(activity, AuthorsActivity::class.java))
        }
        v.findViewById<View>(R.id.about_libraries_container).setOnClickListener {
            activity.startActivity(Intent(activity, LibrariesActivity::class.java))
        }
        v.findViewById<View>(R.id.about_vlc_card).setOnClickListener {
           var licenseText = ""
            activity.lifecycleScope.launchWhenStarted {
                licenseText = AppContextProvider.appResources.openRawResource(R.raw.vlc_license).bufferedReader().use {
                   it.readText()
               }
            }
            LicenseDialog.newInstance(LibraryWithLicense(activity.getString(R.string.app_name),activity.getString(R.string.about_copyright) , activity.getString(R.string.about_license), licenseText, "https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt")).show(activity.supportFragmentManager, "LicenseDialog")
        }

        val donationsButton = v.findViewById<CardView>(R.id.donationsButton)
//        VLCBilling.getInstance(activity.application).addStatusListener {
//            manageDonationVisibility(activity,donationsButton)
//        }
//        manageDonationVisibility(activity,donationsButton)


        donationsButton.setOnClickListener {
            activity.showDonations()
        }
    }

//    private fun manageDonationVisibility(activity: FragmentActivity, donationsButton:View) {
//        if (VLCBilling.getInstance(activity.application).status == BillingStatus.FAILURE ||  VLCBilling.getInstance(activity.application).skuDetails.isEmpty()) donationsButton.setGone() else donationsButton.setVisible()
//    }


    /**
     * Update the incognito mode setting
     *
     * @param activity the activity that launched the change
     * @param item the menu item that was clicked
     * @return true if the change has been applied, false otherwise
     */
    fun updateIncognitoMode(activity: FragmentActivity, item: MenuItem): Boolean {
        if (activity.showPinIfNeeded()) return false
        val settings = Settings.getInstance(activity)
        settings.putSingle(KEY_INCOGNITO, !settings.getBoolean(KEY_INCOGNITO, false))
        item.isChecked = !item.isChecked
        if (!item.isChecked) {
            settings.edit {
                remove(KEY_INCOGNITO_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE)
                remove(KEY_INCOGNITO_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE)
            }
        }
        return true
    }

    fun setKeyboardVisibility(v: View?, show: Boolean) {
        if (v == null) return
        val inputMethodManager = v.context.applicationContext.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        sHandler.post {
            if (show)
                inputMethodManager.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
            else
                inputMethodManager.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    fun FragmentActivity.addToPlaylistAsync(parent: String, includeSubfolders: Boolean = false, defaultTitle:String = "") {
        if (!isStarted()) return
        val savePlaylistDialog = SavePlaylistDialog()
        savePlaylistDialog.arguments = bundleOf(SavePlaylistDialog.KEY_FOLDER to parent,
                SavePlaylistDialog.KEY_SUB_FOLDERS to includeSubfolders, SavePlaylistDialog.KEY_DEFAULT_TITLE to defaultTitle)
        savePlaylistDialog.show(supportFragmentManager, "fragment_add_to_playlist")
    }

    fun FragmentActivity.addToPlaylist(list: List<MediaWrapper>) {
        addToPlaylist(list.toTypedArray(), SavePlaylistDialog.KEY_NEW_TRACKS)
    }

    fun FragmentActivity.addToPlaylist(tracks: Array<MediaWrapper>, key: String) {
        if (!isStarted()) return
        val savePlaylistDialog = SavePlaylistDialog()
        savePlaylistDialog.arguments = bundleOf(key to tracks)
        savePlaylistDialog.show(supportFragmentManager, "fragment_add_to_playlist")
    }

    /**
     * Display a restricted access snack bar if needed
     *
     * @return has the access been blocked
     */
    fun FragmentActivity.showPinIfNeeded():Boolean {
        if (Settings.safeMode && PinCodeDelegate.pinUnlocked.value != true) {
            if (Settings.tvUI) {
                lifecycleScope.launch {
                    if (checkPIN(true)) {
                        snacker(this@showPinIfNeeded, R.string.pin_code_access_granted, false)
                    }
                }
            } else {
                snackerConfirm(this, getString(R.string.restricted_access), false, R.string.unlock) {
                    lifecycleScope.launch { checkPIN(true) }
                }
            }
            return true
        }
        return false
    }

    fun FragmentActivity.addToGroup(tracks: List<MediaWrapper>, forbidNewGroup:Boolean) {
        if (!isStarted()) return
        val addToGroupDialog = AddToGroupDialog()
        addToGroupDialog.arguments = bundleOf(AddToGroupDialog.KEY_TRACKS to tracks.toTypedArray(), AddToGroupDialog.FORBID_NEW_GROUP to forbidNewGroup)
        addToGroupDialog.show(supportFragmentManager, "fragment_add_to_group")
    }

    /**
     * Creates a shortcut to the media on the launcher
     * @param mediaLibraryItem: the [MediaLibraryItem] to create a shortcut to
     */
    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun FragmentActivity.createShortcut(mediaLibraryItem: MediaLibraryItem) {
        if (!isStarted()) return

        val context = this
        withContext(Dispatchers.IO) {
            val iconBitmap = if (mediaLibraryItem is Genre || mediaLibraryItem is Playlist)
                ThumbnailsProvider.getPlaylistOrGenreImage("playlist:${mediaLibraryItem.id}_${48.dp}", mediaLibraryItem.tracks.toList(), 48.dp)
            else
                BitmapCache.getBitmapFromMemCache(ThumbnailsProvider.getMediaCacheKey(mediaLibraryItem is MediaWrapper, mediaLibraryItem, 48.dp.toString()))
                        ?: ThumbnailsProvider.obtainBitmap(mediaLibraryItem, 48.dp)


            val size = min(48.dp, iconBitmap?.height ?: 0)
            val iconCompat = IconCompat.createWithAdaptiveBitmap(iconBitmap.centerCrop(size, size)
                    ?: vectorToBitmap(context, R.drawable.ic_icon, 48.dp, 48.dp))
            val actionType = when (mediaLibraryItem) {
                is Album -> "album"
                is Artist -> "artist"
                is Genre -> "genre"
                is Playlist -> "playlist"
                else -> "media"
            }
            val pinShortcutInfo = ShortcutInfoCompat.Builder(context, mediaLibraryItem.id.toString())
                    .setShortLabel(mediaLibraryItem.title)
                    .setIntent(Intent(context, StartActivity::class.java).apply { action = "vlc.mediashortcut:$actionType:${mediaLibraryItem.id}" })
                    .setIcon(iconCompat)
                    .build()

            val pinnedShortcutCallbackIntent = ShortcutManagerCompat.createShortcutResultIntent(context, pinShortcutInfo)
            val successCallback = PendingIntent.getBroadcast(context, 0, pinnedShortcutCallbackIntent,  PendingIntent.FLAG_IMMUTABLE)
            ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, successCallback.intentSender)
        }
    }

    fun FragmentActivity.showVideoTrack(menuListener:(VideoTracksDialog.VideoTrackOption) -> Unit, trackSelectionListener:(String, VideoTracksDialog.TrackType) -> Unit) {
        if (!isStarted()) return
        val videoTracksDialog = VideoTracksDialog()
        videoTracksDialog.arguments = bundleOf()
        videoTracksDialog.show(supportFragmentManager, "fragment_video_tracks")
        videoTracksDialog.menuItemListener = menuListener
        videoTracksDialog.trackSelectionListener = trackSelectionListener
    }

    fun FragmentActivity.showDonations() {
        if (!isStarted()) return
//        val videoTracksDialog = VLCBillingDialog()
//        videoTracksDialog.show(supportFragmentManager, "fragment_donations")
    }

    fun FragmentActivity.showMediaInfo(mediaWrapper: MediaWrapper) {
        val i = Intent(this, InfoActivity::class.java)
        i.putExtra(TAG_ITEM, mediaWrapper)
        startActivity(i)
    }

    fun Context.isTablet() = resources.getBoolean(R.bool.is_tablet)

    fun getDefaultCover(context: Context, item: MediaLibraryItem): BitmapDrawable {
        return when (item.itemType) {
            MediaLibraryItem.TYPE_ARTIST -> getDefaultArtistDrawable(context)
            MediaLibraryItem.TYPE_ALBUM -> getDefaultAlbumDrawable(context)
            MediaLibraryItem.TYPE_MEDIA -> {
                if ((item as MediaWrapper).type == MediaWrapper.TYPE_VIDEO) getDefaultVideoDrawable(context) else getDefaultAudioDrawable(context)
            }
            else -> getDefaultAudioDrawable(context)
        }
    }

    /**
     * Set a blur effect on the whole [ImageView] using [RenderEffect]
     * with a fallback on [RenderScript] if needed
     *
     * @param imageView the [ImageView] to blur
     * @param bitmap the [Bitmap] to display
     * @param radius the blur radius
     * @param colorFilter the color filter to be used on the view depending on the theme
     */
    suspend fun blurView(imageView: ImageView, bitmap: Bitmap?, radius: Float, colorFilter: Int) {
        imageView.setColorFilter(colorFilter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            imageView.setRenderEffect(blur)
            imageView.setImageBitmap(bitmap)
        } else {
            val blurred = withContext(Dispatchers.IO) {
                blurBitmap(bitmap, radius)
            }
            withContext(Dispatchers.Main) {
                imageView.setImageBitmap(blurred)
            }
        }
    }

    /**
     * Blur a [Bitmap]. it uses a deprecated API and therefore should not be used
     * except for a fallback for API < 31
     *
     * @param bitmap the [Bitmap] to blur
     * @param radius the bur radius
     * @return a blurred bitmap
     */
    @Suppress("DEPRECATION")
    @JvmOverloads
    fun blurBitmap(bitmap: Bitmap?, radius: Float = 15.0f): Bitmap? {
        if (bitmap == null || bitmap.config == null) return null
        try {
            //Let's create an empty bitmap with the same size of the bitmap we want to blur
            val outBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

            //Instantiate a new Renderscript
            val rs = RenderScript.create(AppContextProvider.appContext)

            //Create an Intrinsic Blur Script using the Renderscript
            val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

            //Create the Allocations (in/out) with the Renderscript and the in/out bitmaps
            val allIn = Allocation.createFromBitmap(rs, if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true))
            val allOut = Allocation.createFromBitmap(rs, outBitmap)

            //Set the radius of the blur
            blurScript.setRadius(radius)

            //Perform the Renderscript
            blurScript.setInput(allIn)
            blurScript.forEach(allOut)

            //Copy the final bitmap created by the out Allocation to the outBitmap
            allOut.copyTo(outBitmap)

            //After finishing everything, we destroy the Renderscript.
            rs.destroy()

            return outBitmap
        } catch (ignored: RSInvalidStateException) {
            return null
        }

    }

    fun updateSortTitles(menu: Menu, provider: MedialibraryProvider<*>) {
        val sort = provider.sort
        val desc = provider.desc
        menu.appendSortOrder(provider.context, R.id.ml_menu_sortby_name, R.string.sortby_name, (sort == Medialibrary.SORT_ALPHA || sort == Medialibrary.SORT_DEFAULT), desc)
        menu.appendSortOrder(provider.context, R.id.ml_menu_sortby_filename, R.string.sortby_filename, sort == Medialibrary.SORT_FILENAME, desc)
        menu.appendSortOrder(provider.context, R.id.ml_menu_sortby_artist_name, R.string.sortby_artist_name, sort == Medialibrary.SORT_ARTIST, desc)
        menu.appendSortOrder(provider.context, R.id.ml_menu_sortby_album_name, R.string.sortby_album_name, sort == Medialibrary.SORT_ALBUM, desc)
        menu.appendSortOrder(provider.context, R.id.ml_menu_sortby_length, R.string.sortby_length, sort == Medialibrary.SORT_DURATION, desc)
        menu.appendSortOrder(provider.context, R.id.ml_menu_sortby_date, R.string.sortby_date_release, sort == Medialibrary.SORT_RELEASEDATE, desc)
        menu.appendSortOrder(provider.context,R.id.ml_menu_sortby_last_modified, R.string.sortby_date_last_modified, sort == Medialibrary.SORT_LASTMODIFICATIONDATE, desc)
        menu.appendSortOrder(provider.context,R.id.ml_menu_sortby_insertion_date, R.string.sortby_date_insertion, sort == Medialibrary.SORT_INSERTIONDATE, desc)
        //        item = menu.findItem(R.id.ml_menu_sortby_number); TODO sort by track number
        //        if (item != null) item.setTitle(sort == Medialibrary.SORT_ && !desc ? R.string.sortby_number_desc : R.string.sortby_number);

    }

    fun updateSortTitles(sortable: MediaBrowserFragment<*>) {
        val menu = sortable.menu ?: return
        val model = sortable.viewModel
        val sort = model.sort
        val desc = model.desc
        menu.appendSortOrder(sortable.requireActivity(), R.id.ml_menu_sortby_name, R.string.sortby_name, sort == Medialibrary.SORT_ALPHA, desc)
        menu.appendSortOrder(sortable.requireActivity(), R.id.ml_menu_sortby_filename, R.string.sortby_filename, (sort == Medialibrary.SORT_FILENAME || sort == Medialibrary.SORT_DEFAULT), desc)
        menu.appendSortOrder(sortable.requireActivity(), R.id.ml_menu_sortby_artist_name, R.string.sortby_artist_name, sort == Medialibrary.SORT_ARTIST, desc)
        menu.appendSortOrder(sortable.requireActivity(), R.id.ml_menu_sortby_album_name, R.string.sortby_album_name, sort == Medialibrary.SORT_ALBUM, desc)
        menu.appendSortOrder(sortable.requireActivity(), R.id.ml_menu_sortby_length, R.string.sortby_length, sort == Medialibrary.SORT_DURATION, desc)
        menu.appendSortOrder(sortable.requireActivity(), R.id.ml_menu_sortby_date, R.string.sortby_date_release, sort == Medialibrary.SORT_RELEASEDATE, desc)
        menu.appendSortOrder(sortable.requireActivity(),R.id.ml_menu_sortby_last_modified, R.string.sortby_date_last_modified, sort == Medialibrary.SORT_RELEASEDATE, desc)
        //        item = menu.findItem(R.id.ml_menu_sortby_number); TODO sort by track number
        //        if (item != null) item.setTitle(sort == Medialibrary.SORT_ && !desc ? R.string.sortby_number_desc : R.string.sortby_number);

    }

    /**
     * Sets a [MenuItem] title and contentDescription depending on the sort it shows
     *
     * @param context the context to be used for strings
     * @param id the [MenuItem] id
     * @param titleRes the string resource to use as a title
     * @param isCurrent is this the current sort
     * @param desc is the sort descending
     */
    private fun Menu.appendSortOrder(context: Context, @IdRes id:Int, @StringRes titleRes:Int, isCurrent:Boolean, desc:Boolean) = findItem(id)?.let { menuItem ->
        val title = context.getString(titleRes)
        menuItem.title = if (!isCurrent) title else "$title ${if (desc) "▼" else "▲"}"
        MenuItemCompat.setContentDescription(menuItem, if (!isCurrent) title else "$title. ${context.getString(if (desc) R.string.descending else R.string.ascending)}")
    }

    fun confirmExit(activity: Activity) {
        AlertDialog.Builder(activity)
                .setMessage(R.string.exit_app_msg)
                .setTitle(R.string.exit_app)
                .setPositiveButton(R.string.ok) { _, _ -> activity.finish() }
                .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }.create().show()
    }

    fun newStorageDetected(activity: Activity?, path: String?) {
        if (activity == null) return
        val uuid = FileUtils.getFileNameFromPath(path)
        val deviceName = FileUtils.getStorageTag(uuid)
        val message = String.format(activity.getString(R.string.ml_external_storage_msg), deviceName
                ?: uuid)
        val si = Intent(ACTION_DISCOVER_DEVICE, null, activity, MediaParsingService::class.java)
                .putExtra(EXTRA_PATH, path)
        if (activity is AppCompatActivity) {
            val builder = AlertDialog.Builder(activity)
                    .setTitle(R.string.ml_external_storage_title)
                    .setCancelable(false)
                    .setMessage(message)
                    .setPositiveButton(R.string.ml_external_storage_accept) { _, _ ->
                        activity.launchForeground(si)
                    }
                    .setNegativeButton(R.string.ml_external_storage_decline) { dialog, _ -> dialog.dismiss() }
            builder.show()
        } else {
            val builder = android.app.AlertDialog.Builder(activity)
                    .setTitle(R.string.ml_external_storage_title)
                    .setCancelable(false)
                    .setMessage(message)
                    .setPositiveButton(R.string.ml_external_storage_accept) { _, _ ->
                        activity.launchForeground(si)
                    }
                    .setNegativeButton(R.string.ml_external_storage_decline) { dialog, _ -> dialog.dismiss() }
            builder.show()
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    fun setOnDragListener(activity: Activity) {
        val view = if (AndroidUtil.isNougatOrLater) activity.window.peekDecorView() else null
        view?.setOnDragListener(View.OnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DROP -> {
                    val clipData = event.clipData ?: return@OnDragListener false
                    val itemsCount = clipData.itemCount
                    val uriList = mutableListOf<MediaWrapper>()
                    for (i in 0 until itemsCount) {
                        val permissions = activity.requestDragAndDropPermissions(event)
                        if (permissions != null) {
                            val item = clipData.getItemAt(i)
                            val uri = item.uri ?: item.text.toString().toUri()
                            val media = MLServiceLocator.getAbstractMediaWrapper(uri)
                            if ("file" != uri.scheme)
                                media.type = MediaWrapper.TYPE_STREAM
                            uriList.add(media)
                        }
                    }
                    MediaUtils.openList(activity, uriList, 0)
                    false
                }
                else -> false
            }
        })
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun setRotationAnimation(activity: Activity) {
        if (!VlcMigrationHelper.isJellyBeanMR2OrLater) return
        val win = activity.window
        val winParams = win.attributes
        winParams.rotationAnimation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS else WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT
        win.attributes = winParams
    }


    fun restartDialog(
        activity: Activity,
        fromLeanback: Boolean = false,
        leanbackResultCode: Int = 0,
        leanbackCaller: Any? = null
    ) {

        if (fromLeanback) {
            val intent = Intent(Intent.ACTION_VIEW).setClassName(activity, TV_CONFIRMATION_ACTIVITY)

            intent.putExtra(
                "confirmation_dialog_title",
                activity.getString(R.string.restart_vlc)
            )
            intent.putExtra(
                "confirmation_dialog_text",
                activity.getString(R.string.restart_message)
            )
            when (leanbackCaller) {
                is Activity -> leanbackCaller.startActivityForResult(intent, leanbackResultCode)
                is Fragment -> leanbackCaller.startActivityForResult(intent, leanbackResultCode)
                is android.app.Fragment -> leanbackCaller.startActivityForResult(intent, leanbackResultCode)
                else -> throw IllegalStateException("Invalid caller")
            }

            return
        }

        AlertDialog.Builder(activity)
                .setTitle(activity.resources.getString(R.string.restart_vlc))
                .setMessage(activity.resources.getString(R.string.restart_message))
                .setPositiveButton(R.string.restart_message_OK) { _, _ -> android.os.Process.killProcess(android.os.Process.myPid()) }
                .setNegativeButton(R.string.restart_message_Later, null)
                .create()
                .show()
    }



    fun deleteSubtitleDialog(context: Context, positiveListener: DialogInterface.OnClickListener, negativeListener: DialogInterface.OnClickListener) {
        AlertDialog.Builder(context)
                .setTitle(context.resources.getString(R.string.delete_sub_title))
                .setMessage(context.resources.getString(R.string.delete_sub_message))
                .setPositiveButton(R.string.delete, positiveListener)
                .setNegativeButton(R.string.cancel, negativeListener)
                .create()
                .show()
    }

    fun hasSecondaryDisplay(context: Context): Boolean {
        val mediaRouter = context.getSystemService<MediaRouter>()!!
        val route = mediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO)
        val presentationDisplay = route?.presentationDisplay
        return presentationDisplay != null
    }

    /**
     * Invalidate the default bitmaps that are different in light and dark modes
     */
    fun invalidateBitmaps() {
        DEFAULT_COVER_VIDEO_DRAWABLE = null
    }

    fun TextView.addFavoritesIcon() {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_emoji_favorite)
        setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
    }

    fun TextView.removeDrawables() {
        setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
    }

}

/**
 * Set the alignment mode of the specified TextView with the desired align
 * mode from preferences.
 *
 *
 * See @array/list_title_alignment_values
 *
 * @param t         Reference to the textview
 * @param activated is the ellipsize mode activated
 */
@BindingAdapter("ellipsizeMode")
fun setEllipsizeModeByPref(t: TextView, activated: Boolean) {
    if (!activated) return

    when (Settings.listTitleEllipsize) {
        0 -> {}
        1 -> t.ellipsize = TextUtils.TruncateAt.START
        2 -> t.ellipsize = TextUtils.TruncateAt.END
        3 -> t.ellipsize = TextUtils.TruncateAt.MIDDLE
        4 -> {
            t.ellipsize = TextUtils.TruncateAt.MARQUEE
            t.marqueeRepeatLimit = 1
        }
    }
}

interface MarqueeViewHolder {
    val titleView: TextView?
}

const val MARQUEE_ACTION = "marquee_action"
fun enableMarqueeEffect(recyclerView: RecyclerView):LifecycleAwareScheduler? {
    (recyclerView.layoutManager as? LinearLayoutManager)?.let { layoutManager ->
        val scheduler = LifecycleAwareScheduler(object :SchedulerCallback {
            override fun onTaskTriggered(id: String, data: Bundle) {
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                for (i in firstVisibleItemPosition..lastVisibleItemPosition) {
                    val holder = recyclerView.findViewHolderForLayoutPosition(i)
                    (holder as? MarqueeViewHolder)?.titleView?.isSelected = true
                }
            }

            override val lifecycle: Lifecycle
                get() = recyclerView.findViewTreeLifecycleOwner()!!.lifecycle
        })
        //Initial animation for already visible items
        scheduler.scheduleAction(MARQUEE_ACTION, 1500)
        //Animation when done scrolling
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                scheduler.cancelAction(MARQUEE_ACTION)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) scheduler.scheduleAction(MARQUEE_ACTION, 1500)
            }
        })
        return scheduler
    }
    return null
}

@BindingAdapter("selected")
fun isSelected(v: View, isSelected: Boolean?) {
    v.isSelected = isSelected!!
}

@BindingAdapter("selectedPadding")
fun selectedPadding(v: View, isSelected: Boolean?) {
    val padding = if (isSelected == true) 16.dp else 0.dp
    v.setPadding(padding, padding, padding, padding)
}

@BindingAdapter("selectedElevation")
fun selectedElevation(v: View, isSelected: Boolean?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val elevation = if (isSelected == true) 0.dp else 4.dp
        if (v is CardView) v.cardElevation = elevation.toFloat() else v.elevation = elevation.toFloat()
    }
}

@BindingAdapter("trackNumber")
fun trackNumber(v: View, media: MediaWrapper) {
    (v as? TextView)?.text = media.trackNumberText()
}

fun BaseActivity.applyTheme() {
    forcedTheme()?.let {
        setTheme(it)
        return
    }
    if (Settings.showTvUi) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setTheme(R.style.Theme_VLC_Black)
        return
    }

    val string = settings.getString(KEY_APP_THEME, "-1")
    when (string) {
        "1" -> {
            window.setBackgroundDrawable(ContextCompat.getColor(this, R.color.white).toDrawable())
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        }
        "2" -> {
            window.setBackgroundDrawable(ContextCompat.getColor(this, R.color.mini_player_dark).toDrawable())
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        }
    }
    AppCompatDelegate.setDefaultNightMode(Integer.valueOf(string!!))
}

fun getTvIconRes(mediaLibraryItem: MediaLibraryItem) = when (mediaLibraryItem.itemType) {
    MediaLibraryItem.TYPE_ALBUM -> R.drawable.ic_album_big
    MediaLibraryItem.TYPE_ARTIST -> R.drawable.ic_artist_big
    MediaLibraryItem.TYPE_GENRE -> R.drawable.ic_genre_big
    MediaLibraryItem.TYPE_MEDIA -> {
        val mw = mediaLibraryItem as MediaWrapper
        when (mw.type) {
            MediaWrapper.TYPE_VIDEO -> R.drawable.ic_video_big
            MediaWrapper.TYPE_DIR -> if (mw.uri.scheme == "file") R.drawable.ic_folder_big else R.drawable.ic_network_big
            MediaWrapper.TYPE_AUDIO -> R.drawable.ic_song_big
            else -> R.drawable.ic_unknown_big
        }
    }
    MediaLibraryItem.TYPE_DUMMY -> {
        when (mediaLibraryItem.id) {
            HEADER_VIDEO -> R.drawable.ic_video_big
            HEADER_PERMISSION -> R.drawable.ic_permission_big
            HEADER_DIRECTORIES -> R.drawable.ic_folder_big
            HEADER_NETWORK -> R.drawable.ic_network_big
            HEADER_SERVER -> R.drawable.ic_network_add_big
            HEADER_STREAM -> R.drawable.ic_stream_big
            HEADER_PLAYLISTS -> R.drawable.ic_playlist_big
            HEADER_MOVIES, CATEGORY_NOW_PLAYING_PIP -> R.drawable.ic_browser_movie_big
            HEADER_TV_SHOW -> R.drawable.ic_browser_tvshow_big
            HEADER_ADD_STREAM -> R.drawable.ic_stream_add
            ID_SETTINGS -> R.drawable.ic_settings_big
            ID_ABOUT_TV -> R.drawable.ic_default_cone
            ID_REMOTE_ACCESS -> R.drawable.ic_remote_access_big
            ID_SPONSOR -> R.drawable.ic_donate_big
            CATEGORY_ARTISTS -> R.drawable.ic_artist_big
            CATEGORY_ALBUMS -> R.drawable.ic_album_big
            CATEGORY_GENRES -> R.drawable.ic_genre_big
            CATEGORY_SONGS, CATEGORY_NOW_PLAYING -> R.drawable.ic_song_big
            else -> R.drawable.ic_unknown_big
        }
    }
    else -> R.drawable.ic_unknown_big
}

suspend fun fillActionMode(context: Context, mode: ActionMode, multiSelectHelper: MultiSelectHelper<MediaLibraryItem>) {
    var realCount = 0
    var length = 0L
    //checks if the selection can be retrieved (if the adapter is populated).
    // If not, we want to prevent changing the title to avoid flashing an invalid empty title
    var ready: Boolean
    withContext(Dispatchers.IO) {
        val selection = multiSelectHelper.getSelection()
        ready = selection.size == multiSelectHelper.getSelectionCount()
        selection.forEach { mediaItem ->
            when (mediaItem) {
                is MediaWrapper -> realCount += 1
                is Album -> realCount += mediaItem.realTracksCount
                is Artist -> realCount += mediaItem.tracksCount
                is VideoGroup -> realCount += mediaItem.mediaCount()
                is Folder -> realCount += mediaItem.mediaCount(Folder.TYPE_FOLDER_VIDEO)
            }
        }

        selection.forEach { mediaItem ->
            when (mediaItem) {
                is MediaWrapper -> length += mediaItem.length
                is Album -> mediaItem.getAll().forEach { length += it.length }
                is Artist -> mediaItem.getAll().forEach { length += it.length }
                is VideoGroup -> mediaItem.getAll().forEach { length += it.length }
                is Folder -> mediaItem.getAll().forEach { length += it.length }
            }
        }
    }
    if (ready) {
        mode.title = context.getString(R.string.selection_count, realCount)
        mode.subtitle = Tools.millisToString(length)
    }
}