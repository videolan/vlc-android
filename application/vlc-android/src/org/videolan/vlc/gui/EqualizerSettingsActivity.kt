package org.videolan.vlc.gui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
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
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.resources.AndroidDevices
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.tools.dp
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.databinding.EqualizerSettingItemBinding
import org.videolan.vlc.databinding.EqualizerSettingsActivityBinding
import org.videolan.vlc.gui.browser.EXTRA_MRL
import org.videolan.vlc.gui.browser.FilePickerActivity
import org.videolan.vlc.gui.browser.KEY_PICKER_TYPE
import org.videolan.vlc.gui.dialogs.EqualizerFragmentDialog
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getWritePermission
import org.videolan.vlc.mediadb.models.EqualizerWithBands
import org.videolan.vlc.providers.PickerType
import org.videolan.vlc.repository.EqualizerRepository
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.JsonUtil
import java.io.File
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import org.videolan.tools.setGone
import org.videolan.tools.setVisible

private const val FILE_PICKER_RESULT_CODE = 10000

/**
 * Equalizer settings activity allowing to enable/disable/delete/export/import the presets
 *
 * @constructor Create empty Equalizer settings activity
 */
class EqualizerSettingsActivity : BaseActivity() {

    private lateinit var adapter: EqualizerSettingsAdapter
    private val model: EqualizerSettingsModel by viewModels {
        EqualizerSettingsModelFactory(this, EqualizerRepository.getInstance(application))
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
                    model.delete(this, equalizer)
                    UiTools.snackerConfirm(this, getString(R.string.equalizer_deleted), confirmMessage = R.string.undo) {
                        model.restore(this)
                    }
                }

                ClickType.EQUALIZER_EXPORT -> model.export(this, equalizer)
            }
        }
        binding.equalizers.adapter = adapter

        model.equalizerEntries.observe(this, Observer {
            adapter.update(it)
        })
        binding.renameInputText.addTextChangedListener {
            if (model.checkForbidden(it.toString())) {
                binding.overwrite.text = getString(R.string.overwrite)
                binding.overwrite.isEnabled = false
                binding.renameInputText.error = getString(R.string.eq_cannot_overwrite)
                return@addTextChangedListener

            }
            binding.renameInputText.error = null
                binding.overwrite.isEnabled = true
            if (model.checkAvailability(it.toString()))
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
                                if (model.checkAvailability(it.equalizerEntry.name)) {
                                    model.insert(this@EqualizerSettingsActivity, it)
                                } else showOverwriteDialog(it)
                            }
                        }
                    } catch (_: Exception) {
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

class EqualizerSettingsModel(private val equalizerRepository: EqualizerRepository) : ViewModel() {
    private var oldEqualizer: EqualizerWithBands? = null
    val equalizerEntries = equalizerRepository.equalizerEntriesUnfiltered.asLiveData()

    fun enable(context: Context, equalizer: EqualizerWithBands) = viewModelScope.launch(Dispatchers.IO) {
        equalizerRepository.addOrUpdateEqualizerWithBands(context, equalizer.copy(equalizerEntry = equalizer.equalizerEntry.copy(isDisabled = false).apply { id = equalizer.equalizerEntry.id }))
    }

    fun disable(context: Context, equalizer: EqualizerWithBands) = viewModelScope.launch(Dispatchers.IO) {
        equalizerRepository.addOrUpdateEqualizerWithBands(context, equalizer.copy(equalizerEntry = equalizer.equalizerEntry.copy(isDisabled = true).apply { id = equalizer.equalizerEntry.id }))
    }

    fun delete(context: Context, equalizer: EqualizerWithBands) = viewModelScope.launch(Dispatchers.IO) {
        oldEqualizer = equalizer
        equalizerRepository.delete(equalizer.equalizerEntry)
    }

    fun restore(context: Context) = viewModelScope.launch(Dispatchers.IO) {
        equalizerRepository.addOrUpdateEqualizerWithBands(context, oldEqualizer!!)
    }

    fun export(context: FragmentActivity, equalizer: EqualizerWithBands) = viewModelScope.launch(Dispatchers.IO) {
        val characterFilter = Regex("[^\\p{L}\\p{M}\\p{N}\\p{P}\\p{Z}\\p{Cf}\\p{Cs}\\s]")
        val fileName: String? = equalizer.equalizerEntry.name
            .replace(characterFilter, "")
            .lowercase()
            .trim()
            .replace(" ", "_")
            .replace("/", "")
        UiTools.snacker(context, context.getString(R.string.equalizer_exported, fileName))
        val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + File.separator + fileName + ".json")
        if (context.getWritePermission(Uri.fromFile(dst))) {
            JsonUtil.convertToJson(equalizer).let {
                dst.writeText(it)
            }
        }
    }

    fun showAll(context: Context) = viewModelScope.launch(Dispatchers.IO) {
        equalizerEntries.value?.forEach {
            if (it.equalizerEntry.presetIndex != -1 && it.equalizerEntry.isDisabled)
            equalizerRepository.addOrUpdateEqualizerWithBands(context, it.copy(equalizerEntry = it.equalizerEntry.copy(isDisabled = false).apply { id = it.equalizerEntry.id }))
        }
    }

    fun hideAll(context: Context) = viewModelScope.launch(Dispatchers.IO) {
        equalizerEntries.value?.forEach {
            if (it.equalizerEntry.presetIndex != -1 && !it.equalizerEntry.isDisabled)
            equalizerRepository.addOrUpdateEqualizerWithBands(context, it.copy(equalizerEntry = it.equalizerEntry.copy(isDisabled = true).apply { id = it.equalizerEntry.id }))
        }
    }

    fun checkAvailability(name:String): Boolean {
        return equalizerEntries.value?.none { it.equalizerEntry.name == name } != false
    }

    fun checkForbidden(name:String): Boolean {
        return equalizerEntries.value?.any { it.equalizerEntry.name == name && it.equalizerEntry.presetIndex != -1 } != false
    }

    fun insert(context: Context, equalizerWithBands: EqualizerWithBands) = viewModelScope.launch(Dispatchers.IO) {
        val eq = EqualizerWithBands(equalizerWithBands.equalizerEntry.copy().apply { id = 0 }, equalizerWithBands.bands)
        equalizerRepository.addOrUpdateEqualizerWithBands(context, eq)
    }
}

class EqualizerSettingsModelFactory(private val context: Context, private val repository: EqualizerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EqualizerSettingsModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EqualizerSettingsModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class EqualizerSettingsAdapter(private val itemClickHandler: (equalizer: EqualizerWithBands, ClickType) -> Unit) : DiffUtilAdapter<EqualizerWithBands, EqualizerSettingsAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(EqualizerSettingItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.equalizer = dataset[position]
    }

    override fun getItemCount() = dataset.size

    inner class ViewHolder(vdb: EqualizerSettingItemBinding) : SelectorViewHolder<EqualizerSettingItemBinding>(vdb) {
        init {
            binding.holder = this
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
    EQUALIZER_DISABLE, EQUALIZER_ENABLE, EQUALIZER_DELETE, EQUALIZER_EXPORT
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
        if (BuildConfig.DEBUG) Log.d("BandBitmap", "band value is ${it.bandValue} and height is ${heightPc * 38}")
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


