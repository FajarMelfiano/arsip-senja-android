# QA — Fitur Topik Telegram, Thumbnail & Select-Sync

Tanggal: 2026-10-14. Build target: CI `build-apk.yml` → rolling release `debug-terbaru`.

## 1. Topik / Vault Telegram

- [ ] Pengaturan → "Pilih grup lama": daftar supergroup muncul, lencana Forum/Belum mode forum benar.
- [ ] Pilih grup forum lama → vault aktif berubah, pesan konfirmasi muncul.
- [ ] Pilih grup non-forum → petunjuk "aktifkan Topik dulu" muncul, tidak crash.
- [ ] "Buat vault baru" → grup forum baru terbuat di Telegram dan jadi vault aktif.
- [ ] Dasbor → Kelola koleksi → buat "Dokumen": topik "Dokumen" terbuat otomatis di grup vault.
- [ ] Unggah berkas ke koleksi "Dokumen": pesan masuk ke topik yang benar (cek di Telegram).
- [ ] Ubah nama koleksi → nama topik di Telegram ikut berubah.
- [ ] Hapus koleksi → topik di Telegram ikut terhapus (beserta pesannya).
- [ ] Channel lama (dibuat sebelum fitur topik) tetap tampil dan bisa disinkron.
- [ ] Sinkron per topik hanya menarik riwayat topik itu, bukan seluruh grup.

## 2. Thumbnail media

- [ ] Kartu kisi menampilkan thumbnail untuk: foto, video (dengan lencana putar), audio ber-cover, dokumen ber-thumbnail.
- [ ] Berkas tanpa thumbnail tetap menampilkan ikon tipe (fallback).
- [ ] Thumbnail gagal diunduh → kartu tetap tampil, tidak crash, tidak ada spinner macet.
- [ ] Buka ulang aplikasi → thumbnail langsung dari cache lokal (tidak unduh ulang).

## 3. Select-before-sync

- [ ] Tombol sinkron (kepala dasbor & empty state) membuka dialog "Pilih koleksi".
- [ ] Semua koleksi tercentang default; "Pilih semua" mencentang/melepas semua.
- [ ] Tombol "Sinkron (n)" nonaktif bila tidak ada yang dipilih.
- [ ] Konfirmasi → dialog tertutup, sinkron berjalan berurutan dengan progres "Menyinkron <nama> (i/total)…".
- [ ] Batal (tutup dialog) → tidak ada sinkron yang jalan.
- [ ] 0 koleksi → tombol sinkron membuka dialog kosong dengan pesan yang jelas.

## 4. Regresi umum

- [ ] Login → dasbor terbuka tanpa force-close.
- [ ] Daftar "Koleksi di akunmu" bisa di-scroll sampai bawah (93+ item).
- [ ] Filter koleksi di dasbor: pilih satu koleksi → daftar berkas tersaring ke koleksi itu saja.
- [ ] Channel lama + topik vault tampil bersama tanpa duplikat.
- [ ] Migrasi DB v1→v2: data pengguna lama (build 13) tidak hilang setelah update.

## Catatan implementasi (untuk pengembang)

- Koleksi diidentifikasi komposit `(chatId, topicId)`; channel lama memakai `topicId = 0`.
- Upload ke topik memakai `TdApi.SendMessage.topicId = MessageTopicForum(topicId)`.
- Thumbnail: `tdThumbFileId` dari `document/video/animation.thumbnail`,
  `audio.albumCoverThumbnail`, atau `photo.sizes` terkecil; cache di `filesDir/thumbs`.
- Dialog sinkron memanggil `DasborViewModel.sinkronTerpilih(List<KunciKoleksi>)`.
