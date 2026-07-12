package com.example.mgbalink

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * The emulator screen. Launched by LibraryActivity with EXTRA_ROM_URI containing
 * the DocumentFile URI of the ROM to load. Screen orientation and display settings
 * are read from AppPrefs each time the activity starts.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROM_URI = "rom_uri"
    }

    private lateinit var emulatorView: EmulatorView
    private lateinit var touchControls: TouchControlsView
    private var emulatorCore: EmulatorCore? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emulatorView  = findViewById(R.id.emulatorView)
        touchControls = findViewById(R.id.touchControlsView)

        applyOrientation()

        // Apply the virtual-gamepad layout chosen in Settings.
        touchControls.setLayout(AppPrefs.getLayout(this))

        // Back to library.
        findViewById<android.widget.Button>(R.id.btnBackToLibrary).setOnClickListener {
            emulatorCore?.stop()
            emulatorCore = null
            finish()
        }

        // Dolphin Link — keep same as before.
        findViewById<android.widget.Button>(R.id.btnDolphinLink).setOnClickListener {
            DolphinLinkDialog.show(this)
        }

        // Load the ROM that LibraryActivity passed us.
        val uriString = intent.getStringExtra(EXTRA_ROM_URI)
        if (uriString != null) {
            loadRom(Uri.parse(uriString))
        } else {
            Toast.makeText(this, "No ROM specified", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadRom(uri: Uri) {
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
        if (bytes == null) {
            Toast.makeText(this, "Couldn't read that ROM file", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val savePath = savePathFor(uri, bytes)

        emulatorCore?.stop()
        emulatorCore = null

        val loaded = NativeBridge.nativeLoadRom(bytes, savePath)
        if (!loaded) {
            Toast.makeText(this, "Couldn't load ROM (GBA ROMs only)", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Apply video settings.
        emulatorView.stretchToFit = AppPrefs.getStretch(this)

        val core = EmulatorCore(
            emulatorView = emulatorView,
            maxFrameSkip = AppPrefs.getFrameSkip(this),
            soundEnabled = AppPrefs.getSoundEnabled(this)
        )
        emulatorCore = core
        core.start()
    }

    /** Save file lives in internal storage, named after the ROM. */
    private fun savePathFor(uri: Uri, bytes: ByteArray): String {
        val displayName = queryDisplayName(uri)
        val baseName = if (displayName != null) {
            displayName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_")
        } else {
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            digest.joinToString("") { "%02x".format(it) }.take(16)
        }
        val savesDir = java.io.File(filesDir, "saves").apply { mkdirs() }
        return java.io.File(savesDir, "$baseName.sav").absolutePath
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: android.database.Cursor? = null
        try {
            cursor = contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun applyOrientation() {
        requestedOrientation = when (AppPrefs.getOrientation(this)) {
            "reverse_landscape" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            "portrait"          -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "auto"              -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            "system"            -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else                -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    override fun onPause() {
        super.onPause()
        emulatorCore?.pause()
    }

    override fun onResume() {
        super.onResume()
        emulatorCore?.resume()
    }

    override fun onDestroy() {
        emulatorCore?.stop()
        emulatorCore = null
        super.onDestroy()
    }
}
