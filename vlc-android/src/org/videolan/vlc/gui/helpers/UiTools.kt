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
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaRouter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.renderscript.*
import android.text.Html
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.RotateAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.tools.isStarted
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.BaseActivity
import org.videolan.vlc.gui.browser.MediaBrowserFragment
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.util.*
import java.util.Locale
import java.util.TreeMap
import kotlin.collections.ArrayList
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
object UiTools {
    private val TAG = "VLC/UiTools"
    private var DEFAULT_COVER_VIDEO_DRAWABLE: BitmapDrawable? = null
    private var DEFAULT_COVER_AUDIO_DRAWABLE: BitmapDrawable? = null
    private var DEFAULT_COVER_ALBUM_DRAWABLE: BitmapDrawable? = null
    private var DEFAULT_COVER_ARTIST_DRAWABLE: BitmapDrawable? = null

    private var DEFAULT_COVER_VIDEO_DRAWABLE_BIG: BitmapDrawable? = null
    private var DEFAULT_COVER_AUDIO_DRAWABLE_BIG: BitmapDrawable? = null
    private var DEFAULT_COVER_ALBUM_DRAWABLE_BIG: BitmapDrawable? = null
    private var DEFAULT_COVER_ARTIST_DRAWABLE_BIG: BitmapDrawable? = null

    private val sHandler = Handler(Looper.getMainLooper())
    const val DELETE_DURATION = 3000

    fun getDefaultVideoDrawable(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_VIDEO_DRAWABLE == null) {
            val DEFAULT_COVER_VIDEO = getBitmapFromDrawable(context, R.drawable.ic_no_thumbnail_1610)
            DEFAULT_COVER_VIDEO_DRAWABLE = BitmapDrawable(context.resources, DEFAULT_COVER_VIDEO)
        }
        return DEFAULT_COVER_VIDEO_DRAWABLE!!
    }

    fun getDefaultAudioDrawable(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_AUDIO_DRAWABLE == null) {
            val DEFAULT_COVER_AUDIO = getBitmapFromDrawable(context, R.drawable.ic_no_song)
            DEFAULT_COVER_AUDIO_DRAWABLE = BitmapDrawable(context.resources, DEFAULT_COVER_AUDIO)
        }
        return DEFAULT_COVER_AUDIO_DRAWABLE!!
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

    fun getDefaultVideoDrawableBig(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_VIDEO_DRAWABLE_BIG == null) {
            val DEFAULT_COVER_VIDEO = getBitmapFromDrawable(context, R.drawable.ic_browser_video_big_normal)
            DEFAULT_COVER_VIDEO_DRAWABLE_BIG = BitmapDrawable(context.resources, DEFAULT_COVER_VIDEO)
        }
        return DEFAULT_COVER_VIDEO_DRAWABLE_BIG!!
    }

    fun getDefaultAudioDrawableBig(context: Context): BitmapDrawable {
        if (DEFAULT_COVER_AUDIO_DRAWABLE_BIG == null) {
            val DEFAULT_COVER_AUDIO = getBitmapFromDrawable(context, R.drawable.ic_song_big)
            DEFAULT_COVER_AUDIO_DRAWABLE_BIG = BitmapDrawable(context.resources, DEFAULT_COVER_AUDIO)
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

    /**
     * Print an on-screen message to alert the user
     */
    fun snacker(view: View, stringId: Int) {
        Snackbar.make(view, stringId, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * Print an on-screen message to alert the user
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun snacker(view: View, message: String) {
        val snack = Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
        if (AndroidUtil.isLolliPopOrLater)
            snack.view.elevation = view.resources.getDimensionPixelSize(R.dimen.audio_player_elevation).toFloat()
        snack.show()
    }

    /**
     * Print an on-screen message to alert the user, with undo action
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun snackerConfirm(view: View, message: String, action: Runnable) {
        val snack = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                .setAction(android.R.string.ok) { action.run() }
        if (AndroidUtil.isLolliPopOrLater)
            snack.view.elevation = view.resources.getDimensionPixelSize(R.dimen.audio_player_elevation).toFloat()
        snack.show()
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun CoroutineScope.snackerConfirm(view: View, message: String, action: suspend() -> Unit) {
        val snack = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                .setAction(android.R.string.ok) { launch { action.invoke() } }
        if (AndroidUtil.isLolliPopOrLater)
            snack.view.elevation = view.resources.getDimensionPixelSize(R.dimen.audio_player_elevation).toFloat()
        snack.show()
    }


    /**
     * Print an on-screen message to alert the user, with undo action
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun snackerWithCancel(view: View, message: String, action: Runnable?, cancelAction: Runnable?) {
        @SuppressLint("WrongConstant") val snack = Snackbar.make(view, message, DELETE_DURATION)
                .setAction(android.R.string.cancel) {
                    if (action != null)
                        sHandler.removeCallbacks(action)
                    cancelAction?.run()
                }
        if (AndroidUtil.isLolliPopOrLater)
            snack.view.elevation = view.resources.getDimensionPixelSize(R.dimen.audio_player_elevation).toFloat()
        snack.show()
        if (action != null)
            sHandler.postDelayed(action, DELETE_DURATION.toLong())
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


    fun fillAboutView(v: View) {
        val link = v.findViewById<TextView>(R.id.main_link)
        link.text = Html.fromHtml(v.context.getString(R.string.about_link))

        val feedback : TextView= v.findViewById(R.id.feedback)
        feedback.text = Html.fromHtml(v.context.getString(R.string.feedback_link, v.context.getString(R.string.feedback_forum)))
        feedback.movementMethod = LinkMovementMethod.getInstance()

        val revision = v.context.getString(R.string.build_revision) + " VLC: " + v.context.getString(R.string.build_vlc_revision)
        val builddate = v.context.getString(R.string.build_time)
        val builder = v.context.getString(R.string.build_host)

        val compiled = v.findViewById<TextView>(R.id.main_compiled)
        compiled.text = "$builder ($builddate)"
        val textViewRev = v.findViewById<TextView>(R.id.main_revision)
        textViewRev.text = v.context.getString(R.string.revision) + " " + revision + " (" + builddate + ") "

        val logo = v.findViewById<ImageView>(R.id.logo)
        logo.setOnClickListener {
            val anim = AnimationSet(true)
            val rotate = RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
            rotate.duration = 800
            rotate.interpolator = DecelerateInterpolator()
            anim.addAnimation(rotate)
            logo.startAnimation(anim)
        }
    }

    fun setKeyboardVisibility(v: View?, show: Boolean) {
        if (v == null) return
        val inputMethodManager = v.context.applicationContext.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        sHandler.post {
            if (show)
                inputMethodManager.showSoftInput(v, InputMethodManager.SHOW_FORCED)
            else
                inputMethodManager.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    fun savePlaylist(activity: FragmentActivity, list: List<AbstractMediaWrapper>) {
        addToPlaylist(activity, list.toTypedArray(), SavePlaylistDialog.KEY_TRACKS)
    }

    fun addToPlaylist(activity: FragmentActivity, list: List<AbstractMediaWrapper>) {
        addToPlaylist(activity, list.toTypedArray(), SavePlaylistDialog.KEY_NEW_TRACKS)
    }

    fun addToPlaylist(activity: FragmentActivity, tracks: Array<AbstractMediaWrapper>, key: String) {
        if (!activity.isStarted()) return
        val savePlaylistDialog = SavePlaylistDialog()
        val args = Bundle()
        args.putParcelableArray(key, tracks)
        savePlaylistDialog.arguments = args
        savePlaylistDialog.show(activity.supportFragmentManager, "fragment_add_to_playlist")
    }


    fun getDefaultCover(context: Context, item: MediaLibraryItem): BitmapDrawable {
        return when (item.itemType) {
            MediaLibraryItem.TYPE_ARTIST -> getDefaultArtistDrawable(context)
            MediaLibraryItem.TYPE_ALBUM -> getDefaultAlbumDrawable(context)
            MediaLibraryItem.TYPE_MEDIA -> {
                if ((item as AbstractMediaWrapper).type == AbstractMediaWrapper.TYPE_VIDEO) getDefaultVideoDrawable(context) else getDefaultAudioDrawable(context)
            }
            else -> getDefaultAudioDrawable(context)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @JvmOverloads
    fun blurBitmap(bitmap: Bitmap?, radius: Float = 15.0f): Bitmap? {
        if (bitmap == null || bitmap.config == null) return null
        try {
            //Let's create an empty bitmap with the same size of the bitmap we want to blur
            val outBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

            //Instantiate a new Renderscript
            val rs = RenderScript.create(VLCApplication.appContext)

            //Create an Intrinsic Blur Script using the Renderscript
            val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))


            //Create the Allocations (in/out) with the Renderscript and the in/out bitmaps
            val allIn = Allocation.createFromBitmap(rs, bitmap)
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
        var item: MenuItem? = menu.findItem(R.id.ml_menu_sortby_name)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_ALPHA && !desc) R.string.sortby_name_desc else R.string.sortby_name)
        item = menu.findItem(R.id.ml_menu_sortby_filename)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_FILENAME && !desc) R.string.sortby_filename_desc else R.string.sortby_filename)
        item = menu.findItem(R.id.ml_menu_sortby_artist_name)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_ARTIST && !desc) R.string.sortby_artist_name_desc else R.string.sortby_artist_name)
        item = menu.findItem(R.id.ml_menu_sortby_album_name)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_ALBUM && !desc) R.string.sortby_album_name_desc else R.string.sortby_album_name)
        item = menu.findItem(R.id.ml_menu_sortby_length)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_DURATION && !desc) R.string.sortby_length_desc else R.string.sortby_length)
        item = menu.findItem(R.id.ml_menu_sortby_date)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_RELEASEDATE && !desc) R.string.sortby_date_desc else R.string.sortby_date)
        item = menu.findItem(R.id.ml_menu_sortby_last_modified)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_RELEASEDATE && !desc) R.string.sortby_last_modified_date_desc else R.string.sortby_last_modified_date)
        //        item = menu.findItem(R.id.ml_menu_sortby_number); TODO sort by track number
        //        if (item != null) item.setTitle(sort == AbstractMedialibrary.SORT_ && !desc ? R.string.sortby_number_desc : R.string.sortby_number);

    }

    fun updateSortTitles(sortable: MediaBrowserFragment<*>) {
        val menu = sortable.menu ?: return
        val model = sortable.viewModel
        val sort = model.sort
        val desc = model.desc
        var item: MenuItem? = menu.findItem(R.id.ml_menu_sortby_name)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_ALPHA && !desc) R.string.sortby_name_desc else R.string.sortby_name)
        item = menu.findItem(R.id.ml_menu_sortby_filename)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_FILENAME && !desc) R.string.sortby_filename_desc else R.string.sortby_filename)
        item = menu.findItem(R.id.ml_menu_sortby_artist_name)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_ARTIST && !desc) R.string.sortby_artist_name_desc else R.string.sortby_artist_name)
        item = menu.findItem(R.id.ml_menu_sortby_album_name)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_ALBUM && !desc) R.string.sortby_album_name_desc else R.string.sortby_album_name)
        item = menu.findItem(R.id.ml_menu_sortby_length)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_DURATION && !desc) R.string.sortby_length_desc else R.string.sortby_length)
        item = menu.findItem(R.id.ml_menu_sortby_date)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_RELEASEDATE && !desc) R.string.sortby_date_desc else R.string.sortby_date)
        item = menu.findItem(R.id.ml_menu_sortby_last_modified)
        item?.setTitle(if (sort == AbstractMedialibrary.SORT_RELEASEDATE && !desc) R.string.sortby_last_modified_date_desc else R.string.sortby_last_modified_date)
        //        item = menu.findItem(R.id.ml_menu_sortby_number); TODO sort by track number
        //        if (item != null) item.setTitle(sort == AbstractMedialibrary.SORT_ && !desc ? R.string.sortby_number_desc : R.string.sortby_number);

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
                        ContextCompat.startForegroundService(activity, si)
                    }
                    .setNegativeButton(R.string.ml_external_storage_decline) { dialog, _ -> dialog.dismiss() }
            builder.show()
        } else {
            val builder = android.app.AlertDialog.Builder(activity)
                    .setTitle(R.string.ml_external_storage_title)
                    .setCancelable(false)
                    .setMessage(message)
                    .setPositiveButton(R.string.ml_external_storage_accept) { _, _ ->
                        ContextCompat.startForegroundService(activity, si)
                    }
                    .setNegativeButton(R.string.ml_external_storage_decline) { dialog, _ -> dialog.dismiss() }
            builder.show()
        }
    }

    fun setOnDragListener(activity: Activity) {
        val view = if (AndroidUtil.isNougatOrLater) activity.window.peekDecorView() else null
        view?.setOnDragListener(View.OnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DROP -> {
                    val clipData = event.clipData ?: return@OnDragListener false
                    val itemsCount = clipData.itemCount
                    for (i in 0 until itemsCount) {
                        val permissions = activity.requestDragAndDropPermissions(event)
                        if (permissions != null) {
                            val item = clipData.getItemAt(i)
                            if (item.uri != null)
                                MediaUtils.openUri(activity, item.uri)
                            else if (item.text != null) {
                                val uri = Uri.parse(item.text.toString())
                                val media = MLServiceLocator.getAbstractMediaWrapper(uri)
                                if ("file" != uri.scheme)
                                    media.type = AbstractMediaWrapper.TYPE_STREAM
                                MediaUtils.openMedia(activity, media)
                            }
                            return@OnDragListener true
                        }
                    }
                    false
                }
                else -> false
            }
        })
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun setRotationAnimation(activity: Activity) {
        if (!AndroidUtil.isJellyBeanMR2OrLater) return
        val win = activity.window
        val winParams = win.attributes
        winParams.rotationAnimation = if (AndroidUtil.isOOrLater) WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS else WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT
        win.attributes = winParams
    }

    fun setLocale(context: Context) {
        // Are we using advanced debugging - locale?
        val p = VLCApplication.locale
        if (p != "") {
            val locale = getLocaleFromString(p!!)
            Locale.setDefault(locale)
            val config = Configuration()
            config.locale = locale
            context.resources.updateConfiguration(config,
                    context.resources.displayMetrics)
        }
    }

    fun restartDialog(context: Context) {
        AlertDialog.Builder(context)
                .setTitle(context.resources.getString(R.string.restart_vlc))
                .setMessage(context.resources.getString(R.string.restart_message))
                .setPositiveButton(R.string.restart_message_OK) { _, _ -> android.os.Process.killProcess(android.os.Process.myPid()) }
                .setNegativeButton(R.string.restart_message_Later, null)
                .create()
                .show()
    }

    fun getLocalesUsedInProject(context: Context): LocalePair {
        val localesEntryValues = BuildConfig.TRANSLATION_ARRAY


        val localesEntry = arrayOfNulls<String>(localesEntryValues.size)
        for (i in localesEntryValues.indices) {

            val localesEntryValue = localesEntryValues[i]

            val locale = getLocaleFromString(localesEntryValue)

            val displayLanguage = locale.getDisplayLanguage(locale)
            val displayCountry = locale.getDisplayCountry(locale)
            if (displayCountry.isEmpty()) {
                localesEntry[i] = firstLetterUpper(displayLanguage)
            } else {
                localesEntry[i] = firstLetterUpper(displayLanguage) + " - " + firstLetterUpper(displayCountry)
            }
        }

        //sort
        val localeTreeMap = TreeMap<String, String>()
        for (i in localesEntryValues.indices) {
            localeTreeMap[localesEntry[i]!!] = localesEntryValues[i]
        }


        val finalLocaleEntries = ArrayList<String>(localeTreeMap.size + 1).apply { add(0, context.getString(R.string.device_default)) }
        val finalLocaleEntryValues = ArrayList<String>(localeTreeMap.size + 1).apply { add(0, "") }

        var i = 1
        for ((key, value) in localeTreeMap) {
            finalLocaleEntries.add(i, key)
            finalLocaleEntryValues.add(i, value)
            i++
        }

        return LocalePair(finalLocaleEntries.toTypedArray(), finalLocaleEntryValues.toTypedArray())
    }


    private fun getLocaleFromString(string: String): Locale {

        /**
         * See [android.content.res.AssetManager.getLocales]
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Locale.forLanguageTag(string)
        }

        //Best effort on determining the locale

        val separators = arrayOf("_", "-")

        for (separator in separators) {
            //see if there is a language and a country
            if (string.contains(separator)) {
                val splittedLocale = string.split(separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (splittedLocale.size == 2) {
                    return Locale(splittedLocale[0], splittedLocale[1])
                }
            }
        }


        return Locale(string)
    }

    private fun firstLetterUpper(string: String?): String? {
        if (string == null) {
            return null
        }
        if (string.isEmpty()) {
            return ""
        }
        return if (string.length == 1) {
            string.toUpperCase(Locale.getDefault())
        } else Character.toUpperCase(string[0]) + string.substring(1).toLowerCase(Locale.getDefault())

    }

    fun deleteSubtitleDialog(context: Context, positiveListener: DialogInterface.OnClickListener, negativeListener: DialogInterface.OnClickListener) {
        AlertDialog.Builder(context)
                .setTitle(context.resources.getString(R.string.delete_sub_title))
                .setMessage(context.resources.getString(R.string.delete_sub_message))
                .setPositiveButton(R.string.delete_sub_yes, positiveListener)
                .setNegativeButton(R.string.delete_sub_no, negativeListener)
                .create()
                .show()
    }

    fun hasSecondaryDisplay(context: Context): Boolean {
        val mediaRouter = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as MediaRouter
        val route = mediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO)
        val presentationDisplay = route?.presentationDisplay
        return presentationDisplay != null
    }


}

/**
 * Set the alignment mode of the specified TextView with the desired align
 * mode from preferences.
 *
 *
 * See @array/audio_title_alignment_values
 *
 * @param alignMode Align mode as read from preferences
 * @param t         Reference to the textview
 */
@BindingAdapter("alignMode")
fun setAlignModeByPref(t: TextView, alignMode: Int) {
    when (alignMode) {
        0 -> {
        }
        1 -> t.ellipsize = TextUtils.TruncateAt.END
        2 -> t.ellipsize = TextUtils.TruncateAt.START
        3 -> {
            t.ellipsize = TextUtils.TruncateAt.MARQUEE
            t.marqueeRepeatLimit = -1
            t.isSelected = true
        }
    }
}

/**
 * sets the touch listener for a view
 *
 * @param view            the view
 * @param onTouchListener the listener
 */
@BindingAdapter("touchListener")
fun setTouchListener(view: View, onTouchListener: View.OnTouchListener?) {
    if (onTouchListener != null)
        view.setOnTouchListener(onTouchListener)
}

@BindingAdapter("selected")
fun isSelected(v: View, isSelected: Boolean?) {
    v.isSelected = isSelected!!
}

fun BaseActivity.applyTheme() {
    if (Settings.showTvUi) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setTheme(R.style.Theme_VLC_Black)
        return
    }
    if (settings.contains(KEY_APP_THEME)) {
        AppCompatDelegate.setDefaultNightMode(Integer.valueOf(settings.getString(KEY_APP_THEME, "-1")!!))
    } else if (settings.contains(KEY_DAYNIGHT) || settings.contains(KEY_BLACK_THEME)) { // legacy support
        val daynight = settings.getBoolean(KEY_DAYNIGHT, false)
        val dark = settings.getBoolean(KEY_BLACK_THEME, false)
        val mode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else if (daynight) AppCompatDelegate.MODE_NIGHT_AUTO else AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
