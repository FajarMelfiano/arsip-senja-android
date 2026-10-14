# BRIEF — Perbaiki Error Kelola Koleksi + Kategori Kustom, Rute Otomatis, Label

Repo: `~/workspace/arsip-senja-android`, branch `main`. Stack: Kotlin, Jetpack
Compose, Material 3, TDLib, Room, WorkManager, DataStore. Bahasa UI: Indonesia.
Anti-AI-slop: tanpa emoji sebagai ikon (pakai ArsipIcons/Lucide), tanpa
template generik.

Konteks: commit `2f3fac0` (HEAD) berisi fitur topik Telegram + thumbnail +
select-sebelum-sinkron. Build CI hijau. Pengguna melaporkan: **muncul error
saat membuat koleksi di lembar "Kelola koleksi"**.

## BAGIAN A — Perbaiki error (wajib, akar masalah sudah diketahui)

`ArsipRepository.pilihVault(chatId)` menyimpan chatId apa adanya TANPA
memastikan grup sudah mode forum. Akibatnya `buatKoleksi` → `pastikanTopik` →
`GetForumTopics`/`CreateForumTopic` melempar error TDLib ("chat is not a
forum") pada grup lama yang bukan forum. Itu error yang dilihat pengguna.

Perbaikan:
1. Tambah di `TelegramClient`: `suspend fun aktifkanForum(chatId: Long)` yang
   mengirim `TdApi.ToggleForumTopics().apply { this.chatId = chatId;
   isEnabled = true }`.
2. Ubah `ArsipRepository.pilihVault(chatId)`: sebelum `setVaultChatId`, coba
   `telegram.daftarTopik(chatId)`; bila melempar (bukan forum), panggil
   `telegram.aktifkanForum(chatId)` lalu simpan. Bila aktivasi gagal,
   lempar error yang jelas ("Grup ini tidak bisa dijadikan vault: ...").
3. `PengaturanViewModel.pilihVault`: pesan sukses harus menyebut bila mode
   forum baru saja diaktifkan ("Mode forum diaktifkan di grup ini.").
4. Daftar kandidat vault (`daftarKandidatVault`) sudah menandai `forumSiap`;
   pastikan UI Pengaturan menampilkan badge untuk grup yang belum forum
   ("akan diaktifkan otomatis saat dipilih").

## BAGIAN B — Kategori kustom + rute otomatis + label

Konsep: "koleksi" SAAT INI = satu topik/channel. Perluas menjadi kategori
yang bisa dibuat sendiri pengguna, masing-masing memetakan tipe berkas
(Gambar/Video/Audio/Dokumen/Arsip/Lainnya — lihat enum `TipeFile`) dan
memiliki label.

1. **Skema (Room v2 → v3, tulis MIGRASI_2_3):**
   - `koleksi` tambah kolom: `label TEXT` (nullable, tag pendek mis. "PENTING"),
     `warna INTEGER NOT NULL DEFAULT 0` (ARGB untuk dot/chip),
     `tipeFile TEXT NOT NULL DEFAULT ''` (CSV nama enum `TipeFile`;
     kosong = menangani SEMUA tipe, menjaga kompatibilitas data lama).
   - `KoleksiEntity`: field baru + helper `val tipeDitangani: Set<TipeFile>`
     (parse CSV; kosong → `TipeFile.entries.toSet()`).
2. **CRUD di LembarKoleksi (`DasborScreen.kt`, sheet "Kelola koleksi"):**
   - Form "Buat koleksi": tambah input `Label (opsional)`, pemilih warna
     (deret swatch preset, mis. 8 warna), dan multi-select chip tipe berkas
     (default: semua terpilih = "semua tipe").
   - Teks penjelas sheet sesuaikan: "Setiap koleksi dibuat sebagai topik di
     vault Telegram — topik dibuat otomatis saat koleksi dibuat."
   - Dialog edit (`diedit`) juga bisa mengubah label/warna/tipe.
   - `BarisKoleksi`: tampilkan chip label (bila ada), dot warna, dan ringkasan
     tipe ("Gambar, Video" atau "Semua tipe").
   - `DasborViewModel.buatKoleksi` / `ubahKoleksi`: teruskan parameter baru
     ke `ArsipRepository`.
3. **Rute otomatis saat upload:**
   - `ArsipRepository.unggahOtomatis(uris: List<Uri>)`: untuk tiap URI,
     deteksi `TipeFile` (pakai `TipeFile.dariNama`/`dariMime` yang sudah ada),
     cari koleksi di vault aktif yang `tipeDitangani`-nya memuat tipe itu
     (bila bentrok pilih yang paling spesifik = set terkecil; bila tak ada,
     pakai koleksi "semua tipe" pertama, lalu koleksi pertama),
     panggil `pastikanTopikKoleksi` (topik dibuat otomatis bila belum ada),
     lalu enqueue `UploadWorker` seperti `mulaiUnggah`.
   - `UnggahScreen`: tambah toggle "Rute otomatis (ikut kategori)" — bila
     aktif, tombol unggah memanggil `unggahOtomatis`, bukan koleksi manual.
4. **Aksi "Buatkan topik yang hilang":** tombol di LembarKoleksi yang
   menjalankan `pastikanTopikKoleski` untuk semua koleksi (topik yang dihapus
   dari Telegram dibuat ulang otomatis), dengan pesan progres via
   `DasborViewModel.pesan`.

## Aturan implementasi

- JANGAN masuk Plan Mode. Kerjakan langsung sampai selesai.
- Perhatikan pelajaran bug kemarin: state composable dideklarasikan di scope
  yang benar (jangan di dalam lambda `Column` bila dipakai di luarnya).
- `warna`: simpan sebagai ARGB Int; sediakan 8 preset di UI.
- `label`: trim, maksimal 24 karakter.
- Jangan ubah perilaku channel lama (topicId = 0) dan sinkron yang sudah ada.
- Perbarui `QA_TOPIK.md` (atau buat `QA_KATEGORI.md`) dengan checklist:
  pilih vault grup non-forum → buat koleksi (harus sukses, forum aktif),
  buat kategori dengan label+warna+tipe, edit kategori, rute otomatis per
  tipe, topik terbuat otomatis, fallback "semua tipe".
- Pastikan tidak ada referensi basi (seperti kasus `koleksiId` kemarin):
  cari seluruh pemakaian `KoleksiEntity(` dan `buatKoleksi(` lalu sesuaikan.
- Commit di akhir dengan pesan Bahasa Indonesia yang jelas. JANGAN push —
  verifikasi dan push dilakukan terpisah.

Laporkan: file yang diubah, keputusan desain yang diambil, dan hasil
verifikasi mandiri (apa yang dicek + hasilnya).
