# BRIEF LANJUTAN: Duplikat + Vault + Impor — FASE IMPLEMENTASI

Repo: `~/workspace/arsip-senja-android`. Bahasa: Indonesia. Tanpa emoji di UI.

## Situasi

Run sebelumnya (`duplikat-vault-impor3`) timeout 25 menit. Hasilnya: eksplorasi
SELESAI, implementasi BELUM DIMULAI (nol file Kotlin berubah — sudah diverifikasi
via git). Tugasmu: LANGSUNG IMPLEMENTASI. Jangan mengulang eksplorasi.

## Aturan kerja (non-negotiable)

1. Matikan Plan Mode.
2. WAJIB pakai skill `brainstorming` (`~/workspace/skills/brainstorming/SKILL.md`)
   — tapi SINGKAT: baca `BRAINSTORM_2026-10-16.md` di root repo (hasil sesi
   brainstorming run sebelumnya), validasi keputusan desain di bawah, lalu
   langsung ke implementasi. Jangan tulis dokumen brainstorming baru.
3. Muat skill relevan per fase dari `~/workspace/skills/` (baca INDEX.md dulu):
   kotlin/android, Room/database, test engineering, edge cases.
4. DILARANG membuang waktu untuk: troubleshooting jaringan sandbox, mencari
   Android SDK (TIDAK ADA di sandbox ini — sudah dipastikan), menjalankan
   Gradle build lokal berulang-ulang. Satu percobaan `./gradlew` compile bila
   cepat; bila gagal karena lingkungan (bukan karena kodemu), CATAT sebagai
   blokir dan lanjut — verifikasi penuh dilakukan CI setelah push oleh orkestrator.
5. JANGAN ubah `gradle.properties` atau file build/environment yang tidak
   berkaitan dengan fitur. Hanya sentuh kode fitur + test.
6. Micro-verification per paket. Maks 3x loop perbaikan per paket.
7. Jangan push/merge/deploy. Hanya kode lokal + test.
8. Format laporan akhir WAJIB: (1) file dibuat/diubah + 1 baris per file,
   (2) perintah verifikasi + hasil (OK/gagal + ringkasan JUJUR termasuk yang
   tidak bisa dijalankan di sandbox), (3) cara build/test, (4) blokir/asumsi.
   Jangan klaim "selesai & teruji" bila verifikasi belum dijalankan.

## Konteks arsitektur (dari run sebelumnya — jangan baca ulang dari nol)

- `data/local`: Room (BerkasArsipDao, KoleksiDao, KueriTampilan), DataStore.
- `data/telegram`: TelegramClient (TDLib, 1000+ baris — hati-hati, god object).
- `data/repo`: ArsipRepository (pintu tunggal), AturanRealtime (logika murni).
- `data/work`: UploadWorker (WorkManager).
- Logika murni dipisah dari Android → bisa unit test JVM.
- Dedupe pesan: PK `berkas_arsip.id = "chatId:messageId"` + `@Upsert` (sudah benar).

## Temuan diagnosis (final, jangan diperdebatkan lagi)

Duplikat = SATU FILE terkirim sebagai DUA PESAN Telegram berbeda. Lubang di
guard idempoten `UploadWorker` → `ArsipRepository.sudahTerindeks(nama, ukuran,
chatId, jendelaMs=90mnt)` → `BerkasArsipDao.hitungMirip`:
(a) baris 0 B (TDLib belum tahu ukuran saat indeks dari riwayat) → ukuran tak
cocok → kirim ulang; (b) retry >90 menit setelah kirim pertama → guard buta;
(c) kirim sukses tapi proses mati sebelum `simpanHasilUnggah` → retry buta.
Vault lama tak terdeteksi: `daftarKandidatVault()` cuma GetChats 100 teratas +
SearchChats nama bawaan. Tab Impor masih manual.

## Yang diimplementasikan (3 paket, berurutan)

### Paket 1 — Guard idempoten diperkuat (prioritas tertinggi)
1. Jendela default `sudahTerindeks`: 90 menit → 7 hari.
2. Fallback ukuran tak diketahui: bila ukuran baris terindeks = 0 ATAU ukuran
   lokal = 0, cocokkan HANYA nama+chatId dalam jendela.
3. Pastikan `simpanHasilUnggah` tetap dipanggil setelah server konfirmasi; bila
   `pantauProgresUnggah`/`tungguTerkirim` melempar SETELAH pesan dipastikan
   terkirim, catat jejak idempoten (nama+ukuran+chatId+waktu). JANGAN indeks
   pesan yang belum dikonfirmasi server.
4. Sebelum `keEntity` menyimpan baris: bila `ukuranTotal()==0`, coba refresh
   metadata file via TDLib 1x percobaan ringan; bila tetap 0, tetap simpan.
5. Unit test JVM: cocok/tidak untuk — ukuran 0 vs asli, beda nama, beda chat,
   di dalam/di luar jendela 7 hari.

### Paket 2 — Pindai Duplikat (Pengaturan)
1. Kelompokkan baris per (koleksiId, topicId, nama, ukuran) dengan jumlah > 1;
   tampilkan daftar grup (nama, ukuran, jumlah rangkap, tanggal).
2. Aksi "Rapikan": simpan SATU baris per grup (nomorKatalog terkecil), hapus
   baris lain DARI INDEKS LOKAL SAJA. Pesan Telegram TIDAK DIHAPUS.
3. Opsi lanjutan eksplisit + konfirmasi ganda: "Hapus juga pesannya di Telegram".
4. Unit test JVM: pengelompokan benar, rapikan sisakan tepat 1 per grup.

### Paket 3 — Vault lama + Impor selalu sinkron
1. `daftarKandidatVault()`/`deteksiVaultMilikSendiri()`: mencakup SEMUA
   supergroup milik user (owner/admin) — paginasi GetChats sampai habis bila
   API mendukung, atau semua hasil yang tersedia. Fail-closed: grup orang lain
   jangan pernah muncul.
2. `tarikDaftarKoleksi()`: iterasi SEMUA vault terdeteksi.
3. Tab Impor auto-sync: tiap dibuka → `tarikDaftarKoleksi()` delta +
   `sinkronKoleksi` tiap koleksi impor otomatis (tombol "Tarik" tetap ada);
   daftarkan koleksi impor yang pernah dibuka user sebagai "dikenal" untuk
   batch realtime `mulaiRealtime` (lazy subscribe); tampilkan
   "Terakhir disinkron: <waktu>".
4. Unit test JVM: deteksi mencakup supergroup milik sendiri di luar 100 teratas.

## Batasan skema & keamanan

- Butuh kolom/tabel baru → tulis migrasi Room mengikuti pola yang ada
  (proyek di v8).
- Jangan hapus pesan Telegram tanpa konfirmasi eksplisit berlapis.
- Jangan tampilkan/tebak secret. Jangan commit/push.
