package com.example.mgbalink

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest

/**
 * The emulator screen. Launched by LibraryActivity with EXTRA_ROM_URI.
 *
 * Save-file sync (copy-in / copy-out):
 *  Before loading — if the user has a save folder configured, we look for
 *  "<romBaseName>.sav" in that folder and copy it to internal storage so the
 *  native side can read/write it via a real file path.
 *  After stopping  — we copy the (updated) internal save file back to the
 *  user's save folder, creating the file there if it doesn't exist yet.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROM_URI = "rom_uri"
        private const val TAG   = "MainActivity"
    }

    private lateinit var emulatorView:  EmulatorView
    private lateinit var touchControls: TouchControlsView
    private var emulatorCore: EmulatorCore? = null

    /** Tracks the internal save path and ROM base name for the copy-out step. */
    private var currentSaveLocalPath: String? = null
    private var currentRomBaseName:   String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emulatorView  = findViewById(R.id.emulatorView)
        touchControls = findViewById(R.id.touchControlsView)

        applyOrientation()
        touchControls.setLayout(AppPrefs.getLayout(this))

        findViewById<android.widget.Button>(R.id.btnBackToLibrary).setOnClickListener {
            stopEmulator()
            finish()
        }
        findViewById<android.widget.Button>(R.id.btnDolphinLink).setOnClickListener {
            DolphinLinkDialog.show(this)
        }

        val uriString = intent.getStringExtra(EXTRA_ROM_URI)
        if (uriString != null) {
            loadRom(Uri.parse(uriString))
        } else {
            Toast.makeText(this, "No ROM specified", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ── ROM loading ──────────────────────────────────────────────────────────

    private fun loadRom(uri: Uri) {
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) { null }

        if (bytes == null) {
            Toast.makeText(this, "Couldn't read that ROM file", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val baseName  = romBaseName(uri, bytes)
        val savePath  = prepareSavePath(baseName)

        currentRomBaseName   = baseName
        currentSaveLocalPath = savePath

        stopEmulator() // stop anything currently running

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

    // ── Save-file sync ───────────────────────────────────────────────────────

    /**
     * Returns the absolute path the native side should use for the save file.
     * If a SAF save folder is configured, copies any existing save from there
     * into internal storage first.
     */
    private fun prepareSavePath(baseName: String): String {
        val localFile = localSaveFile(baseName)

        val saveFolderUri = AppPrefs.getSaveFolderUri(this) ?: return localFile.absolutePath
        val saveFolder    = DocumentFile.fromTreeUri(this, saveFolderUri)
            ?: return localFile.absolutePath

        // Look for an existing save file in the user's folder.
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

    /**
     * Copies the internal save file back to the user's save folder after the
     * emulator has flushed it. No-op if no save folder is configured or the
     * save file is empty.
     */
    private fun flushSaveToFolder(baseName: String, localPath: String) {
        val localFile = File(localPath)
        if (!localFile.exists() || localFile.length() == 0L) return

        val saveFolderUri = AppPrefs.getSaveFolderUri(this) ?: return
        val saveFolder    = DocumentFile.fromTreeUri(this, saveFolderUri) ?: return

        val saveName = "$baseName.sav"
        // Find or create the file in the SAF folder.
        val target = saveFolder.listFiles()
            .firstOrNull { it.name?.equals(saveName, ignoreCase = true) == true }
            ?: saveFolder.createFile("application/octet-stream", baseName)

        if (target == null) {
            Log.w(TAG, "Could not create save file in SAF folder")
            return
        }

        try {
            // "wt" = write-truncate so we overwrite the old content cleanly.
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns a safe file-system base name for the ROM (no extension). */
    private fun romBaseName(uri: Uri, bytes: ByteArray): String {
        val displayName = queryDisplayName(uri)
        return if (displayName != null) {
            displayName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_()\\- ]"), "_").trim()
        } else {
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }.take(16)
        }
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

    // ── Lifecycle ────────────────────────────────────────────────────────────

    private fun stopEmulator() {
        val core     = emulatorCore ?: return
        val baseName = currentRomBaseName
        val savePath = currentSaveLocalPath

        core.stop()
        emulatorCore = null

        // After nativeUnloadRom() the save file has been flushed to localPath.
        if (baseName != null && savePath != null) {
            flushSaveToFolder(baseName, savePath)
        }
    }

    override fun onPause()   { super.onPause();   emulatorCore?.pause() }
    override fun onResume()  { super.onResume();  emulatorCore?.resume() }
    override fun onDestroy() { stopEmulator();    super.onDestroy() }
}
