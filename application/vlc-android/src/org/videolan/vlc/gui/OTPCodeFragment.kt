/*****************************************************************************
 * OTPCodeFragment.kt
 *
 * Copyright © 2023 VLC authors and VideoLAN
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

package org.videolan.vlc.gui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.videolan.tools.setGone
import org.videolan.vlc.R
import org.videolan.vlc.databinding.OtpCodeBinding
import org.videolan.vlc.util.RemoteAccessUtils

class OTPCodeFragment : BaseFragment() {

    private lateinit var binding: OtpCodeBinding
    private var code: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(requireActivity(), R.layout.otp_code)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                RemoteAccessUtils.otpFlow.collect {
                    if (it != null && it != code) {
                        code = it
                        manageCodeViews()
                    }
                    if (it == null) requireActivity().finish()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = OtpCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

    override fun onDestroyActionMode(mode: ActionMode?) {}

    override fun getTitle() = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().findViewById<FloatingActionButton>(R.id.fab).setGone()
        requireActivity().findViewById<View>(R.id.sliding_tabs).setGone()
        binding.cancelButton.setOnClickListener {
            lifecycleScope.launch {
                RemoteAccessUtils.otpFlow.emit(null)
            }
            requireActivity().finish()
        }
    }

    private fun manageCodeViews() {
        code?.let { code ->
            binding.code1.text = code.substring(0, 1)
            binding.code2.text = code.substring(1, 2)
            binding.code3.text = code.substring(2, 3)
            binding.code4.text = code.substring(3, 4)
        }
    }
}
