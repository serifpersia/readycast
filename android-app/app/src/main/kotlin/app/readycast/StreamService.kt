package app.readycast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 0, Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setOngoing(true)
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
            ShellAccess.exec("pkill -f com.readycast.caster")?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {}
        val jar = File(filesDir, "caster-server")
        try {
            assets.open("caster-server").use { input -> jar.outputStream().use { input.copyTo(it) } }
        } catch (e: Exception) {
            Log.e(TAG, "could not unpack caster-server: $e")
            running.set(false)
            return
        }

        val cmd = "CLASSPATH=${jar.absolutePath} app_process /system/bin " +
            "com.readycast.caster.CastServer " +
            "display_id=$displayId max_width=$maxWidth max_height=$maxHeight " +
            "video_bit_rate=$bitRate max_fps=$maxFps"
        Log.d(TAG, "advertised to TV: ${maxWidth}x$maxHeight")

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

        readerThread = Thread({
            val splitter = NalSplitter()
            val chunk = ByteArray(64 * 1024)
            var bytes = 0L
            val start = System.currentTimeMillis()
            var lastReport = start
            val input = proc!!.inputStream
            Log.d(TAG, "connected to caster, capturing display $displayId")
            try {
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
            }
        }, "caster-reader").apply { start() }
    }

    private fun logStream(stream: java.io.InputStream, tag: String) {
        Thread({
            try {
                stream.bufferedReader().forEachLine { Log.d(TAG, "$tag: $it") }
            } catch (_: Exception) {
            }
        }, "caster-$tag").apply { isDaemon = true; start() }
    }

    private fun stopCapture() {
        running.set(false)
        try {
            proc?.destroy()
        } catch (_: Exception) {}
        try {
            ShellAccess.exec("pkill -f com.readycast.caster")?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
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

        @Volatile var displayId = 0
        @Volatile var maxWidth = 1920
        @Volatile var maxHeight = 1080
        @Volatile var bitRate = 6_000_000
        @Volatile var maxFps = 60

@Volatile var nalSink: ((Int, ByteArray) -> Unit)? = null
    }
}
