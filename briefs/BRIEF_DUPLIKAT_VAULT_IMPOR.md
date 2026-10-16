# BRIEF: Duplikat otomatis + vault lama tak terdeteksi + Impor selalu sinkron

Repo: `~/workspace/arsip-senja-android` (aplikasi Android Kotlin, Room, TDLib).
Bahasa kode & komentar: Bahasa Indonesia. Tanpa emoji di UI (ikon = Lucide/ArsipIcons).

## Aturan kerja (non-negotiable)

1. Matikan Plan Mode. Kamu builder: implementasi langsung.
2. WAJIB pakai skill `brainstorming` (`~/workspace/skills/brainstorming/SKILL.md`)
   di fase perancangan — divergent, SCAMPER, clustering, convergent — SEBELUM
   menulis kode. Orkestrator sudah brainstorm di pihaknya; hasil ringkasnya ada
   di bawah sebagai "Keputusan desain". Validasi & tajamkan, jangan dibuang.
3. Muat 3–10+ skill relevan per fase dari `~/workspace/skills/` (baca INDEX.md
   dulu, lalu baca SKILL.md tiap skill yang dipakai): arsitektur Android/Kotlin,
   Room/database, test engineering, edge cases. Kebutuhan baru di tengah jalan →
   muat skill-nya DULU, baru lanjut.
4. Micro-verification gate tiap paket: unit test + kompilasi lolos sebelum lanjut
   ke paket berikutnya. Maks 3x loop perbaikan per paket.
5. QA penuh di akhir: `./gradlew :app:testDebugUnitTest` (atau varian yang benar
   untuk proyek ini) SEMUA lolos termasuk test baru; `./gradlew :app:assembleDebug`
   sukses. Perbaiki semua kegagalan. Jangan klaim selesai tanpa bukti perintah.
6. Jangan push/merge/deploy. Hanya ubah kode lokal + test.
7. Format laporan akhir WAJIB: (1) file dibuat/diubah + 1 baris per file,
   (2) perintah verifikasi + hasil, (3) cara menjalankan/build, (4) blokir/asumsi.

## Konteks & temuan diagnosis (sudah diverifikasi orkestrator di kode)

- Dedupe pesan sudah benar: PK `berkas_arsip.id = "chatId:messageId"` + `@Upsert`
  → pesan Telegram yang SAMA tak mungkin terindeks dua kali (jalur realtime
  `prosesBatch`, `UploadWorker.simpanHasilUnggah`, `tarikRiwayatBaru` semua
  memakai kunci yang sama).
- Duplikat yang dilaporkan user = SATU FILE terkirim sebagai DUA PESAN Telegram
  berbeda (muncul sendiri, semua file kena). Artinya ada jalur yang MENGIRIM
  ULANG. Lubang yang ditemukan di guard idempoten `UploadWorker` →
  `ArsipRepository.sudahTerindeks(nama, ukuran, chatId, jendelaMs = 90 menit)` →
  `BerkasArsipDao.hitungMirip` (nama+ukuran+chatId persis):
  - (a) Baris terindeks dengan ukuran 0 (terbukti ada di produksi — kartu
    menampilkan "0 B") karena TDLib belum tahu ukuran saat indeks dari riwayat;
    guard membandingkan `potonganFile.length()` (ukuran asli) vs 0 → TAK COCOK
    → kirim ulang.
  - (b) Jendela 90 menit: retry/lanjut yang terjadi >90 menit setelah kirim
    pertama → guard buta → kirim ulang.
  - (c) Kirim sukses tetapi proses mati sebelum `simpanHasilUnggah` → retry
    tak menemukan jejak → kirim ulang.
- Vault lama user tidak terdeteksi: `daftarKandidatVault()` hanya memakai
  GetChats 100 teratas + SearchChats nama vault bawaan; grup lama yang sepi
  tak masuk 100 teratas dan namanya beda → tak muncul. `tarikDaftarKoleksi()`
  hanya iterasi vault aktif + hasil deteksi itu.
- Tab Impor saat ini manual (tombol "Tarik"); user minta SELALU sinkron seperti
  Telegram asli.

## Keputusan desain (hasil brainstorming orkestrator — tajamkan, jangan dibuang)

### Paket 1 — Hentikan sumber duplikat: guard idempoten diperkuat
1. Perpanjang jendela default `sudahTerindeks` dari 90 menit → 7 hari.
   (Alasan: guard ini HANYA dipakai untuk retry/lanjutan — kiriman baru dari
   user selalu dikirim. Jendela 7 hari aman dari false-skip untuk kasus nyata.)
2. Fallback ukuran tak diketahui: bila ukuran baris terindeks = 0 ATAU ukuran
   lokal = 0, cocokkan HANYA nama+chatId (dalam jendela) — jangan buta kirim ulang.
3. Pastikan indeks tak hilang: bungkus alur kirim agar `simpanHasilUnggah`
   tetap dipanggil setelah server konfirmasi; bila `pantauProgresUnggah` /
   `tungguTerkirim` melempar SETELAH pesan dipastikan terkirim, catat minimal
   jejak idempoten (nama+ukuran+chatId+waktu) agar retry mengenalinya.
   JANGAN mengindeks pesan yang belum dikonfirmasi server.
4. Perbaiki akar 0 B: sebelum `keEntity` menyimpan baris, bila
   `ukuranTotal() == 0`, coba refresh metadata file via TDLib (minta ulang info
   file / pastikan unduhan metadata) maksimal 1x percobaan ringan; bila tetap 0,
   tetap simpan tetapi JANGAN biarkan guard buta (ditangani poin 2).
5. Unit test: guard cocok/tidak untuk tiap kasus — ukuran 0 vs asli, beda nama,
   beda chat, di dalam/di luar jendela 7 hari, retry setelah jeda.

### Paket 2 — Bersihkan yang telanjur ganda: Pindai Duplikat
1. Fitur "Pindai duplikat" (tempatkan di Pengaturan, dekat info penyimpanan):
   kelompokkan baris per (koleksiId, topicId, nama, ukuran) dengan jumlah > 1;
   tampilkan daftar grup (nama file, ukuran, jumlah rangkap, tanggal).
2. Aksi "Rapikan": untuk tiap grup, simpan SATU baris (nomorKatalog terkecil =
   yang tertua), hapus baris lain DARI INDEKS LOKAL SAJA. Pesan di Telegram
   TIDAK DIHAPUS (aman — ini default).
3. Opsi lanjutan eksplisit dengan konfirmasi ganda: "Hapus juga pesannya di
   Telegram" (hapus via TDLib, satu per satu, laporkan yang gagal).
4. Unit test: pengelompokan benar, rapikan menyisakan tepat 1 per grup,
   tak menyentuh grup berisi 1 baris.

### Paket 3 — Vault lama terdeteksi + Impor selalu sinkron
1. Perbaiki deteksi vault: `daftarKandidatVault()` / `deteksiVaultMilikSendiri()`
   harus mencakup SEMUA supergroup milik user (owner/admin), bukan cuma 100 chat
   teratas + search nama bawaan. Ambil daftar chat lebih lengkap (paginasi
   GetChats sampai habis ATAU pakai semua hasil yang tersedia), saring hanya
   supergroup di mana user punya hak kelola. Tetap fail-closed: grup orang lain
   jangan pernah muncul.
2. `tarikDaftarKoleksi()`: iterasi SEMUA vault terdeteksi (poin 1), bukan cuma
   vault aktif.
3. Tab Impor auto-sync "kayak Telegram beneran":
   - Setiap kali tab Impor dibuka: jalankan `tarikDaftarKoleksi()` versi delta
     (ringan) + `sinkronKoleksi` tiap koleksi impor otomatis, tanpa tombol manual.
     Tombol "Tarik" manual boleh tetap ada sebagai pemicu paksa.
   - Daftarkan koleksi impor yang pernah dibuka/disinkron user sebagai "dikenal"
     untuk batch realtime (`mulaiRealtime`) agar pesan baru di chat impor langsung
     masuk indeks — lazy subscribe (hemat baterai/data), bukan semua chat.
   - Tampilkan "Terakhir disinkron: <waktu>" di tab Impor.
4. Unit test: deteksi mencakup supergroup milik sendiri di luar 100 teratas;
   koleksi impor ikut realtime setelah dibuka.

## Batasan

- Jangan ubah skema Room kecuali perlu; bila perlu, tulis migrasi (proyek ini
  sudah di v8 — ikuti pola migrasi yang ada).
- Jangan hapus data Telegram user tanpa konfirmasi eksplisit berlapis (paket 2
  poin 3).
- Jangan tampilkan/tebak isi secret. Jangan commit/push.
