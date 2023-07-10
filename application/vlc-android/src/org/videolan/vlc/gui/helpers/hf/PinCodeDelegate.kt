package org.videolan.vlc.gui.helpers.hf

import android.annotation.TargetApi
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import org.videolan.tools.Settings
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.PinCodeActivity
import org.videolan.vlc.gui.PinCodeReason
import org.videolan.vlc.gui.video.VideoPlayerActivity

private const val UNLOCK = "unlock"
class PinCodeDelegate : BaseHeadlessFragment() {
    private var unlock: Boolean = false
    var pinCodeResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
       model.complete(result.resultCode == Activity.RESULT_OK)
        if (result.resultCode == Activity.RESULT_OK && unlock) pinUnlocked.postValue(true)
        exit()
        (activity as? DialogActivity)?.finish()
    }

    @TargetApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        unlock = arguments?.getBoolean(UNLOCK, false) == true
        super.onCreate(savedInstanceState)
        val intent = PinCodeActivity.getIntent(requireActivity(), PinCodeReason.UNLOCK)
        pinCodeResult.launch(intent)
    }

    companion object {
        internal const val TAG = "VLC/PinCode"
        val pinUnlocked = MutableLiveData(false)
    }
}

suspend fun FragmentActivity.checkPIN(unlock:Boolean = false) : Boolean {
    if (this is VideoPlayerActivity) this.waitingForPin = true
    if (!Settings.safeMode) return true
    val model : PermissionViewmodel by viewModels()
    val fragment = PinCodeDelegate().apply {
        arguments = Bundle().apply { putBoolean(UNLOCK, unlock) }
    }
    model.setupDeferred()
    supportFragmentManager.beginTransaction().add(fragment, PinCodeDelegate.TAG).commitAllowingStateLoss()
    if (this is DialogActivity) this.preventFinish()
    return model.deferredGrant.await()
}

