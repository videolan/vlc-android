package org.videolan.tools

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.net.NetworkInterface
import java.net.SocketException

interface NetworkObserver {
    fun onNetworkChanged()
}

class NetworkMonitor(private val context: Context) : LifecycleObserver {
    private var registered = false
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val connected = MutableLiveData<Boolean>()
    @Volatile
    var isMobile = true
        private set
    @Volatile
    var isVPN = false
        private set
    private var networkObservers: MutableList<WeakReference<NetworkObserver>> = mutableListOf()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this@NetworkMonitor)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun start() {
        if (registered) return
        registered = true
        val networkFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        context.registerReceiver(receiver, networkFilter)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stop() {
        registered = false
        context.unregisterReceiver(receiver)
    }

    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun updateVPNStatus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (network in cm.allNetworks) {
                val nc = cm.getNetworkCapabilities(network) ?: return false
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true
            }
            return false
        } else {
            try {
                val networkInterfaces = NetworkInterface.getNetworkInterfaces()
                while (networkInterfaces.hasMoreElements()) {
                    val networkInterface = networkInterfaces.nextElement()
                    val name = networkInterface.displayName
                    if (name.startsWith("ppp") || name.startsWith("tun") || name.startsWith("tap"))
                        return true
                }
            } catch (ignored: SocketException) {}
            return false
        }
    }

    val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    val networkInfo = cm.activeNetworkInfo
                    val isConnected = networkInfo != null && networkInfo.isConnected
                    isMobile = isConnected && networkInfo!!.type == ConnectivityManager.TYPE_MOBILE
                    isVPN = isConnected && updateVPNStatus()
                    if (connected.value == null || isConnected != connected.value) {
                        connected.value = isConnected
                    }
                    networkObservers.forEach { it.get()?.onNetworkChanged() }
                }

            }
        }

    }

    companion object : SingletonHolder<NetworkMonitor, Context>({ NetworkMonitor(it) })
}
