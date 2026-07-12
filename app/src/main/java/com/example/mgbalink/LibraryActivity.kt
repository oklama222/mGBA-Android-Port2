package com.example.mgbalink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The home screen. Lists every .gba file found in the user's chosen ROM folder.
 * Tapping a game launches the emulator (MainActivity) with that ROM's URI.
 */
class LibraryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView

    private val setupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadLibrary()
        } else if (!AppPrefs.isSetupDone(this)) {
            // User dismissed the folder picker without completing setup; nothing to show.
            tvEmpty.text = getString(R.string.library_tap_folder)
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        recyclerView = findViewById(R.id.recyclerGames)
        tvEmpty      = findViewById(R.id.tvEmpty)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnChangeFolder).setOnClickListener {
            setupLauncher.launch(Intent(this, FolderSetupActivity::class.java))
        }

        if (!AppPrefs.isSetupDone(this)) {
            setupLauncher.launch(Intent(this, FolderSetupActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadLibrary()
    }

    private fun loadLibrary() {
        val folderUri = AppPrefs.getRomFolderUri(this) ?: return

        val dir = DocumentFile.fromTreeUri(this, folderUri)
        if (dir == null || !dir.isDirectory) {
            showEmpty(getString(R.string.library_folder_unreadable))
            return
        }

        val games = dir.listFiles()
            .filter { it.isFile && it.name?.lowercase()?.endsWith(".gba") == true }
            .sortedBy { it.name?.lowercase() }

        if (games.isEmpty()) {
            showEmpty(getString(R.string.library_no_games))
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = GameAdapter(
                games.map { GameEntry(it.name ?: "Unknown", it.uri) }
            )
        }
    }

    private fun showEmpty(msg: String) {
        tvEmpty.text = msg
        tvEmpty.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    // ── RecyclerView ─────────────────────────────────────────────────────────

    private data class GameEntry(val filename: String, val uri: Uri)

    private inner class GameAdapter(private val items: List<GameEntry>) :
        RecyclerView.Adapter<GameAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tvGameName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_game, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = items[position]
            holder.title.text = entry.filename
                .removeSuffix(".gba")
                .removeSuffix(".GBA")

            holder.itemView.setOnClickListener {
                val intent = Intent(this@LibraryActivity, MainActivity::class.java)
                intent.putExtra(MainActivity.EXTRA_ROM_URI, entry.uri.toString())
                startActivity(intent)
            }
        }
    }
}
