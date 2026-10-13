# RESUME BRIEF — Arsip Senja Android (run2, lanjut dari checkpoint)

Proyek: aplikasi Android native Kotlin + Jetpack Compose + TDLib, di
`~/workspace/arsip-senja-android/`. Run sebelumnya (run1) terputus karena VM
restart. Kode yang sudah ada (49 file) JANGAN ditulis ulang — lanjutkan.

Brief asli lengkap ada di `BUILD_BRIEF.md` (baca dulu, tapi yang sudah
selesai jangan diulang).

## Sudah selesai (JANGAN diulang)
- Scaffolding Gradle: settings.gradle.kts, build.gradle.kts, gradle.properties,
  libs.versions.toml, wrapper, .gitignore, AndroidManifest.xml, resources
  (strings/colors/themes/font_certs/backup_rules/data_extraction_rules/file_paths),
  launcher icons.
- Data layer: Room entities+DAO (BerkasArsip, Koleksi, Tautan), ArsipDatabase,
  KueriTampilan, StatistikBerkas, PengaturanStore (DataStore).
- Repository: ArsipRepository.kt, PemetaTelegram.kt.
- Telegram: TelegramClient.kt, StatusOtorisasi.kt, TelegramException.kt.
- Work: UploadWorker.kt, DownloadWorker.kt, Notifikasi.kt.
- UI foundation: ui/theme (Color/Shape/Theme/Type), ui/icons/ArsipIcons.kt,
  ArsipSenjaApp.kt (Application + AppContainer DI manual).
- Util: FormatBytes, FormatTanggal, TipeFile, Urutan.

## Wajib dikerjakan sekarang
1. UI components (Compose, Material 3, Bahasa Indonesia, tanpa emoji):
   kartu katalog bernomor, chip filter, progres transfer, state kosong,
   statistik, pencarian, ikon berkas.
2. ViewModels: Login, Dasbor, Unggah, Berkas, Tautan, Pengaturan.
3. Screens + NavHost (type-safe routes) + MainActivity (FragmentActivity
   + biometrik opsional).
4. Lengkapi TelegramClient: login nomor→OTP→2FA, download/upload dengan
   progres, split file >2GB, tautan pesan — implementasi NYATA terhadap TDLib
   Java API, bukan mock.
5. P4: unit tests JVM (logika repo/util/pemeta) + workflow
   `.github/workflows/build-apk.yml` (JDK 17, debug APK artifact) +
   verifikasi statis (parse semua Kotlin/XML/YAML; pastikan kompilasi
   sintaks valid).
6. P5: README.md + laporan akhir dengan format:
   Active Skills / Current Phase / Deep Work / Micro-Verification /
   Next Phase Readiness.

Aturan: Plan Mode MATI. Jangan tanya balik, kerjakan sampai selesai.
Akhiri dengan baris `EXIT:<kode>` dan ringkasan file yang dibuat.
