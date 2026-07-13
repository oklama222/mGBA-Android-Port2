package com.example.mgbalink

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest

/**
 * The emulator screen.
 *
 * A single small "⋮" menu button sits at the top-centre — between L and R —
 * and opens a popup with "Library" and "Dolphin Link". This keeps the corners
 * free for the L and R shoulder buttons.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROM_URI = "rom_uri"
        private const val TAG   = "MainActivity"
    }

    private lateinit var emulatorView:  EmulatorView
    private lateinit var touchControls: TouchControlsView
    private var emulatorCore: EmulatorCore? = null

    private var currentSaveLocalPath: String? = null
    private var currentRomBaseName:   String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emulatorView  = findViewById(R.id.emulatorView)
        touchControls = findViewById(R.id.touchControlsView)

        applyOrientation()
        touchControls.setLayout(AppPrefs.getLayout(this))

        // Single menu button between L and R — opens a popup.
        val btnMenu = findViewById<Button>(R.id.btnMenu)
        btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, getString(R.string.emu_back_to_library))
            popup.menu.add(0, 2, 1, getString(R.string.dolphin_link))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { stopEmulator(); finish(); true }
                    2 -> { DolphinLinkDialog.show(this); true }
                    else -> false
                }
            }
            popup.show()
        }

        val uriString = intent.getStringExtra(EXTRA_ROM_URI)
        if (uriString != null) {
            loadRom(Uri.parse(uriString))
        } else {
            Toast.makeText(this, "No ROM specified", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ── ROM loading ───────────────────────────────────────────────────────────

    private fun loadRom(uri: Uri) {
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) { null }

        if (bytes == null) {
            Toast.makeText(this, "Couldn't read that ROM file", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val baseName = romBaseName(uri, bytes)
        val savePath = prepareSavePath(baseName)

        currentRomBaseName   = baseName
        currentSaveLocalPath = savePath

        stopEmulator()

        val loaded = NativeBridge.nativeLoadRom(bytes, savePath)
        if (!loaded) {
            Toast.makeText(this, "Couldn't load ROM (GBA ROMs only)", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        emulatorView.stretchToFit = AppPrefs.getStretch(this)

        val core = EmulatorCore(
            emulatorView = emulatorView,
            maxFrameSkip = AppPrefs.getFrameSkip(this),
            soundEnabled = AppPrefs.getSoundEnabled(this)
        )
        emulatorCore = core
        core.start()
    }

    // ── Save-file sync (copy-in / copy-out) ───────────────────────────────────

    private fun prepareSavePath(baseName: String): String {
        val localFile = localSaveFile(baseName)
        val saveFolderUri = AppPrefs.getSaveFolderUri(this) ?: return localFile.absolutePath
        val saveFolder    = DocumentFile.fromTreeUri(this, saveFolderUri)
            ?: return localFile.absolutePath

        val existing = saveFolder.listFiles()
            .firstOrNull { it.name?.equals("$baseName.sav", ignoreCase = true) == true }

        if (existing != null) {
            try {
                contentResolver.openInputStream(existing.uri)?.use { input ->
                    localFile.outputStream().use { output -> input.copyTo(output) }
                }
                Log.d(TAG, "Copied save in: ${existing.name}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not copy save in", e)
            }
        }
        return localFile.absolutePath
    }

    private fun flushSaveToFolder(baseName: String, localPath: String) {
        val localFile = File(localPath)
        if (!localFile.exists() || localFile.length() == 0L) return

        val saveFolderUri = AppPrefs.getSaveFolderUri(this) ?: return
        val saveFolder    = DocumentFile.fromTreeUri(this, saveFolderUri) ?: return

        val saveName = "$baseName.sav"
        val target   = saveFolder.listFiles()
            .firstOrNull { it.name?.equals(saveName, ignoreCase = true) == true }
            ?: saveFolder.createFile("application/octet-stream", baseName)

        if (target == null) { Log.w(TAG, "Could not create save file in SAF folder"); return }

        try {
            contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
                localFile.inputStream().use { input -> input.copyTo(output) }
            }
            Log.d(TAG, "Flushed save out: $saveName")
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy save out", e)
        }
    }

    private fun localSaveFile(baseName: String): File {
        val dir = File(filesDir, "saves").apply { mkdirs() }
        return File(dir, "$baseName.sav")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun romBaseName(uri: Uri, bytes: ByteArray): String {
        val name = queryDisplayName(uri)
        return if (name != null)
            name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_()\\- ]"), "_").trim()
        else
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: android.database.Cursor? = null
        return try {
            cursor = contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        } catch (_: Exception) { null } finally { cursor?.close() }
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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private fun stopEmulator() {
        val core     = emulatorCore ?: return
        val baseName = currentRomBaseName
        val savePath = currentSaveLocalPath
        core.stop()
        emulatorCore = null
        if (baseName != null && savePath != null) flushSaveToFolder(baseName, savePath)
    }

    override fun onPause()   { super.onPause();   emulatorCore?.pause() }
    override fun onResume()  { super.onResume();  emulatorCore?.resume() }
    override fun onDestroy() { stopEmulator();    super.onDestroy() }
}
