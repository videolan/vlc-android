/*
 * ************************************************************************
 *  PinCodeActivity.kt
 * *************************************************************************
 * Copyright © 2023 VLC authors and VideoLAN
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

package org.videolan.vlc.gui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import androidx.core.view.children
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import org.videolan.resources.AndroidDevices
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.tools.KEY_SAFE_MODE_PIN
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PinCodeActivityBinding
import org.videolan.vlc.gui.helpers.UiTools
import java.security.MessageDigest
import java.util.regex.Pattern


private const val PIN_CODE_REASON = "pin_code_reason"

/**
 * Activity allowing to setup the safe mode
 */
class PinCodeActivity : BaseActivity() {


    private lateinit var reason: PinCodeReason
    private lateinit var model: SafeModeModel
    internal lateinit var binding: PinCodeActivityBinding
    override fun getSnackAnchorView(overAudioPlayer: Boolean) = binding.root
    override val displayTitle = true

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!intent.hasExtra(PIN_CODE_REASON)) throw IllegalStateException("No reason given")
        reason = PinCodeReason.values() [intent.getIntExtra(PIN_CODE_REASON, 0)]

        binding = DataBindingUtil.setContentView(this, R.layout.pin_code_activity)
        if (!Settings.tvUI) {
            binding.pinCode.requestFocus()
        } else {
            binding.keyboardButton1.requestFocus()
        }
        if (Settings.tvUI) {
            binding.pinCode.setOnTouchListener { v, event -> true }
            if (Build.VERSION.SDK_INT >= 21) {
                binding.pinCode.showSoftInputOnFocus = false
            } else {
                binding.pinCode.setRawInputType(InputType.TYPE_CLASS_TEXT)
                binding.pinCode.setTextIsSelectable(true)
            }
        }
        if (Settings.tvUI) binding.keyboardGrid.setVisible()
        UiTools.setKeyboardVisibility(binding.pinCode, true)
        binding.pinCodeReason.text = getString(when (reason) {
            PinCodeReason.FIRST_CREATION -> R.string.pin_code_reason_create
            PinCodeReason.MODIFY -> R.string.pin_code_reason_modify
            else -> R.string.pin_code_reason_check
        })
        binding.pinCode.doOnTextChanged { text, start, before, count ->
            text?.let {

                if (text.isNotEmpty() && !Pattern.matches("[0-9]+", text)) {
                    binding.pinCodeParent.error = getString(R.string.safe_mode_pin_error)
                    binding.nextButton.isEnabled = false
                } else {
                    binding.pinCodeParent.isErrorEnabled = false
                    binding.nextButton.isEnabled = text.length > 3
                }
            }
        }

        model = ViewModelProvider.AndroidViewModelFactory(this.application).create(SafeModeModel::class.java)

        model.step.observe(this) { step ->
            if (reason == PinCodeReason.CHECK && model.step.value !in arrayOf( PinStep.INVALID, PinStep.ENTER_EXISTING)) {
                setResult(RESULT_OK)
                finish()
            }
            when (step) {
                PinStep.ENTER_EXISTING -> binding.pinCodeTitle.text = getString(R.string.safe_mode_pin)
                PinStep.ENTER_NEW -> binding.pinCodeTitle.text = getString(R.string.safe_mode_new_pin)
                PinStep.RE_ENTER -> binding.pinCodeTitle.text = getString(R.string.safe_mode_re_pin)
                PinStep.NO_MATCH -> binding.pinCodeTitle.text = getString(R.string.safe_mode_no_match)
                PinStep.INVALID -> binding.pinCodeTitle.text = getString(R.string.safe_mode_invalid_pin)
            }
            if (model.isFinalStep()) {
                binding.pinCode.imeOptions = EditorInfo.IME_ACTION_DONE
                binding.nextButton.text = getString(R.string.done)
            }
            if (!Settings.tvUI) binding.pinCode.requestFocus()
        }

        binding.pinCode.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                next()
            }
            false
        }

        binding.nextButton.setOnClickListener {
            next()
        }
        binding.cancelButton.setOnClickListener {
            finish()
        }
        setResult(RESULT_CANCELED)
        if (AndroidDevices.isTv) applyOverscanMargin(this)
        binding.keyboardGrid.children.forEach {
            it.setOnClickListener { keyboardButton ->
                when (keyboardButton.tag) {
                    "-1" -> binding.pinCode.setText(binding.pinCode.text.toString().replaceFirst(".$".toRegex(), ""))
                    else -> binding.pinCode.setText(binding.pinCode.text.toString() + keyboardButton.tag)
                }
            }
        }

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> {
                binding.pinCode.setText(binding.pinCode.text.toString() + "0")
                true
            }
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> {
                binding.pinCode.setText(binding.pinCode.text.toString() + "1")
                true
            }
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> {
                binding.pinCode.setText(binding.pinCode.text.toString() + "2")
                true
            }
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> {
                binding.pinCode.setText(binding.pinCode.text.toString() + "3")
                true
            }
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> {
                binding.pinCode.setText(binding.pinCode.text.toString() + "4")
                true
            }
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> {
                binding.pinCode.setText(binding.pinCode.text.toString() + "5")
                true
            }
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> {
                binding.pinCode.setText(binding.pinCode.text.toString() + "6")
                true
            }
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> {
                binding.pinCode.setText(binding.pinCode.text.toString() + "7")
                true
            }
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> {
                binding.pinCode.setText(binding.pinCode.text.toString() + "8")
                true
            }
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> {
                binding.pinCode.setText(binding.pinCode.text.toString() + "9")
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun next() {
        if (model.step.value == PinStep.RE_ENTER || model.step.value == PinStep.NO_MATCH) {
            if (model.checkMatch(binding.pinCode.text.toString())) {
                model.savePin(binding.pinCode.text.toString())
                setResult(RESULT_OK)
                finish()
            } else {
                binding.pinCode.text?.clear()
                return
            }
        }

        binding.pinCode.text?.let { text ->
            model.nextStep(text.toString())
            binding.pinCode.text?.clear()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        fun getIntent(context:Context, reason:PinCodeReason) = Intent(context, PinCodeActivity::class.java).apply {
            putExtra(PIN_CODE_REASON, reason.ordinal)
        }
    }

}

class SafeModeModel(application: Application) : AndroidViewModel(application) {
    /**
     * Proceed to old pin verification and go to the next step
     *
     * @param pin the entered pin
     */
    fun nextStep(pin: String) {
        //verify old pin
        if (step.value in arrayOf(PinStep.ENTER_EXISTING, PinStep.INVALID) && !checkValid(pin)) {
            step.postValue(PinStep.INVALID)
            return
        }
        pins[step.value!!] = pin
        when (step.value!!) {
            PinStep.ENTER_EXISTING, PinStep.INVALID -> step.postValue(PinStep.ENTER_NEW)
            else -> step.postValue(PinStep.RE_ENTER)
        }
    }

    /**
     * Is i currently the final step
     *
     * @return true if it is
     */
    fun isFinalStep(): Boolean {
        return step.value == PinStep.RE_ENTER
    }

    /**
     * Check if re-entered pin is the same as the entered one
     *
     * @param pin the entered pin
     * @return true if pins match
     */
    fun checkMatch(pin: String): Boolean {
        val match = pins[PinStep.ENTER_NEW] == pin
        if (!match) step.postValue(PinStep.NO_MATCH)
        return match
    }

    /**
     * Check if the entered pin is valid
     *
     * @param pin the entered pin
     * @return true if it's valid
     */
    private fun checkValid(pin: String) = getSha256(pin) == Settings.getInstance(getApplication()).getString(KEY_SAFE_MODE_PIN, "")

    /**
     * Save the new pin to the sharedpreferences. it's saved as a sha256 hash
     *
     * @param pin the pin to save
     */
    fun savePin(pin: String) {
        Settings.getInstance(getApplication()).putSingle(KEY_SAFE_MODE_PIN, getSha256(pin))
    }

    /**
     * Get the sha256 hash of a string
     *
     * @param input the input string
     * @return the sha256 hash
     */
    private fun getSha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(input.toByteArray())
        return md.digest().joinToString(":") { String.format("%02x", it) }
    }

    val step = MutableLiveData(PinStep.ENTER_EXISTING)
    private val pins = HashMap<PinStep, String>()

    init {
        if (Settings.getInstance(application).getString(KEY_SAFE_MODE_PIN, "").isNullOrBlank()) {
            step.postValue(PinStep.ENTER_NEW)
        }
    }
}


enum class PinStep {
    ENTER_EXISTING, INVALID, ENTER_NEW, RE_ENTER, NO_MATCH;
}

enum class PinCodeReason {
    FIRST_CREATION, MODIFY, CHECK
}

