package com.sixzero.beatpad

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val slotNames = listOf("A", "B", "C", "D")
    private lateinit var project: Project
    private lateinit var engine: BeatEngine
    private lateinit var grid: SequencerView
    private lateinit var playBtn: Button
    private val slotBtns = mutableListOf<Button>()
    private lateinit var chainBtn: Button
    private val trackLabelViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        project = Project(tracks = DrumSynth.names.size, steps = 16, slots = slotNames.size)
        if (!Store.load(this, project)) defaultPattern()
        engine = BeatEngine(project)

        grid = findViewById(R.id.grid)
        playBtn = findViewById(R.id.playBtn)
        grid.tracks = project.tracks
        grid.muted = project.muted
        grid.pattern = project.patterns[project.slot]
        grid.onToggle = { _, _, _ -> save() }

        setupTrackLabels()
        setupSlots()
        setupSliders()
        setupMixer()

        engine.onStep = { slot, step ->
            runOnUiThread {
                grid.playhead = if (slot == project.slot) step else -1
                if (project.chain && step >= 0 && slot != project.slot) selectSlot(slot, fromPlayback = true)
            }
        }

        playBtn.setOnClickListener {
            if (engine.playing.get()) { engine.stop(); playBtn.text = "PLAY" }
            else { engine.play(); playBtn.text = "STOP" }
        }

        findViewById<Button>(R.id.clearBtn).setOnClickListener {
            for (t in project.patterns[project.slot]) t.fill(false)
            grid.invalidate(); save()
        }

        findViewById<Button>(R.id.mixBtn).setOnClickListener {
            val m = findViewById<LinearLayout>(R.id.mixer)
            m.visibility = if (m.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }

        findViewById<Button>(R.id.exportBtn).setOnClickListener { export() }

        engine.start()
    }

    private fun defaultPattern() {
        val p = project.patterns[0]
        p[0][0] = true; p[0][8] = true; p[0][10] = true
        p[1][4] = true; p[1][12] = true
        for (s in 0 until 16 step 2) p[3][s] = true
        p[7][0] = true; p[7][10] = true
    }

    private fun save() = Store.save(this, project)

    // ---- track labels: tap = preview, hold = mute ----
    private fun setupTrackLabels() {
        val labels = findViewById<LinearLayout>(R.id.trackLabels)
        DrumSynth.names.forEachIndexed { i, name ->
            val tv = TextView(this).apply {
                text = name
                textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                setOnClickListener { engine.trigger(i) }
                setOnLongClickListener {
                    project.muted[i] = !project.muted[i]
                    refreshLabel(i); grid.invalidate(); save(); true
                }
            }
            labels.addView(tv); trackLabelViews.add(tv); refreshLabel(i)
        }
    }

    private fun refreshLabel(i: Int) {
        val c = grid.trackColors[i]
        trackLabelViews[i].setTextColor(if (project.muted[i]) Color.argb(90, Color.red(c), Color.green(c), Color.blue(c)) else c)
    }

    // ---- pattern slots: tap = select, hold = toggle in chain; CHAIN button toggles chain mode ----
    private fun setupSlots() {
        val row = findViewById<LinearLayout>(R.id.slotRow)
        slotNames.forEachIndexed { i, n ->
            val b = Button(this).apply {
                text = n
                layoutParams = LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 6.dp() }
                setTextColor(Color.WHITE)
                setOnClickListener { selectSlot(i) }
                setOnLongClickListener { project.chainOn[i] = !project.chainOn[i]; refreshSlots(); save(); true }
            }
            row.addView(b); slotBtns.add(b)
        }
        chainBtn = Button(this).apply {
            text = "CHAIN"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, 44.dp(), 1.4f)
            setTextColor(Color.WHITE)
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
            val selected = i == project.slot
            val inChain = project.chainOn[i]
            b.backgroundTintList = android.content.res.ColorStateList.valueOf(
                when {
                    selected -> Color.parseColor("#FF5C5C")
                    inChain -> Color.parseColor("#4A3A3A")
                    else -> Color.parseColor("#2A2A32")
                }
            )
            b.text = slotNames[i] + (if (inChain) "•" else "")
        }
        chainBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (project.chain) Color.parseColor("#5CE1E6") else Color.parseColor("#2A2A32")
        )
        chainBtn.setTextColor(if (project.chain) Color.BLACK else Color.WHITE)
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

    // ---- mixer: one volume slider per track ----
    private fun setupMixer() {
        val mixer = findViewById<LinearLayout>(R.id.mixer)
        DrumSynth.names.forEachIndexed { i, name ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val tv = TextView(this).apply {
                text = name; textSize = 11f; setTextColor(grid.trackColors[i])
                layoutParams = LinearLayout.LayoutParams(50.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val sb = SeekBar(this).apply {
                max = 100; progress = (project.volume[i] * 100).toInt()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                onChange { p -> project.volume[i] = p / 100f }
            }
            row.addView(tv); row.addView(sb); mixer.addView(row)
        }
    }

    // ---- export to wav and share ----
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

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onPause() { save(); super.onPause() }
    override fun onDestroy() { engine.release(); super.onDestroy() }
}
