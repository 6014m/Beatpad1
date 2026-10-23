package com.sixzero.beatpad

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val trackNames = listOf("KICK", "SNARE", "HAT", "OPEN", "CLAP", "TOM")
    private lateinit var engine: BeatEngine
    private lateinit var grid: SequencerView
    private lateinit var playBtn: Button
    private lateinit var bpmLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        engine = BeatEngine(tracks = trackNames.size, steps = 16)
        grid = findViewById(R.id.grid)
        playBtn = findViewById(R.id.playBtn)
        bpmLabel = findViewById(R.id.bpmLabel)
        val bpmSeek = findViewById<SeekBar>(R.id.bpmSeek)
        val clearBtn = findViewById<Button>(R.id.clearBtn)
        val labels = findViewById<LinearLayout>(R.id.trackLabels)

        grid.tracks = trackNames.size
        grid.pattern = engine.pattern

        // default pattern so it isn't silent on first open
        engine.pattern[0][0] = true; engine.pattern[0][8] = true
        engine.pattern[1][4] = true; engine.pattern[1][12] = true
        for (s in 0 until 16 step 2) engine.pattern[2][s] = true

        // track name buttons on the left: tap = preview sound
        trackNames.forEachIndexed { i, name ->
            val tv = TextView(this).apply {
                text = name
                setTextColor(grid.trackColors[i])
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setOnClickListener { engine.trigger(i) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }
            labels.addView(tv)
        }

        engine.onStep = { step -> runOnUiThread { grid.playhead = step } }

        playBtn.setOnClickListener {
            if (engine.playing.get()) {
                engine.stop()
                playBtn.text = "PLAY"
            } else {
                engine.play()
                playBtn.text = "STOP"
            }
        }

        clearBtn.setOnClickListener {
            for (t in engine.pattern) t.fill(false)
            grid.invalidate()
        }

        bpmSeek.max = 200 - 60
        bpmSeek.progress = 120 - 60
        bpmLabel.text = "120 BPM"
        bpmSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                val bpm = p + 60
                engine.bpm.set(bpm)
                bpmLabel.text = "$bpm BPM"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        engine.start()
    }

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }
}
