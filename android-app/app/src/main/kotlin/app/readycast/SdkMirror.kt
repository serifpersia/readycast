package app.readycast

import android.content.Context
import android.content.Intent
import android.util.Log
import com.connectsdk.device.ConnectableDevice
import com.connectsdk.device.ConnectableDeviceListener
import com.connectsdk.discovery.CapabilityFilter
import com.connectsdk.discovery.DiscoveryManager
import com.connectsdk.discovery.DiscoveryManagerListener
import com.connectsdk.service.DeviceService
import com.connectsdk.service.capability.ScreenMirroringControl
import com.connectsdk.service.command.ServiceCommandError
import java.util.concurrent.CopyOnWriteArrayList

object SdkMirror {
    private var mirror: ScreenMirroringControl? = null
    private var appContext: Context? = null
    private var onStatus: ((String, Boolean) -> Unit)? = null
    private var onStarted: ((Boolean) -> Unit)? = null
    private val found = CopyOnWriteArrayList<ConnectableDevice>()

    @Volatile var onDevicesChanged: (() -> Unit)? = null

    private val listener = object : DiscoveryManagerListener {
        override fun onDeviceAdded(manager: DiscoveryManager, device: ConnectableDevice) = onDevice(device)
        override fun onDeviceUpdated(manager: DiscoveryManager, device: ConnectableDevice) = onDevice(device)
        override fun onDeviceRemoved(manager: DiscoveryManager, device: ConnectableDevice) {
            if (found.remove(device)) onDevicesChanged?.invoke()
        }

        override fun onDiscoveryFailed(manager: DiscoveryManager, error: ServiceCommandError) {
            Log.e("SDKMIRROR", "discovery failed: $error")
            say("Discovery failed: $error", true)
        }
    }

    fun devices(): List<ConnectableDevice> = found.toList()

    fun withConnection(device: ConnectableDevice, block: () -> Unit) {
        if (device.isConnected) {
            block()
            return
        }
        val once = object : ConnectableDeviceListener {
            override fun onDeviceReady(device: ConnectableDevice) {
                device.removeListener(this)
                block()
            }

            override fun onDeviceDisconnected(device: ConnectableDevice) {
                device.removeListener(this)
            }

            override fun onConnectionFailed(device: ConnectableDevice, error: ServiceCommandError) {
                device.removeListener(this)
                Log.e("SDKMIRROR", "connect failed: $error")
            }

            override fun onCapabilityUpdated(
                device: ConnectableDevice,
                added: MutableList<String>,
                removed: MutableList<String>
            ) = Unit

            override fun onPairingRequired(
                device: ConnectableDevice,
                service: DeviceService,
                pairingType: DeviceService.PairingType
            ) {
                device.removeListener(this)
            }
        }
        device.addListener(once)
        device.connect()
    }

    fun discover(ctx: Context) {
        appContext = ctx.applicationContext
        val dm = discoveryManager(ctx) ?: return
        dm.start()
    }

    fun forget(device: ConnectableDevice) {
        if (found.remove(device)) onDevicesChanged?.invoke()
    }

    private fun onDevice(device: ConnectableDevice) {
        val mir = try {
            device.getCapability(ScreenMirroringControl::class.java)
        } catch (e: Exception) {
            Log.e("SDKMIRROR", "getCapability: $e"); null
        }
        if (mir == null) return

        var changed = false
        if (found.none { it.id == device.id }) {
            found.add(device)
            changed = true
        } else {
            val i = found.indexOfFirst { it.id == device.id }
            if (i >= 0) found[i] = device
        }
        if (changed) onDevicesChanged?.invoke()

        val target = pendingTarget ?: return
        if (target.id != device.id || mirror != null) return
        begin(device, pendingProjection)
    }

    @Volatile private var pendingProjection: Intent? = null

    @Volatile private var pendingTarget: ConnectableDevice? = null

    @Volatile var externalVideo = false

    fun start(
        ctx: Context,
        target: ConnectableDevice,
        projection: Intent?,
        external: Boolean,
        status: (String, Boolean) -> Unit,
        onStarted: ((Boolean) -> Unit)? = null
    ) {
        appContext = ctx.applicationContext
        onStatus = status
        this.onStarted = onStarted
        externalVideo = external
        mirror = null
        pendingTarget = target
        pendingProjection = projection
        if (projection == null) {
            pendingTarget = null
            say("No projection consent captured", true)
            return
        }
        if (target.id in found.map { it.id }) {
            val i = found.indexOfFirst { it.id == target.id }
            begin(found[i], projection)
        } else {
            pendingProjection = projection
            val dm = discoveryManager(ctx) ?: return
            say("Looking for ${target.friendlyName}...", false)
            dm.start()
        }
    }

    private fun begin(device: ConnectableDevice, projection: Intent?) {
        val mir = device.getCapability(ScreenMirroringControl::class.java)
        if (mir == null) {
            say("TV has no screen-mirroring capability", true)
            return
        }
        pendingProjection = null
        pendingTarget = null
        mirror = mir
        say("TV found: ${device.friendlyName}, starting...", false)
        com.connectsdk.service.webos.lgcast.screenmirroring.service.MirroringService
            .setExternalVideo(externalVideo)
        mir.setErrorListener(appContext, object : ScreenMirroringControl.ScreenMirroringErrorListener {
            override fun onError(error: ScreenMirroringControl.ScreenMirroringError) {
                Log.e("SDKMIRROR", "error: $error")
                say("Mirror error: $error", true)
            }
        })
        mir.startScreenMirroring(
            appContext!!,
            projection,
            object : ScreenMirroringControl.ScreenMirroringStartListener {
                override fun onPairing() {
                    say("Pairing - accept the prompt on the TV", false)
                }

                override fun onStart(result: Boolean, secondScreen: android.app.Presentation?) {
                    say(if (result) "Mirroring running - check the TV" else "TV refused to start", !result)
                    val cb = onStarted
                    onStarted = null
                    cb?.invoke(result)
                }
            }
        )
    }

    private fun discoveryManager(ctx: Context): DiscoveryManager? {
        DiscoveryManager.init(ctx.applicationContext)
        val dm = try {
            DiscoveryManager.getInstance()
        } catch (_: Throwable) {
            return null
        }
        dm.setServiceIntegration(true)
        dm.setCapabilityFilters(CapabilityFilter(ScreenMirroringControl.ScreenMirroring))
        dm.setPairingLevel(DiscoveryManager.PairingLevel.PROTECTED)
        dm.registerDefaultDeviceTypes()
        dm.addListener(listener)
        return dm
    }

    fun stop(status: (String, Boolean) -> Unit) {
        onStatus = status
        val mir = mirror
        mirror = null
        pendingProjection = null
        pendingTarget = null
        onStarted = null
        externalVideo = false
        com.connectsdk.service.webos.lgcast.screenmirroring.service.MirroringService
            .setExternalVideo(false)
        val dm = try { DiscoveryManager.getInstance() } catch (_: Throwable) { null }
        if (mir != null && appContext != null) {
            mir.stopScreenMirroring(appContext!!, object : ScreenMirroringControl.ScreenMirroringStopListener {
                override fun onStop(result: Boolean) {
                    say(if (result) "Stopped." else "Stop reported false", !result)
                }
            })
        } else {
            say("Stopped.", false)
        }
    }

    private fun say(s: String, bad: Boolean) {
        Log.d("SDKMIRROR", s)
        onStatus?.invoke(s, bad)
    }
}
