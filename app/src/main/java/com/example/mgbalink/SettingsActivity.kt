package com.example.mgbalink

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Settings screen — Video / Audio / Layout sections.
 * Uses a NoActionBar theme so there is no system action bar competing with
 * the content. A manual back button row sits at the top of the scroll view.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Manual back button (no action bar in this activity's theme).
        findViewById<Button>(R.id.btnSettingsBack).setOnClickListener { finish() }

        setupVideoSection()
        setupAudioSection()
        setupLayoutSection()
    }

    // ── Video ─────────────────────────────────────────────────────────────────

    private fun setupVideoSection() {
        val switchStretch = findViewById<Switch>(R.id.switchStretch)
        switchStretch.isChecked = AppPrefs.getStretch(this)
        switchStretch.setOnCheckedChangeListener { _, checked ->
            AppPrefs.setStretch(this, checked)
        }

        val rgOrientation = findViewById<RadioGroup>(R.id.rgOrientation)
        rgOrientation.check(when (AppPrefs.getOrientation(this)) {
            "auto"              -> R.id.radioOrientAuto
            "reverse_landscape" -> R.id.radioOrientReverseLandscape
            "portrait"          -> R.id.radioOrientPortrait
            "system"            -> R.id.radioOrientSystem
            else                -> R.id.radioOrientLandscape
        })
        rgOrientation.setOnCheckedChangeListener { _, id ->
            AppPrefs.setOrientation(this, when (id) {
                R.id.radioOrientAuto             -> "auto"
                R.id.radioOrientReverseLandscape -> "reverse_landscape"
                R.id.radioOrientPortrait         -> "portrait"
                R.id.radioOrientSystem           -> "system"
                else                             -> "landscape"
            })
        }

        val seekFrameSkip  = findViewById<SeekBar>(R.id.seekFrameSkip)
        val tvFrameSkipVal = findViewById<TextView>(R.id.tvFrameSkipValue)
        seekFrameSkip.max      = 10
        seekFrameSkip.progress = AppPrefs.getFrameSkip(this)
        tvFrameSkipVal.text    = seekFrameSkip.progress.toString()
        seekFrameSkip.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                tvFrameSkipVal.text = p.toString()
                AppPrefs.setFrameSkip(this@SettingsActivity, p)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    // ── Audio ─────────────────────────────────────────────────────────────────

    private fun setupAudioSection() {
        val switchSound = findViewById<Switch>(R.id.switchSound)
        switchSound.isChecked = AppPrefs.getSoundEnabled(this)
        switchSound.setOnCheckedChangeListener { _, checked ->
            AppPrefs.setSoundEnabled(this, checked)
        }

        val rgFreq = findViewById<RadioGroup>(R.id.rgSoundFreq)
        rgFreq.check(when (AppPrefs.getSoundFreq(this)) {
            22050 -> R.id.radioFreq22050
            11025 -> R.id.radioFreq11025
            else  -> R.id.radioFreq44100
        })
        rgFreq.setOnCheckedChangeListener { _, id ->
            AppPrefs.setSoundFreq(this, when (id) {
                R.id.radioFreq22050 -> 22050
                R.id.radioFreq11025 -> 11025
                else                -> 44100
            })
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private fun setupLayoutSection() {
        val rgLayout = findViewById<RadioGroup>(R.id.rgLayout)
        rgLayout.check(when (AppPrefs.getLayout(this)) {
            "left_handed" -> R.id.radioLayoutLeftHanded
            "custom"      -> R.id.radioLayoutCustom
            else          -> R.id.radioLayoutDefault
        })
        rgLayout.setOnCheckedChangeListener { _, id ->
            AppPrefs.setLayout(this, when (id) {
                R.id.radioLayoutLeftHanded -> "left_handed"
                R.id.radioLayoutCustom     -> "custom"
                else                       -> "default"
            })
        }

        // Open the drag editor
        findViewById<Button>(R.id.btnEditLayout).setOnClickListener {
            // Auto-select "custom" when the user opens the editor
            AppPrefs.setLayout(this, "custom")
            rgLayout.check(R.id.radioLayoutCustom)
            startActivity(Intent(this, LayoutEditorActivity::class.java))
        }
    }
}
