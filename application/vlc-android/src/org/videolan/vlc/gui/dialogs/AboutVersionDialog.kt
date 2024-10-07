/**
 * **************************************************************************
 * AboutVersionDialog.kt
 * ****************************************************************************
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.dialogs

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogAboutVersionBinding
import java.security.MessageDigest


/**
 * Dialog showing the info of the current version
 */
class AboutVersionDialog : VLCBottomSheetDialogFragment() {

    private lateinit var binding: DialogAboutVersionBinding

    companion object {

        fun newInstance(): AboutVersionDialog {
            return AboutVersionDialog()
        }
    }

    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun needToManageOrientation(): Boolean {
        return false
    }

    override fun initialFocusedView(): View = binding.medias2

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = DialogAboutVersionBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.version.text = BuildConfig.VLC_VERSION_NAME
        binding.medias2.text = getString(R.string.build_time)
        binding.changelog.text = getString(R.string.changelog).replace("*", "•")
        binding.revision.text = getString(R.string.build_revision)
        binding.vlcRevision.text = getString(R.string.build_vlc_revision)
        binding.libvlcRevision.text = getString(R.string.build_libvlc_revision)
        binding.libvlcVersion.text = BuildConfig.LIBVLC_VERSION
        binding.remoteAccessVersion.text = getString(R.string.remote_access_version)
        binding.remoteAccessRevision.text = getString(R.string.build_remote_access_revision)
        binding.compiledBy.text = getString(R.string.build_host)
        binding.moreButton.setOnClickListener {
            val whatsNewDialog = WhatsNewDialog()
            whatsNewDialog.show(requireActivity().supportFragmentManager, "fragment_whats_new")
            dismiss()
        }


        val signatures =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) requireActivity().packageManager.getPackageInfo(
                requireActivity().packageName,
                PackageManager.GET_SIGNATURES
            ).signatures
            else
                requireActivity().packageManager.getPackageInfo(
                    requireActivity().packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo.apkContentsSigners
        var signer = requireActivity().getString(R.string.unknown)
        signatures.forEach {
            try {
                val md = MessageDigest.getInstance("SHA1")
                md.update(it.toByteArray())

                val digest = md.digest()
                val toRet = java.lang.StringBuilder()
                for (i in digest.indices) {
                    if (i != 0) toRet.append(":")
                    val b = digest[i].toInt() and 0xff
                    val hex = Integer.toHexString(b)
                    if (hex.length == 1) toRet.append("0")
                    toRet.append(hex)
                }
                when (toRet.toString().uppercase()) {
                    "AC:5A:BC:F1:99:AC:86:61:6A:79:65:CB:84:59:94:89:A5:A7:3F:86" -> signer = "VideoLAN nightly"
                    "4D:D5:44:A7:51:D3:D5:4C:17:D8:7E:1D:D3:60:F0:C6:40:A5:C1:50" -> signer = "Google"
                    "EE:FB:C9:81:42:83:43:BB:DD:FF:F6:B2:3B:6B:D8:71:73:51:41:0C" -> signer = "VideoLAN"
                    "40:80:86:F9:AE:A6:52:A8:61:44:70:4F:11:79:9A:CA:BA:31:C7:A0" -> signer = "F-Droid"
                }
                Log.i(this::class.java.simpleName, "Found signature. Fingerprint: $toRet")
            } catch (e: Exception) {
                Log.e("Signature",e.message, e)
            }
        }
        binding.signedBy.text = signer
    }


}





