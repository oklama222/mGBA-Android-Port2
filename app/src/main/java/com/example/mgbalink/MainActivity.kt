package com.example.mgbalink

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var emulatorView: EmulatorView
    private var emulatorCore: EmulatorCore? = null

    private val pickRom = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadRom(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emulatorView = findViewById(R.id.emulatorView)

        findViewById<android.widget.Button>(R.id.btnLoadRom).setOnClickListener {
            pickRom.launch(arrayOf("*/*"))
        }
        findViewById<android.widget.Button>(R.id.btnDolphinLink).setOnClickListener {
            DolphinLinkDialog.show(this)
        }
    }

    private fun loadRom(uri: Uri) {
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
        if (bytes == null) {
            Toast.makeText(this, "Couldn't read that file", Toast.LENGTH_SHORT).show()
            return
        }

        val savePath = savePathFor(uri, bytes)

        // Stop whatever's currently running (this also flushes/closes its save file).
        emulatorCore?.stop()
        emulatorCore = null

        val loaded = NativeBridge.nativeLoadRom(bytes, savePath)
        if (!loaded) {
            Toast.makeText(this, "Couldn't load that ROM (GBA ROMs only in this build)", Toast.LENGTH_LONG).show()
            return
        }

        val core = EmulatorCore(emulatorView)
        emulatorCore = core
        core.start()
    }

    /** One save file per ROM, named after the ROM's display name (falling back to a content hash). */
    private fun savePathFor(uri: Uri, bytes: ByteArray): String {
        val displayName = queryDisplayName(uri)
        val baseName = if (displayName != null) {
            displayName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_")
        } else {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            digest.joinToString("") { "%02x".format(it) }.take(16)
        }
        val savesDir = File(filesDir, "saves").apply { mkdirs() }
        return File(savesDir, "$baseName.sav").absolutePath
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        } catch (e: Exception) {
            // fall through to hash-based naming
        } finally {
            cursor?.close()
        }
        return null
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
