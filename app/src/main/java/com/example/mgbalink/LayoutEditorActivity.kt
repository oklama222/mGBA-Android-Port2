package com.example.mgbalink

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen layout editor. Launched from Settings → Layouts section.
 * Changes are saved to SharedPreferences immediately on each drag release;
 * no explicit "Save" step needed — just press Back when done.
 */
class LayoutEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_layout_editor)

        val editorView = findViewById<LayoutEditorView>(R.id.layoutEditorView)

        findViewById<Button>(R.id.btnEditorBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnResetLayout).setOnClickListener {
            editorView.resetToDefaults()
        }
    }
}
