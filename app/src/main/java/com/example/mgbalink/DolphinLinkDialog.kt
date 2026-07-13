package com.example.mgbalink

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Dialog for connecting/disconnecting the Dolphin GBA link cable emulation.
 *
 * The native nativeDolphinConnect call opens two TCP sockets (one for data,
 * one for clock) to a running Dolphin instance.  It must run on a background
 * thread for two reasons:
 *   1. NetworkOnMainThreadException — Android forbids network I/O on the
 *      main thread.
 *   2. The connect() syscall can block for the OS TCP timeout (~20 s) if
 *      Dolphin isn't reachable; we don't want that to freeze the UI.
 *
 * The JNI side releases g_coreMutex around the slow connect so the emulator
 * frame loop keeps rendering while we wait.
 */
object DolphinLinkDialog {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Default ports as defined in mGBA's dolphin.c */
    private const val DEFAULT_DATA_PORT  = 54970
    private const val DEFAULT_CLOCK_PORT = 49420

    fun show(context: Context) {
        val dp = context.resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val halfPad = (8 * dp).toInt()

        // ── root layout ──────────────────────────────────────────────────
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, halfPad)
        }

        // ── status row ───────────────────────────────────────────────────
        val statusLabel = TextView(context).apply {
            text = context.getString(R.string.dolphin_status_label)
        }
        val statusValue = TextView(context).apply {
            textSize = 14f
        }
        fun refreshStatus() {
            val connected = NativeBridge.nativeDolphinIsConnected()
            statusValue.text = context.getString(
                if (connected) R.string.dolphin_status_connected
                else R.string.dolphin_status_disconnected
            )
            statusValue.setTextColor(
                if (connected) 0xFF4CAF50.toInt()   // green
                else 0xFFF44336.toInt()              // red
            )
        }
        refreshStatus()

        val statusRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(statusLabel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = halfPad })
            addView(statusValue)
        }

        // ── IP field ─────────────────────────────────────────────────────
        val ipLabel = TextView(context).apply {
            text = context.getString(R.string.dolphin_ip_label)
        }
        val ipField = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "127.0.0.1"
            setText("127.0.0.1")
        }

        // ── port fields ──────────────────────────────────────────────────
        val dataPortLabel = TextView(context).apply {
            text = context.getString(R.string.dolphin_data_port_label)
        }
        val dataPortField = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "$DEFAULT_DATA_PORT"
            setText("$DEFAULT_DATA_PORT")
        }
        val clockPortLabel = TextView(context).apply {
            text = context.getString(R.string.dolphin_clock_port_label)
        }
        val clockPortField = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "$DEFAULT_CLOCK_PORT"
            setText("$DEFAULT_CLOCK_PORT")
        }

        // ── disconnect button ─────────────────────────────────────────────
        val disconnectBtn = Button(context).apply {
            text = context.getString(R.string.dolphin_disconnect)
            isEnabled = NativeBridge.nativeDolphinIsConnected()
            setOnClickListener {
                NativeBridge.nativeDolphinDisconnect()
                refreshStatus()
                isEnabled = false
                Toast.makeText(context, R.string.dolphin_status_disconnected, Toast.LENGTH_SHORT).show()
            }
        }

        // ── connect button ────────────────────────────────────────────────
        val connectBtn = Button(context).apply {
            text = context.getString(R.string.dolphin_connect)
        }

        // ── assemble layout ───────────────────────────────────────────────
        fun lp(bottomMargin: Int = halfPad) = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = bottomMargin }

        root.addView(statusRow,      lp())
        root.addView(ipLabel,        lp(0))
        root.addView(ipField,        lp())
        root.addView(dataPortLabel,  lp(0))
        root.addView(dataPortField,  lp())
        root.addView(clockPortLabel, lp(0))
        root.addView(clockPortField, lp())
        root.addView(disconnectBtn,  lp())
        root.addView(connectBtn,     lp(0))

        // ── build dialog ──────────────────────────────────────────────────
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.dolphin_link)
            .setView(root)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        connectBtn.setOnClickListener {
            val ip        = ipField.text.toString().trim().ifEmpty { "127.0.0.1" }
            val dataPort  = dataPortField.text.toString().toIntOrNull() ?: DEFAULT_DATA_PORT
            val clockPort = clockPortField.text.toString().toIntOrNull() ?: DEFAULT_CLOCK_PORT

            connectBtn.isEnabled    = false
            connectBtn.text         = context.getString(R.string.dolphin_connecting)
            disconnectBtn.isEnabled = false

            Thread {
                val ok = NativeBridge.nativeDolphinConnect(ip, dataPort, clockPort)
                mainHandler.post {
                    connectBtn.isEnabled    = true
                    connectBtn.text         = context.getString(R.string.dolphin_connect)
                    disconnectBtn.isEnabled = ok
                    refreshStatus()
                    Toast.makeText(
                        context,
                        if (ok) R.string.dolphin_status_connected
                        else    R.string.dolphin_connect_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.start()
        }

        dialog.show()
    }
}
