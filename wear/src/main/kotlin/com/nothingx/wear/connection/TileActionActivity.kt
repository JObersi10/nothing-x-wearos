package com.nothingx.wear.connection

import android.app.Activity
import android.os.Bundle
import com.nothingx.protocol.AncMode

/**
 * Invisible trampoline activity that lets the Tile change ANC mode without
 * the user perceiving the app opening. A fully transparent theme
 * (`Theme.Transparent` in styles.xml) plus finishing in onCreate before any
 * frame is drawn means nothing visible happens — the tile just updates.
 *
 * Chosen over ProtoLayout's native `LoadAction`+state-round-trip pattern
 * (the "correct" way to make an interactive Tile) deliberately: that pattern
 * needs several ProtoLayout APIs (`StateBuilders.State`, request-state
 * handling in `onTileRequest`) this project has never exercised, in a
 * sandbox that can't compile-check any of it. This trampoline uses only
 * `ActionBuilders.LaunchAction`/`AndroidActivity`, which the Tile already
 * uses successfully to open MainActivity — same mechanism, just pointed at
 * a different, invisible activity. Lower risk, and the user won't be able
 * to tell the difference in practice.
 */
class TileActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val modeName = intent?.getStringExtra(EXTRA_ANC_MODE)
        val mode = modeName?.let { runCatching { AncMode.valueOf(it) }.getOrNull() }

        // Make sure there's a live connection to act on — the process may
        // have been killed since the app was last open, in which case
        // EarbudsConnectionHolder has nothing to send to yet. init() is
        // idempotent and cheap; if the underlying socket isn't connected yet
        // the command is dropped gracefully (same guard DirectRfcommTransport
        // already has everywhere else), not a crash — reconnecting takes a
        // moment, there's no way around that physical reality from a tap.
        EarbudsConnectionHolder.init(applicationContext)
        EarbudsConnectionService.start(applicationContext)
        if (mode != null) {
            EarbudsConnectionHolder.setAncMode(mode)
        }

        finish()
    }

    companion object {
        const val EXTRA_ANC_MODE = "anc_mode"
    }
}
