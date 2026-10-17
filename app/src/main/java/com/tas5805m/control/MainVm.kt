package com.tas5805m.control

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Mode hardware = AUDIO_MODE di firmware (platformio.ini: esp32dev_stereo / _2chip_21 / _1chip_21). */
enum class AudioMode(val code: Int, val label: String, val detail: String) {
    STEREO(0, "Stereo (1 chip)", "1x TAS5805M, L/R full-range. Env: esp32dev_stereo"),
    TWO_CHIP_21(1, "2.1 — 2 chip", "TAS #1 satelit L/R, TAS #2 subwoofer. Env: esp32dev_2chip_21"),
    ONE_CHIP_21(2, "2.1 — 1 chip", "OUT L satelit mono HP, OUT R sub mono LP. Env: esp32dev_1chip_21");

    val isTwoOne get() = this != STEREO
    companion object { fun from(code: Int) = entries.firstOrNull { it.code == code } }
}

/** Nama band EQ 1..15 sesuai firmware. */
val EQ_FREQ = listOf("20 Hz", "31.5 Hz", "50 Hz", "80 Hz", "125 Hz", "200 Hz", "315 Hz", "500 Hz",
    "800 Hz", "1.25 kHz", "2 kHz", "3.15 kHz", "5 kHz", "8 kHz", "16 kHz")

/** Preset genre firmware (GENRE_PRESETS di main.cpp): hanya mengubah band 3..15. */
class Preset(val key: String, val label: String, val bands3to15: IntArray?)

val PRESETS = listOf(
    Preset("flat", "Flat", IntArray(13)),
    Preset("enak", "Enak", intArrayOf(0, 0, 1, 1, 0, 0, 0, -1, -1, -2, -2, -2, -2)),
    Preset("pop", "Pop", intArrayOf(0, 0, 1, 0, -1, 0, 0, 0, 1, 1, 1, 1, 0)),
    Preset("rock", "Rock", intArrayOf(0, 0, 1, 0, -2, -1, -1, 0, 1, 1, 2, 1, 0)),
    Preset("metal", "Metal", intArrayOf(0, 0, 1, 0, -3, -1, -1, 0, 0, 1, 2, 2, 1)),
    Preset("edm", "EDM", intArrayOf(0, 1, 2, 0, -2, -1, 0, 0, 0, 1, 2, 2, 1)),
    Preset("hiphop", "Hip-hop", intArrayOf(0, 1, 2, 0, -1, 0, 0, 0, 0, 1, 1, 1, 0)),
    Preset("rnb", "R&B", intArrayOf(0, 0, 2, 1, -1, 0, 0, 0, 0, 1, 1, 1, 0)),
    Preset("jazz", "Jazz", intArrayOf(0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1)),
    Preset("classical", "Klasik", intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1)),
    Preset("acoustic", "Akustik", intArrayOf(0, 0, 1, 0, -1, 0, 0, 0, 1, 1, 0, 1, 1)),
    Preset("dangdut", "Dangdut", intArrayOf(0, 0, 2, 1, -1, 0, 0, 0, 1, 1, 1, 0, 0)),
    // preset di bawah mengubah profile/HPF juga; nilai slider tidak disinkronkan
    Preset("2inch", "2\" 15W", null),
    Preset("punch", "Punch", null),
    Preset("voice", "Voice", null),
)

class MainVm(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("tas", 0)
    var mode by mutableStateOf(AudioMode.from(prefs.getInt("mode", -1)))
        private set
    var detecting by mutableStateOf(false); private set

    var link by mutableStateOf<LinkState>(LinkState.Disconnected); private set
    val log = mutableStateListOf<String>()

    // ---- state pengaturan (hanya tampilan; sumber kebenaran tetap firmware) ----
    var volume by mutableIntStateOf(20)
    var analogGain by mutableIntStateOf(-9)
    var trimL by mutableIntStateOf(0)
    var trimR by mutableIntStateOf(0)
    var eqOn by mutableStateOf(true)
    var eqMode by mutableIntStateOf(1)
    val bands = mutableStateListOf<Int>().apply { repeat(15) { add(0) } }
    var preset by mutableStateOf<String?>(null)

    var xoverHz by mutableIntStateOf(150)
    var xoverOn by mutableStateOf(false)
    var subVol by mutableIntStateOf(50)
    var subInvert by mutableStateOf(false)
    var spaceOn by mutableStateOf(false)
    var spaceLvl by mutableIntStateOf(30)
    var vbassOn by mutableStateOf(false)
    var vbassLvl by mutableIntStateOf(30)

    private val btLink = BtLink(
        scope = viewModelScope,
        onLine = { l -> viewModelScope.launch(Dispatchers.Main) { addLog(l) } },
        onState = { st ->
            viewModelScope.launch(Dispatchers.Main) {
                link = st
                if (st is LinkState.Connected) {
                    addLog("[app] terhubung ke ${st.name}")
                    btLink.send("status")
                } else if (st is LinkState.Disconnected) addLog("[app] terputus")
            }
        },
    )

    private fun addLog(s: String) {
        if (log.size > 400) repeat(100) { log.removeAt(0) }
        log.add(s)
        // baris dari perintah 'all': "AUDIO_MODE=1 (2 chip 2.1) | ..."
        val m = Regex("AUDIO_MODE=(\\d)").find(s)
        if (m != null) {
            AudioMode.from(m.groupValues[1].toInt())?.let { setMode(it); addLog("[app] mode terdeteksi: ${it.label}") }
            detecting = false
        }
    }

    /** Pilih mode manual / hasil deteksi. Disimpan di HP. Nilai slider 2.1 diset ke default firmware (150 Hz, aktif). */
    fun setMode(m: AudioMode) {
        mode = m
        prefs.edit().putInt("mode", m.code).apply()
        spaceOn = false; vbassOn = false
        if (m.isTwoOne) { xoverHz = 150; xoverOn = true }
    }

    fun clearMode() { mode = null; prefs.edit().remove("mode").apply() }

    /** Kirim 'all' (diagnostik firmware) dan baca baris AUDIO_MODE=... dari balasannya. */
    fun detectMode() { detecting = true; send("all") }

    fun connect(dev: BluetoothDevice) = btLink.connect(dev)
    fun disconnect() = btLink.close()

    fun send(cmd: String) {
        if (link !is LinkState.Connected) { addLog("[app] belum terhubung"); return }
        addLog("> $cmd")
        btLink.send(cmd)
    }

    // ---- audio ----
    fun commitVolume() = send("vol $volume")
    fun commitAnalogGain() = send("again $analogGain")
    fun commitTrim(ch: Int) = send("trim $ch ${if (ch == 1) trimL else trimR}")
    fun setEq(on: Boolean) { eqOn = on; send(if (on) "eqon" else "eqoff") }
    fun setEqModeTo(m: Int) { eqMode = m; send("eqmode $m") }

    // ---- EQ ----
    fun commitBand(i: Int) = send("eqgain ${i + 1} ${bands[i]}")
    fun resetBands() {
        viewModelScope.launch { for (i in 0 until 15) { bands[i] = 0; send("eqgain ${i + 1} 0"); delay(40) } }
    }
    fun applyPreset(p: Preset) {
        preset = p.key
        p.bands3to15?.let { v -> for (i in v.indices) bands[i + 2] = v[i] }
        send("preset ${p.key}")
    }

    // ---- 2.1 ----
    fun xoverApply() { xoverOn = true; send("xover $xoverHz") }
    fun xoverOff() { xoverOn = false; send("xover off") }
    fun commitSubVol() = send("subvol $subVol")
    fun setSubInvert(v: Boolean) { subInvert = v; send("subpol ${if (v) 1 else 0}") }
    fun setSpace(on: Boolean) { spaceOn = on; send(if (on) "space on" else "space off") }
    fun commitSpace() = send("space $spaceLvl")
    fun setVbass(on: Boolean) { vbassOn = on; send(if (on) "vbass on" else "vbass off") }
    fun commitVbass() = send("vbass $vbassLvl")

    override fun onCleared() { btLink.close() }
}
