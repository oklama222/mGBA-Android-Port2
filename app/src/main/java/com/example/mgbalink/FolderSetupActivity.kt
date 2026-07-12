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
 * Shown on first launch (and from "Change folder" in the library).
 * Asks the user to pick the folder that contains their GBA ROMs.
 * Save files are managed in internal storage automatically — no folder
 * pick needed for saves.
 */
class FolderSetupActivity : AppCompatActivity() {

    private var romFolderUri: Uri? = null

    private lateinit var tvRomPath: TextView
    private lateinit var btnDone: Button

    private val pickRomFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Keep access across reboots.
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            romFolderUri = uri
            tvRomPath.text = friendlyPath(uri)
            btnDone.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_setup)

        tvRomPath = findViewById(R.id.tvRomFolderPath)
        btnDone   = findViewById(R.id.btnSetupDone)

        // Pre-fill if already configured.
        AppPrefs.getRomFolderUri(this)?.let {
            romFolderUri = it
            tvRomPath.text = friendlyPath(it)
            btnDone.isEnabled = true
        } ?: run {
            btnDone.isEnabled = false
        }

        findViewById<Button>(R.id.btnPickRomFolder).setOnClickListener {
            pickRomFolder.launch(null)
        }

        btnDone.setOnClickListener {
            val uri = romFolderUri
            if (uri == null) {
                Toast.makeText(this, "Please choose a ROM folder first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppPrefs.setRomFolderUri(this, uri)
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun friendlyPath(uri: Uri): String {
        // SAF tree URIs look like content://...primary%3ADownload%2FROMs — decode the tail.
        val raw = uri.lastPathSegment ?: uri.toString()
        return raw.substringAfter(':').ifBlank { raw }
    }
}
