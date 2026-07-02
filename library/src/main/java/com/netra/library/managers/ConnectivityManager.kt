import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.annotation.RequiresPermission
import com.netra.library.enums.NetworkSeverity
import com.netra.library.managers.ObserverManager
import com.netra.library.managers.OfflineQueueManager
import com.netra.library.observers.NetworkEvent

internal class NetraConnectivityManager private constructor(
    private val context: Context
) {
    var connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isConnected(): Boolean {
        val network = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(network)
        val isConnectedResult =
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
        if (!isConnectedResult) {
            ObserverManager.notifyNetworkEvent(NetworkEvent.Offline)
        }
        return isConnectedResult
    }

    fun getNetworkSpeedState(): NetworkSeverity {
        val network = connectivityManager.activeNetwork ?: return NetworkSeverity.NORMAL
        val caps = connectivityManager.getNetworkCapabilities(network)
            ?: return NetworkSeverity.NORMAL

        val result = when {
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) -> {
                ObserverManager.notifyNetworkEvent(NetworkEvent.SlowNetwork)
                return NetworkSeverity.DEGRADED
            }

            else -> NetworkSeverity.NORMAL
        }
        return result
    }

    fun init() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                ObserverManager.notifyNetworkEvent(NetworkEvent.ConnectionRestored)
                OfflineQueueManager.processQueue()
                super.onAvailable(network)
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    companion object {
        @Volatile
        private var instance: NetraConnectivityManager? = null

        fun getInstance(context: Context): NetraConnectivityManager {
            return (instance ?: synchronized(this) {
                instance ?: NetraConnectivityManager(context).also {
                    instance = it
                }
            })
        }
    }
}