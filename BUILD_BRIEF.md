# BUILD BRIEF — Arsip Senja Android (Native)

## 1. Ringkasan
Bangun aplikasi **Android native** "Arsip Senja" — Telegram Drive yang beneran jadi 1 aplikasi: ngomong langsung ke Telegram via MTProto/TDLib, tanpa backend server terpisah. Ini port + perluasan dari Arsip Senja web (~/workspace/arsip-senja) ke native Android dengan bahasa desain yang sama.

**Stack wajib:** Kotlin, Jetpack Compose (Material 3), TDLib (official Telegram Database Library) untuk akses Telegram, Room (SQLite lokal), WorkManager (transfer background), DataStore (pengaturan).

## 2. Bahasa Desain — "Arsip Senja" (port dari web, WAJIB konsisten)
- Palet: `#0e0c1a` (indigo-hitam), `#141126` (surface), aksen amber lentera `#e8a33d`, aksen ungu senja `#7c5cbf`, teks `#f2ecdf` / `#a89fb8`.
- Tipografi: **Fraunces** (display/angka) + **Plus Jakarta Sans** (body) — via Google Fonts downloadable fonts.
- Metafora **kartu katalog**: tiap file punya nomor indeks (No. 024), stempel koleksi, sudut 10dp, hover/press → glow amber.
- Ikon: gaya **Lucide** (outline 2dp). DILARANG emoji sebagai ikon UI.
- Bahasa UI: Bahasa Indonesia. DILARANG AI-slop generik.

## 3. Arsitektur (MVVM)
```
app/src/main/java/com/arsipsenja/
  data/
    telegram/   — TdClient wrapper (login, kirim/terima file, progress)
    local/      — Room: FileEntity, KoleksiEntity, TautanEntity + DAO
    repo/       — ArsipRepository (sumber kebenaran: Room + sinkron Telegram)
    work/       — UploadWorker, DownloadWorker (WorkManager, retry, notifikasi progres)
  ui/
    theme/      — warna, tipografi, shape (Arsip Senja)
    components/ — KatalogCard, KoleksiChip, ProgresUnggah, EmptyState, dsb.
    screens/    — Login, Dasbor, Unggah, Pratinjau, Tautan, Pengaturan
    nav/        — NavHost
  util/         — FormatBytes, FormatTanggal, TipeFile
```

## 4. Fitur MVP (fase 1)
1. **Login Telegram** (TDLib): nomor HP → kode OTP → password 2FA (bila ada); session tersimpan aman (EncryptedSharedPreferences).
2. **Dasbor Arsip**: grid kartu katalog + toggle list; statistik (jumlah, total ukuran, "Tak terbatas"); filter tipe; sort (nama/tanggal/ukuran).
3. **Koleksi**: CRUD koleksi (dipetakan ke channel/grup privat Telegram); hitung per koleksi.
4. **Unggah**: pilih file (SAF), antrean upload dengan progres per file (WorkManager + notifikasi), chunk otomatis >2GB di-split.
5. **Unduh**: ke penyimpanan perangkat dengan progres; buka file.
6. **Pencarian**: cari nama file di index lokal (as-you-type).
7. **Pratinjau**: gambar (zoom), video (exoplayer/streaming), audio, PDF, teks.
8. **Tautan berbagi**: tautan pesan Telegram + salin; daftar + cabut.
9. **Detail file**: nomor katalog, ukuran, tanggal, koleksi, id pesan Telegram.
10. **Kunci biometrik** (sidik jari) opsional di Pengaturan.

**Catatan TDLib:** pakai AAR TDLib prebuilt yang mendukung arm64 (telusuri opsi: build resmi tdlib, atau AAR komunitas yang terpercaya — pilih yang paling stabil dan dokumentasikan pilihannya). `api_id`/`api_hash` diinput user saat login pertama (dari my.telegram.org) — JANGAN hardcode milik siapa pun.

## 5. Verifikasi (realistis untuk VM ini)
VM ini TIDAK bisa menjalankan Gradle build (RAM <1GB — sudah terbukti). Maka:
- Verifikasi statis WAJIB: semua file Kotlin lolos parse (`kotlinc` bila tersedia, minimal cek struktur), XML valid, `AndroidManifest.xml` + `build.gradle.kts` konsisten (applicationId `com.arsipsenja`, minSdk 26, targetSdk 34).
- Tulis **GitHub Actions workflow** (`.github/workflows/build-apk.yml`): checkout → setup JDK 17 → setup Android SDK → `./gradlew assembleDebug` → upload APK sebagai artifact. INI verifikasi build yang sebenarnya — pastikan workflow valid (YAML parse + action version pin).
- JANGAN klaim "APK jadi" — yang dijanjikan adalah: kode lengkap + CI hijau yang menghasilkan APK artifact.

## 6. Aturan Operasi
- Ikuti `~/workspace/opencode/AGENTS.md`: skill-router (Android/Kotlin/Compose/TDLib/Material3/security skills), fase P0→P5 + micro-verification, format laporan: Active Skills / Current Phase / Deep Work / Micro-Verification / Next Phase Readiness.
- **Plan Mode MATI.** Jangan bertanya — eksekusi, tulis kode, perbaiki yang gagal.
- Direktori kerja: `~/workspace/arsip-senja-android/`. Bahasa Indonesia untuk laporan & UI.
