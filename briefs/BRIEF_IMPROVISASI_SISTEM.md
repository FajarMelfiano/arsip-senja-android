# BRIEF — Improvisasi Besar-besaran Sistem Arsip Senja Android

## 0. Aturan Operasi (NON-NEGOTIABLE)

- Ikuti `~/workspace/opencode/AGENTS.md`: skill-router (Android/Kotlin/Compose/TDLib/Material3/security/testing skills), fase P0→P5 + micro-verification gate, format laporan: Active Skills / Current Phase / Deep Work / Micro-Verification / Next Phase Readiness.
- **Plan Mode MATI.** Jangan bertanya — eksekusi, tulis kode, perbaiki yang gagal.
- **WAJIB brainstorming:** di fase perancangan (P0/P1), muat dan pakai skill `brainstorming` (`~/workspace/skills/brainstorming/SKILL.md`): divergent → SCAMPER → clustering → convergent, SEBELUM menulis satu baris kode pun.
- Direktori kerja: `~/workspace/arsip-senja-android/`. Bahasa Indonesia untuk laporan & UI.
- **DILARANG push/PR/merge/deploy.** Commit lokal ke branch baru `improvisasi-sistem-vault-sync` saja. CI GitHub yang akan build.
- Bahasa desain Arsip Senja WAJIB konsisten: indigo-hitam `#0e0c1a`, surface `#141126`, amber `#e8a33d`, ungu senja `#7c5cbf`, teks `#f2ecdf`/`#a89fb8`, Fraunces + Plus Jakarta Sans, kartu katalog bernomor, ikon Lucide (DILARANG emoji sebagai ikon), Bahasa Indonesia, anti AI-slop.
- Jangan merusak fitur yang sudah ada: realtime sync, deteksi duplikat, impor auto-sync, resume unggah, Senja Memories, popup status/penamaan, kunci per koleksi.

## 1. Konteks

Arsip Senja = arsip pribadi di atas Telegram (TDLib), Kotlin + Jetpack Compose + Room + WorkManager.
Keluhan nyata user yang WAJIB diselesaikan brief ini:
1. Status vault di Pengaturan basi — tidak segar, tidak auto-deteksi vault sehat yang sudah ada.
2. Pemilih grup lama menampilkan grup milik orang lain.
3. Tombol "Perbaiki" menyesatkan saat vault aktif berupa channel biasa yang tidak bisa menjadi forum.

## 2. Paket A — Sistem Vault Sehat Otomatis

### A1. Wizard "Siapkan Vault" (satu tombol)
- Satu tombol di Pengaturan + saat vault bermasalah: buat supergroup forum privat otomatis.
- Langkah otomatis: buat supergroup → ubah jadi forum (aktifkan topik) → set judul/deskripsi/foto default → pastikan user admin/owner → simpan sebagai vault aktif → verifikasi tulis (cek izin kirim).
- Tiap langkah lapor progres ke UI (step indicator). Gagal di satu langkah → pesan jelas Bahasa Indonesia + tombol "Coba lagi".

### A2. Health check vault berkala (status SEGAR)
- WorkManager periodik (±6 jam, boleh dikonfigurasi): vault masih ada? masih supergroup forum? user masih admin/owner? bisa tulis?
- Hasil disimpan di Room + tampil di Pengaturan: "Sehat • dicek 12 menit lalu" atau "Bermasalah: <penyebab> • dicek ...".
- Auto-deteksi: saat dibuka, pindai supergroup milik user; jika ada yang sehat dan belum ditandai → tawarkan "Pakai sebagai vault".

### A3. Alur "Ganti Vault" yang jujur (ganti tombol "Perbaiki")
- Jika vault aktif = channel biasa / grup non-forum yang TIDAK bisa diubah jadi forum: JANGAN tawarkan "perbaiki".
- Tawarkan: "Ganti Vault" (jalankan wizard A1) atau "Putuskan vault ini". Jelaskan kenapa tidak bisa diperbaiki, dengan bahasa awam.

### A4. Pemilih grup lama yang benar
- Hanya tampilkan grup/supergroup di mana user = owner atau admin. Tampilkan badge peran (Pemilik/Admin).
- Grup milik orang lain: sembunyikan total (jangan cuma di-disable).

## 3. Paket B — Mesin Sinkronisasi Tahan Banting (offline-first)

### B1. Operation log (Room)
- Setiap aksi mutasi (rename file, pindah koleksi, hapus file, buat/hapus/rename koleksi, kunci/buka koleksi) dicatat sebagai operasi: `id, jenis, payload, status=pending, dibuatPada`.
- Worker sinkronisasi: kirim operasi pending saat online → tandai `confirmed`. UI beri indikator "menunggu sinkron" pada item yang operasinya pending.
- Operasi idempoten: aman diulang (pakai kunci idempoten per operasi).

### B2. Sinkronisasi dua arah + resolusi konflik
- Manfaatkan update TDLib yang sudah ada (basis realtime): perubahan dari perangkat lain (file baru di vault, rename di Telegram) diterapkan ke Room otomatis.
- Konflik (operasi lokal pending vs perubahan remote pada item sama): strategi **last-write-wins** berdasar timestamp + catat ke "Log Konflik" yang bisa dilihat user di Pengaturan (apa yang menang, kapan).

### B3. Antrean unggah cerdas
- Prioritas: user bisa susun ulang urutan antrean (naik/turun).
- Sadar kondisi: jeda otomatis saat baterai < 15% atau di data seluler (keduanya bisa dimatikan di Pengaturan); lanjut otomatis saat WiFi tersambung / baterai cukup.
- Notifikasi progres tetap jalan (foreground service seperti sekarang).

## 4. Paket C — Kurator Cerdas (STRETCH, kerjakan hanya jika A+B selesai baik)

- Pencarian lanjutan: chip filter tipe file, rentang ukuran, rentang tanggal, koleksi — kombinasikan dengan pencarian teks yang ada.
- Wawasan arsip: kartu statistik — pertumbuhan per bulan (grafik batang sederhana), tipe file dominan, 5 file terbesar.

## 5. Verifikasi (WAJIB, bukan klaim)

- Unit test JVM (JUnit4 + Truth, tanpa emulator) untuk SEMUA logika murni baru: evaluator kesehatan vault (A2), aturan filter grup milik sendiri (A4), state machine operation log + idempoten (B1), resolusi konflik last-write-wins (B2), aturan jeda/lanjut antrean (B3).
- `kotlinc`-level: semua file Kotlin harus lolos parse; tidak ada import/activity yang hilang.
- Workflow `.github/workflows/build-apk.yml` tetap valid (YAML parse).
- Uji regresi mental: alur lama (login, unggah, unduh, impor, duplikat, realtime) tidak berubah perilakunya — tulis di laporan apa yang dicek.
- Target: semua test baru lolos; tidak ada test lama yang gagal.

## 6. Laporan Akhir

Format `~/workspace/opencode/AGENTS.md` seksi 4: (1) file dibuat/diubah + 1 baris per file, (2) verifikasi: perintah + hasil, (3) cara menjalankan/menguji, (4) blokir/asumsi. Jujur: tulis yang belum terverifikasi sebagai belum terverifikasi.
