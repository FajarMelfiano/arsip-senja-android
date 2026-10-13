# RESUME BRIEF — Arsip Senja Android (run3, finishing dari checkpoint)

Proyek di `~/workspace/arsip-senja-android/`. Run2 terputus oleh VM restart
saat P4 QA. Kode 86 file SUDAH LENGKAP — JANGAN tulis ulang file yang ada
kecuali untuk perbaikan bug nyata yang kamu temukan saat verifikasi.

Brief asli: `BUILD_BRIEF.md`. Status komponen:
- SELESAI: Gradle scaffolding, Room (entities/DAO/database/DataStore),
  ArsipRepository, TDLib client (TelegramClient/StatusOtorisasi/PemetaTelegram),
  WorkManager (Upload/Download/Notifikasi), PemecahBerkas (split >2GB),
  theme, ArsipIcons, 8 komponen UI Compose, 6 ViewModel, 5 screen +
  NavHost, MainActivity (biometrik), 6 unit test JVM, workflow
  `.github/workflows/build-apk.yml`, README.md.

## Tugas run3 (QA final + tutup)
1. Verifikasi statis PENUH: parse/validasi sintaks SEMUA file .kt, .xml,
   .yml/.yaml di proyek. Bila menemukan error sintaks, perbaiki filenya.
2. Cek konsistensi: semua import merujuk ke file/kelas yang benar-benar ada;
   tidak ada referensi yatim; resource XML yang dirujuk ada.
3. Review TelegramClient.kt: pastikan implementasi NYATA terhadap TDLib
   Java API (login nomor→OTP→2FA, download/upload berprogres, split >2GB,
   tautan pesan) — bukan stub/mock. Bila masih stub, lengkapi.
4. JANGAN coba build Gradle lokal (VM kehabisan RAM; build APK resmi via
   GitHub Actions nanti).
5. Tulis/rapikan laporan akhir di akhir run dengan format:
   Active Skills / Current Phase / Deep Work / Micro-Verification /
   Next Phase Readiness.

Aturan: Plan Mode MATI. Jangan tanya balik, kerjakan sampai selesai.
Akhiri dengan baris `EXIT:0` bila semua lolos, atau `EXIT:1` + daftar
masalah yang tersisa.
