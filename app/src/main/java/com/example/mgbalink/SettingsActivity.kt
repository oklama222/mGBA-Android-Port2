package com.example.mgbalink

import android.os.Bundle
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Settings screen — three sections: Video, Audio, Layout.
 * All changes are written to SharedPreferences immediately and take effect
 * the next time the emulator (MainActivity) is started or resumed.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        setupVideoSection()
        setupAudioSection()
        setupLayoutSection()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ── Video ────────────────────────────────────────────────────────────────

    private fun setupVideoSection() {
        // Stretch to fit
        val switchStretch = findViewById<Switch>(R.id.switchStretch)
        switchStretch.isChecked = AppPrefs.getStretch(this)
        switchStretch.setOnCheckedChangeListener { _, checked ->
            AppPrefs.setStretch(this, checked)
        }

        // Screen orientation
        val rgOrientation = findViewById<RadioGroup>(R.id.rgOrientation)
        val orientationId = when (AppPrefs.getOrientation(this)) {
            "auto"              -> R.id.radioOrientAuto
            "reverse_landscape" -> R.id.radioOrientReverseLandscape
            "portrait"          -> R.id.radioOrientPortrait
            "system"            -> R.id.radioOrientSystem
            else                -> R.id.radioOrientLandscape
        }
        rgOrientation.check(orientationId)
        rgOrientation.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radioOrientAuto             -> "auto"
                R.id.radioOrientReverseLandscape -> "reverse_landscape"
                R.id.radioOrientPortrait         -> "portrait"
                R.id.radioOrientSystem           -> "system"
                else                             -> "landscape"
            }
            AppPrefs.setOrientation(this, value)
        }

        // Max frame skip
        val seekFrameSkip = findViewById<SeekBar>(R.id.seekFrameSkip)
        val tvFrameSkipVal = findViewById<TextView>(R.id.tvFrameSkipValue)
        seekFrameSkip.max = 10
        seekFrameSkip.progress = AppPrefs.getFrameSkip(this)
        tvFrameSkipVal.text = seekFrameSkip.progress.toString()
        seekFrameSkip.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                tvFrameSkipVal.text = progress.toString()
                AppPrefs.setFrameSkip(this@SettingsActivity, progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    // ── Audio ────────────────────────────────────────────────────────────────

    private fun setupAudioSection() {
        val switchSound = findViewById<Switch>(R.id.switchSound)
        switchSound.isChecked = AppPrefs.getSoundEnabled(this)
        switchSound.setOnCheckedChangeListener { _, checked ->
            AppPrefs.setSoundEnabled(this, checked)
        }

        val rgFreq = findViewById<RadioGroup>(R.id.rgSoundFreq)
        val freqId = when (AppPrefs.getSoundFreq(this)) {
            22050 -> R.id.radioFreq22050
            11025 -> R.id.radioFreq11025
            else  -> R.id.radioFreq44100
        }
        rgFreq.check(freqId)
        rgFreq.setOnCheckedChangeListener { _, checkedId ->
            val freq = when (checkedId) {
                R.id.radioFreq22050 -> 22050
                R.id.radioFreq11025 -> 11025
                else                -> 44100
            }
            AppPrefs.setSoundFreq(this, freq)
        }
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    private fun setupLayoutSection() {
        val rgLayout = findViewById<RadioGroup>(R.id.rgLayout)
        val layoutId = when (AppPrefs.getLayout(this)) {
            "left_handed" -> R.id.radioLayoutLeftHanded
            else          -> R.id.radioLayoutDefault
        }
        rgLayout.check(layoutId)
        rgLayout.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radioLayoutLeftHanded -> "left_handed"
                else                       -> "default"
            }
            AppPrefs.setLayout(this, value)
        }
    }
}
