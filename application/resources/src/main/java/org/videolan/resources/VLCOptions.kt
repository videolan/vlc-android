/*****************************************************************************
 * VLCOptions.java
 *
 * Copyright © 2015 VLC authors and VideoLAN
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

package org.videolan.resources

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.libvlc.util.HWDecoderUtil
import org.videolan.libvlc.util.VLCUtil
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.Preferences
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.isVLC4
import java.io.File
import java.util.*

object VLCOptions {
    private const val TAG = "VLC/VLCConfig"

    private const val AOUT_AAUDIO = 0
    private const val AOUT_AUDIOTRACK = 1
    private const val AOUT_OPENSLES = 2

    private const val HW_ACCELERATION_AUTOMATIC = -1
    private const val HW_ACCELERATION_DISABLED = 0
    private const val HW_ACCELERATION_DECODING = 1
    private const val HW_ACCELERATION_FULL = 2

    var audiotrackSessionId = 0
        private set

    // TODO should return List<String>
    /* generate an audio session id so as to share audio output with external equalizer *//* CPU intensive plugin, setting for slow devices *//* XXX: why can't the default be fine ? #7792 *//* Configure keystore *///Chromecast
    val libOptions: ArrayList<String>
        get() {
            val context = AppContextProvider.appContext
            val pref = Settings.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && audiotrackSessionId == 0) {
                val audioManager = context.getSystemService<AudioManager>()!!
                audiotrackSessionId = audioManager.generateAudioSessionId()
            }

            val options = ArrayList<String>(50)

            val timeStrechingDefault = context.resources.getBoolean(R.bool.time_stretching_default)
            val timeStreching = pref.getBoolean("enable_time_stretching_audio", timeStrechingDefault)
            val subtitlesEncoding = pref.getString("subtitle_text_encoding", "") ?: ""
            val frameSkip = pref.getBoolean("enable_frame_skip", false)
            val verboseMode = pref.getBoolean("enable_verbose_mode", true)

            var deblocking = -1
            try {
                deblocking = getDeblocking(Integer.parseInt(pref.getString("deblocking", "-1")!!))
            } catch (ignored: NumberFormatException) {
            }

            val networkCaching = pref.getInt("network_caching_value", 0).coerceIn(0, 60000)
            val freetypeRelFontsize = pref.getString("subtitles_size", "16")
            val freetypeBold = pref.getBoolean("subtitles_bold", false)

            val freetypeColor = Integer.decode(String.format("0x%06X", (0xFFFFFF and pref.getInt("subtitles_color", 16777215))))
            val freetypeColorOpacity = pref.getInt("subtitles_color_opacity", 255)

            val freetypeBackgroundColor = Integer.decode(String.format("0x%06X", (0xFFFFFF and pref.getInt("subtitles_background_color", 16777215))))
            val freetypeBackgroundColorOpacity = pref.getInt("subtitles_background_color_opacity", 255)
            val freetypeBackgroundEnabled = pref.getBoolean("subtitles_background", false)

            val freetypeOutlineEnabled = pref.getBoolean("subtitles_outline", true)
            val freetypeOutlineSize = pref.getString("subtitles_outline_size", "4")
            val freetypeOutlineColor = Integer.decode(String.format("0x%06X", (0xFFFFFF and pref.getInt("subtitles_outline_color", 0))))
            val freetypeOutlineOpacity = pref.getInt("subtitles_outline_color_opacity", 255)


            val freetypeShadowEnabled = pref.getBoolean("subtitles_shadow", true)
            val freetypeShadowColor = Integer.decode(String.format("0x%06X", (0xFFFFFF and pref.getInt("subtitles_shadow_color", ContextCompat.getColor(context, R.color.black)))))
            val freetypeShadowOpacity = pref.getInt("subtitles_shadow_color_opacity", 128)


            val opengl = Integer.parseInt(pref.getString("opengl", "-1")!!)
            options.add(if (timeStreching) "--audio-time-stretch" else "--no-audio-time-stretch")
            options.add("--avcodec-skiploopfilter")
            options.add("" + deblocking)
            options.add("--avcodec-skip-frame")
            options.add(if (frameSkip) "2" else "0")
            options.add("--avcodec-skip-idct")
            options.add(if (frameSkip) "2" else "0")
            options.add("--subsdec-encoding")
            options.add(subtitlesEncoding)
            options.add("--stats")
            if (networkCaching > 0) options.add("--network-caching=$networkCaching")
            options.add("--audio-resampler")
            options.add("soxr")
            options.add("--audiotrack-session-id=$audiotrackSessionId")

            if (isVLC4()) options.add("--sub-text-scale=" + (1600 / freetypeRelFontsize!!.toFloat()).toString()) else options.add("--freetype-rel-fontsize=" + freetypeRelFontsize!!)
            if (freetypeBold) options.add("--freetype-bold")
            options.add("--freetype-color=$freetypeColor")
            options.add("--freetype-opacity=$freetypeColorOpacity")
            if (freetypeBackgroundEnabled) {
                options.add("--freetype-background-color=$freetypeBackgroundColor")
                options.add("--freetype-background-opacity=$freetypeBackgroundColorOpacity")
            } else options.add("--freetype-background-opacity=0")

            if (freetypeShadowEnabled) {
                options.add("--freetype-shadow-color=$freetypeShadowColor")
                options.add("--freetype-shadow-opacity=$freetypeShadowOpacity")
            } else options.add("--freetype-shadow-opacity=0")

            if (freetypeOutlineEnabled) {
                options.add("--freetype-outline-thickness=$freetypeOutlineSize")
                    options.add("--freetype-outline-color=$freetypeOutlineColor")
                    options.add("--freetype-outline-opacity=$freetypeOutlineOpacity")
            } else options.add("--freetype-outline-opacity=0")


            if (opengl == 1) options.add("--vout=gles2,none")
            else if (opengl == 0) options.add("--vout=android_display,none")
            options.add("--keystore")
            options.add(if (AndroidUtil.isMarshMallowOrLater) "file_crypt,none" else "file_plaintext,none")
            options.add("--keystore-file")
            options.add(File(context.getDir("keystore", Context.MODE_PRIVATE), "file").absolutePath)
            options.add(if (verboseMode) "-vv" else "-v")
            // fixme comment temporarily
            if (!isVLC4()) {
                if (pref.getBoolean("casting_passthrough", false))
                    options.add("--sout-chromecast-audio-passthrough")
                else
                    options.add("--no-sout-chromecast-audio-passthrough")
                options.add("--sout-chromecast-conversion-quality=" + pref.getString("casting_quality", "2")!!)
            }
            options.add("--sout-keep")

            val customOptions = pref.getString("custom_libvlc_options", null)
            if (!customOptions.isNullOrEmpty()) {
                val optionsArray = customOptions.split("\\r?\\n".toRegex()).toTypedArray()
                if (!optionsArray.isNullOrEmpty()) Collections.addAll(options, *optionsArray)
            }
            if (pref.getBoolean("prefer_smbv1", true))
                options.add("--smb-force-v1")
            if (!Settings.showTvUi) {
                //Ambisonic
                val hstfDir = context.getDir("vlc", Context.MODE_PRIVATE)
                val hstfPath = "${hstfDir.absolutePath}/.share/hrtfs/dodeca_and_7channel_3DSL_HRTF.sofa"
                options.add("--spatialaudio-headphones")
                options.add("--hrtf-file")
                options.add(hstfPath)
            }
            if (pref.getBoolean("audio-replay-gain-enable", false)) {
                options.add("--audio-replay-gain-mode=${pref.getString("audio-replay-gain-mode", "track")}")
                options.add("--audio-replay-gain-preamp=${pref.getString("audio-replay-gain-preamp", "0.0")}")
                options.add("--audio-replay-gain-default=${pref.getString("audio-replay-gain-default", "-7.0")}")
                if (pref.getBoolean("audio-replay-gain-peak-protection", true))
                    options.add("--audio-replay-gain-peak-protection")
                else
                    options.add("--no-audio-replay-gain-peak-protection")
            }
            val soundFontFile = getSoundFontFile(context)
            if (soundFontFile.exists()) {
                options.add("--soundfont=${soundFontFile.path}")
            }
            options.add("--preferred-resolution=${pref.getString("preferred_resolution", "-1")!!}")
            if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "VLC Options: ${options.joinToString(" ")}")
            return options
        }

    fun isAudioDigitalOutputEnabled(pref: SharedPreferences) = pref.getBoolean("audio_digital_output", false)

    fun setAudioDigitalOutputEnabled(pref: SharedPreferences, enabled: Boolean) {
        pref.putSingle("audio_digital_output", enabled)
    }

    fun getAout(pref: SharedPreferences): String? {
        var aout = -1
        try {
            aout = Integer.parseInt(pref.getString("aout", "-1")!!)
        } catch (ignored: NumberFormatException) {
        }

        val hwaout = HWDecoderUtil.getAudioOutputFromDevice()
        if (hwaout == HWDecoderUtil.AudioOutput.OPENSLES)
            aout = AOUT_OPENSLES

        return if (aout == AOUT_OPENSLES) "opensles" else if (aout == AOUT_AUDIOTRACK) "audiotrack" else null /* aaudio is the default */
    }

    private fun getDeblocking(deblocking: Int): Int {
        var ret = deblocking
        if (deblocking < 0) {
            /**
             * Set some reasonable deblocking defaults:
             *
             * Skip all (4) for armv6 and MIPS by default
             * Skip non-ref (1) for all armv7 more than 1.2 Ghz and more than 2 cores
             * Skip non-key (3) for all devices that don't meet anything above
             */
            val m = VLCUtil.getMachineSpecs() ?: return ret
            if (m.hasArmV6 && !m.hasArmV7 || m.hasMips)
                ret = 4
            else if (m.frequency >= 1200 && m.processors > 2)
                ret = 1
            else if (m.bogoMIPS >= 1200 && m.processors > 2) {
                ret = 1
                Log.d(TAG, "Used bogoMIPS due to lack of frequency info")
            } else
                ret = 3
        } else if (deblocking > 4) { // sanity check
            ret = 3
        }
        return ret
    }

    fun setMediaOptions(media: IMedia, context: Context, flags: Int, hasRenderer: Boolean) {
        val noHardwareAcceleration = flags and MediaWrapper.MEDIA_NO_HWACCEL != 0
        val noVideo = flags and MediaWrapper.MEDIA_VIDEO == 0
        val paused = flags and MediaWrapper.MEDIA_PAUSED != 0
        var hardwareAcceleration = HW_ACCELERATION_DISABLED
        val prefs = Settings.getInstance(context)

        if (!noHardwareAcceleration) {
            try {
                hardwareAcceleration = Integer.parseInt(prefs.getString("hardware_acceleration", "$HW_ACCELERATION_AUTOMATIC")!!)
            } catch (ignored: NumberFormatException) {}

        }
        if (hardwareAcceleration == HW_ACCELERATION_DISABLED)
            media.setHWDecoderEnabled(false, false)
        else if (hardwareAcceleration == HW_ACCELERATION_FULL || hardwareAcceleration == HW_ACCELERATION_DECODING) {
            media.setHWDecoderEnabled(true, true)
            if (hardwareAcceleration == HW_ACCELERATION_DECODING) {
                media.addOption(":no-mediacodec-dr")
                media.addOption(":no-omxil-dr")
            }
        } /* else automatic: use default options */

        if (noVideo) media.addOption(":no-video")
        if (paused) media.addOption(":start-paused")
        if (!prefs.getBoolean("subtitles_autoload", true)) media.addOption(":sub-language=none")

        if (hasRenderer) {
            media.addOption(":sout-chromecast-audio-passthrough=" + prefs.getBoolean("casting_passthrough", true))
            media.addOption(":sout-chromecast-conversion-quality=" + prefs.getString("casting_quality", "2")!!)
        }
    }

    private fun getEqualizerSetFromSettings(pref: SharedPreferences): MediaPlayer.Equalizer? {
        val bands = Preferences.getFloatArray(pref, "equalizer_values")
        if (bands != null && pref.contains("equalizer_enabled")) {
            val bandCount = MediaPlayer.Equalizer.getBandCount()
            if (bands.size != bandCount + 1)
                return null

            val eq = MediaPlayer.Equalizer.create()
            eq.preAmp = bands[0]
            for (i in 0 until bandCount)
                eq.setAmp(i, bands[i + 1])
            return eq
        } else
            return MediaPlayer.Equalizer.createFromPreset(0)
    }

    @MainThread
    @JvmOverloads
    fun getEqualizerSetFromSettings(context: Context, force: Boolean = false): MediaPlayer.Equalizer? {
        val pref = Settings.getInstance(context)
        return if (!force && !pref.getBoolean("equalizer_enabled", false)) null else getEqualizerSetFromSettings(pref)
    }

    @MainThread
    fun getEqualizerNameFromSettings(context: Context): String? {
        val pref = Settings.getInstance(context)
        return pref.getString("equalizer_set", "Flat")
    }

    @MainThread
    fun saveEqualizerInSettings(context: Context, eq: MediaPlayer.Equalizer?, name: String, enabled: Boolean, saved: Boolean) {
        Settings.getInstance(context).edit {
            if (eq != null) {
                putBoolean("equalizer_enabled", enabled)
                val bandCount = MediaPlayer.Equalizer.getBandCount()
                val bands = FloatArray(bandCount + 1)
                bands[0] = eq.preAmp
                for (i in 0 until bandCount) {
                    bands[i + 1] = eq.getAmp(i)
                }
                Preferences.putFloatArray(this, "equalizer_values", bands)
                putString("equalizer_set", name)
            } else {
                putBoolean("equalizer_enabled", false)
            }
            putBoolean("equalizer_saved", saved)
        }
    }

    @MainThread
    fun getCustomSet(context: Context, customName: String): MediaPlayer.Equalizer? {
        try {
            val pref = Settings.getInstance(context)
            val key = "custom_equalizer_" + customName.replace(" ", "_")
            val bands = Preferences.getFloatArray(pref, key)
            val bandCount = MediaPlayer.Equalizer.getBandCount()
            if (bands!!.size != bandCount + 1)
                return null

            val eq = MediaPlayer.Equalizer.create()
            eq.preAmp = bands[0]
            for (i in 0 until bandCount)
                eq.setAmp(i, bands[i + 1])
            return eq
        } catch (e: Exception) {
            return MediaPlayer.Equalizer.createFromPreset(0)
        }

    }

    @MainThread
    fun saveCustomSet(context: Context, eq: MediaPlayer.Equalizer, customName: String) {
        Settings.getInstance(context).edit {
            val formattedName = customName.replace(" ", "_")
            val key = "custom_equalizer_$formattedName"
            val bandCount = MediaPlayer.Equalizer.getBandCount()
            val bands = FloatArray(bandCount + 1)
            bands[0] = eq.preAmp
            for (i in 0 until bandCount) {
                bands[i + 1] = eq.getAmp(i)
            }
            Preferences.putFloatArray(this, key, bands)
        }
    }

    @MainThread
    fun deleteCustomSet(context: Context, customName: String) {
        Settings.getInstance(context).edit {
            remove("custom_equalizer_" + customName.replace(" ", "_"))
        }
    }

    fun getEqualizerSavedState(context: Context): Boolean {
        return Settings.getInstance(context).getBoolean("equalizer_saved", true)
    }

    fun getEqualizerEnabledState(context: Context): Boolean {
        return Settings.getInstance(context).getBoolean("equalizer_enabled", false)
    }

    fun getSoundFontFile(context: Context)= File( context.getDir("soundfont", Context.MODE_PRIVATE)!!.path+"/soundfont.sf2")
}
