package com.tas5805m.control

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.util.UUID

sealed interface LinkState {
    object Disconnected : LinkState
    object Connecting : LinkState
    data class Connected(val name: String) : LinkState
}

/**
 * Koneksi Bluetooth Classic SPP (RFCOMM) ke konsol firmware (-D BT_SPP_CONSOLE=1).
 * Perangkat harus sudah di-pair lewat Pengaturan Bluetooth HP.
 */
@SuppressLint("MissingPermission")
class BtLink(
    private val scope: CoroutineScope,
    private val onLine: (String) -> Unit,
    private val onState: (LinkState) -> Unit,
) {
    companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val GAP_MS = 60L   // jeda antar perintah: buffer RX firmware hanya 512 byte
    }

    private var socket: BluetoothSocket? = null
    private var job: Job? = null
    private var tx = Channel<String>(Channel.UNLIMITED)

    fun connect(dev: BluetoothDevice) {
        close()
        onState(LinkState.Connecting)
        val queue = Channel<String>(Channel.UNLIMITED)
        tx = queue
        job = scope.launch(Dispatchers.IO) {
            var s: BluetoothSocket? = null
            try {
                s = dev.createRfcommSocketToServiceRecord(SPP)
                socket = s
                s.connect()
                val out = s.outputStream
                onState(LinkState.Connected(dev.name ?: dev.address))
                // penulis: satu antrean supaya urutan perintah terjaga
                launch {
                    for (cmd in queue) {
                        try {
                            out.write((cmd + "\n").toByteArray(Charsets.UTF_8))
                            out.flush()
                        } catch (e: IOException) { break }
                        delay(GAP_MS)
                    }
                }
                readLoop(s.inputStream)
            } catch (e: IOException) {
                onLine("[app] koneksi gagal/terputus: ${e.message}")
            } catch (e: SecurityException) {
                onLine("[app] izin Bluetooth ditolak")
            } finally {
                try { s?.close() } catch (_: IOException) {}
                queue.close()
                if (tx === queue) onState(LinkState.Disconnected)   // abaikan bila sudah ada koneksi baru
            }
        }
    }

    private fun readLoop(input: InputStream) {
        val buf = ByteArray(512)
        val line = StringBuilder()
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            val text = String(buf, 0, n, Charsets.UTF_8)
            for (ch in text) {
                if (ch == '\n') {
                    onLine(line.toString().trimEnd('\r'))
                    line.setLength(0)
                } else line.append(ch)
            }
        }
    }

    fun send(cmd: String) { tx.trySend(cmd) }

    fun close() {
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        job?.cancel()
        job = null
    }
}
