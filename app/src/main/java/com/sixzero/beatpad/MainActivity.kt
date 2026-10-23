package com.sixzero.beatpad

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private val slotNames = listOf("A", "B", "C", "D")
    private lateinit var project: Project
    private lateinit var engine: BeatEngine
    private lateinit var kit: Kit

    private lateinit var grid: SequencerView
    private lateinit var pads: PadsView
    private lateinit var playBtn: Pill
    private lateinit var recBtn: Pill
    private lateinit var chainBtn: Pill
    private lateinit var hint: TextView
    private val slotBtns = mutableListOf<Pill>()
    private val kitBtns = mutableListOf<Pill>()
    private val tabBtns = mutableListOf<Pill>()
    private val trackLabelViews = mutableListOf<TextView>()
    private val mixerLabels = mutableListOf<TextView>()

    private var recording = false
    private var page = 0 // 0 seq, 1 pads, 2 mix

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        project = Project(tracks = 8, steps = 16, slots = slotNames.size)
        if (!Store.load(this, project)) defaultPattern()
        engine = BeatEngine(project)
        kit = Kits.load(this, project.kit)
        engine.samples = kit.samples

        grid = findViewById(R.id.grid)
        pads = findViewById(R.id.pads)
        hint = findViewById(R.id.hint)
        grid.tracks = project.tracks
        grid.muted = project.muted
        grid.pattern = project.patterns[project.slot]
        grid.onChange = { save() }

        setupKits()
        setupTransport()
        setupSlots()
        setupSliders()
        setupTrackLabels()
        setupPads()
        setupMixer()
        setupTabs()
        applyKitNames()

        engine.onStep = { slot, step ->
            runOnUiThread {
                grid.playhead = if (slot == project.slot) step else -1
                if (project.chain && step >= 0 && slot != project.slot) selectSlot(slot, fromPlayback = true)
            }
        }
        engine.start()
    }

    private fun defaultPattern() {
        val p = project.patterns[0]
        p[0][0] = ACCENT; p[0][8] = HIT; p[0][10] = HIT
        p[1][4] = ACCENT; p[1][12] = ACCENT
        for (s in 0 until 16 step 2) p[3][s] = if (s % 4 == 0) ACCENT else HIT
        p[7][0] = HIT; p[7][10] = HIT
    }

    private fun save() = Store.save(this, project)
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun lp(weight: Float, h: Int = LinearLayout.LayoutParams.MATCH_PARENT, end: Int = 6) =
        LinearLayout.LayoutParams(0, h, weight).apply { marginEnd = end.dp() }

    // ---- kits ----
    private fun setupKits() {
        val row = findViewById<LinearLayout>(R.id.kitRow)
        Kits.names.forEach { name ->
            val b = Pill(this).apply {
                text = name; textSize = 11f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT).apply { marginStart = 5.dp() }
                setOnClickListener { switchKit(name) }
            }
            row.addView(b); kitBtns.add(b)
        }
        refreshKits()
    }

    private fun switchKit(name: String) {
        if (name == project.kit) return
        project.kit = name
        refreshKits()
        thread {
            val k = Kits.load(this, name)
            runOnUiThread { kit = k; engine.samples = k.samples; applyKitNames(); save() }
        }
    }

    private fun refreshKits() {
        kitBtns.forEach { b ->
            val on = b.text.toString() == project.kit
            b.style(if (on) Palette.cyan else Palette.panel, if (on) Color.BLACK else Palette.dim)
        }
    }

    private fun applyKitNames() {
        kit.trackNames.forEachIndexed { i, n ->
            trackLabelViews.getOrNull(i)?.text = n
            mixerLabels.getOrNull(i)?.text = n
        }
        pads.names = kit.trackNames
    }

    // ---- transport ----
    private fun setupTransport() {
        val row = findViewById<LinearLayout>(R.id.transport)
        playBtn = Pill(this).apply {
            text = "PLAY"; textSize = 15f
            layoutParams = lp(2f)
            style(Palette.accent)
            setOnClickListener { togglePlay() }
        }
        recBtn = Pill(this).apply {
            text = "● REC"
            layoutParams = lp(1f)
            setOnClickListener { recording = !recording; refreshRec() }
        }
        val clear = Pill(this).apply {
            text = "CLEAR"
            layoutParams = lp(1f, end = 0)
            setOnClickListener {
                for (t in project.patterns[project.slot]) t.fill(OFF)
                grid.invalidate(); save()
            }
        }
        row.addView(playBtn); row.addView(recBtn); row.addView(clear)
        refreshRec()
    }

    private fun togglePlay() {
        if (engine.playing.get()) { engine.stop(); playBtn.text = "PLAY"; playBtn.style(Palette.accent) }
        else { engine.play(); playBtn.text = "STOP"; playBtn.style(Palette.panel, Palette.accent, Palette.accent) }
    }

    private fun refreshRec() {
        if (recording) recBtn.style(Color.parseColor("#3A1818"), Palette.accent, Palette.accent)
        else recBtn.style(Palette.cell, Palette.dim)
    }

    // ---- slots ----
    private fun setupSlots() {
        val row = findViewById<LinearLayout>(R.id.slotRow)
        slotNames.forEachIndexed { i, n ->
            val b = Pill(this).apply {
                text = n
                layoutParams = lp(1f)
                setOnClickListener { selectSlot(i) }
                setOnLongClickListener { project.chainOn[i] = !project.chainOn[i]; refreshSlots(); save(); true }
            }
            row.addView(b); slotBtns.add(b)
        }
        chainBtn = Pill(this).apply {
            text = "CHAIN"; textSize = 11f
            layoutParams = lp(1.5f, end = 0)
            setOnClickListener { project.chain = !project.chain; refreshSlots(); save() }
        }
        row.addView(chainBtn)
        refreshSlots()
    }

    private fun selectSlot(i: Int, fromPlayback: Boolean = false) {
        project.slot = i
        grid.pattern = project.patterns[i]
        refreshSlots()
        if (!fromPlayback) save()
    }

    private fun refreshSlots() {
        slotBtns.forEachIndexed { i, b ->
            val sel = i == project.slot; val inChain = project.chainOn[i]
            b.text = slotNames[i] + (if (inChain) " •" else "")
            when {
                sel -> b.style(Palette.accent, Color.WHITE)
                inChain -> b.style(Palette.panel, Palette.text, Color.parseColor("#5A3A3A"))
                else -> b.style(Palette.panel, Palette.dim)
            }
        }
        if (project.chain) chainBtn.style(Palette.cyan, Color.BLACK) else chainBtn.style(Palette.panel, Palette.dim)
    }

    // ---- bpm + swing ----
    private fun setupSliders() {
        val bpmLabel = findViewById<TextView>(R.id.bpmLabel)
        val bpmSeek = findViewById<SeekBar>(R.id.bpmSeek)
        bpmSeek.max = 200 - 60
        bpmSeek.progress = project.bpm - 60
        bpmLabel.text = "${project.bpm} BPM"
        bpmSeek.onChange { p -> project.bpm = p + 60; bpmLabel.text = "${project.bpm} BPM" }

        val swingLabel = findViewById<TextView>(R.id.swingLabel)
        val swingSeek = findViewById<SeekBar>(R.id.swingSeek)
        swingSeek.max = 100
        swingSeek.progress = project.swing
        swingLabel.text = "SWING ${project.swing}"
        swingSeek.onChange { p -> project.swing = p; swingLabel.text = "SWING $p" }
    }

    private fun SeekBar.onChange(f: (Int) -> Unit) {
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { f(p) }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { save() }
        })
    }

    // ---- track labels: tap = preview, hold = mute ----
    private fun setupTrackLabels() {
        val labels = findViewById<LinearLayout>(R.id.trackLabels)
        for (i in 0 until project.tracks) {
            val tv = TextView(this).apply {
                textSize = 11f
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                setOnClickListener { engine.trigger(i, accent = true) }
                setOnLongClickListener { toggleMute(i); true }
            }
            labels.addView(tv); trackLabelViews.add(tv); refreshLabel(i)
        }
    }

    private fun toggleMute(i: Int) {
        project.muted[i] = !project.muted[i]
        refreshLabel(i); grid.invalidate(); save()
    }

    private fun refreshLabel(i: Int) {
        val c = Palette.tracks[i]
        val col = if (project.muted[i]) Color.argb(80, Color.red(c), Color.green(c), Color.blue(c)) else c
        trackLabelViews[i].setTextColor(col)
        mixerLabels.getOrNull(i)?.setTextColor(col)
    }

    // ---- pads: tap = play (and record if REC + playing), hold = mute ----
    private fun setupPads() {
        pads.onPad = { i ->
            engine.trigger(i, accent = true)
            if (recording && engine.playing.get()) {
                val step = engine.positionInSteps().roundToInt() % project.steps
                val pat = project.patterns[project.slot]
                if (pat[i][step] == OFF) { pat[i][step] = HIT; grid.invalidate(); save() }
            }
        }
        pads.onPadLongPress = { i -> toggleMute(i) }
    }

    // ---- mixer ----
    private fun setupMixer() {
        val mixer = findViewById<LinearLayout>(R.id.mixer)
        for (i in 0 until project.tracks) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 6.dp(), 0, 6.dp())
            }
            val tv = TextView(this).apply {
                textSize = 12f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(60.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener { engine.trigger(i, accent = true) }
                setOnLongClickListener { toggleMute(i); true }
            }
            mixerLabels.add(tv)
            val sb = SeekBar(this).apply {
                max = 100; progress = (project.volume[i] * 100).toInt()
                progressTintList = ColorStateList.valueOf(Palette.tracks[i])
                thumbTintList = ColorStateList.valueOf(Palette.tracks[i])
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                onChange { p -> project.volume[i] = p / 100f }
            }
            row.addView(tv); row.addView(sb); mixer.addView(row)
            refreshLabel(i)
        }
        // export lives at the bottom of the mixer page
        val export = Pill(this).apply {
            text = "EXPORT WAV (4 BARS)"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp()).apply { topMargin = 12.dp() }
            style(Palette.panel, Palette.cyan, Palette.cyan)
            setOnClickListener { export() }
        }
        mixer.addView(export)
    }

    // ---- tabs ----
    private fun setupTabs() {
        val tabs = findViewById<LinearLayout>(R.id.tabs)
        listOf("SEQ", "PADS", "MIX").forEachIndexed { i, n ->
            val b = Pill(this).apply {
                text = n
                layoutParams = lp(1f, end = if (i == 2) 0 else 6)
                setOnClickListener { showPage(i) }
            }
            tabs.addView(b); tabBtns.add(b)
        }
        showPage(0)
    }

    private fun showPage(i: Int) {
        page = i
        findViewById<View>(R.id.seqPage).visibility = if (i == 0) View.VISIBLE else View.GONE
        pads.visibility = if (i == 1) View.VISIBLE else View.GONE
        findViewById<View>(R.id.mixPage).visibility = if (i == 2) View.VISIBLE else View.GONE
        hint.text = when (i) {
            0 -> "tap = hit • drag = paint • hold = accent • hold name = mute"
            1 -> "tap pads to play • turn on REC while playing to record into the grid"
            else -> "volume per sound • tap name = preview • hold = mute"
        }
        tabBtns.forEachIndexed { k, b ->
            if (k == i) b.style(Palette.cell, Palette.text, Color.parseColor("#44444F")) else b.style(Palette.panel, Palette.dim)
        }
    }

    // ---- export ----
    private fun export() {
        Toast.makeText(this, "Rendering…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val dir = File(cacheDir, "exports").apply { mkdirs() }
                val f = File(dir, "beatpad_${System.currentTimeMillis()}.wav")
                engine.exportWav(f, bars = 4)
                val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/wav"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { startActivity(Intent.createChooser(share, "Share beat")) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onPause() { save(); super.onPause() }
    override fun onDestroy() { engine.release(); super.onDestroy() }
}
