package app.readycast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class StreamService : Service() {
    private val running = AtomicBoolean(false)
    private var readerThread: Thread? = null
    private var proc: Process? = null
    private var scid = 0

    private fun socketName() = "scrcpy_%08x".format(scid)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = "cast"
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(channel, "Cast", NotificationManager.IMPORTANCE_LOW)
                )
        }
        startForeground(
            1,
            NotificationCompat.Builder(this, channel)
                .setContentTitle("readycast")
                .setContentText("Capturing display $displayId")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running.get()) startCapture()
        return START_NOT_STICKY
    }

    private fun startCapture() {
        running.set(true)
        try {
            ShellAccess.exec("pkill -f scrcpy.Server")?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {}
        scid = (System.nanoTime() and 0xFFFF).toInt() * 7919 % 0xFFFFFF
        val jar = File(filesDir, "scrcpy-server")
        try {
            assets.open("scrcpy-server").use { input -> jar.outputStream().use { input.copyTo(it) } }
        } catch (e: Exception) {
            Log.e(TAG, "could not unpack scrcpy-server: $e")
            running.set(false)
            return
        }

        val cmd = "CLASSPATH=${jar.absolutePath} app_process /system/bin " +
            "com.genymobile.scrcpy.Server $SCRCPY_VERSION " +
            "scid=%08x".format(scid) + " tunnel_forward=true video=true audio=false control=false " +
            "video_codec=h264 " +
            "video_bit_rate=$bitRate max_fps=$maxFps max_size=$maxWidth display_id=$displayId " +
            "show_touches=false stay_awake=true cleanup=false " +
            "send_device_meta=false send_stream_meta=false send_frame_meta=false send_dummy_byte=false"
        Log.d(TAG, "exec: $cmd")

        try {
            proc = ShellAccess.exec(cmd)
        } catch (e: Exception) {
            Log.e(TAG, "capture failed to start: $e")
            proc = null
        }
        if (proc == null) {
            Log.e(TAG, "no usable privilege: install Magisk or start Shizuku")
            running.set(false)
            return
        }
        Log.d(TAG, "started via ${ShellAccess.mode()}")
        logStream(proc!!.errorStream, "err")
        logStream(proc!!.inputStream, "out")

        readerThread = Thread({
            val splitter = NalSplitter()
            val chunk = ByteArray(64 * 1024)
            var bytes = 0L
            val start = System.currentTimeMillis()
            var lastReport = start
            var socket: LocalSocket? = null
            try {
                while (running.get()) {
                    try {
                        val s = LocalSocket()
                        s.connect(LocalSocketAddress(socketName(), LocalSocketAddress.Namespace.ABSTRACT))
                        socket = s
                        break
                    } catch (_: Exception) {
                        Thread.sleep(100)
                    }
                }
                if (socket == null) {
                    Log.e(TAG, "scrcpy-server never opened its socket")
                    return@Thread
                }
                Log.d(TAG, "connected to scrcpy-server, capturing display $displayId")
                val input = socket!!.inputStream
                while (running.get()) {
                    val n = try {
                        input.read(chunk)
                    } catch (_: Exception) {
                        break
                    }
                    if (n <= 0) break
                    bytes += n
                    for (nal in splitter.feed(chunk, n)) {
                        val sink = nalSink
                        if (sink != null) sink(nal[0].toInt() and 0x1F, nal)
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 5000) {
                        Log.d(TAG, "captured ${bytes / 1024}KB in ${(now - start) / 1000}s from display $displayId")
                        lastReport = now
                    }
                }
            } catch (e: Exception) {
                if (running.get()) Log.e(TAG, "reader failed: $e")
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }
        }, "scrcpy-reader").apply { start() }
    }

    private fun logStream(stream: java.io.InputStream, tag: String) {
        Thread({
            try {
                stream.bufferedReader().forEachLine { Log.d(TAG, "$tag: $it") }
            } catch (_: Exception) {
            }
        }, "scrcpy-$tag").apply { isDaemon = true; start() }
    }

    private fun stopCapture() {
        running.set(false)
        try {
            proc?.destroy()
        } catch (_: Exception) {}
        try {
            ShellAccess.exec("pkill -f scrcpy.Server")?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {}
        readerThread?.interrupt()
        nalSink = null
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "READYCAST"
        private const val SCRCPY_VERSION = "4.1"

        @Volatile var displayId = 0
        @Volatile var maxWidth = 1920
        @Volatile var bitRate = 6_000_000
        @Volatile var maxFps = 60

@Volatile var nalSink: ((Int, ByteArray) -> Unit)? = null
    }
}
