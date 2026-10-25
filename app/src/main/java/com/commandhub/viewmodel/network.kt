package com.commandhub.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.commandhub.server.WebAssetLoader
import com.commandhub.server.routes.configureBluetoothRoutes
import com.commandhub.server.routes.configureCameraRoutes
import com.commandhub.server.routes.configureCoreRoutes
import com.commandhub.server.routes.configureFlashlightRoutes
import com.commandhub.server.routes.configureHardwareRoutes
import com.commandhub.server.routes.configureSensorRoutes
import com.commandhub.server.routes.configureTcpRoutes
import com.commandhub.server.routes.configureUartRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.Serializable
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

@Serializable
data class LogRequest(val id: String = "", val text: String = "")

@Serializable
data class TcpConnectRequest(val ip: String = "", val port: Int = 8888)

@Serializable
data class FlashlightRequest(val enabled: Boolean = false)

class DashboardServer(
    private val context: Context,
    private val port: Int = 8080,
    private val layoutProvider: () -> String,
    private val getProcessingUrl: () -> String = { "" },
    private val setProcessingUrl: (String) -> Unit = {},
    private val publishDomain: () -> Unit = {},
    private val setKeepScreenOnEnabled: (Boolean) -> Unit = {},
    private val uartManager: UartManager,
    private val bluetoothManager: BluetoothClassicManager,
    private val cameraManager: CameraManager,
    private val sensorManager: SensorManager,
    private val tcpManager: TcpManager,
    private val hardwareManager: HardwareManager,
    private val onLogReceived: (String, String) -> Unit,
) {
    private val webAssetLoader = WebAssetLoader(context)
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var nsdManager: NsdManager? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceName = "command-hub"
    private val serviceType = "_http._tcp."
    private var registrationListener: NsdManager.RegistrationListener? = null

    private val registeredEndpoints =
        Collections.synchronizedList(mutableListOf<Pair<String, String>>())

    @SuppressLint("WakelockTimeout")
    fun start() {
        registeredEndpoints.clear()
        // Acquire Locks to prevent sleep and throttling
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("CommandHubLock").apply {
                setReferenceCounted(true)
                acquire()
            }

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CommandHub:WakeLock",
            ).apply {
                setReferenceCounted(false)
                // Held until stop() releases it, so the server stays alive while the app runs.
                acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                install(ContentNegotiation) { json() }
                install(WebSockets) {
                    pingPeriod = 20.seconds
                    timeout = 40.seconds
                    maxFrameSize = Long.MAX_VALUE
                    masking = false
                }

                routing {
                    fun register(path: String, name: String, body: suspend RoutingContext.() -> Unit) {
                        registeredEndpoints.add(path to name)
                        get(path) { body() }
                    }

                    configureCoreRoutes(
                        assetLoader = webAssetLoader,
                        layoutProvider = layoutProvider,
                        getProcessingUrl = getProcessingUrl,
                        setProcessingUrl = setProcessingUrl,
                        publishDomain = publishDomain,
                        setKeepScreenOnEnabled = setKeepScreenOnEnabled,
                        onLogReceived = onLogReceived,
                        registeredEndpoints = registeredEndpoints,
                        register = ::register,
                    )
                    configureBluetoothRoutes(webAssetLoader, bluetoothManager, ::register)
                    configureTcpRoutes(webAssetLoader, tcpManager, ::register)
                    configureUartRoutes(webAssetLoader, uartManager, ::register)
                    configureCameraRoutes(webAssetLoader, cameraManager, ::register)
                    configureSensorRoutes(webAssetLoader, sensorManager, ::register)
                    configureHardwareRoutes(webAssetLoader, hardwareManager, ::register)
                    configureFlashlightRoutes(cameraManager)
                }
            }.start(wait = false)
            registerService(port)
        } catch (e: Exception) {
            Log.e("DashboardServer", "Failed to start Ktor server on port $port", e)
        }
    }

    fun registerService(port: Int = this.port) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        registrationListener?.let {
            try {
                nsdManager?.unregisterService(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            registrationListener = null
        }

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = this@DashboardServer.serviceName
            serviceType = this@DashboardServer.serviceType
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                println("mDNS: Service registered")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }

        try {
            nsdManager?.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener,
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        multicastLock?.let { if (it.isHeld) it.release() }
        wakeLock?.let { if (it.isHeld) it.release() }
        server?.stop(1000, 2000)
        nsdManager?.unregisterService(registrationListener)
        registrationListener = null
    }
}
