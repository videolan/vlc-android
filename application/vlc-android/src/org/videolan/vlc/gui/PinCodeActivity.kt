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
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.core.view.children
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import org.videolan.resources.AndroidDevices
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.tools.KEY_SAFE_MODE_PIN
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PinCodeActivityBinding
import org.videolan.vlc.gui.helpers.UiTools
import java.security.MessageDigest


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
    private val pinTexts by lazy { arrayOf(binding.pinCode1, binding.pinCode2, binding.pinCode3, binding.pinCode4) }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!intent.hasExtra(PIN_CODE_REASON)) throw IllegalStateException("No reason given")
        reason = PinCodeReason.values()[intent.getIntExtra(PIN_CODE_REASON, 0)]

        binding = DataBindingUtil.setContentView(this, R.layout.pin_code_activity)
        if (!Settings.tvUI) {
            updateFocus()
            UiTools.setKeyboardVisibility(binding.pinCode1, true)
            binding.keyboardGrid.setGone()
        } else {
            //On TV show virtual keyboard and make edit texts not focusable
            binding.keyboardGrid.setVisible()
            binding.keyboardButton1.requestFocus()
            pinTexts.forEach { it.isFocusable = false }
        }
        binding.pinCodeReason.text = getString(when (reason) {
            PinCodeReason.FIRST_CREATION -> R.string.pin_code_reason_create
            PinCodeReason.MODIFY -> R.string.pin_code_reason_modify
            else -> R.string.pin_code_reason_check
        })

        //Set listeners for edit texts
        pinTexts.forEach { editText ->
            editText.doOnTextChanged { text, start, before, count ->
                text?.let {
                    val codeFilled = pinTexts.none { it.text.isNullOrBlank() }
                    //enable next button if possible
                    binding.nextButton.isEnabled = codeFilled
                    updateFocus()
                    //focus next button on TV
                    if (Settings.tvUI && codeFilled) binding.nextButton.requestFocus()
                    if (editText == binding.pinCode4 && pinTexts.filter { it.text?.isNotEmpty() == true }.size == 4) next()
                }
            }
            editText.setOnKeyListener { v, keyCode, event ->
                //Manage backspace button
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (editText.text?.isNotEmpty() == true) return@setOnKeyListener false
                    getLastSetET()?.clearText()
                    updateFocus()
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
            editText.setOnFocusChangeListener { v, hasFocus ->
                if (Settings.tvUI) return@setOnFocusChangeListener
                if (hasFocus) pinTexts.forEach { if (v != it) it.clearFocus() }
                editText.transformationMethod = PasswordTransformationMethod()
            }
        }

        val getModel by viewModels<SafeModeModel> { SafeModeModel.Factory(this, reason) }
        model = getModel

        //Observe the current step
        model.step.observe(this) { step ->
            if (model.step.value == PinStep.EXIT) {
                finish()
                return@observe
            }
            if (reason == PinCodeReason.CHECK && model.step.value !in arrayOf(PinStep.INVALID, PinStep.ENTER_EXISTING)) {
                setResult(RESULT_OK)
                finish()
                return@observe
            }
            if (reason == PinCodeReason.UNLOCK && model.step.value !in arrayOf(PinStep.INVALID, PinStep.ENTER_EXISTING)) {
                setResult(RESULT_OK)
                showTips()
                return@observe
            }

            when (step) {
                PinStep.ENTER_EXISTING -> binding.pinCodeTitle.text = getString(R.string.safe_mode_pin)
                PinStep.ENTER_NEW -> binding.pinCodeTitle.text = getString(R.string.safe_mode_new_pin)
                PinStep.RE_ENTER -> binding.pinCodeTitle.text = getString(R.string.safe_mode_re_pin)
                PinStep.NO_MATCH -> binding.pinCodeTitle.text = getString(R.string.safe_mode_no_match)
                PinStep.INVALID -> binding.pinCodeTitle.text = getString(R.string.safe_mode_invalid_pin)
                PinStep.LOGIN_SUCCESS -> binding.pinCodeTitle.text = getString(R.string.safe_mode_invalid_pin)
                PinStep.EXIT -> {}
            }
            if (model.isFinalStep()) {
                pinTexts.forEach { it.imeOptions = EditorInfo.IME_ACTION_DONE }
                binding.nextButton.text = getString(R.string.done)
            }
            if (!Settings.tvUI) updateFocus()
        }

        pinTexts.forEach {
            it.setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    next()
                }
                false
            }
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
                    "-1" -> {
                        getLastSetET()?.clearText()
                    }

                    else -> getCurrentInput()?.setText(keyboardButton.tag as String)
                }
            }
        }

    }

    private fun showTips() {
        UiTools.setKeyboardVisibility(binding.pinCode1, false)

        binding.pinGroup.setGone()
        binding.sucessGroup.setVisible()
        binding.nextButton.text = getString(R.string.done)
        binding.nextButton.isEnabled = true
        binding.keyboardGrid.setGone()
        binding.nextButton.requestFocus()
        binding.cancelButton.setGone()
    }

    /**
     * Get the last filled EditText (or null if all are filled)
     *
     */
    private fun getLastSetET() = pinTexts.reversedArray().firstOrNull { it.text?.isNotBlank() == true }

    /**
     * Give the focus to the last not filled EditText
     *
     */
    private fun updateFocus() {
        if (Settings.tvUI) return
        getCurrentInput()?.requestFocus() ?: binding.pinCode4.requestFocus()
    }

    /**
     * Get the current first not filled EditText or null
     *
     */
    private fun getCurrentInput() = pinTexts.firstOrNull { it.text.isNullOrBlank() }

    /**
     * Get the PIN code text
     *
     * @return the PIN code text
     */
    private fun getPinCode(): String = buildString { pinTexts.forEach { append(it.text.toString()) } }


    /**
     * Use a keyboard / remote controller to type the PIN
     *
     * @param keyCode
     * @param event
     * @return
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> {
                getCurrentInput()?.setText("0")
                true
            }

            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> {
                getCurrentInput()?.setText("1")
                true
            }

            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> {
                getCurrentInput()?.setText("2")
                true
            }

            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> {
                getCurrentInput()?.setText("3")
                true
            }

            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> {
                getCurrentInput()?.setText("4")
                true
            }

            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> {
                getCurrentInput()?.setText("5")
                true
            }

            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> {
                getCurrentInput()?.setText("6")
                true
            }

            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> {
                getCurrentInput()?.setText("7")
                true
            }

            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> {
                getCurrentInput()?.setText("8")
                true
            }

            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> {
                getCurrentInput()?.setText("9")
                true
            }

            KeyEvent.KEYCODE_DEL -> {
                getLastSetET()?.clearText()
                true
            }

            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                next()
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    /**
     * Move to next step
     *
     */
    private fun next() {
        if (model.step.value == PinStep.RE_ENTER || model.step.value == PinStep.NO_MATCH) {
            if (model.checkMatch(getPinCode())) {
                model.savePin(getPinCode())
                setResult(RESULT_OK)
                finish()
            } else {
                pinTexts.forEach { it.clearText() }
                return
            }
        }
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "nextStep: ${getPinCode()}", Exception("Give me a stacktrace"))
        model.nextStep(getPinCode())
        pinTexts.forEach { it.clearText() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        fun getIntent(context: Context, reason: PinCodeReason) = Intent(context, PinCodeActivity::class.java).apply {
            putExtra(PIN_CODE_REASON, reason.ordinal)
        }
    }

}

class SafeModeModel(application: Application, val reason: PinCodeReason) : AndroidViewModel(application) {
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
        if (reason == PinCodeReason.CHECK || reason == PinCodeReason.UNLOCK) {
            step.postValue(if (step.value == PinStep.LOGIN_SUCCESS) PinStep.EXIT else PinStep.LOGIN_SUCCESS)
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

    class Factory(private val context: Context, private val reason: PinCodeReason) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SafeModeModel(context.applicationContext as Application, reason) as T
        }
    }
}


enum class PinStep {
    ENTER_EXISTING, INVALID, ENTER_NEW, RE_ENTER, NO_MATCH, LOGIN_SUCCESS, EXIT;
}

enum class PinCodeReason {
    FIRST_CREATION, MODIFY, CHECK, UNLOCK
}

fun TextInputEditText.clearText() {
    text?.clear()
    requestLayout()
}

