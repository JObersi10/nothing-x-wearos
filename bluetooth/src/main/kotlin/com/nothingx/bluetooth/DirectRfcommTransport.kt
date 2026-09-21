package com.nothingx.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.nothingx.protocol.AncMode
import com.nothingx.protocol.Commands
import com.nothingx.protocol.DeviceState
import com.nothingx.protocol.EarbudsSession
import com.nothingx.protocol.EqPreset
import com.nothingx.protocol.Frame
import com.nothingx.protocol.FrameEncoder
import com.nothingx.protocol.FrameParser
import com.nothingx.protocol.OutgoingCommand
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "NothingX"

/**
 * Watch-side direct RFCOMM connection to a Nothing/CMF earbuds device —
 * the "watch connects directly" path from the architecture plan. Ports
 * something-x's `NothingDevice` (protocol.py) channel-probe + recv-loop
 * approach to Android's Bluetooth Classic APIs.
 *
 * Confirmed working on real hardware (2026-09-21): ANC mode switching over
 * this exact code path against a CMF Buds Pro 2, direct-connected from a
 * Galaxy Watch 4. The reflection-based RFCOMM channel connect (see
 * `openRfcommChannel`) does work on Wear OS 3 — that was the open question
 * this class's doc comment used to flag as unconfirmed.
 *
 * All logging in this class uses tag "$TAG" — `adb logcat -s $TAG:V` to
 * follow a connection attempt live. Every RX frame is dumped as raw hex
 * before parsing (at DEBUG level) so a hardware capture can confirm or
 * correct command IDs without guessing — the "raw frame debug logging"
 * CLAUDE.md flagged as not implemented yet.
 */
class DirectRfcommTransport(context: Context) : EarbudsTransport {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectionJob: Job? = null
    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var fsn: Int = 0
    private val writeMutex = Mutex()

    // A raw RFCOMM socket's blocking read() doesn't reliably throw promptly
    // when the peer's ACL link actually drops (e.g. earbuds taken out of
    // range, or the user disconnects them from the phone/watch's Bluetooth
    // settings) — on some stacks it can sit blocked for a long OS-level
    // timeout before noticing. That left the app reporting "Connected" long
    // after the earbuds were actually gone: stale battery, dead commands
    // (Find My Earbuds, ANC) silently dropped, no reconnect ever triggered.
    // Listening for the system's own ACTION_ACL_DISCONNECTED and force-
    // closing the socket the moment it fires for this exact device is what
    // actually detects a real-world disconnect promptly, instead of relying
    // on the socket noticing on its own.
    private var aclReceiver: BroadcastReceiver? = null
    private var aclTargetAddress: String? = null

    private val session = EarbudsSession()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _deviceState = MutableStateFlow(DeviceState())
    override val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String) {
        requirePermission()
        Log.i(TAG, "connect() address=$address")
        val bluetoothAdapter = adapter ?: run {
            Log.e(TAG, "connect: no BluetoothAdapter on this device")
            _connectionState.value = ConnectionState.Failed("No Bluetooth adapter on this device")
            return
        }

        connectionJob?.cancel()
        connectionJob = scope.launch {
            val device = try {
                bluetoothAdapter.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "connect: invalid address $address", e)
                _connectionState.value = ConnectionState.Failed("Invalid address: $address")
                return@launch
            }

            registerAclReceiver(address)

            var tried = 0
            for (channel in PROBE_CHANNELS) {
                tried++
                _connectionState.value = ConnectionState.Connecting(tried)
                Log.d(TAG, "probing channel $channel ($tried/${PROBE_CHANNELS.size})")
                val result = tryChannel(device, channel)
                if (result != null) {
                    val (openSocket, initialBytes) = result
                    socket = openSocket
                    output = openSocket.outputStream
                    fsn = 0
                    Log.i(TAG, "connected on channel $channel, initial=${initialBytes.toHexString()}")
                    _connectionState.value = ConnectionState.Connected
                    runReceiveLoop(openSocket.inputStream, initialBytes)
                    return@launch
                }
            }
            Log.w(TAG, "no channel responded after ${PROBE_CHANNELS.size} attempts")
            unregisterAclReceiver()
            _connectionState.value = ConnectionState.Failed(
                "No channel on $address responded to the Nothing protocol probe " +
                    "(tried ${PROBE_CHANNELS.size} channels). See DirectRfcommTransport's " +
                    "doc comment for the reflection-based RFCOMM caveat.",
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryChannel(device: BluetoothDevice, channel: Int): Pair<BluetoothSocket, ByteArray>? {
        val sock = try {
            openRfcommChannel(device, channel)
        } catch (e: Exception) {
            Log.d(TAG, "channel $channel: createRfcommSocket reflection failed: ${e.message}")
            return null
        } ?: return null

        try {
            sock.connect()
        } catch (e: IOException) {
            Log.d(TAG, "channel $channel: connect() failed: ${e.message}")
            closeQuietly(sock)
            return null
        }

        return try {
            sock.inputStream.let { input ->
                sock.outputStream.let { out ->
                    val probe = FrameEncoder.encode(Commands.GET_PROTO_VERSION, byteArrayOf(0x01), fsn = 1)
                    out.write(probe)
                    out.flush()
                    Log.d(TAG, "channel $channel: sent probe ${probe.toHexString()}")

                    val initial = readWithDeadline(input, PROBE_TIMEOUT_MS)
                    // Only accept the two known frame headers — a channel that answers
                    // with anything else (e.g. HFP's AT-command channel) is not ours,
                    // same guard something-x applies for the same reason.
                    if (initial.isEmpty() || (initial[0].toInt() and 0xFF) != Commands.SOF) {
                        Log.d(TAG, "channel $channel: no usable response, got ${initial.toHexString()}")
                        closeQuietly(sock)
                        null
                    } else {
                        sock to initial
                    }
                }
            }
        } catch (e: IOException) {
            Log.d(TAG, "channel $channel: probe I/O failed: ${e.message}")
            closeQuietly(sock)
            null
        }
    }

    /** Reflection call for `BluetoothDevice.createRfcommSocket(int channel)` — see class doc. */
    private fun openRfcommChannel(device: BluetoothDevice, channel: Int): BluetoothSocket? {
        return try {
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            method.invoke(device, channel) as BluetoothSocket
        } catch (e: ReflectiveOperationException) {
            null
        }
    }

    private fun readWithDeadline(input: InputStream, timeoutMs: Long): ByteArray {
        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = ByteArray(256)
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val n = input.read(buffer)
                if (n > 0) return buffer.copyOf(n)
            } else {
                Thread.sleep(20)
            }
        }
        return ByteArray(0)
    }

    private suspend fun runReceiveLoop(input: InputStream, initialBytes: ByteArray) {
        val parser = FrameParser()
        try {
            if (initialBytes.isNotEmpty()) {
                dispatchFrames(parser.feed(initialBytes))
            }
            val buffer = ByteArray(256)
            while (true) {
                val n = withContext(Dispatchers.IO) { input.read(buffer) }
                if (n < 0) {
                    Log.i(TAG, "recv loop: stream closed by peer")
                    break
                }
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "RX raw ${buffer.copyOf(n).toHexString()}")
                }
                dispatchFrames(parser.feed(buffer.copyOf(n)))
            }
        } catch (e: IOException) {
            Log.w(TAG, "recv loop: I/O error, disconnecting: ${e.message}")
        } finally {
            _connectionState.value = ConnectionState.Disconnected
            closeQuietly(socket)
            socket = null
            output = null
            unregisterAclReceiver()
        }
    }

    private suspend fun dispatchFrames(frames: List<Frame>) {
        for (frame in frames) {
            // isLoggable guard: the hex-dump formatting itself (toHexString's
            // per-byte String.format allocations) ran unconditionally before,
            // on every single RX frame, even with logcat not attached — real,
            // if modest, CPU/allocation overhead on every command exchange.
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "RX cmd=0x${frame.cmd.toString(16)} payload=${frame.payload.toHexString()}")
            }
            val followUps = session.handleFrame(frame)
            _deviceState.value = session.state
            for (cmd in followUps) {
                sendCommand(cmd)
            }
        }
    }

    private suspend fun sendCommand(command: OutgoingCommand) {
        val out = output ?: run {
            Log.w(TAG, "sendCommand: no output stream (not connected), dropped ${command.label}")
            return
        }
        writeMutex.withLock {
            fsn = (fsn + 1) and 0xFF
            val frame = FrameEncoder.encode(command.cmd, command.payload, fsn)
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "TX cmd=0x${command.cmd.toString(16)} ${command.label} ${frame.toHexString()}")
            }
            withContext(Dispatchers.IO) {
                try {
                    out.write(frame)
                    out.flush()
                } catch (e: IOException) {
                    Log.e(TAG, "sendCommand: write failed: ${e.message}")
                    _connectionState.value = ConnectionState.Failed("Write failed: ${e.message}")
                }
            }
        }
    }

    override suspend fun disconnect() {
        Log.i(TAG, "disconnect()")
        connectionJob?.cancel()
        unregisterAclReceiver()
        closeQuietly(socket)
        socket = null
        output = null
        _connectionState.value = ConnectionState.Idle
    }

    private fun registerAclReceiver(address: String) {
        unregisterAclReceiver()
        aclTargetAddress = address
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device = IntentCompat.getParcelableExtra(
                    intent,
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java,
                )
                if (device?.address != aclTargetAddress) return
                Log.i(TAG, "ACL disconnected from $aclTargetAddress — forcing socket closed")
                closeQuietly(socket)
            }
        }
        aclReceiver = receiver
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun unregisterAclReceiver() {
        val receiver = aclReceiver ?: return
        aclReceiver = null
        try {
            appContext.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Already unregistered (e.g. disconnect() raced the receive loop's own cleanup) — fine.
        }
    }

    override suspend fun setAncMode(mode: AncMode) {
        Log.i(TAG, "setAncMode($mode)")
        val cmd = session.setAncMode(mode)
        _deviceState.value = session.state
        sendCommand(cmd)
    }

    override suspend fun setEqPreset(preset: EqPreset) {
        Log.i(TAG, "setEqPreset($preset)")
        val cmd = session.setEqPreset(preset)
        _deviceState.value = session.state
        sendCommand(cmd)
    }

    override suspend fun querySettings() {
        Log.i(TAG, "querySettings()")
        for (cmd in session.querySettingsCommand()) sendCommand(cmd)
    }

    override suspend fun setInEarDetection(enabled: Boolean) {
        Log.i(TAG, "setInEarDetection($enabled)")
        val cmd = session.setInEarDetection(enabled)
        _deviceState.value = session.state
        sendCommand(cmd)
    }

    override suspend fun setLowLatency(enabled: Boolean) {
        Log.i(TAG, "setLowLatency($enabled)")
        val cmd = session.setLowLatency(enabled)
        _deviceState.value = session.state
        sendCommand(cmd)
    }

    override suspend fun setPersonalizedAnc(enabled: Boolean) {
        Log.i(TAG, "setPersonalizedAnc($enabled)")
        val cmd = session.setPersonalizedAnc(enabled)
        _deviceState.value = session.state
        sendCommand(cmd)
    }

    override suspend fun setBassEnhance(enabled: Boolean, level: Int) {
        Log.i(TAG, "setBassEnhance($enabled, $level)")
        val cmd = session.setBassEnhance(enabled, level)
        _deviceState.value = session.state
        sendCommand(cmd)
    }

    override suspend fun ringBuds(ring: Boolean, isLeft: Boolean?) {
        Log.i(TAG, "ringBuds($ring, $isLeft)")
        sendCommand(session.ringBuds(ring, isLeft))
    }

    override suspend fun launchEarFitTest() {
        Log.i(TAG, "launchEarFitTest()")
        sendCommand(session.launchEarFitTest())
    }

    private fun closeQuietly(sock: BluetoothSocket?) {
        try {
            sock?.close()
        } catch (e: IOException) {
            // nothing to do
        }
    }

    private fun requirePermission() {
        val granted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        check(granted) { "BLUETOOTH_CONNECT permission not granted; request it before calling connect()" }
    }

    companion object {
        private const val PROBE_TIMEOUT_MS = 2000L

        // Same priority order as something-x's `_PROBE_CHANNELS`: channels the
        // Nothing app has been observed using first, then every other valid
        // RFCOMM channel number.
        private val PROBE_CHANNELS = listOf(15, 17, 16, 18, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1)
    }
}

private fun ByteArray.toHexString(): String = joinToString(" ") { "%02x".format(it) }
