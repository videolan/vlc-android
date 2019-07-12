package org.videolan.vlc.gui.helpers

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ViewStubCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.OnLifecycleEvent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PlayerOptionItemBinding
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.audio.EqualizerFragment
import org.videolan.vlc.gui.dialogs.*
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.interfaces.IPlaybackSettingsController
import org.videolan.vlc.media.ABRepeat
import org.videolan.vlc.util.*
import java.util.*

private const val ACTION_AUDIO_DELAY = 2
private const val ACTION_SPU_DELAY = 3

private const val ID_PLAY_AS_AUDIO = 0
private const val ID_SLEEP = 1
private const val ID_JUMP_TO = 2
private const val ID_AUDIO_DELAY = 3
private const val ID_SPU_DELAY = 4
private const val ID_CHAPTER_TITLE = 5
private const val ID_PLAYBACK_SPEED = 6
private const val ID_EQUALIZER = 7
private const val ID_SAVE_PLAYLIST = 8
private const val ID_POPUP_VIDEO = 9
private const val ID_REPEAT = 10
private const val ID_SHUFFLE = 11
private const val ID_PASSTHROUGH = 12
private const val ID_ABREPEAT = 13
private const val ID_OVERLAY_SIZE = 14

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@SuppressLint("ShowToast")
class PlayerOptionsDelegate(val activity: AppCompatActivity, val service: PlaybackService) : LifecycleObserver {

    private lateinit var recyclerview: RecyclerView
    private lateinit var rootView: FrameLayout
    private lateinit var playerOptionType: PlayerOptionType
    var flags: Int = 0
    private val toast by lazy(LazyThreadSafetyMode.NONE) { Toast.makeText(activity, "", Toast.LENGTH_SHORT) }

    private val primary = activity is VideoPlayerActivity && activity.displayManager.isPrimary
    private val video = activity is VideoPlayerActivity
    private val res = activity.resources
    private val settings = Settings.getInstance(activity)
    private lateinit var abrBinding: PlayerOptionItemBinding
    private lateinit var ptBinding: PlayerOptionItemBinding
    private lateinit var repeatBinding: PlayerOptionItemBinding
    private lateinit var shuffleBinding: PlayerOptionItemBinding
    private lateinit var sleepBinding: PlayerOptionItemBinding

    private val abrObs = Observer<ABRepeat> { abr ->
        if (abr == null || !this::abrBinding.isInitialized) return@Observer
        val resid = when {
            abr.start == -1L -> R.attr.ic_abrepeat_seta
            abr.stop == -1L -> R.attr.ic_abrepeat_setb
            else -> R.attr.ic_abrepeat_reset
        }
        abrBinding.optionIcon.setImageResource(UiTools.getResourceFromAttribute(activity, resid))
    }

    init {
        service.playlistManager.abRepeat.observe(activity, abrObs)
    }

    fun setup() {
        if (!this::recyclerview.isInitialized || service.playlistManager.player.playbackState == PlaybackStateCompat.STATE_STOPPED) return
        val options = mutableListOf<PlayerOption>()
        when (playerOptionType) {
            PlayerOptionType.ADVANCED -> {
                options.add(PlayerOption(playerOptionType, ID_SLEEP, R.attr.ic_sleep_normal_style, res.getString(R.string.sleep_title)))
                if (service.isSeekable) {
                    options.add(PlayerOption(playerOptionType, ID_PLAYBACK_SPEED, R.attr.ic_speed_normal_style, res.getString(R.string.playback_speed)))
                }
                options.add(PlayerOption(playerOptionType, ID_JUMP_TO, R.attr.ic_jumpto_normal_style, res.getString(R.string.jump_to_time)))
                options.add(PlayerOption(playerOptionType, ID_EQUALIZER, R.attr.ic_equalizer_normal_style, res.getString(R.string.equalizer)))
                if (video) {
                    if (primary && !Settings.showTvUi && service.audioTracksCount > 0)
                        options.add(PlayerOption(playerOptionType, ID_PLAY_AS_AUDIO, R.attr.ic_playasaudio_on, res.getString(R.string.play_as_audio)))
                    if (primary && AndroidDevices.pipAllowed && !AndroidDevices.isDex(activity))
                        options.add(PlayerOption(playerOptionType, ID_POPUP_VIDEO, R.attr.ic_popup_dim, res.getString(R.string.ctx_pip_title)))
                    if (primary)
                        options.add(PlayerOption(playerOptionType, ID_OVERLAY_SIZE, R.attr.ic_crop_player, res.getString(R.string.resize)))
                    options.add(PlayerOption(playerOptionType, ID_REPEAT, R.drawable.ic_repeat, res.getString(R.string.repeat_title)))
                    if (service.canShuffle()) options.add(PlayerOption(playerOptionType, ID_SHUFFLE, R.drawable.ic_shuffle, res.getString(R.string.shuffle_title)))
                    val chaptersCount = service.getChapters(-1)?.size ?: 0
                    if (chaptersCount > 1) options.add(PlayerOption(playerOptionType, ID_CHAPTER_TITLE, R.attr.ic_chapter_normal_style, res.getString(R.string.go_to_chapter)))
                }
                options.add(PlayerOption(playerOptionType, ID_ABREPEAT, R.attr.ic_abrepeat, res.getString(R.string.ab_repeat)))
                options.add(PlayerOption(playerOptionType, ID_SAVE_PLAYLIST, R.attr.ic_save, res.getString(R.string.playlist_save)))
                if (service.playlistManager.player.canDoPassthrough() && settings.getString("aout", "0") == "0")
                    options.add(PlayerOption(playerOptionType, ID_PASSTHROUGH, R.attr.ic_passthrough, res.getString(R.string.audio_digital_title)))
            }
            PlayerOptionType.MEDIA_TRACKS -> {
                if (flags and CTX_VIDEO_TRACK != 0) options.add(PlayerOption(playerOptionType, CTX_VIDEO_TRACK, R.drawable.ic_video_track_w, res.getString(R.string.ctx_player_video_track)))
                if (flags and CTX_AUDIO_TRACK != 0) {
                    options.add(PlayerOption(playerOptionType, CTX_AUDIO_TRACK, R.drawable.ic_audiotrack_w, res.getString(R.string.ctx_player_audio_track)))
                    options.add(PlayerOption(playerOptionType, ID_AUDIO_DELAY, R.drawable.ic_audiodelay_w, res.getString(R.string.audio_delay)))
                }
                if (flags and CTX_SUBS_TRACK != 0) {
                    options.add(PlayerOption(playerOptionType, CTX_SUBS_TRACK, R.drawable.ic_subtitle_w, res.getString(R.string.ctx_player_subs_track)))
                    options.add(PlayerOption(playerOptionType, ID_SPU_DELAY, R.drawable.ic_subtitledelay_w, res.getString(R.string.spu_delay)))
                }
                if (flags and CTX_PICK_SUBS != 0) options.add(PlayerOption(playerOptionType, CTX_PICK_SUBS, R.drawable.ic_subtitle_open_w, res.getString(R.string.subtitle_select)))
                if (flags and CTX_DOWNLOAD_SUBTITLES_PLAYER != 0) options.add(PlayerOption(playerOptionType, CTX_DOWNLOAD_SUBTITLES_PLAYER, R.drawable.ic_downsub_w, res.getString(R.string.download_subtitles)))
            }
        }
        (recyclerview.adapter as OptionsAdapter).update(options)
    }

    fun show(type: PlayerOptionType) {
        this.playerOptionType = type
        activity.findViewById<ViewStubCompat>(R.id.player_options_stub)?.let {
            rootView = it.inflate() as FrameLayout
            recyclerview = rootView.findViewById(R.id.options_list)
            service.lifecycle.addObserver(this)
            activity.lifecycle.addObserver(this)
            if (recyclerview.layoutManager == null) recyclerview.layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
            recyclerview.adapter = OptionsAdapter()
            recyclerview.itemAnimator = null

            rootView.setOnClickListener { hide() }
        }
        setup()
        rootView.visibility = View.VISIBLE
        if (Settings.showTvUi) AppScope.launch {
            delay(100L)
            val position = (recyclerview.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            (recyclerview.layoutManager as LinearLayoutManager).findViewByPosition(position)?.requestFocus()
        }
    }

    fun hide() {
        rootView.visibility = View.GONE
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun release() {
        service.playlistManager.abRepeat.removeObserver(abrObs)
    }

    fun onClick(option: PlayerOption) {
        when (option.type) {
            PlayerOptionType.ADVANCED -> {
                when (option.id) {
                    ID_SLEEP -> {
                        showFragment(ID_SLEEP)
                    }
                    ID_PLAY_AS_AUDIO -> (activity as VideoPlayerActivity).switchToAudioMode(true)
                    ID_POPUP_VIDEO -> {
                        (activity as VideoPlayerActivity).switchToPopup()
                        hide()
                    }
                    ID_OVERLAY_SIZE -> {
                        (activity as VideoPlayerActivity).resizeVideo()
                    }
                    ID_REPEAT -> setRepeatMode()
                    ID_SHUFFLE -> {
                        service.shuffle()
                        setShuffle()
                    }
                    ID_PASSTHROUGH -> togglePassthrough()
                    ID_ABREPEAT -> service.playlistManager.toggleABRepeat()
                    else -> showFragment(option.id)
                }
            }
            PlayerOptionType.MEDIA_TRACKS -> {
                if (service.currentMediaWrapper == null) return
                (activity as VideoPlayerActivity).run {
                    when (option.id) {
                        ID_AUDIO_DELAY -> showValueControls(ACTION_AUDIO_DELAY)
                        ID_SPU_DELAY -> showValueControls(ACTION_SPU_DELAY)
                        CTX_VIDEO_TRACK -> selectVideoTrack()
                        CTX_AUDIO_TRACK -> selectAudioTrack()
                        CTX_SUBS_TRACK -> selectSubtitles()
                        CTX_PICK_SUBS -> pickSubtitles()
                        CTX_DOWNLOAD_SUBTITLES_PLAYER -> downloadSubtitles()
                        else -> Unit
                    }
                }
                hide()
            }
        }
    }

    private fun showFragment(id: Int) {
        val newFragment: DialogFragment
        val tag: String
        when (id) {
            ID_PLAYBACK_SPEED -> {
                newFragment = PlaybackSpeedDialog.newInstance()
                tag = "playback_speed"
            }
            ID_JUMP_TO -> {
                newFragment = JumpToTimeDialog.newInstance()
                tag = "time"
            }
            ID_SLEEP -> {
                newFragment = SleepTimerDialog.newInstance()
                tag = "time"
            }
            ID_CHAPTER_TITLE -> {
                newFragment = SelectChapterDialog.newInstance()
                tag = "select_chapter"
            }
            ID_EQUALIZER -> {
                newFragment = EqualizerFragment()
                tag = "equalizer"
            }
            ID_SAVE_PLAYLIST -> {
                UiTools.addToPlaylist(activity, service.media)
                hide()
                return
            }
            else -> return
        }
        if (newFragment is VLCBottomSheetDialogFragment && activity is VideoPlayerActivity)
            newFragment.onDismissListener = DialogInterface.OnDismissListener { activity.dimStatusBar(true) }
        newFragment.show(activity.supportFragmentManager, tag)
        hide()
    }

    private fun showValueControls(action: Int) {
        val controller = activity as? IPlaybackSettingsController ?: return
        when (action) {
            ACTION_AUDIO_DELAY -> controller.showAudioDelaySetting()
            ACTION_SPU_DELAY -> controller.showSubsDelaySetting()
            else -> return
        }
        hide()
    }

    private fun setRepeatMode() {
        when (service.repeatType) {
            REPEAT_NONE -> {
                repeatBinding.optionIcon.setImageResource(R.drawable.ic_repeat_one)
                service.repeatType = REPEAT_ONE
            }
            REPEAT_ONE -> if (service.hasPlaylist()) {
                repeatBinding.optionIcon.setImageResource(R.drawable.ic_repeat_all)
                service.repeatType = REPEAT_ALL
            } else {
                repeatBinding.optionIcon.setImageResource(R.drawable.ic_repeat)
                service.repeatType = REPEAT_NONE
            }
            REPEAT_ALL -> {
                repeatBinding.optionIcon.setImageResource(R.drawable.ic_repeat)
                service.repeatType = REPEAT_NONE
            }
        }
    }

    private fun setShuffle() {
        shuffleBinding.optionIcon.setImageResource(if (service.isShuffling) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle)
    }

    private fun initShuffle(binding: PlayerOptionItemBinding) {
        shuffleBinding = binding
        AppScope.launch(Dispatchers.Main) {
            shuffleBinding.optionIcon.setImageResource(if (service.isShuffling) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle)
        }
    }

    private fun initSleep() {
        sleepBinding.optionTitle.text = if (playerSleepTime == null) {
            sleepBinding.optionIcon.setImageResource(UiTools.getResourceFromAttribute(activity, R.attr.ic_sleep_normal_style))
            null
        } else {
            sleepBinding.optionIcon.setImageResource(R.drawable.ic_sleep_on)
            DateFormat.getTimeFormat(activity).format(playerSleepTime!!.time)
        }
    }

    private fun initPlaybackSpeed(binding: PlayerOptionItemBinding) {
        if (!service.isSeekable) {
            binding.root.isEnabled = false
            binding.optionIcon.setImageResource(R.drawable.ic_speed_disable)
            return
        }
        if (service.rate == 1.0f) {
            binding.optionTitle.text = null
            binding.optionIcon.setImageResource(UiTools.getResourceFromAttribute(activity, R.attr.ic_speed_normal_style))
        } else {
            binding.optionTitle.text = service.rate.formatRateString()
            binding.optionIcon.setImageResource(R.drawable.ic_speed_on)
        }
    }

    private fun initChapters(binding: PlayerOptionItemBinding) {
        val chapters = service.getChapters(-1) ?: return
        if (chapters.isEmpty()) return
        val index = service.chapterIdx
        if (chapters[index].name.isNullOrEmpty())
            binding.optionTitle.text = String.format("%s %d", res.getString(R.string.chapter), index)
        else binding.optionTitle.text = chapters[index].name
    }

    private fun initJumpTo(binding: PlayerOptionItemBinding) {
        binding.root.isEnabled = service.isSeekable
        binding.optionIcon.setImageResource(UiTools.getResourceFromAttribute(activity, if (service.isSeekable)
            UiTools.getResourceFromAttribute(activity, R.attr.ic_jumpto_normal_style)
        else R.drawable.ic_jumpto_disable))
    }

    private fun initAudioDelay(binding: PlayerOptionItemBinding) {
        val audiodelay = service.audioDelay / 1000L
        if (audiodelay == 0L) {
            binding.optionTitle.text = null
            binding.optionIcon.setImageResource(UiTools.getResourceFromAttribute(activity, R.attr.ic_audiodelay))
        } else {
            binding.optionTitle.text = String.format("%s ms", java.lang.Long.toString(audiodelay))
            binding.optionIcon.setImageResource(R.drawable.ic_audiodelay_on)
        }
    }

    private fun initSpuDelay(binding: PlayerOptionItemBinding) {
        val spudelay = service.spuDelay / 1000L
        if (spudelay == 0L) {
            binding.optionTitle.text = null
            binding.optionIcon.setImageResource(UiTools.getResourceFromAttribute(activity, R.attr.ic_subtitledelay))
        } else {
            binding.optionTitle.text = String.format("%s ms", java.lang.Long.toString(spudelay))
            binding.optionIcon.setImageResource(R.drawable.ic_subtitledelay_on)
        }
    }

    private fun initRepeat(binding: PlayerOptionItemBinding) {
        repeatBinding = binding
        AppScope.launch(Dispatchers.Main) {
            repeatBinding.optionIcon.setImageResource(when (service.repeatType) {
                REPEAT_ONE -> R.drawable.ic_repeat_one
                REPEAT_ALL -> R.drawable.ic_repeat_all
                else -> R.drawable.ic_repeat
            })
        }
    }

    private fun togglePassthrough() {
        val enabled = !VLCOptions.isAudioDigitalOutputEnabled(settings)
        if (service.setAudioDigitalOutputEnabled(enabled)) {
            ptBinding.optionIcon.setImageResource(if (enabled) R.drawable.ic_passthrough_on
            else UiTools.getResourceFromAttribute(activity, R.attr.ic_passthrough))
            VLCOptions.setAudioDigitalOutputEnabled(settings, enabled)
            toast.setText(res.getString(if (enabled) R.string.audio_digital_output_enabled else R.string.audio_digital_output_disabled))
        } else
            toast.setText(R.string.audio_digital_failed)
        toast.show()
    }

    fun isShowing() = rootView.visibility == View.VISIBLE

    private inner class OptionsAdapter : DiffUtilAdapter<PlayerOption, OptionsAdapter.ViewHolder>() {

        private lateinit var layountInflater: LayoutInflater

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            if (!this::layountInflater.isInitialized) layountInflater = LayoutInflater.from(parent.context)
            return ViewHolder(PlayerOptionItemBinding.inflate(layountInflater, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val option = dataset[position]
            holder.binding.option = option
            when {
                option.id == ID_ABREPEAT -> abrBinding = holder.binding
                option.id == ID_PASSTHROUGH -> ptBinding = holder.binding
                option.id == ID_REPEAT -> initRepeat(holder.binding)
                option.id == ID_SHUFFLE -> initShuffle(holder.binding)
                option.id == ID_SLEEP -> sleepBinding = holder.binding
                option.id == ID_CHAPTER_TITLE -> initChapters(holder.binding)
                option.id == ID_PLAYBACK_SPEED -> initPlaybackSpeed(holder.binding)
                option.id == ID_AUDIO_DELAY -> initAudioDelay(holder.binding)
                option.id == ID_JUMP_TO -> initJumpTo(holder.binding)
                option.id == ID_SPU_DELAY -> initSpuDelay(holder.binding)
            }
            when (option.type) {
                PlayerOptionType.ADVANCED -> holder.binding.optionIcon.setImageResource(UiTools.getResourceFromAttribute(activity, option.icon))
                PlayerOptionType.MEDIA_TRACKS -> holder.binding.optionIcon.setImageDrawable(ContextCompat.getDrawable(activity, option.icon))
            }
        }

        inner class ViewHolder(val binding: PlayerOptionItemBinding) : RecyclerView.ViewHolder(binding.root) {
            val textColor = binding.optionTitle.currentTextColor

            init {
                itemView.setOnClickListener { onClick(dataset[layoutPosition]) }
                itemView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    binding.optionTitle.setTextColor(if (hasFocus) ContextCompat.getColor(itemView.context, R.color.orange500transparent) else textColor)
                }
            }
        }
    }

    companion object {
        var playerSleepTime: Calendar? = null
    }
}

fun Context.setSleep(time: Calendar?) {
    val alarmMgr = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(SLEEP_INTENT)
    val sleepPendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

    if (time != null) alarmMgr.set(AlarmManager.RTC_WAKEUP, time.timeInMillis, sleepPendingIntent)
    else alarmMgr.cancel(sleepPendingIntent)
    PlayerOptionsDelegate.playerSleepTime = time
}

data class PlayerOption(val type: PlayerOptionType, val id: Int, val icon: Int, val title: String)

enum class PlayerOptionType {
    ADVANCED, MEDIA_TRACKS
}
