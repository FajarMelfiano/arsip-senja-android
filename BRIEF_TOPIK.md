# BRIEF: Koleksi berbasis Forum Topics Telegram

## Konteks
- Repo: `~/workspace/arsip-senja-android`, branch `main`.
- AWALI dengan `git pull origin main` (commit terbaru `8c819b6` = perbaikan scroll
  daftar koleksi — jangan timpa/hilangkan).
- Aplikasi: Arsip Senja, Android native Kotlin + Jetpack Compose + Material 3,
  komunikasi langsung ke Telegram via TDLib (tanpa backend).
- Buka dan pahami dulu file terkait sebelum coding:
  `data/local/*` (KoleksiEntity, DAO, migrasi),
  `data/telegram/TelegramClient.kt`, `data/ArsipRepository.kt` (atau sejenisnya),
  `ui/viewmodel/DasborViewModel.kt`, `ui/viewmodel/UnggahViewModel.kt`,
  `ui/screens/PengaturanScreen.kt`, `ui/screens/UnggahScreen.kt`,
  `ui/screens/DasborScreen.kt` (LembarKoleksi), `util/*` (caption marker).

## Masalah & tujuan
Saat ini tiap "koleksi" = satu channel privat Telegram. Pengguna meminta model
seperti grup "Telegram Drive" miliknya (lihat pola umum): SATU supergroup vault
berisi FORUM TOPICS yang berfungsi sebagai kategori/folder. Saat upload, user
pilih kategori → berkas otomatis masuk ke topik yang sesuai; topik yang belum
ada dibuat otomatis; semua bisa dikustom user (buat/ubah/hapus kategori,
pilih vault sendiri).

## Desain (ikuti; jangan diubah tanpa alasan kuat yang ditulis di laporan)
1. **Vault** = satu supergroup forum. Di Pengaturan tambah bagian "Grup vault":
   - Daftar kandidat: supergroup milik user yang forum-nya aktif. Cara deteksi:
     ambil daftar chat supergroup lalu coba `GetForumTopics` per chat — yang
     tidak error dianggap kandidat.
   - Tombol "Buat vault baru" → buat supergroup "Arsip Senja" dengan forum aktif.
   - Simpan chatId vault terpilih di DataStore/pengaturan. Default: belum ada
     (minta user pilih/buat saat pertama kali butuh).
2. **KoleksiEntity**: tambah kolom `topicId: Int?` (messageThreadId forum).
   - `topicId != null` → koleksi topik (`chatId` = id vault).
   - `topicId == null` → koleksi channel lama: TETAP didukung untuk tampil/baca/
     unduh; upload BARU selalu ke topik. Jangan hapus/migrasi paksa data lama user.
   - Buat migrasi Room manual (ALTER TABLE) dengan versi DB naik.
3. **TelegramClient** — tambah fungsi (nama ikuti gaya kode yang ada):
   - `pastikanVault(): Long`
   - `daftarTopik(chatId: Long): List<TopikInfo>`
   - `pastikanTopik(nama: String): TopikInfo` (cari di daftar → buat bila tak ada)
   - `ubahTopik(...)`, `hapusTopik(...)`
   - kirim berkas dengan `messageThreadId = topicId`
   - `riwayatTopik(chatId, topicId, ...)` untuk sinkronisasi
   - PENTING: periksa dulu kelas & fungsi TdApi yang TERSEDIA di binding TDLib
     proyek (AAR/dependensi) sebelum coding — JANGAN mengarang nama API.
     Pola yang umum ada: CreateForumTopic, GetForumTopics, EditForumTopic,
     DeleteForumTopic/CloseForumTopic, GetMessageThreadHistory, dan
     CreateNewSupergroupChat dengan flag forum. Jika satu API tak tersedia,
     catat di laporan dan pakai alternatif terdekat.
4. **Repository**: sinkronisasi berjalan per-topik; statistik & pencarian tetap
   bekerja lintas topik+channel lama.
5. **UnggahScreen**: pemilih kategori = daftar topik vault + opsi "Kategori baru"
   (isian nama bebas) → saat upload, `pastikanTopik` dulu lalu kirim ke topik itu.
6. **Kelola koleksi** (lembar di Dasbor): CRUD penuh terhadap topik
   (buat/ubah/hapus memanggil API forum). "Tarik dari Telegram" → daftar topik
   dari vault terpilih.
7. **Caption marker** (`::tdrive::v2::` atau yang dipakai sekarang): pertahankan
   format lama agar tetap terbaca; bila perlu tambah info topik TANPA merusak
   parser lama. Tulis/uji parser-nya.

## Batasan
- UI Bahasa Indonesia. 100% ikon ArsipIcons/Lucide — tanpa emoji di mana pun.
- Jangan ubah `applicationId`. Jangan hapus data koleksi lama user.
- Kotlin rapi: null-safety, error TDLib ditangani dengan pesan Indonesia yang jelas.
- Setelah selesai: `git add -A`, commit dengan pesan yang jelas, `git push origin main`.

## Verifikasi (WAJIB sebelum lapor selesai)
1. Matikan Plan Mode.
2. Baca ulang SEMUA file yang diubah/dibuat; pastikan: import lengkap, tidak ada
   referensi ke fungsi/kelas yang tidak ada, semua call site konsisten, tidak ada
   placeholder/TODO.
3. Tulis `QA_TOPIK.md` berisi daftar periksa: setiap poin fitur di atas + kasus
   tepi (vault belum dipilih, topik dihapus langsung dari Telegram, nama topik
   duplikat, offline, koleksi channel lama tetap tampil).
4. Perbaiki semua temuan hingga bersih. Ulangi loop periksa-perbaiki maksimal 3x.

## Format laporan (wajib)
Active Skills / Current Phase / Deep Work / Micro-Verification /
Next Phase Readiness, plus: daftar file diubah/dibuat, dan hal yang TIDAK bisa
diverifikasi tanpa device/API (jujur apa adanya).
