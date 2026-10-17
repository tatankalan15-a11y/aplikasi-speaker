package com.tas5805m.control

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ============================================================================
// Root
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    vm: MainVm,
    devices: List<BluetoothDevice>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onOpenBtSettings: () -> Unit,
) {
    val connected = vm.link is LinkState.Connected
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TAS5805M Control")
                        Text(
                            when (val l = vm.link) {
                                is LinkState.Connected -> "Terhubung: ${l.name}"
                                LinkState.Connecting -> "Menghubungkan…"
                                LinkState.Disconnected -> "Tidak terhubung"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                actions = {
                    if (connected) OutlinedButton(
                        onClick = { vm.disconnect() },
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Putus") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!connected) {
                ConnectScreen(vm, devices, hasPermission, onRequestPermission, onRefresh, onOpenBtSettings)
            } else {
                val mode = vm.mode
                if (mode == null) {
                    ModePicker(vm)
                } else {
                    var tab by remember { mutableIntStateOf(0) }
                    val titles = listOf("Audio", "EQ", if (mode == AudioMode.STEREO) "Bass" else "Sub / 2.1", "Konsol")
                    TabRow(selectedTabIndex = tab) {
                        titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                    }
                    when (tab) {
                        0 -> AudioTab(vm, mode)
                        1 -> EqTab(vm, mode)
                        2 -> if (mode == AudioMode.STEREO) BassTab(vm) else SubTab(vm, mode)
                        else -> ConsoleTab(vm)
                    }
                }
            }
        }
    }
}

// ============================================================================
// Koneksi
// ============================================================================
@SuppressLint("MissingPermission")
@Composable
fun ConnectScreen(
    vm: MainVm,
    devices: List<BluetoothDevice>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onOpenBtSettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Cara menyambung", style = MaterialTheme.typography.titleMedium)
                Text("1. Firmware harus dibuild dengan -D BT_SPP_CONSOLE=1 (sudah ada di platformio.ini).")
                Text("2. Pair HP dengan \"ESP32-TAS5805M\" lewat Pengaturan Bluetooth.")
                Text("3. Kembali ke sini, pilih perangkat di bawah.")
            }
        }
        if (!hasPermission) {
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text("Izinkan akses Bluetooth") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenBtSettings) { Text("Pengaturan Bluetooth") }
                OutlinedButton(onClick = onRefresh) { Text("Segarkan daftar") }
            }
            if (devices.isEmpty()) Text("Belum ada perangkat yang di-pair.")
            devices.forEach { d ->
                val isTas = (d.name ?: "").contains("TAS", ignoreCase = true)
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name ?: "(tanpa nama)", style = MaterialTheme.typography.titleSmall)
                            Text(d.address + if (isTas) "  • kemungkinan DAC" else "", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { vm.connect(d) },
                            enabled = vm.link != LinkState.Connecting,
                        ) { Text("Hubungkan") }
                    }
                }
            }
        }
        if (vm.log.isNotEmpty()) {
            Text("Log terakhir", style = MaterialTheme.typography.titleSmall)
            Text(vm.log.takeLast(4).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

// ============================================================================
// Komponen kecil
// ============================================================================
@Composable
fun Section(title: String, note: String? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/** Slider bilangan bulat. Perintah dikirim saat jari dilepas (bukan tiap geser) agar buffer firmware aman. */
@Composable
fun IntSlider(
    label: String, value: Int, range: IntRange, unit: String = "",
    onValue: (Int) -> Unit, onCommit: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text("$value$unit", style = MaterialTheme.typography.titleSmall)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            onValueChangeFinished = onCommit,
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ============================================================================
// Tab Audio
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioTab(vm: MainVm, mode: AudioMode) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Mode perangkat", "Harus sama dengan firmware yang di-flash. Salah mode = fitur tertentu ditolak firmware.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioMode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { vm.setMode(m) }, label = { Text(m.label) })
                }
            }
            OutlinedButton(onClick = vm::detectMode, enabled = !vm.detecting) {
                Text(if (vm.detecting) "Mendeteksi…" else "Deteksi otomatis (kirim 'all')")
            }
        }
        Section("Volume", "Firmware boot di volume 100%. Turunkan dulu sebelum tes dengan speaker kecil.") {
            IntSlider("Volume digital", vm.volume, 0..100, "%", { vm.volume = it }, vm::commitVolume)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.volume = 0; vm.commitVolume() }) { Text("Mute (0)") }
                OutlinedButton(onClick = { vm.volume = 20; vm.commitVolume() }) { Text("20%") }
                OutlinedButton(onClick = { vm.volume = 50; vm.commitVolume() }) { Text("50%") }
            }
        }
        Section("Analog gain", "Sesuaikan dengan PVDD: 12 V → -9 dB, 15 V → -7 dB, 19–24 V → -3..0 dB." +
            if (mode == AudioMode.TWO_CHIP_21) " Berlaku untuk kedua chip." else "") {
            IntSlider("Analog gain", vm.analogGain, -15..0, " dB", { vm.analogGain = it }, vm::commitAnalogGain)
        }
        if (mode == AudioMode.STEREO) {
            Section("Trim L / R", "Penyeimbang kiri/kanan, rentang -6..+6 dB.") {
                IntSlider("Kiri", vm.trimL, -6..6, " dB", { vm.trimL = it }, { vm.commitTrim(1) })
                IntSlider("Kanan", vm.trimR, -6..6, " dB", { vm.trimR = it }, { vm.commitTrim(2) })
            }
        }
        Section("Diagnostik") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.send("status") }) { Text("Status") }
                OutlinedButton(onClick = { vm.send("fault") }) { Text("Fault") }
                OutlinedButton(onClick = { vm.send("clearfault") }) { Text("Clear") }
            }
        }
    }
}

// ============================================================================
// Tab EQ
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqTab(vm: MainVm, mode: AudioMode) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Preset", "Preset genre hanya mengubah band 3–15. Preset 2\"/Punch/Voice juga mengatur profile; slider tidak ikut berubah.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { p ->
                    FilterChip(selected = vm.preset == p.key, onClick = { vm.applyPreset(p) }, label = { Text(p.label) })
                }
            }
        }
        Section("Mode EQ") {
            SwitchRow("EQ aktif", vm.eqOn, vm::setEq)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("OFF", "ON", "BIAMP", "BIAMP OFF").forEachIndexed { i, t ->
                    FilterChip(selected = vm.eqMode == i, onClick = { vm.setEqModeTo(i) }, label = { Text(t) })
                }
            }
        }
        Section("15 band (±15 dB)",
            if (mode.isTwoOne) "Mode 2.1: band 1–2 dipakai crossover satelit. Mengubahnya bisa menimpa high-pass, jadi biarkan 0 dB. EQ ini berlaku ke chip satelit; sub diatur lewat tab Sub / 2.1."
            else "EQ berlaku ke kiri dan kanan sekaligus.") {
            for (i in 0 until 15) {
                IntSlider(EQ_FREQ[i], vm.bands[i], -15..15, " dB", { vm.bands[i] = it }, { vm.commitBand(i) })
            }
            OutlinedButton(onClick = vm::resetBands) { Text("Reset semua band ke 0 dB") }
        }
    }
}

// ============================================================================
// Tab Sub / 2.1 (mode 2-chip dan 1-chip)
// ============================================================================
@Composable
fun SubTab(vm: MainVm, mode: AudioMode) {
    val twoChip = mode == AudioMode.TWO_CHIP_21
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section(
            "Crossover LR4",
            if (twoChip) "TAS #1 = high-pass satelit L/R, TAS #2 = low-pass sub. 'Matikan' = satelit full-range, sub senyap."
            else "1 chip: OUT L = satelit mono (high-pass), OUT R = sub mono (low-pass). 'Matikan' = stereo flat.",
        ) {
            IntSlider("Frekuensi", vm.xoverHz, 40..250, " Hz", { vm.xoverHz = it }, { if (vm.xoverOn) vm.xoverApply() })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::xoverApply) { Text("Aktifkan") }
                OutlinedButton(onClick = vm::xoverOff) { Text("Matikan") }
                OutlinedButton(onClick = { vm.send("xoverget") }) { Text("Baca") }
            }
        }
        Section("Subwoofer", "Volume sub dan polaritas. Balik polaritas bila bass terdengar tipis di sekitar titik crossover.") {
            IntSlider("Volume sub", vm.subVol, 0..100, "%", { vm.subVol = it }, vm::commitSubVol)
            SwitchRow("Polaritas terbalik", vm.subInvert, vm::setSubInvert)
            if (twoChip) OutlinedButton(onClick = { vm.send("substatus") }) { Text("Status chip sub") }
        }
        if (twoChip) {
            Section("Space (pelebaran stereo)", "Hanya aktif saat crossover menyala.") {
                SwitchRow("Space", vm.spaceOn, vm::setSpace)
                IntSlider("Level", vm.spaceLvl, 0..100, "%", { vm.spaceLvl = it }, vm::commitSpace)
            }
        } else {
            Text(
                "Space hanya ada di mode 2 chip, dan virtual bass hanya di mode stereo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ============================================================================
// Tab Bass (mode stereo)
// ============================================================================
@Composable
fun BassTab(vm: MainVm) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Virtual bass", "Menambah harmonik bass supaya speaker kecil terdengar lebih berat. OFF = jalur audio lurus.") {
            SwitchRow("Virtual bass", vm.vbassOn, vm::setVbass)
            IntSlider("Level", vm.vbassLvl, 0..100, "%", { vm.vbassLvl = it }, vm::commitVbass)
        }
        Text(
            "Mode stereo tidak punya crossover atau subwoofer. Untuk 2.1, flash env esp32dev_2chip_21 atau " +
                "esp32dev_1chip_21, lalu ganti mode di tab Audio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ============================================================================
// Pemilih mode (muncul saat pertama terhubung)
// ============================================================================
@Composable
fun ModePicker(vm: MainVm) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pilih mode perangkat", style = MaterialTheme.typography.titleLarge)
        Text("Pilih sesuai firmware yang di-flash ke ESP32. Pilihan diingat di HP ini.")
        AudioMode.entries.forEach { m ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(m.label, style = MaterialTheme.typography.titleMedium)
                    Text(m.detail, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { vm.setMode(m) }) { Text("Pakai mode ini") }
                }
            }
        }
        OutlinedButton(onClick = vm::detectMode, enabled = !vm.detecting, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.detecting) "Mendeteksi…" else "Deteksi otomatis (kirim 'all')")
        }
        Text(
            "Deteksi mengirim perintah 'all' (diagnostik firmware, membaca baris AUDIO_MODE=...). " +
                "Bila tidak ada balasan, pilih manual.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ============================================================================
// Konsol
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsoleTab(vm: MainVm) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.log.size) { if (vm.log.isNotEmpty()) listState.scrollToItem(vm.log.size - 1) }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("help", "status", "all", "fault", "xoverget", "volget", "btforget").forEach { c ->
                OutlinedButton(onClick = { vm.send(c) }) { Text(c) }
            }
        }
        Card(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(vm.log) { l -> Text(l, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            }
        }
        val send = { if (text.isNotBlank()) { vm.send(text.trim()); text = "" } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("perintah, mis. eqgain 3 2") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            Button(onClick = { send() }) { Text("Kirim") }
        }
    }
}
