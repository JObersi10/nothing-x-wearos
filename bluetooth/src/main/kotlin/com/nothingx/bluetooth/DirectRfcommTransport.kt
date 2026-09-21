package com.nothingx.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Watch-side direct RFCOMM connection to a Nothing/CMF earbuds device —
 * the "watch connects directly" path from the architecture plan. Ports
 * something-x's `NothingDevice` (protocol.py) channel-probe + recv-loop
 * approach to Android's Bluetooth Classic APIs.
 *
 * UNVERIFIED, this is the central open risk of the whole direct-connect
 * design: Android's public [BluetoothDevice] API only exposes
 * `createRfcommSocketToServiceRecord(UUID)`, which does an SDP lookup by
 * service UUID — there is no public API to open RFCOMM on an arbitrary
 * channel number, which is what the Nothing protocol needs (no known SDP
 * UUID for it). This class uses the same private `createRfcommSocket(int)`
 * reflection call that every Android Bluetooth-SPP-terminal app on the Play
 * Store relies on for the same reason. It has worked across Android versions
 * for years but is not a stable contract — Google could break it, and on a
 * Wear OS build specifically nobody has confirmed it here. First real build
 * milestone: run this against your Galaxy Watch 4 and see if a socket opens
 * at all.
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

    private val session = EarbudsSession()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _deviceState = MutableStateFlow(DeviceState())
    override val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String) {
        requirePermission()
        val bluetoothAdapter = adapter ?: run {
            _connectionState.value = ConnectionState.Failed("No Bluetooth adapter on this device")
            return
        }

        connectionJob?.cancel()
        connectionJob = scope.launch {
            val device = try {
                bluetoothAdapter.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                _connectionState.value = ConnectionState.Failed("Invalid address: $address")
                return@launch
            }

            var tried = 0
            for (channel in PROBE_CHANNELS) {
                tried++
                _connectionState.value = ConnectionState.Connecting(tried)
                val result = tryChannel(device, channel)
                if (result != null) {
                    val (openSocket, initialBytes) = result
                    socket = openSocket
                    output = openSocket.outputStream
                    fsn = 0
                    _connectionState.value = ConnectionState.Connected
                    runReceiveLoop(openSocket.inputStream, initialBytes)
                    return@launch
                }
            }
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
            return null
        } ?: return null

        try {
            sock.connect()
        } catch (e: IOException) {
            closeQuietly(sock)
            return null
        }

        return try {
            sock.inputStream.let { input ->
                sock.outputStream.let { out ->
                    val probe = FrameEncoder.encode(Commands.GET_PROTO_VERSION, byteArrayOf(0x01), fsn = 1)
                    out.write(probe)
                    out.flush()

                    val initial = readWithDeadline(input, PROBE_TIMEOUT_MS)
                    // Only accept the two known frame headers — a channel that answers
                    // with anything else (e.g. HFP's AT-command channel) is not ours,
                    // same guard something-x applies for the same reason.
                    if (initial.isEmpty() || (initial[0].toInt() and 0xFF) != Commands.SOF) {
                        closeQuietly(sock)
                        null
                    } else {
                        sock to initial
                    }
                }
            }
        } catch (e: IOException) {
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
                if (n < 0) break
                dispatchFrames(parser.feed(buffer.copyOf(n)))
            }
        } catch (e: IOException) {
            // Falls through to disconnect handling below.
        } finally {
            _connectionState.value = ConnectionState.Disconnected
            closeQuietly(socket)
            socket = null
            output = null
        }
    }

    private suspend fun dispatchFrames(frames: List<Frame>) {
        for (frame in frames) {
            val followUps = session.handleFrame(frame)
            _deviceState.value = session.state
            for (cmd in followUps) {
                sendCommand(cmd)
            }
        }
    }

    private suspend fun sendCommand(command: OutgoingCommand) {
        val out = output ?: return
        writeMutex.withLock {
            fsn = (fsn + 1) and 0xFF
            val frame = FrameEncoder.encode(command.cmd, command.payload, fsn)
            withContext(Dispatchers.IO) {
                try {
                    out.write(frame)
                    out.flush()
                } catch (e: IOException) {
                    _connectionState.value = ConnectionState.Failed("Write failed: ${e.message}")
                }
            }
        }
    }

    override suspend fun disconnect() {
        connectionJob?.cancel()
        closeQuietly(socket)
        socket = null
        output = null
        _connectionState.value = ConnectionState.Idle
    }

    override suspend fun setAncMode(mode: AncMode) {
        val cmd = session.setAncMode(mode)
        _deviceState.value = session.state
        sendCommand(cmd)
    }

    override suspend fun setEqPreset(preset: EqPreset) {
        val cmd = session.setEqPreset(preset)
        _deviceState.value = session.state
        sendCommand(cmd)
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
