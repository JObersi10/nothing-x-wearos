package com.nothingx.phone

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Phone-side companion app — SCAFFOLD ONLY, not functional yet.
 *
 * Per the direct-vs-relay architecture decision: the watch connects to the
 * earbuds directly by default (see :bluetooth's DirectRfcommTransport). This
 * module exists as the landing spot for the fallback relay path — a
 * Wear Data Layer (MessageClient/ChannelClient) listener that would run
 * DirectRfcommTransport's same RFCOMM logic on the phone (which already
 * holds a normal pairing to the earbuds) and relay commands/state to and
 * from the watch. That listener is NOT implemented yet; this is just enough
 * of a module to prove the dependency wiring (:protocol, :bluetooth) and
 * give v2 a place to start. Don't ship this as "phone relay works."
 *
 * Plain [Activity], not AppCompatActivity: this had a real crash-on-launch
 * bug (confirmed on device) — AppCompatActivity requires a Theme.AppCompat
 * (or descendant) theme, but the manifest uses the platform's
 * Theme.DeviceDefault, so it threw immediately in onCreate/setContentView.
 * This scaffold doesn't need any AppCompat feature, so dropping down to
 * plain Activity removes the whole bug class instead of juggling themes.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply {
            text = "Nothing X companion — relay not implemented yet.\n" +
                "The watch app connects to your earbuds directly; this app isn't required for v1."
            setPadding(48, 96, 48, 48)
        }
        setContentView(view)
    }
}
