# BRIEF — Build #24: Bilah bawah rapi + satu tombol buat semuanya + kunci koleksi (PIN/sandi/pola/biometrik)

Baca dan kerjakan brief ini sampai tuntas. Ikuti semua aturan di brief dan di
`~/workspace/opencode/AGENTS.md` (skill-router, fase P0–P5, anti-slop, format
laporan). JANGAN commit atau push. Kerja di working tree saja.

## P0 — Diagnosis (sudah diverifikasi dari kode + screenshot user 14 Okt 2026 13:26 WIB)

### Masalah 1 — Label "Pengaturan" terpotong di bilah bawah
Bilah bawah kini 5 item (Dasbor, Unggah, Tautan, Pohon, Pengaturan). Label
"Pengaturan" terpotong menjadi dua baris ("Pengatura n") di HP user.
Sumber: `ui/nav/ArsipNavHost.kt` baris ~111–127, `NavigationBarItem` dengan
`label = { Text(item.label) }` tanpa batas baris.

### Masalah 2 — User bingung: apa bedanya vault dan topik?
Fakta dari kode yang menjelaskan kebingungan user:
- **Vault** = SATU grup Telegram tempat SEMUA koleksi disimpan.
  (`ArsipRepository.pastikanVault`, `TelegramClient.buatVault` membuat
  supergroup dengan `isForum = true`.)
- **Koleksi / topik** = SATU rak/topik forum DI DALAM vault itu.
  (`ArsipRepository.buatKoleksi` → `pastikanTopikDenganPerbaikanForum`.)
- "Vault aktif" di Pengaturan HANYA berarti "grup terpilih" (chatId tersimpan
  di `PengaturanStore`), BUKAN berarti mode forum sudah aktif. Itulah kenapa
  user melihat vault "sudah ada" di Pengaturan tapi pembuatan koleksi tetap
  gagal dengan "Vault masih belum menjadi forum setelah aktivasi otomatis."
- Celah perilaku sekarang: jalur `pesanForumMasihGagal` (aktivasi forum
  "berhasil" dipanggil tanpa throw, tapi percobaan kedua `pastikanTopik`
  tetap gagal bukan-forum) adalah JALAN BUNTU — user hanya disuruh pilih
  ulang vault manual. Padahal bila vault itu tidak dipakai koleksi mana pun,
  kebijakan yang sudah ada di `pulihkanVaultTakSah` (buang vault basi + buat
  vault baru + lanjutkan) seharusnya berlaku juga untuk jalur ini.
- Bukti dari screenshot terbaru: koleksi user = 0 ("Koleksi di akunmu (0)"),
  jadi auto-ganti vault aman di kasus ini — tapi kode sekarang tidak
  melakukannya untuk jalur forum-masih-gagal.
- Cacat terkait yang WAJIB diperbaiki bersamaan: `tarikTopikVault` dan
  `tarikChannelLama` di `ArsipRepository.kt` membangun ulang `KoleksiEntity`
  dan hanya mempertahankan `deskripsi`. Metadata lokal (label, warna,
  tipeFile — dan kunci baru di bawah) akan HILANG setiap tarik daftar.
  Pertahankan SEMUA metadata lokal saat rebuild.

### Masalah 3 — Keinginan user (ini permintaan fitur, bukan sekadar bug)
1. "Satu tombol langsung buat semuanya": user tidak mau mengurus vault
   secara terpisah. Satu tombol "Buat koleksi" harus membereskan semuanya
   (vault disiapkan otomatis, topik dibuat), dengan kustomisasi penuh per
   koleksi (nama topik, label, warna, tipe, dan kunci).
2. "Vault dijadiin koleksi yang diproteksi password": tiap koleksi bisa
   dikunci. Jenis kunci yang diminta user: **PIN, kata sandi, pola, dan
   biometrik**. Catatan arsitektur: yang dikunci adalah KOLEKSI (per
   koleksi, karena tiap koleksi = satu topik dengan namanya sendiri).
   Vault sebagai grup penyimpanan tetap ada di belakang layar, tapi user
   tidak perlu mengaturnya manual lagi. Kunci biometrik level aplikasi
   (`MainActivity` + `PengaturanStore.biometrikAktif`) sudah ada dan TIDAK
   diubah — yang baru adalah kunci PER KOLEKSI.

## P1 — Desain UX (wajib diikuti)

- Bahasa Indonesia semua. Tanpa emoji sebagai ikon, 100% ikon Lucide.
  Palet `WarnaArsip`, judul Fraunces, isi JakartaSans (sama seperti layar lain).
- Penjelasan vault vs topik ditulis dengan kata sederhana, di DUA tempat:
  - Sheet Kelola koleksi, di bawah judul: "Koleksi = satu rak (topik) di
    dalam vault. Vault = grup Telegram tempat semua koleksi disimpan;
    aplikasi menyiapkannya otomatis, kamu tidak perlu mengaturnya."
  - Pengaturan > Vault Telegram: ganti tampilan "Grup -1004..." (ID mentah)
    menjadi: **nama grup** (ambil via `GetChat`, fallback ke ID bila gagal)
    + status kesehatan: "Forum siap" (hijau) atau "Bermasalah" (merah) +
    tombol "Perbaiki" satu ketuk bila bermasalah (memicu jalur perbaikan
    di bawah). Status dihitung dengan cara yang sama seperti
    `periksaVault` (GetChat + `adalahVaultSah` + coba `daftarTopik`).
- Form "Buat koleksi" TIDAK BOLEH ter-reset saat pembuatan gagal. Data
  (nama, deskripsi, label, warna, tipe, kunci) tetap terisi sehingga user
  bisa menekan "coba lagi" tanpa mengulang dari nol.
- Bila pembuatan koleksi gagal karena vault rusak DAN vault dipakai koleksi
  lain: banner galat menampilkan tombol aksi "Buat vault baru & coba lagi"
  (bukan sekadar teks). Menekan tombol itu: buat vault forum baru,
  jadikan aktif, lalu ULANGI pembuatan koleksi dengan data form yang sama.
  Vault lama + koleksinya TIDAK dihapus/diutak-atik (user bisa kembali ke
  vault lama lewat Pengaturan bila mau).

## P2 — Arsitektur

### P2a. Satu tombol buat semuanya (perbaikan alur vault)
- `buatKoleksi`: pastikan vault kerja otomatis sebelum buat topik:
  - Tanpa vault tersimpan → buat vault forum baru ("Arsip Senja"), lanjut.
  - Vault rusak (bukan supergroup ATAU forum-masih-gagal setelah aktivasi)
    DAN tidak ada koleksi terdaftar yang memakai vault itu → buang vault
    basi, buat vault baru, lanjutkan pembuatan topik. Ini memperluas
    kebijakan `pulihkanVaultTakSah` ke jalur forum-masih-gagal.
  - Vault rusak DAN ada koleksi terdaftar yang memakainya → JANGAN buang;
    lempar galat terstruktur yang membawa flag "tawarkan buat vault baru"
    agar UI bisa menampilkan tombol aksi (bukan sekadar pesan).
- `pastikanVault()` yang sekarang (buat otomatis bila null) dipertahankan
  dan dipakai sebagai dasar; logika baru di atas menutup dua celah sisanya.
- `pilihVault` di Pengaturan tetap seperti sekarang (validasi tipe +
  aktivasi forum + pesan jelas).

### P2b. Kunci koleksi (fitur baru)
- Room v3 → v4, migrasi `MIGRASI_3_4` (ALTER TABLE, JANGAN destructive,
  jangan ubah kolom lain). Kolom baru di tabel `koleksi`:
  - `kunciTipe` TEXT NOT NULL DEFAULT 'TIDAK_ADA'
    (nilai: `TIDAK_ADA` / `PIN` / `KATA_SANDI` / `POLA`).
  - `kunciGaram` TEXT (null bila tidak terkunci).
  - `kunciHash` TEXT (null bila tidak terkunci).
  - `kunciBiometrik` INTEGER NOT NULL DEFAULT 0 (1 = boleh buka biometrik).
  - Tambah `MIGRASI_3_4` ke `.addMigrations(...)`, naikkan `version = 4`.
- Helper murni + bisa diuji unit (file baru, mis.
  `data/repo/KunciKoleksi.kt` atau `util/KunciKoleksi.kt`):
  - `buatGaram()`: garam acak per koleksi (SecureRandom, Base64).
  - `hash(rahasia: String, garam: String)`: SHA-256 hex. JANGAN pakai
    hash tanpa garam. JANGAN PERNAH menyimpan rahasia plaintext.
  - `verifikasi(rahasia, garam, hash)`: bandingkan hash.
  - Validasi: PIN = 4–6 digit angka saja; KATA_SANDI = min 4 karakter;
    POLA = min 4 titik, direpresentasikan sebagai string urutan titik
    mis. `"0-1-4-7-8"` lalu di-hash seperti rahasia lain.
  - Komentar jujur di kode: kunci adalah gerbang privasi lokal, BUKAN
    enkripsi — perangkat di-root bisa membaca DB. Jangan klaim enkripsi.
- Repository (`ArsipRepository`):
  - `buatKoleksi(..., kunci: KunciBaru? = null)`: bila kunci diberikan,
    buat garam + hash, simpan di entity baru. `KunciBaru` = data class
    (tipe, rahasia, biometrik: Boolean).
  - `ubahKunci(kunciKoleksi, rahasiaLama: String?, kunciBaru: KunciBaru?)`:
    ganti/hapus kunci WAJIB verifikasi rahasia lama dulu bila koleksi
    sedang terkunci. `kunciBaru = null` berarti hapus kunci.
  - `bukaKunci(kunciKoleksi, rahasia: String): Boolean`: verifikasi hash;
    bila benar, tambahkan kunci ke set terbuka sesi.
  - `bukaKunciBiometrik(kunciKoleksi)`: tandai terbuka sesi (dipanggil
    SETELAH BiometricPrompt sukses di UI; repo tidak memanggil biometrik).
  - State terbuka sesi: `koleksiTerbuka: StateFlow<Set<KunciKoleksi>>`
    in-memory di repository (atau ViewModel bersama — pilih satu tempat,
    konsisten). Hilang saat proses mati. `perluBukaKunci(kunci): Boolean`
    = terkunci DAN belum dibuka sesi ini.
  - `tarikTopikVault` / `tarikChannelLama`: pertahankan SEMUA metadata
    lokal saat rebuild (label, warna, tipeFile, kunciTipe, kunciGaram,
    kunciHash, kunciBiometrik) dari `lama`.
- ViewModel: `DasborViewModel` (dan `PohonArsipViewModel` bila perlu)
  mengekspos `koleksiTerbuka` + fungsi `bukaKunci` + `perluBukaKunci`
  agar Katalog, Pohon, Kelola, dan Unggah memakai sumber yang sama.

## P3 — Implementasi UI

### P3a. Bilah bawah
- `ArsipNavHost.kt`: label `NavigationBarItem` satu baris, tidak wrap,
  di lebar 360dp: `Text(item.label, maxLines = 1, softWrap = false)` dengan
  ukuran label yang muat (mis. 11.sp — sesuaikan, jangan mengecilkan ikon).
  JANGAN ganti nama label "Pengaturan". JANGAN ubah ikon/rute/urutan.

### P3b. Form buat koleksi (sheet Kelola koleksi)
- Tambah bagian "Kunci koleksi" (default MATI). Bila hidup:
  - Pilih tipe: PIN / Kata sandi / Pola (chip pilihan seperti pilih tipe).
  - Input sesuai tipe + konfirmasi (ketik ulang / ulang pola).
    - PIN: keyboard angka, 4–6 digit, tampilkan sebagai titik-titik.
    - Kata sandi: teks + toggle tampilkan/sembunyikan, min 4 karakter.
    - Pola: grid 3x3, gambar jalur antar titik, min 4 titik, konfirmasi
      dengan mengulang pola yang sama.
  - Checkbox "Buka dengan biometrik": tampilkan HANYA bila
    `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)` == sukses.
  - Validasi sebelum kirim: tipe dipilih, rahasia valid, konfirmasi cocok.
    Pesan validasi Bahasa Indonesia, spesifik ("PIN harus 4–6 angka.",
    "Pola minimal 4 titik.", "Konfirmasi tidak cocok.").
- Tombol "Buat koleksi" memakai alur P2a. Saat gagal: form TIDAK reset;
  banner galat + (bila galat membawa flag aksi) tombol
  "Buat vault baru & coba lagi".

### P3c. Dialog ubah koleksi
- Tambah kelola kunci: atur kunci baru (bila belum terkunci), ubah kunci,
  hapus kunci. Ubah/hapus WAJIB memasukkan kunci lama dulu. Bila kunci
  lama salah: pesan "Kunci lama salah." tanpa petunjuk lain.

### P3d. Gating (penguncian berlaku di semua permukaan)
- Koleksi terkunci DAN belum dibuka sesi ini:
  - Katalog: berkas koleksi itu DISEMBUNYIKAN (tidak muncul di daftar,
    tidak muncul di hasil cari). Baris/filter koleksi menampilkan ikon
    gembok + "Terkunci" + tombol "Buka".
  - Pohon: simpul koleksi tampil nama + gembok + "Terkunci", TANPA anak
    sampai dibuka.
  - Ketuk berkas milik koleksi terkunci (dari mana pun, termasuk hasil
    cari): tampilkan sheet buka kunci dulu, baru buka detail bila benar.
  - Unggah ke koleksi terkunci: minta buka kunci dulu sebelum upload jalan.
- Sheet buka kunci: judul "Buka <nama koleksi>", input sesuai tipe kunci,
  tombol "Buka", tombol biometrik (ikon sidik jari) bila
  `kunciBiometrik == 1` — panggil BiometricPrompt mengikuti pola yang
  sudah ada di `MainActivity` (tanpa CryptoObject, cukup autentikasi;
  gagal permanen → fallback ke input rahasia, jangan kunci user keluar).
  Salah 5x berturut-turut → cooldown 30 detik (in-memory), pesan
  "Terlalu banyak percobaan. Coba lagi dalam 30 detik."
- Setelah dibuka, koleksi tetap terbuka selama aplikasi berjalan
  (sampai proses mati). Tidak ada "kunci lagi" manual — sederhana dan
  sesuai ekspektasi.

### P3e. Pengaturan > Vault Telegram
- Tampilkan nama grup + status kesehatan (Forum siap / Bermasalah) +
  tombol "Perbaiki" bila bermasalah (satu ketuk → jalur perbaikan P2a,
  lalu refresh status). Pertahankan tombol "Pilih grup lama" dan
  "Buat vault baru" yang sudah ada. Tambah 1 kalimat penjelasan:
  "Vault = grup penyimpanan; koleksi = topik di dalamnya. Aplikasi
  menyiapkan vault otomatis saat kamu membuat koleksi."

## P4 — Verifikasi (wajib jujur)
- Jalankan `./gradlew testDebugUnitTest` bila memungkinkan. Bila Gradle
  tidak jalan di sandbox (sudah terjadi di run sebelumnya: tidak ada JDK /
  daemon bermasalah), katakan sejujurnya — JANGAN klaim test lolos.
- Unit test baru (tambahkan, jangan hapus test lama):
  - Hash/verifikasi kunci: rahasia benar → lolos; salah → gagal;
    garam berbeda → hash berbeda; hash tidak sama dengan plaintext.
  - Validasi tiap tipe kunci (PIN pendek/bukan angka ditolak, sandi
    < 4 ditolak, pola < 4 titik ditolak).
  - `perluBukaKunci`: tanpa kunci → false; terkunci belum dibuka → true;
    setelah `bukaKunci` benar → false.
  - Migrasi 3→4 mempertahankan metadata lokal (label/warna/tipeFile/kunci)
    — pakai MigrationTestHelper bila tersedia; bila tidak, tulis test
    SQL migrasi manual minimal dan catat batasannya.
  - `tarikTopikVault` mempertahankan kunci lokal saat rebuild (bila mudah
    dengan fake DAO; bila tidak, trace manual + catat di laporan).
  - `terjemahkanGalatForum` lama tetap lolos (regresi Build #23).
- Cek seimbangnya kurung semua file baru/diubah.
- JANGAN ubah perilaku benar lainnya: rute upload/label/warna/tipe,
  pesan sukses, kunci biometrik level aplikasi, skema selain kolom kunci.

## P5 — Laporan akhir (format wajib)
Active Skills / Current Phase / Deep Work / Micro-Verification /
Next Phase Readiness. Sebutkan: file diubah/dibuat, hasil test + bukti
perintah (atau pengakuan jujur bila tidak jalan), apa yang belum
terverifikasi, dan cara uji di perangkat (buat koleksi terkunci PIN →
tutup sheet → buka lagi → minta kunci; buat koleksi saat vault rusak;
cek label Pengaturan satu baris di HP sempit).
