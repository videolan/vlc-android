package org.videolan.vlc.gui.helpers.hf

import android.annotation.TargetApi
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import org.videolan.tools.Settings
import org.videolan.vlc.gui.PinCodeActivity
import org.videolan.vlc.gui.PinCodeReason

class PinCodeDelegate : BaseHeadlessFragment() {
    var pinCodeResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
       model.complete(result.resultCode == Activity.RESULT_OK)
        exit()
    }

    @TargetApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = PinCodeActivity.getIntent(requireActivity(), PinCodeReason.CHECK)
        pinCodeResult.launch(intent)
    }

    companion object {
        internal const val TAG = "VLC/PinCode"
    }
}

suspend fun FragmentActivity.checkPIN() : Boolean {
    if (!Settings.safeMode) return true
    val model : PermissionViewmodel by viewModels()
    val fragment = PinCodeDelegate()
    model.setupDeferred()
    supportFragmentManager.beginTransaction().add(fragment, PinCodeDelegate.TAG).commitAllowingStateLoss()
    return model.deferredGrant.await()
}

