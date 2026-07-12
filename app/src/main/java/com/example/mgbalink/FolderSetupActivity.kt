package com.example.mgbalink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Shown on first launch and from "Change folder" in the library.
 *
 * Step 1 — pick the ROM folder (required).
 * Step 2 — pick the save-file folder (optional but recommended).
 *          Save files in that folder must be named the same as the ROM
 *          (e.g. Pokemon.gba → Pokemon.sav). The emulator copies the save
 *          in before loading and copies it back out after stopping.
 */
class FolderSetupActivity : AppCompatActivity() {

    private var romFolderUri:  Uri? = null
    private var saveFolderUri: Uri? = null

    private lateinit var tvRomPath:      TextView
    private lateinit var tvSavePath:     TextView
    private lateinit var saveFolderCard: android.view.View
    private lateinit var btnDone:        Button

    private val pickRomFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            romFolderUri = uri
            tvRomPath.text = friendlyPath(uri)
            // Reveal the save-folder step once the ROM folder is chosen.
            saveFolderCard.visibility = android.view.View.VISIBLE
            updateDoneButton()
        }
    }

    private val pickSaveFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            saveFolderUri = uri
            tvSavePath.text = friendlyPath(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_setup)

        tvRomPath      = findViewById(R.id.tvRomFolderPath)
        tvSavePath     = findViewById(R.id.tvSaveFolderPath)
        saveFolderCard = findViewById(R.id.cardSaveFolder)
        btnDone        = findViewById(R.id.btnSetupDone)

        // Pre-fill if already configured.
        AppPrefs.getRomFolderUri(this)?.let {
            romFolderUri = it
            tvRomPath.text = friendlyPath(it)
            saveFolderCard.visibility = android.view.View.VISIBLE
        }
        AppPrefs.getSaveFolderUri(this)?.let {
            saveFolderUri = it
            tvSavePath.text = friendlyPath(it)
        }
        updateDoneButton()

        // Initially hide the save-folder step until the ROM folder is picked.
        if (romFolderUri == null) saveFolderCard.visibility = android.view.View.GONE

        findViewById<Button>(R.id.btnPickRomFolder).setOnClickListener {
            pickRomFolder.launch(null)
        }
        findViewById<Button>(R.id.btnPickSaveFolder).setOnClickListener {
            pickSaveFolder.launch(null)
        }

        btnDone.setOnClickListener {
            val rom = romFolderUri
            if (rom == null) {
                Toast.makeText(this, "Please choose a ROM folder first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppPrefs.setRomFolderUri(this, rom)
            saveFolderUri?.let { AppPrefs.setSaveFolderUri(this, it) }
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun updateDoneButton() {
        btnDone.isEnabled = romFolderUri != null
    }

    private fun friendlyPath(uri: Uri): String {
        val raw = uri.lastPathSegment ?: uri.toString()
        return raw.substringAfter(':').ifBlank { raw }
    }
}
