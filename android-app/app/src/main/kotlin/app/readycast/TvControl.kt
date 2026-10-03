package app.readycast

import android.util.Log
import com.connectsdk.device.ConnectableDevice
import com.connectsdk.service.WebOSTVService
import com.connectsdk.service.capability.VolumeControl
import com.connectsdk.service.capability.listeners.ResponseListener
import com.connectsdk.service.command.ServiceCommand
import com.connectsdk.service.command.ServiceCommandError

object TvControl {
    private const val TAG = "TVCTL"
    private const val GET_POWER = "ssap://com.webos.service.tvpower/power/getPowerState"
    private const val TURN_OFF = "ssap://system/turnOff"

    @Volatile private var muted = false

    private fun listener(what: String, status: (String, Boolean) -> Unit, done: String? = null) =
        object : ResponseListener<Any> {
            override fun onSuccess(responseObject: Any) {
                Log.d(TAG, "$what ok")
                if (done != null) status(done, false)
            }

            override fun onError(error: ServiceCommandError) {
                Log.e(TAG, "$what failed: $error")
                status("$what failed: ${error.message}", true)
            }
        }

    private inline fun withVolume(
        device: ConnectableDevice,
        what: String,
        status: (String, Boolean) -> Unit,
        block: (VolumeControl) -> Unit
    ) {
        val vol = device.getCapability(VolumeControl::class.java)
        if (vol == null) {
            status("TV has no volume control", true)
            return
        }
        block(vol)
    }

    fun volumeUp(device: ConnectableDevice, status: (String, Boolean) -> Unit) =
        withVolume(device, "volumeUp", status) { it.volumeUp(listener("volumeUp", status)) }

    fun volumeDown(device: ConnectableDevice, status: (String, Boolean) -> Unit) =
        withVolume(device, "volumeDown", status) { it.volumeDown(listener("volumeDown", status)) }

    fun toggleMute(device: ConnectableDevice, status: (String, Boolean) -> Unit) =
        withVolume(device, "mute", status) {
            muted = !muted
            it.setMute(muted, listener("mute", status, if (muted) "TV muted" else "TV unmuted"))
        }

    fun powerOff(device: ConnectableDevice, status: (String, Boolean) -> Unit) {
        val svc = device.getServiceByName(WebOSTVService.ID) as? WebOSTVService
        if (svc == null) {
            status("TV power control unavailable", true)
            return
        }
        ServiceCommand<ResponseListener<Any>>(
            svc,
            TURN_OFF,
            null,
            true,
            object : ResponseListener<Any> {
                override fun onSuccess(responseObject: Any) {
                    Log.d(TAG, "turnOff ok")
                    status("TV powering off", false)
                }

                override fun onError(error: ServiceCommandError) {
                    Log.e(TAG, "turnOff failed: $error")
                    status("turnOff failed: ${error.message}", true)
                }
            }
        ).send()
    }

    private fun send(
        svc: WebOSTVService,
        uri: String,
        status: (String, Boolean) -> Unit,
        onSuccess: (Any) -> Unit
    ) {
        ServiceCommand<ResponseListener<Any>>(
            svc,
            uri,
            null,
            true,
            object : ResponseListener<Any> {
                override fun onSuccess(responseObject: Any) {
                    Log.d(TAG, "$uri ok")
                    onSuccess(responseObject)
                }

                override fun onError(error: ServiceCommandError) {
                    Log.e(TAG, "$uri failed: $error")
                    status("$uri failed: ${error.message}", true)
                }
            }
        ).send()
    }
}
