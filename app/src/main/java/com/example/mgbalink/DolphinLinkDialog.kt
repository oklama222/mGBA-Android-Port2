package com.example.mgbalink

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * A small dialog wrapping the three Dolphin-link calls in NativeBridge.
 * Mirrors mGBA's own Qt DolphinConnector: an IP field (defaulting to
 * localhost, same as Qt's "local" option) plus Connect/Disconnect.
 *
 * Just like the desktop version, this only does anything while a GBA ROM
 * is loaded — nativeDolphinConnect will simply fail otherwise.
 */
object DolphinLinkDialog {

    fun show(context: Context) {
        val padding = (16 * context.resources.displayMetrics.density).toInt()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val status = TextView(context)
        val ipField = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = context.getString(R.string.dolphin_ip_hint)
            setText("127.0.0.1")
        }

        fun refreshStatus() {
            status.text = context.getString(
                if (NativeBridge.nativeDolphinIsConnected()) {
                    R.string.dolphin_status_connected
                } else {
                    R.string.dolphin_status_disconnected
                }
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
                val ok = NativeBridge.nativeDolphinConnect(
                    ip,
                    NativeBridge.DOLPHIN_DATA_PORT_DEFAULT,
                    NativeBridge.DOLPHIN_CLOCK_PORT_DEFAULT
                )
                val messageRes = if (ok) R.string.dolphin_status_connected else R.string.dolphin_status_failed
                Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(R.string.dolphin_disconnect) { _, _ ->
                NativeBridge.nativeDolphinDisconnect()
                Toast.makeText(context, R.string.dolphin_status_disconnected, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }
}
