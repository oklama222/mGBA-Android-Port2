package com.example.mgbalink

import android.content.Context
import android.net.Uri

/** Single source of truth for all SharedPreferences keys and typed accessors. */
object AppPrefs {

    private const val NAME = "mgba_prefs"

    private const val KEY_ROM_FOLDER    = "rom_folder_uri"
    private const val KEY_SAVE_FOLDER   = "save_folder_uri"

    // Video
    private const val KEY_STRETCH       = "stretch_to_fit"
    private const val KEY_ORIENTATION   = "screen_orientation"
    private const val KEY_FRAME_SKIP    = "max_frame_skip"

    // Audio
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_SOUND_FREQ    = "sound_frequency"

    // Layout
    private const val KEY_LAYOUT        = "gamepad_layout"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ── ROM folder ──────────────────────────────────────────────────────────
    fun getRomFolderUri(ctx: Context): Uri? =
        prefs(ctx).getString(KEY_ROM_FOLDER, null)?.let { Uri.parse(it) }

    fun setRomFolderUri(ctx: Context, uri: Uri) {
        prefs(ctx).edit().putString(KEY_ROM_FOLDER, uri.toString()).apply()
    }

    fun getSaveFolderUri(ctx: Context): Uri? =
        prefs(ctx).getString(KEY_SAVE_FOLDER, null)?.let { Uri.parse(it) }

    fun setSaveFolderUri(ctx: Context, uri: Uri) {
        prefs(ctx).edit().putString(KEY_SAVE_FOLDER, uri.toString()).apply()
    }

    fun isSetupDone(ctx: Context) = getRomFolderUri(ctx) != null

    // ── Video ────────────────────────────────────────────────────────────────
    fun getStretch(ctx: Context)         = prefs(ctx).getBoolean(KEY_STRETCH, true)
    fun setStretch(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_STRETCH, v).apply()

    /** Values: "landscape", "reverse_landscape", "portrait", "auto", "system" */
    fun getOrientation(ctx: Context)         = prefs(ctx).getString(KEY_ORIENTATION, "landscape") ?: "landscape"
    fun setOrientation(ctx: Context, v: String) = prefs(ctx).edit().putString(KEY_ORIENTATION, v).apply()

    fun getFrameSkip(ctx: Context)       = prefs(ctx).getInt(KEY_FRAME_SKIP, 0)
    fun setFrameSkip(ctx: Context, v: Int)   = prefs(ctx).edit().putInt(KEY_FRAME_SKIP, v).apply()

    // ── Audio ────────────────────────────────────────────────────────────────
    fun getSoundEnabled(ctx: Context)        = prefs(ctx).getBoolean(KEY_SOUND_ENABLED, true)
    fun setSoundEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_SOUND_ENABLED, v).apply()

    /** Values: 44100, 22050, 11025 */
    fun getSoundFreq(ctx: Context)       = prefs(ctx).getInt(KEY_SOUND_FREQ, 44100)
    fun setSoundFreq(ctx: Context, v: Int)   = prefs(ctx).edit().putInt(KEY_SOUND_FREQ, v).apply()

    // ── Layout ───────────────────────────────────────────────────────────────
    /** Values: "default", "left_handed" */
    fun getLayout(ctx: Context)          = prefs(ctx).getString(KEY_LAYOUT, "default") ?: "default"
    fun setLayout(ctx: Context, v: String)   = prefs(ctx).edit().putString(KEY_LAYOUT, v).apply()
}
