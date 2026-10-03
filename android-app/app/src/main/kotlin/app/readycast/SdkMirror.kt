package app.readycast

import android.content.Context
import android.content.Intent
import android.util.Log
import com.connectsdk.device.ConnectableDevice
import com.connectsdk.discovery.CapabilityFilter
import com.connectsdk.discovery.DiscoveryManager
import com.connectsdk.discovery.DiscoveryManagerListener
import com.connectsdk.service.command.ServiceCommandError
import com.connectsdk.service.capability.ScreenMirroringControl

object SdkMirror {
    private var mirror: ScreenMirroringControl? = null
    private var appContext: Context? = null
    private var onStatus: ((String, Boolean) -> Unit)? = null
    private var onStarted: ((Boolean) -> Unit)? = null
    private val listener = object : DiscoveryManagerListener {
        override fun onDeviceAdded(manager: DiscoveryManager, device: ConnectableDevice) = onDevice(device)
        override fun onDeviceUpdated(manager: DiscoveryManager, device: ConnectableDevice) = onDevice(device)
        override fun onDeviceRemoved(manager: DiscoveryManager, device: ConnectableDevice) = Unit
        override fun onDiscoveryFailed(manager: DiscoveryManager, error: ServiceCommandError) {
            Log.e("SDKMIRROR", "discovery failed: $error")
            say("Discovery failed: $error", true)
        }
    }

    private fun onDevice(device: ConnectableDevice) {
        if (mirror != null) return
        discovered = device
        val mir = try {
            device.getCapability(ScreenMirroringControl::class.java)
        } catch (e: Exception) {
            Log.e("SDKMIRROR", "getCapability: $e"); null
        }
        if (mir == null) {
            say("TV found but no screen-mirroring capability", true)
            return
        }
        val projection = pendingProjection
        pendingProjection = null
        if (projection == null && !externalVideo) {
            say("No projection consent captured", true)
            return
        }
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

    @Volatile private var pendingProjection: Intent? = null

    @Volatile private var discovered: ConnectableDevice? = null

    fun device(): ConnectableDevice? = discovered

    @Volatile var externalVideo = false

    fun start(ctx: Context, projection: Intent?, external: Boolean, status: (String, Boolean) -> Unit, onStarted: ((Boolean) -> Unit)? = null) {
        appContext = ctx.applicationContext
        onStatus = status
        this.onStarted = onStarted
        pendingProjection = projection
        externalVideo = external
        mirror = null
        DiscoveryManager.init(ctx.applicationContext)
        val dm = DiscoveryManager.getInstance()
        dm.setCapabilityFilters(CapabilityFilter(ScreenMirroringControl.ScreenMirroring))
        dm.setPairingLevel(DiscoveryManager.PairingLevel.PROTECTED)
        dm.registerDefaultDeviceTypes()
        dm.addListener(listener)
        dm.start()
        say("Looking for a mirroring-capable TV...", false)
    }

    fun stop(status: (String, Boolean) -> Unit) {
        onStatus = status
        val mir = mirror
        mirror = null
        pendingProjection = null
        onStarted = null
        externalVideo = false
        com.connectsdk.service.webos.lgcast.screenmirroring.service.MirroringService
            .setExternalVideo(false)
val dm = try { DiscoveryManager.getInstance() } catch (_: Throwable) { null }
        dm?.removeListener(listener)
        if (mir != null && appContext != null) {
            mir.stopScreenMirroring(appContext!!, object : ScreenMirroringControl.ScreenMirroringStopListener {
                override fun onStop(result: Boolean) {
                    say(if (result) "Stopped." else "Stop reported false", !result)
                }
            })
        } else {
            say("Stopped.", false)
        }
        try { dm?.stop() } catch (_: Exception) {}
    }

    fun running() = mirror != null

    private fun say(s: String, bad: Boolean) {
        Log.d("SDKMIRROR", s)
        onStatus?.invoke(s, bad)
    }
}
