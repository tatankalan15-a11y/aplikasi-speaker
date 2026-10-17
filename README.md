# TAS5805M Control (Android)

Aplikasi pengatur untuk firmware ESP32 TAS5805M (`tas5805m_2_1_v4_3`).
Terhubung lewat **Bluetooth Classic SPP** ke konsol firmware, lalu mengirim perintah
yang sama dengan Serial Monitor (`vol`, `eqgain`, `preset`, `xover`, `subvol`, dst.).

## Syarat firmware
- Build dengan `-D BT_SPP_CONSOLE=1` (sudah ada di `platformio.ini` untuk ketiga env audio).
- Pair HP dengan **ESP32-TAS5805M** di Pengaturan Bluetooth sebelum membuka aplikasi.
- Audio (A2DP) dan konsol (SPP) bisa jalan bersamaan.

## Build lewat GitHub (otomatis, tanpa Android Studio)
1. Buat repo baru di GitHub, lalu unggah **isi** zip ini ke root repo
   (file `build.gradle.kts` dan folder `.github` harus ada di root, bukan di dalam subfolder).
   Cara cepat: `git init && git add . && git commit -m "init" && git branch -M main`
   lalu `git remote add origin <url-repo> && git push -u origin main`.
2. Buka tab **Actions** > workflow **Build APK**. Build berjalan otomatis setiap push
   (atau klik **Run workflow**). Butuh sekitar 3–6 menit.
3. Setelah hijau, buka run tersebut dan unduh **TAS5805M-Control-apk** di bagian *Artifacts*
   (berisi `TAS5805M-Control.apk`).
4. Untuk membuat Release dengan APK terlampir: `git tag v1.0 && git push origin v1.0`.
- APK hasilnya bertanda tangan *debug*: cukup untuk dipasang di HP sendiri
  (aktifkan "Pasang dari sumber tidak dikenal"), belum untuk Play Store.

## Build lokal
1. Buka folder ini di **Android Studio** (Koala/2024.1 atau lebih baru), tunggu Gradle sync.
   Bila diminta, izinkan Android Studio membuat Gradle wrapper (Gradle 8.7).
2. Colok HP (USB debugging) lalu **Run**, atau `Build > Build APK(s)`.
- minSdk 26 (Android 8.0), targetSdk 34.

## Mode yang didukung
Pilih sesuai env firmware yang di-flash (pilihan diingat di HP; bisa diganti di tab Audio).

| Mode | Env PlatformIO | Tab yang muncul |
|---|---|---|
| Stereo, 1 chip (`AUDIO_MODE 0`) | `esp32dev_stereo` | Audio (+ trim L/R), EQ, **Bass** (virtual bass), Konsol |
| 2.1, 2 chip (`AUDIO_MODE 1`) | `esp32dev_2chip_21` | Audio, EQ, **Sub / 2.1** (crossover, sub, polaritas, space, status chip sub), Konsol |
| 2.1, 1 chip (`AUDIO_MODE 2`) | `esp32dev_1chip_21` | Audio, EQ, **Sub / 2.1** (crossover, sub, polaritas), Konsol |

Deteksi otomatis mengirim perintah `all` dan membaca baris `AUDIO_MODE=n` dari balasannya.

## Fitur
- **Audio:** volume, analog gain, trim L/R (stereo), status/fault/clear fault.
- **EQ:** preset genre, EQ on/off, mode EQ, 15 band ±15 dB.
- **Sub / 2.1:** crossover LR4 40–250 Hz, volume dan polaritas sub, space (2 chip).
- **Bass:** virtual bass (stereo).
- **Konsol:** terminal penuh untuk semua perintah firmware.

## Catatan
- Slider mengirim perintah **saat jari dilepas**, bukan tiap geser (buffer RX firmware hanya 512 byte).
- Perintah dikirim satu per satu dengan jeda 60 ms.
- Tampilan slider adalah perkiraan lokal; sumber kebenaran tetap firmware. Tekan **Status** untuk membaca kondisi asli.
- Fitur yang tidak berlaku di suatu mode disembunyikan. Bila mode di aplikasi tidak sama dengan firmware,
  firmware membalas pesan penolakan di tab Konsol.
- Pengaturan tidak disimpan ke NVS oleh firmware dan kembali default setelah reboot.
- Firmware boot di volume 100%: turunkan dulu sebelum tes.
