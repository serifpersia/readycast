package app.readycast

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

object ShellAccess {
    private const val TAG = "READYCAST"

    const val ROOT = "root"
    const val SHIZUKU = "shizuku"
    const val NONE = "none"

    private var rootChecked = false
    private var rootWorks = false

    fun rootAvailable(): Boolean {
        if (rootChecked) return rootWorks
        rootWorks = try {
            java.util.concurrent.Executors.newSingleThreadExecutor().submit<Boolean> {
                try {
                    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                    p.outputStream.close()
                    val out = p.inputStream.bufferedReader().readLine() ?: ""
                    p.errorStream.close()
                    p.destroy()
                    out.contains("uid=0")
                } catch (e: Exception) {
                    Log.d(TAG, "no root: $e")
                    false
                }
            }.get(6, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.d(TAG, "root check timed out: $e")
            false
        }
        rootChecked = true
        return rootWorks
    }

    fun shizukuAvailable(): Boolean =
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun mode(): String = when {
        rootAvailable() -> ROOT
        shizukuAvailable() -> SHIZUKU
        else -> NONE
    }

    fun exec(command: String): Process? = when (mode()) {
        ROOT -> try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        } catch (e: Exception) {
            Log.e(TAG, "root exec failed: $e")
            null
        }

        SHIZUKU -> try {
            Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        } catch (e: Exception) {
            Log.e(TAG, "shizuku exec failed: $e")
            null
        }

        else -> {
            Log.e(TAG, "neither root nor Shizuku is available")
            null
        }
    }
}
