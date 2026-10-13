# Arsip Senja

Arsip pribadi di atas Telegram — tanpa server, tanpa biaya penyimpanan.
Aplikasi Android native (Kotlin + Jetpack Compose) yang memakai channel/saved
messages Telegram sebagai penyimpanan, dengan TDLib sebagai lapisan klien.

## Fitur

- Login Telegram: nomor telepon → kode OTP → (opsional) 2FA, lewat TDLib nyata.
- Dasbor arsip: grid atau daftar katalog bernomor, pencarian debounce, filter
  tipe/koleksi, urut nama/tanggal/ukuran.
- Unggah berkas via WorkManager (foreground service + notifikasi), progres
  per-bagian, pemecahan otomatis berkas > 2 GB.
- Unduh & buka berkas: pratinjau gambar (Coil), pemutar video/audio (Media3),
  buka lewat FileProvider.
- Koleksi (folder) dan tautan pesan Telegram (salin/buka/share).
- Kunci biometrik opsional (fail-open bila hardware tidak ada).
- Pengaturan: mode kisi/daftar, urutan berkas, kunci biometrik, kredensial
  api_id/api_hash, sinkronisasi, statistik arsip.

## Stack

| Lapisan | Teknologi |
|---------|-----------|
| Bahasa / UI | Kotlin 2.0.21, Jetpack Compose (BOM 2024.09.03), Material 3 |
| Navigasi | androidx.navigation-compose 2.8.4, type-safe routes |
| Penyimpanan | Room 2.6.1, DataStore Preferences |
| Latar | WorkManager 2.9.1 (unggah/unduh), notifikasi channel sendiri |
| Telegram | TDLib (AAR `com.github.capullo-tech:lib-tdlib-android`) |
| Media | Coil 2.7.0, Media3 1.4.1, androidx.biometric 1.1.0 |
| DI | Manual (`AppContainer` di `ArsipSenjaApp`), tanpa Hilt |
| Uji | JUnit 4.13.2, Google Truth 1.4.4 (JVM unit test, tanpa emulator) |

Ketentuan build: `compileSdk`/`targetSdk` 34, `minSdk` 26,
`applicationId` `com.arsipsenja`, Gradle wrapper 8.7, AGP 8.5.2.

## Cara membangun

Prasyarat: JDK 17 (Temurin), Android SDK dengan platform `android-34` dan
`build-tools;34.0.0`.

1. Buat `local.properties` di root proyek:

   ```
   sdk.dir=/path/ke/android-sdk
   ```

2. Build dari Android Studio, atau CLI:

   ```bash
   ./gradlew testDebugUnitTest      # unit test JVM
   ./gradlew assembleDebug          # APK debug
   ```

   APK keluar di `app/build/outputs/apk/debug/app-debug.apk`.

## Continuous integration

`.github/workflows/build-apk.yml` berjalan pada push/PR/permintaan manual:
JDK 17 (Temurin) → `testDebugUnitTest` → `assembleDebug` → unggah artefak
`arsip-senja-debug` (APK debug) plus laporan test.

## Catatan Telegram

- Daftarkan aplikasi di <https://my.telegram.org> lalu isi `api_id`/`api_hash`
  di layar Login (atau Pengaturan → Akun Telegram). Kredensial disimpan aman di
  perangkat dan tidak pernah dikodekan di sumber.
- Penyimpanan adalah channel/supergroup privat Telegram: satu **koleksi** = satu
  channel, dibuat dan dikelola dari dasbor (Koleksi → CRUD).
- Berkas >2 GB dipecah menjadi potongan ~1,9 GB (`nama.partN.ekstensi`) saat
  unggah; tiap potongan menjadi entri katalog terpisah dan saat diunduh kembali
  sebagai berkas terpisah (penggabungan otomatis belum tersedia).

## Struktur sumber

```
app/src/main/java/com/arsipsenja/
├── data/
│   ├── local/      # Room (berkas_arsip, koleksi, tautan), KueriTampilan, PengaturanStore
│   ├── repo/       # ArsipRepository (indeks arsip vs Telegram)
│   ├── work/       # UploadWorker, DownloadWorker, Notifikasi
│   └── telegram/   # TelegramClient (TDLib), pemeta entitas, status otorisasi
├── ui/
│   ├── components/ # kartu katalog, chip, progres, state kosong, statistik, dst.
│   ├── screens/    # Login, Dasbor, Unggah, Berkas, Tautan, Pengaturan
│   ├── viewmodel/  # ViewModel per layar + factory manual
│   ├── nav/        # rute type-safe + NavHost
│   ├── icons/      # ArsipIcons (vektor outline, tanpa emoji)
│   └── theme/      # warna, bentuk, tipografi (Fraunces + Plus Jakarta Sans)
└── util/           # format ukuran/tanggal, tipe berkas, pemecah >2GB
```

## Pengujian

Unit test JVM (tanpa emulator) di `app/src/test/`:

- `util/` — format ukuran & tanggal, klasifikasi tipe berkas, pemecah berkas
  (pecahan, penamaan, gabung).
- `data/local/KueriTampilanTest` — SQL Room dinamis (kata kunci, filter,
  urutan).
- `data/telegram/PemetaTelegramTest` — pemetaan `BerkasMentah` → entity,
  progres persen.

Jalankan semua: `./gradlew testDebugUnitTest`.
