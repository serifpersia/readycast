package app.readycast

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.connectsdk.device.ConnectableDevice
import com.connectsdk.service.webos.lgcast.screenmirroring.service.MirroringService

class MainActivity : AppCompatActivity() {
    companion object {
        const val MAIN_DISPLAY = 0
        private const val REQUEST_SHIZUKU = 4242
        val RESOLUTIONS = listOf(1920 to 1080, 1280 to 720, 960 to 540, 854 to 480)
        const val BITRATE_MIN_MBPS = 1
        const val BITRATE_MAX_MBPS = 50
        const val FPS_MIN = 15
        const val FPS_MAX = 60
        const val FPS_STEP = 5

        @Volatile var mirroring = false
        @Volatile var externalRunning = false
    }

    private lateinit var status: TextView
    private lateinit var statusDot: View
    private lateinit var toggle: MaterialButton
    private lateinit var source: Spinner
    private lateinit var encoderCard: LinearLayout
    private lateinit var tvSpinner: Spinner
    private var selectedTv: ConnectableDevice? = null
    private lateinit var resSpinner: Spinner
    private lateinit var bitrateBar: SeekBar
    private lateinit var bitrateLabel: TextView
    private lateinit var fpsBar: SeekBar
    private lateinit var fpsLabel: TextView
    @Volatile private var pendingExternal = false
    @Volatile private var pendingCapture = false

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK && it.data != null) {
            val ext = pendingExternal
            pendingExternal = false
            val tv = selectedTv
            if (tv == null) {
                say("Pick a TV first", true)
                markStopped()
                return@registerForActivityResult
            }
            if (ext) startExternal(tv, it.data!!) else SdkMirror.start(this, tv, it.data!!, false, ::say) { started ->
                if (!started) runOnUiThread { stop() }
            }
        } else {
            pendingExternal = false
            pendingCapture = false
            say("Screen capture denied", true)
            markStopped()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 4243)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun card(title: String, content: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = pillDrawable(resources.getColor(R.color.surface_container, null), 20)
        addView(TextView(context).apply {
            text = title
            setTextColor(resources.getColor(R.color.on_surface_variant, null))
            textSize = 12f
            letterSpacing = 0.08f
            setPadding(dp(2), 0, dp(2), dp(10))
        })
        addView(content)
    }

    private fun pillDrawable(color: Int, radius: Int = 14): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(color)
        }

    private fun spinner(items: List<String>, selected: Int = 0): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@MainActivity,
            R.layout.item_spinner,
            items
        ).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }
        setSelection(selected)
        background = pillDrawable(resources.getColor(R.color.surface_container_high, null), 12)
        setPadding(dp(14), dp(4), dp(14), dp(4))
        minimumHeight = dp(52)
    }

    private fun slider(bar: SeekBar) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        addView(bar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun buildUi() {
        val fgC = resources.getColor(R.color.on_surface, null)
        val dimC = resources.getColor(R.color.on_surface_variant, null)
        statusDot = View(this).apply {
            background = pillDrawable(dimC, 6)
        }
        status = TextView(this).apply {
            text = "Idle."
            textSize = 14f
            setTextColor(dimC)
        }
        source = spinner(listOf("Main display (phone)", "External display (desktop)"))
        source.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (::encoderCard.isInitialized) encoderCard.visibility = if (position == 0) View.GONE else View.VISIBLE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        resSpinner = spinner(RESOLUTIONS.map { "${it.first}x${it.second}" })
        bitrateLabel = TextView(this).apply {
            setTextColor(fgC)
            textSize = 14f
        }
        bitrateBar = SeekBar(this).apply {
            max = BITRATE_MAX_MBPS - BITRATE_MIN_MBPS
            progress = 5
        }
        fpsLabel = TextView(this).apply {
            setTextColor(fgC)
            textSize = 14f
        }
        fpsBar = SeekBar(this).apply {
            max = (FPS_MAX - FPS_MIN) / FPS_STEP
            progress = (60 - FPS_MIN) / FPS_STEP
        }
        fun refreshLabels() {
            bitrateLabel.text = "${bitrateMbps()} Mbps"
            fpsLabel.text = "${fps()} fps"
        }
        bitrateBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) = refreshLabels()
            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })
        fpsBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) = refreshLabels()
            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })
        refreshLabels()
        toggle = MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonStyle).apply {
            text = "Start mirroring"
            isAllCaps = false
            textSize = 16f
            cornerRadius = dp(16)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            minimumHeight = dp(56)
            setOnClickListener { mirrorButton() }
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(resources.getColor(R.color.background, null))
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(20), dp(16), dp(28))
                    addView(
                        TextView(this@MainActivity).apply {
                            text = "readycast"
                            setTextColor(fgC)
                            textSize = 28f
                            setPadding(dp(2), 0, dp(2), dp(2))
                        }
                    )
                    addView(
                        TextView(this@MainActivity).apply {
                            text = "Secondary-display casting for LG webOS"
                            setTextColor(dimC)
                            textSize = 13f
                            setPadding(dp(2), 0, dp(2), dp(18))
                        }
                    )
                    addView(card("TV", tvLayout()), spaced(top = 0))
                    addView(card("SOURCE", source), spaced(top = 16))
                    addView(toggle, spaced(top = 16))
                    encoderCard = card(
                        "ENCODER",
                        LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(resSpinner, spaced(top = 2))
                            addView(bitrateLabel, spaced(top = 18))
                            addView(slider(bitrateBar), spaced(top = 2))
                            addView(fpsLabel, spaced(top = 18))
                            addView(slider(fpsBar), spaced(top = 2))
                        }
                    )
                    encoderCard.visibility = if (source.selectedItemPosition == 0) View.GONE else View.VISIBLE
                    addView(encoderCard, spaced(top = 16))
                    addView(card("REMOTE", remoteLayout()), spaced(top = 16))
                    addView(
                        card(
                            "STATUS",
                            LinearLayout(this@MainActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                addView(statusDot, LinearLayout.LayoutParams(dp(10), dp(10)))
                                addView(
                                    status,
                                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                                        .apply { leftMargin = dp(10) }
                                )
                            }
                        ),
                        spaced(top = 16)
                    )
                    addView(
                        TextView(this@MainActivity).apply {
                            text = "v${appVersion()}"
                            setTextColor(dimC)
                            textSize = 12f
                            gravity = Gravity.CENTER
                        },
                        spaced(top = 16)
                    )
                }
            )
        }
        setContentView(scroll)
        syncUi()
    }

    override fun onStart() {
        super.onStart()
        if (::toggle.isInitialized) syncUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && mirroring) stop()
    }

    private fun mirrorButton() {
        if (mirroring) {
            stop()
            return
        }
        if (selectedTv == null) {
            say("Tap Search for TVs and pick one", true)
            return
        }
        mirroring = true
        toggle.text = "Stop mirroring"
        toggle.setBackgroundColor(resources.getColor(R.color.danger, null))
        setControlsEnabled(false)
        start()
    }

    private fun markStopped() {
        mirroring = false
        toggle.text = "Start mirroring"
        toggle.setBackgroundColor(resources.getColor(R.color.accent, null))
        setControlsEnabled(true)
    }

    private fun setControlsEnabled(on: Boolean) {
        source.isEnabled = on
        resSpinner.isEnabled = on
        bitrateBar.isEnabled = on
        fpsBar.isEnabled = on
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun syncUi() {
        toggle.text = if (mirroring) "Stop mirroring" else "Start mirroring"
        toggle.setBackgroundColor(
            resources.getColor(if (mirroring) R.color.danger else R.color.accent, null)
        )
        setControlsEnabled(!mirroring)
    }

    private fun spaced(top: Int = 10) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun tvLayout(): View {
        tvSpinner = spinner(listOf("No TVs found"))
        tvSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedTv = SdkMirror.devices().getOrNull(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val search = MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Search for TVs"
            isAllCaps = false
            textSize = 15f
            cornerRadius = dp(14)
            minimumHeight = dp(48)
            setOnClickListener {
                SdkMirror.onDevicesChanged = { runOnUiThread { fillTvList() } }
                SdkMirror.discover(this@MainActivity)
                say("Searching for TVs...", false)
            }
        }
        SdkMirror.onDevicesChanged = { runOnUiThread { fillTvList() } }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tvSpinner, spaced(top = 2))
            addView(search, spaced(top = 10))
        }
    }

    private fun fillTvList() {
        val devices = SdkMirror.devices()
        val previous = selectedTv?.id
        tvSpinner.adapter = ArrayAdapter(
            this,
            R.layout.item_spinner,
            if (devices.isEmpty()) listOf("No TVs found") else devices.map { "${it.friendlyName} (${it.ipAddress})" }
        ).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }
        val keep = devices.indexOfFirst { it.id == previous }
        if (keep >= 0) tvSpinner.setSelection(keep)
        selectedTv = devices.getOrNull(if (keep >= 0) keep else 0)
        say(
            if (devices.isEmpty()) "No TVs found yet." else "Found ${devices.size} TV(s).",
            devices.isEmpty()
        )
    }

    private fun remoteLayout(): View {
        fun remoteButton(text: String, filled: Boolean = false, onClick: () -> Unit) =
            MaterialButton(this, null, if (filled)
                com.google.android.material.R.attr.materialButtonStyle
            else
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                this.text = text
                textSize = 16f
                isAllCaps = false
                letterSpacing = 0.02f
                cornerRadius = dp(14)
                setPadding(dp(18), dp(14), dp(18), dp(14))
                minimumHeight = dp(54)
                setOnClickListener { onClick() }
            }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(remoteButton("Vol +", true) { withTv { TvControl.volumeUp(it, ::say) } }, lp(0))
            addView(remoteButton("Vol -") { withTv { TvControl.volumeDown(it, ::say) } }, lp(10))
            addView(remoteButton("Mute") { withTv { TvControl.toggleMute(it, ::say) } }, lp(10))
            addView(remoteButton("Power off") { withTv { TvControl.powerOff(it, ::say) } }, lp(10))
        }
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun applyEncoderSettings() {
        val (w, h) = RESOLUTIONS[resSpinner.selectedItemPosition]
        StreamService.maxWidth = w
        StreamService.maxHeight = h
        StreamService.bitRate = bitrateMbps() * 1_000_000
        StreamService.maxFps = fps()
        MirroringService.setExternalSource(w, h, StreamService.bitRate)
    }

    private fun bitrateMbps() = BITRATE_MIN_MBPS + bitrateBar.progress
    private fun fps() = FPS_MIN + fpsBar.progress * FPS_STEP

    private fun start() {
        applyEncoderSettings()
        if (source.selectedItemPosition == 0) startMain() else startExternal()
    }

    private fun startMain() {
        if (externalRunning) stop()
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        consent.launch(mpm.createScreenCaptureIntent())
    }

    private fun startExternal() {
        if (secondaryDisplayId() == null) {
            say("No external display. Plug HDMI (or force desktop mode) first.", true)
            markStopped()
            return
        }
        say("Checking for root...", false)
        Thread({
            val mode = try {
                ShellAccess.mode()
            } catch (e: Exception) {
                Log.e("CAST", "privilege check failed: $e")
                ShellAccess.NONE
            }
            runOnUiThread {
                if (mode == ShellAccess.NONE) {
                    if (rikka.shizuku.Shizuku.pingBinder()) {
                        rikka.shizuku.Shizuku.requestPermission(REQUEST_SHIZUKU)
                        say("Allow readycast to use Shizuku, then press start again.", false)
                    } else {
                        say("External display needs root or Shizuku. Start Shizuku (or root the phone).", true)
                    }
                    markStopped()
                    return@runOnUiThread
                }
                pendingExternal = true
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                consent.launch(mpm.createScreenCaptureIntent())
            }
        }, "priv-check").start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SHIZUKU) {
            say(
                if (ShellAccess.mode() == ShellAccess.NONE) "Shizuku permission denied" else "Shizuku ready",
                ShellAccess.mode() == ShellAccess.NONE
            )
        }
    }

    private fun startExternal(tv: ConnectableDevice, projection: Intent) {
        val secondary = secondaryDisplayId() ?: return
        externalRunning = true
        StreamService.displayId = secondary
        val au = java.io.ByteArrayOutputStream()
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        var t0 = 0L
        StreamService.nalSink = sink@{ _, nal ->
            if (nal.isEmpty()) return@sink
            val t = nal[0].toInt() and 0x1F
            when (t) {
                7 -> sps = nal
                8 -> pps = nal
                1, 5 -> {
                    if (t == 5) {
                        sps?.let { put(au, it) }
                        pps?.let { put(au, it) }
                    }
                    put(au, nal)
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (t0 == 0L) t0 = now
                    MirroringService.injectVideoFrame(au.toByteArray(), now - t0)
                    au.reset()
                }
                else -> put(au, nal)
            }
        }
        say("Mirroring desktop ID $secondary to TV...", false)
        pendingCapture = true
        SdkMirror.start(this, tv, projection, true, ::say) { started ->
            runOnUiThread {
                if (started && pendingCapture) {
                    pendingCapture = false
                    startForegroundService(Intent(this, StreamService::class.java))
                } else if (!started) {
                    pendingCapture = false
                    say("TV refused to start", true)
                    stop()
                }
            }
        }
    }

    private fun put(out: java.io.ByteArrayOutputStream, nal: ByteArray) {
        out.write(0); out.write(0); out.write(0); out.write(1)
        out.write(nal)
    }

    private fun stop() {
        markStopped()
        pendingCapture = false
        if (externalRunning) {
            externalRunning = false
            startForegroundService(Intent(this, StreamService::class.java).setAction("STOP"))
        }
        SdkMirror.stop { s, _ -> say(s, false) }
    }

    private fun withTv(block: (ConnectableDevice) -> Unit) {
        val d = selectedTv
        if (d == null) {
            say("Pick a TV first", true)
            return
        }
        try { block(d) } catch (e: Exception) { say("command failed: $e", true) }
    }

    private fun secondaryDisplayId(): Int? =
        (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .displays.firstOrNull { it.displayId != MAIN_DISPLAY }?.displayId

    private fun say(s: String, bad: Boolean) {
        Log.d("CAST", s.replace("\n", " | "))
        if (bad && s.startsWith("Mirror error")) runOnUiThread { stop() }
        runOnUiThread {
            status.text = s
            val dot = when {
                bad -> resources.getColor(R.color.danger, null)
                mirroring -> Color.parseColor("#30D158")
                else -> resources.getColor(R.color.on_surface_variant, null)
            }
            status.setTextColor(
                if (bad) resources.getColor(R.color.danger, null)
                else resources.getColor(R.color.on_surface_variant, null)
            )
            statusDot.background = pillDrawable(dot, 6)
        }
    }
}
