# QA Kategori, Label & Rute Otomatis

Build: kategori kustom (label, warna, tipe), auto-enable forum, rute otomatis, buatkan topik.

## Jalur uji perangkat (Android 8.0+, akun pemilik grup)

### A. Vault grup lama non-forum (error "the chat is not a forum")
- [ ] Pengaturan → vault: pilih grup LAMA yang belum mode forum → mode forum
      aktif otomatis, pesan "Mode forum diaktifkan di grup ini."
- [ ] Badge kandidat: "Forum akan diaktifkan" untuk grup non-forum.
- [ ] Dasbor → Kelola koleksi → buat koleksi → topik muncul di grup
      Telegram, TIDAK ada error "the chat is not a forum".
- [ ] Akun BUKAN pemilik grup memilih vault → pesan galat jelas
      (Telegram menolak; butuh hak pemilik).

### B. Kategori kustom
- [ ] Buat kategori: nama + label (maks 24) + warna + tipe (mis. hanya Gambar).
- [ ] Baris koleksi menampilkan chip label, dot warna, ringkasan tipe.
- [ ] Edit (ikon pensil): ubah label/warna/tipe → tersimpan setelah tutup dialog.
- [ ] Data lama (tanpa label/warna/tipe) tetap terbuka: migrasi v2→v3,
      tipe = "Semua tipe".

### C. Rute otomatis
- [ ] Buat 3 kategori: Foto (Gambar), Film (Video), Arsipku (semua tipe).
- [ ] Unggah: aktifkan "Rute otomatis (ikut kategori)", pilih 1 foto + 1 video
      + 1 PDF → foto ke Foto, video ke Film, PDF ke Arsipku.
- [ ] Kategori paling spesifik menang bila beberapa cocok.
- [ ] Mode manual tetap jalan (toggle mati → pilih koleksi manual).

### D. Buatkan topik yang hilang
- [ ] Hapus satu topik langsung dari aplikasi Telegram → Kelola koleksi →
      "Buatkan topik" → topik dibuat ulang, pesan jumlah yang diperbaiki.
- [ ] Bila tidak ada yang hilang → pesan "Semua topik kategori sudah ada".

### E. Regresi
- [ ] Sinkron, pencarian, unduh, pratinjau, tautan berbagi tetap normal.
- [ ] Tidak ada force-close saat buka Kelola koleksi / Unggah / Pengaturan.

## Bukti CI
- Unit test: KoleksiKategoriTest (parsing tipeDitangani, default label/warna).
- Workflow: .github/workflows/build-apk.yml (test + assembleDebug).
