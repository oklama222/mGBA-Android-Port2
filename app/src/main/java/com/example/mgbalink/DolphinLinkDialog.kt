package com.example.mgbalink

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * A small dialog wrapping the Dolphin-link native calls.
 *
 * IMPORTANT: nativeDolphinConnect opens a TCP socket and MUST run on a
 * background thread — calling it on the main thread throws
 * NetworkOnMainThreadException and crashes the app.
 */
object DolphinLinkDialog {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(context: Context) {
        val density = context.resources.displayMetrics.density
        val padding  = (16 * density).toInt()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val status  = TextView(context)
        val ipField = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint      = context.getString(R.string.dolphin_ip_hint)
            setText("127.0.0.1")
        }

        fun refreshStatus() {
            status.text = context.getString(
                if (NativeBridge.nativeDolphinIsConnected()) R.string.dolphin_status_connected
                else R.string.dolphin_status_disconnected
            )
        }
        refreshStatus()

        layout.addView(status)
        layout.addView(ipField)

        AlertDialog.Builder(context)
            .setTitle(R.string.dolphin_link)
            .setView(layout)
            .setPositiveButton(R.string.dolphin_connect) { _, _ ->
                val ip = ipField.text.toString().trim()
                // Network calls must not run on the main thread.
                Thread {
                    val ok = NativeBridge.nativeDolphinConnect(
                        ip,
                        NativeBridge.DOLPHIN_DATA_PORT_DEFAULT,
                        NativeBridge.DOLPHIN_CLOCK_PORT_DEFAULT
                    )
                    mainHandler.post {
                        Toast.makeText(
                            context,
                            if (ok) R.string.dolphin_status_connected else R.string.dolphin_status_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.start()
            }
            .setNegativeButton(R.string.dolphin_disconnect) { _, _ ->
                NativeBridge.nativeDolphinDisconnect()
                Toast.makeText(context, R.string.dolphin_status_disconnected, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }
}
