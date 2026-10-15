# BRIEF — Tutup kebocoran error "The chat is not a forum" + Halaman Pohon Arsip

Ikuti semua aturan di AGENTS.md (skill-router, fase P0–P5, anti-slop, format laporan).
JANGAN commit atau push. Kerjakan sampai tuntas di working tree.

## Konteks (terverifikasi dari kode + screenshot pengguna 14 Okt 2026 13:01 WIB)

Pengguna mencoba membuat koleksi "Kenangan 2026" dan masih dapat banner merah:
`Gagal membuat koleksi: The chat is not a forum`.
Di sheet Kelola koleksi sudah ada 2 koleksi: `DONGKAK ft KERANGSLOT` dan `LOVABLE DELIGHT X`,
masing-masing "0 berkas · Semua tipe".

Build #22 (commit 9ac72dc) SUDAH menangani satu jalur vault-bukan-supergroup, TAPI
screenshot membuktikan pesan mentah Inggris dari TDLib masih bisa lolos ke UI.
Dua hipotesis, keduanya harus ditutup (jangan berasumsi salah satu saja):
(a) Perangkat pengguna mungkin masih di Build #19/#20 (tanpa self-healing di
`buatKoleksi`) — screenshot diambil ~9 menit setelah Build #22 rilis.
(b) Build #22 punya kebocoran nyata (dibuktikan dari kode yang dibaca langsung):

## P0 — Diagnosis kebocoran (WAJIB dibaca sebelum mengubah kode)

1. `DasborViewModel.buatKoleksi` memakai `"Gagal membuat koleksi: ${e.message}"`.
   `TelegramException` memang `Exception(pesan)`, jadi pesan mentah TDLib Inggris
   langsung tampil di UI. Ini garis pertahanan terakhir yang saat ini TIDAK ada.
2. `ArsipRepository.pastikanTopikDenganPerbaikanForum` — percobaan kedua
   `return telegram.pastikanTopik(vault, nama)` berada DI LUAR try-catch.
   Bila aktivasi forum "berhasil" tetapi Telegram masih menjawab bukan-forum
   (propagasi / forum belum efektif), galat mentah lolos tanpa terjemahan.
   Jalur yang sama di `pulihkanVaultTakSah` (BuangDanBuatBaru):
   `telegram.pastikanTopik(baru, nama)` juga di luar try-catch.
3. `telegram.aktifkanForum` memanggil `grupIdDariChat` yang melempar
   `IllegalArgumentException("Bukan chat supergroup: ...")` bila chatId tidak
   berbentuk supergroup. Itu BUKAN `TelegramException`, jadi TIDAK tertangkap
   oleh `catch (gagal: TelegramException)` di repository — lolos mentah.
4. Galat pertama yang bukan `bukanForum` dan bukan `hanyaSupergroup` langsung
   `throw e` mentah.
5. `TelegramClient.pastikanTopik` memanggil `daftarTopik` (GetForumTopics) dulu —
   di chat non-forum, GetForumTopics-lah yang melempar "The chat is not a forum".

## P3 — Perbaikan 1: tidak ada lagi pesan mentah "not a forum" di UI

Prinsip: SETIAP jalur yang bisa menghasilkan galat forum HARUS berakhir dengan
pesan Bahasa Indonesia yang jelas + langkah yang bisa dilakukan pengguna.
Jangan pernah menampilkan teks mentah TDLib Inggris untuk kasus ini.

1. `PenjagaVault` — tambah helper terpusat (pure, bisa diuji unit):
   - `pesanForumMasihGagal()`: kurang-lebih "Vault masih belum menjadi forum
     setelah aktivasi otomatis. Pastikan kamu pemilik grup, lalu pilih ulang
     vault di Pengaturan > Vault Telegram atau buat vault baru."
   - `terjemahkanGalatForum(pesan: String): String`: bila pesan mengandung
     "not a forum" / "CHAT_NOT_FORUM" / "forum" dalam konteks galat forum →
     kembalikan `pesanForumMasihGagal()` (atau pesan vault-bukan-supergroup
     bila pesan juga mengandung "supergroup"); bila pesan adalah
     "Bukan chat supergroup: ..." → kembalikan pesan vault-bukan-supergroup
     dengan label "tak diketahui" + saran pilih ulang / buat baru;
     bila bukan galat forum → kembalikan pesan apa adanya (jangan ubah
     pesan lain).
   - Jangan mengubah pesan-pesan Build #22 yang sudah jelas
     (`pesanVaultBukanSupergroup`, `pesanAktivasiGagal`) — pakai ulang.
2. `ArsipRepository.pastikanTopikDenganPerbaikanForum`:
   - Bungkus percobaan kedua `pastikanTopik` dalam try-catch: bila gagal lagi
     dengan galat forum → lempar `TelegramException(PenjagaVault.pesanForumMasihGagal())`,
     bukan galat mentah. Jangan retry lebih dari sekali (hindari loop).
   - Bungkus `telegram.pastikanTopik(baru, nama)` di `pulihkanVaultTakSah`
     dengan try-catch yang sama.
   - Tangkap `IllegalArgumentException` dari `aktifkanForum` (asal `grupIdDariChat`)
     dan terjemahkan menjadi galat vault-bukan-supergroup yang jelas
     (jangan lempar mentah). Perilaku lain tetap sama: supergroup belum forum →
     aktivasi + coba lagi sekali; bukan supergroup → jalur pulih Build #22
     (tanpa koleksi → ganti otomatis; dengan koleksi → galat jelas).
   - Pastikan `buatKoleksi` TIDAK menyimpan koleksi parsial bila pembuatan
     topik gagal (verifikasi: saat ini simpan hanya terjadi setelah topik
     sukses — pertahankan).
3. `DasborViewModel` (pertahanan terakhir di UI):
   - `buatKoleksi` dan `perbaikiTopikHilang`: tampilkan
     `PenjagaVault.terjemahkanGalatForum(e.message ?: ...)` untuk pesan galat,
     bukan `e.message` mentah. Terapkan juga ke `tarikDaftarKoleksi` bila
     menyentuh jalur topik (opsional, tapi jangan ubah pesan non-forum).
   - Jangan ubah pesan sukses / pesan non-galat.
4. Unit test baru (di `PenjagaVaultTest` atau file test baru yang setara):
   - `"The chat is not a forum"` → terjemahan Indonesia, TIDAK mengandung
     "The chat is not a forum".
   - `"Forums can be enabled in supergroups only"` → pesan vault-bukan-supergroup.
   - `"Bukan chat supergroup: -123"` → pesan vault-bukan-supergroup.
   - Pesan non-forum (mis. "Jaringan bermasalah") → lolos apa adanya.
   - `pesanForumMasihGagal()` menyebut pemilik grup + Pengaturan > Vault Telegram.

JANGAN ubah: perilaku benar lainnya (pemilih grup tetap supergroup saja,
`Buat vault baru` tetap supergroup forum, rute upload/label/warna/tipe tidak
disentuh). Jangan ubah skema Room / versi database.

## P3 — Perbaikan 2: Halaman Pohon Arsip (folder tree per kategori, menarik)

Tambahkan halaman baru "Pohon" untuk melihat struktur folder per kategori.

### Struktur data (turunan, tanpa skema baru)
- Berkas TIDAK punya subfolder di data model, jadi pohon diturunkan dari data
  yang ada: Vault (akar) → Koleksi (kategori) → Tipe berkas → Berkas.
- `BerkasArsipEntity`: `koleksiId` (nullable), `topicId`, `nama`, `ukuran`,
  `tanggal`, `tipe` (nama enum TipeFile), `id`.
- `KoleksiEntity`: `kunci` (chatId+topicId), `nama`, `label`, `warna`,
  `tipeDitangani`, `jumlahBerkas`.
- Berkas yang tidak punya koleksi yang cocok (koleksiId null / kunci tak
  ditemukan) → kelompok "Tanpa kategori" di bawah akar.
- Koleksi dengan 0 berkas → tampil dengan baris "Belum ada berkas".

### Tampilan
- File baru `PohonArsipScreen.kt` + `PohonArsipViewModel.kt` + builder murni
  `PohonArsip.kt` (tanpa Android/Compose, bisa diuji unit) + `PohonArsipTest.kt`.
- Rute baru `"pohon"`, item bilah bawah kelima "Pohon" (ikon Folder yang sudah
  ada di `ArsipIcons`; bila menambah ikon FolderTree baru, ikuti pola file
  `ArsipIcons.kt` yang sama — bila ragu, pakai Folder saja).
- `ArsipNavHost`: tambah composable pohon; ketuk berkas → navigasi ke
  `Rute.berkas(id)` sama seperti DasborScreen (`onBukaBerkas`).
- Tiap simpul menampilkan: ikon (Vault=Database/HardDrive, Koleksi=Folder +
  dot warna kategori, Tipe=ikon per tipe yang sudah ada: Image/Film/Music/
  FileText/Archive/File, Berkas=ikon per tipe), nama, chip label bila ada,
  jumlah berkas + total ukuran (format "3 berkas · 12,4 MB"; ukuran 0 → "0 B").
- Garis panduan indentasi (garis vertikal) + chevron Buka/Tutup per simpul.
- Interaksi: ketuk simpul Koleksi/Tipe → buka/tutup; tombol "Buka semua" /
  "Tutup semua"; kolom cari "Cari nama berkas..." — filter berdasarkan nama
  berkas, leluhur simpul yang cocok tetap terlihat; koleksi/tipe kosong
  karena filter disembunyikan.
- Header: judul "Pohon arsip", ringkasan akar (jumlah koleksi, jumlah berkas,
  total ukuran), kalimat penjelasan satu baris.
- Empty state global (belum ada koleksi sama sekali): teks jelas + tombol ke
  Dasbor / Kelola koleksi tidak wajib — cukup teks + saran buat koleksi.
- Gaya: palette `WarnaArsip` (Kanvas/Veil/Permukaan, Amber), tipografi
  Fraunces untuk judul + JakartaSans untuk isi, kartu Permukaan, tanpa emoji,
  hanya ikon Lucide via `ArsipIcons`. Tanpa placeholder generik.

### ViewModel + builder
- ViewModel mengombinasikan `repo.alirKoleksi()` +
  `repo.alirBerkas(KueriTampilan())` (tanpa filter = semua berkas, urut nama)
  menjadi `StateFlow` pohon; state buka/tutup disimpan di ViewModel
  (Map idSimpul → Boolean), bukan di builder.
- Builder murni: `bangunPohon(koleksi: List<KoleksiEntity>, berkas: List<BerkasArsipEntity>, kataCari: String): PohonArsip`
  dengan node: Akar / Koleksi / Tipe / Berkas, masing-masing membawa
  `jumlahBerkas`, `totalUkuran`, `anak`. Pengelompokan berkas ke koleksi
  memakai `(koleksiId, topicId)` yang cocok dengan `kunci` koleksi;
  pengelompokan ke tipe memakai `TipeFile` dari `berkas.tipe` (nilai invalid
  → LAINNYA). Urutan: koleksi sesuai urutan `alirKoleksi`, tipe sesuai urutan
  enum `TipeFile.entries`, berkas urut nama (case-insensitive).
- Test builder: pohon dari 2 koleksi + 3 berkas (termasuk 1 yatim) → struktur,
  hitungan, dan total ukuran benar; filter nama menyisakan hanya cabang yang
  cocok + leluhurnya; koleksi kosong tetap muncul dengan 0 berkas.

## P4 — Verifikasi
- Jalankan `./gradlew testDebugUnitTest` bila memungkinkan. Bila Gradle tidak
  jalan di sandbox (masalah lingkungan yang sudah dikenal: proxy/CA/daemon),
  katakan sejujurnya apa yang gagal dan jangan klaim test lolos.
- JANGAN commit atau push.
- Laporan akhir: file diubah/dibuat, hasil test + bukti perintah, apa yang
  belum terverifikasi, dan cara menguji di perangkat (buat koleksi di vault
  non-forum → pesan Indonesia; buka tab Pohon → expand/collapse, cari, ketuk
  berkas).
