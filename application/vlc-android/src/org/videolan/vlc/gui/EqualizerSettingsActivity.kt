package org.videolan.vlc.gui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import org.videolan.resources.AndroidDevices
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.tools.dp
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.databinding.EqualizerSettingItemBinding
import org.videolan.vlc.databinding.EqualizerSettingsActivityBinding
import org.videolan.vlc.gui.browser.EXTRA_MRL
import org.videolan.vlc.gui.browser.FilePickerActivity
import org.videolan.vlc.gui.browser.KEY_PICKER_TYPE
import org.videolan.vlc.gui.dialogs.EqualizerFragmentDialog
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.mediadb.models.EqualizerWithBands
import org.videolan.vlc.providers.PickerType
import org.videolan.vlc.repository.EqualizerRepository
import org.videolan.vlc.util.EqualizerUtil
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.JsonUtil
import org.videolan.vlc.viewmodels.EqualizerViewModel
import org.videolan.vlc.viewmodels.EqualizerViewModelFactory

private const val FILE_PICKER_RESULT_CODE = 10000
private const val FILE_PICKER_ALL_RESULT_CODE = 10001

/**
 * Equalizer settings activity allowing to enable/disable/delete/export/import the presets
 *
 * @constructor Create empty Equalizer settings activity
 */
class EqualizerSettingsActivity : BaseActivity() {

    private var scrollTopNext: Boolean = false
    private lateinit var adapter: EqualizerSettingsAdapter

    private val model: EqualizerViewModel by viewModels {
        EqualizerViewModelFactory(this, EqualizerRepository.getInstance(application))
    }

    internal lateinit var binding: EqualizerSettingsActivityBinding
    override fun getSnackAnchorView(overAudioPlayer: Boolean) = binding.root
    override val displayTitle = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.equalizer_settings_activity)
        val toolbar = findViewById<MaterialToolbar>(R.id.main_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_up)
        title = getString(R.string.equalizer)

        binding.equalizers.layoutManager = LinearLayoutManager(this)
        adapter = EqualizerSettingsAdapter { equalizer, type ->
            when (type) {
                ClickType.EQUALIZER_ENABLE -> model.enable(this, equalizer)
                ClickType.EQUALIZER_DISABLE -> model.disable(this, equalizer)
                ClickType.EQUALIZER_DELETE -> {
                    model.delete( equalizer)
                    UiTools.snackerConfirm(this, getString(R.string.equalizer_deleted), confirmMessage = R.string.undo) {
                        model.restore(this)
                    }
                }

                ClickType.EQUALIZER_EXPORT -> model.export(this, equalizer)
                ClickType.EQUALIZER_CHANGE_CURRENT -> {
                    model.currentEqualizerId = equalizer.equalizerEntry.id
                    model.updateEqualizer()
                }
            }
        }
        binding.equalizers.adapter = adapter

        EqualizerViewModel.currentEqualizerIdLive.observe(this) {
            val oldCurrent = adapter.currentId
            adapter.currentId = it
            if (oldCurrent != adapter.currentId) {
                val oldIndex = adapter.dataset.indexOfFirst { it.equalizerEntry.id == oldCurrent }
                val newIndex = adapter.dataset.indexOfFirst { it.equalizerEntry.id == adapter.currentId }
                adapter.notifyItemChanged(oldIndex)
                adapter.notifyItemChanged(newIndex)
            }
            if (scrollTopNext) {
                binding.equalizers.scrollToPosition(0)
                scrollTopNext = false
            }
        }

        model.equalizerUnfilteredEntries.observe(this) {
            adapter.update(it)
        }
        model.equalizerEntries.observe(this) { }

        binding.renameInputText.addTextChangedListener {
            if (model.checkForbidden(it.toString())) {
                binding.overwrite.text = getString(R.string.overwrite)
                binding.overwrite.isEnabled = false
                binding.renameInputText.error = getString(R.string.eq_cannot_overwrite)
                return@addTextChangedListener

            }
            binding.renameInputText.error = null
                binding.overwrite.isEnabled = true
            if (model.isNameAllowed(it.toString()))
                binding.overwrite.text = getString(R.string.rename)
            else
                binding.overwrite.text = getString(R.string.overwrite)
        }
        if (AndroidDevices.isTv) applyOverscanMargin(this)

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.equalizer_settings_option, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.show_equalizer -> EqualizerFragmentDialog().show(supportFragmentManager, "equalizer")
            R.id.equalizer_show_all -> model.showAll(this)
            R.id.equalizer_hide_all -> model.hideAll(this)
            R.id.equalizer_import -> {
                val filePickerIntent = Intent(this, FilePickerActivity::class.java)
                filePickerIntent.putExtra(KEY_PICKER_TYPE, PickerType.EQUALIZER.ordinal)
                startActivityForResult(filePickerIntent, FILE_PICKER_RESULT_CODE)
            }
            R.id.equalizer_export_all -> model.exportAll(this)
            R.id.equalizer_import_all ->  {
                val filePickerIntent = Intent(this, FilePickerActivity::class.java)
                filePickerIntent.putExtra(KEY_PICKER_TYPE, PickerType.EQUALIZER.ordinal)
                startActivityForResult(filePickerIntent, FILE_PICKER_ALL_RESULT_CODE)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return
        if (requestCode == FILE_PICKER_RESULT_CODE) {
            if (data.hasExtra(EXTRA_MRL)) lifecycleScope.launch {
                data.getStringExtra(EXTRA_MRL)?.toUri()?.path?.let {
                    val equalizerString = FileUtils.getStringFromFile(it)
                    try {
                        val equalizer = JsonUtil.getEqualizerFromJson(equalizerString)
                        equalizer?.let {
                            if (it.equalizerEntry == null || it.bands == null || it.bands.isEmpty())
                                UiTools.snacker(this@EqualizerSettingsActivity, getString(R.string.invalid_equalizer_file))
                            else {
                                if (model.isNameAllowed(it.equalizerEntry.name)) {
                                    model.insert(this@EqualizerSettingsActivity, it)
                                } else showOverwriteDialog(it)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EqualizerSettings", "onActivityResult: ${e.message}", e)
                        UiTools.snacker(this@EqualizerSettingsActivity, getString(R.string.invalid_equalizer_file))
                    }
                }
            }
        } else if (requestCode == FILE_PICKER_ALL_RESULT_CODE) {
            if (data.hasExtra(EXTRA_MRL)) lifecycleScope.launch {
                data.getStringExtra(EXTRA_MRL)?.toUri()?.path?.let {
                    val equalizerString = FileUtils.getStringFromFile(it)
                    try {
                        scrollTopNext = true
                        EqualizerUtil.importAll(this@EqualizerSettingsActivity, equalizerString) { newId ->
                            model.currentEqualizerId = newId
                            model.updateEqualizer()
                            adapter.notifyDataSetChanged()
                        }
                    } catch (e: Exception) {
                        Log.e("EqualizerSettings", "onActivityResult: ${e.message}", e)
                        UiTools.snacker(this@EqualizerSettingsActivity, getString(R.string.invalid_equalizer_file))
                    }
                }
            }
        }
    }

    /**
     * Show the overwrite dialog
     *
     * @param equalizer The equalizer to overwrite
     */
    fun showOverwriteDialog(equalizer: EqualizerWithBands) {
        binding.renameInputText.setText(equalizer.equalizerEntry.name)
        binding.overwriteContainer.setVisible()
        binding.cancel.setOnClickListener {
            binding.overwriteContainer.setGone()
        }
        binding.overwrite.setOnClickListener {
            model.insert(this, equalizer.copy(equalizerEntry = equalizer.equalizerEntry.copy(name = binding.renameInputText.text.toString())))
            UiTools.setKeyboardVisibility(binding.renameInputText, false)
            binding.overwriteContainer.setGone()
        }

    }
}

class EqualizerSettingsAdapter(private val itemClickHandler: (equalizer: EqualizerWithBands, ClickType) -> Unit) : DiffUtilAdapter<EqualizerWithBands, EqualizerSettingsAdapter.ViewHolder>() {
    var currentId = -1L

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(EqualizerSettingItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.equalizer = dataset[position]
        holder.binding.current = currentId == dataset[position].equalizerEntry.id
    }

    override fun getItemCount() = dataset.size

    inner class ViewHolder(vdb: EqualizerSettingItemBinding) : SelectorViewHolder<EqualizerSettingItemBinding>(vdb) {
        init {
            binding.holder = this
            itemView.setOnClickListener{v ->
                if (!dataset[layoutPosition].equalizerEntry.isDisabled && currentId != dataset[position].equalizerEntry.id)
                    itemClickHandler.invoke(dataset[layoutPosition], ClickType.EQUALIZER_CHANGE_CURRENT)
            }
        }

        fun onClickEnable(@Suppress("UNUSED_PARAMETER") v: View) {
            itemClickHandler.invoke(dataset[layoutPosition], ClickType.EQUALIZER_ENABLE)
        }

        fun onClickDisable(@Suppress("UNUSED_PARAMETER") v: View) {
            itemClickHandler.invoke(dataset[layoutPosition], ClickType.EQUALIZER_DISABLE)
        }

        fun onClickDelete(@Suppress("UNUSED_PARAMETER") v: View) {
            itemClickHandler.invoke(dataset[layoutPosition], ClickType.EQUALIZER_DELETE)
        }

        fun onClickExport(@Suppress("UNUSED_PARAMETER") v: View) {
            itemClickHandler.invoke(dataset[layoutPosition], ClickType.EQUALIZER_EXPORT)
        }
    }
}

enum class ClickType {
    EQUALIZER_DISABLE, EQUALIZER_ENABLE, EQUALIZER_DELETE, EQUALIZER_EXPORT, EQUALIZER_CHANGE_CURRENT
}

fun EqualizerWithBands.getBitmap(context: Context): Bitmap {
    val imageSize = 72.dp
    val step = 8.dp

    val typedValue = TypedValue()
    val theme = context.theme
    theme.resolveAttribute(R.attr.default_divider, typedValue, true)
    @ColorInt val color: Int = typedValue.data


    val bitmap = createBitmap(imageSize, imageSize)
    val canvas = Canvas(bitmap)
    val paint = Paint()

    val path = Path()
    path.addRoundRect(RectF(0F, 0F, imageSize.toFloat(), imageSize.toFloat()), step.toFloat(), step.toFloat(), Path.Direction.CCW)

    canvas.clipPath(path)


    paint.color = if (equalizerEntry.presetIndex == -1) ContextCompat.getColor(context, R.color.orange500focus) else color
    var x = 0
    bands.sortedBy { it.index }.forEach {
        val heightPc = (it.bandValue + 20) / 40
        canvas.drawRect(Rect(x, imageSize, x + step, imageSize - (66 * heightPc).toInt().dp), paint)
        x += step
    }
    paint.color = color

    canvas.drawPath(path, paint)
    return bitmap
}

@BindingAdapter("equalizerImage")
fun equalizerImage(view: ImageView, item: EqualizerWithBands) {
    view.setImageBitmap(item.getBitmap(view.context))
}

@BindingAdapter("equalizerNameColor")
fun equalizerNameColor(view: TextView, item: EqualizerWithBands) {
    val typedValue = TypedValue()
    val theme = view.context.theme
    theme.resolveAttribute(if (item.equalizerEntry.isDisabled)R.attr.font_disabled else R.attr.font_default, typedValue, true)
    @ColorInt val color: Int = typedValue.data
    view.setTextColor(color)
}


