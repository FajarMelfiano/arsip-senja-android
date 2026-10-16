# BRIEF LANJUTAN 2: Selesaikan Paket 3 + Perbaiki Temuan Review — FASE IMPLEMENTASI

Repo: `~/workspace/arsip-senja-android`. Bahasa: Indonesia. Tanpa emoji di UI.

## Situasi

Run sebelumnya (`duplikat-vault-impor4`) timeout 30 menit TETAPI menghasilkan
perubahan nyata (terverifikasi via `git diff`, BUKAN klaim): Paket 1 (guard
idempoten) dan Paket 2 (Pindai Duplikat) SELESAI diimplementasikan. Paket 3
(vault lama + Impor sinkron) BELUM DIKERJAKAN SAMA SEKALI — run mati saat
menulis UI Paket 2. Tugasmu: kerjakan HANYA yang belum + perbaiki temuan
review di bawah. JANGAN mengulang/menulis ulang Paket 1 & 2 yang sudah ada.

## Yang SUDAH SELESAI (jangan diulang, jangan ditulis ulang)

- `data/repo/AturanIdempoten.kt` (jendela 7 hari, fallback ukuran 0).
- `BerkasArsipDao.hitungMirip`: fallback nama+chat bila ukuran 0; jendela via
  `(dibuatPada > :sejak OR tanggal > :sejak)`.
- `ArsipRepository.sudahTerindeks` default 7 hari; `pastikanUkuran()` (refresh
  metadata TDLib 1x bila ukuran 0).
- `TelegramClient`: wrapper `ambilFile`, `ambilPesan`, `hapusPesan`.
- `UploadWorker`: konfirmasi server dulu (`tungguTerkirim` + fallback
  `ambilPesan`), catat jejak idempoten bila `keBerkasMentah()` null, refresh
  ukuran 1x. Jejak TIDAK ditulis sebelum konfirmasi server.
- `data/repo/AturanDuplikat.kt` + `pindaiDuplikat()` /
  `rapikanDuplikatLokal()` (indeks lokal saja) /
  `rapikanDuplikatDenganTelegram()` (opsi lanjutan).
- UI Pindai Duplikat di `PengaturanScreen.kt` + `PengaturanViewModel.kt`
  dengan KONFIRMASI GANDA untuk hapus Telegram (sudah benar, jangan diubah).
- Skema: hanya indeks baru di `8.json`, versi tetap 8, TANPA migrasi.
- Test: `AturanIdempotenTest.kt` (ditulis, BELUM dijalankan).

## Aturan kerja (non-negotiable)

1. Matikan Plan Mode.
2. WAJIB pakai skill `brainstorming` (`~/workspace/skills/brainstorming/SKILL.md`)
   — SINGKAT saja: validasi desain Paket 3 di bawah, langsung implementasi.
3. Muat skill relevan per fase dari `~/workspace/skills/` (baca INDEX.md dulu):
   kotlin/android, Room/database, test engineering, edge cases.
4. DILARANG: troubleshooting jaringan sandbox, mencari Android SDK (TIDAK ADA),
   Gradle build lokal berulang. Satu percobaan `./gradlew` compile bila cepat;
   bila gagal karena lingkungan (bukan kodemu), CATAT sebagai blokir dan lanjut
   — verifikasi penuh via CI oleh orkestrator.
5. JANGAN ubah `gradle.properties` atau file build/environment tak berkaitan.
   Hanya sentuh kode fitur + test.
6. Micro-verification per paket. Maks 3x loop perbaikan per paket.
7. Jangan push/merge/deploy. Hanya kode lokal + test.
8. Format laporan akhir WAJIB: (1) file dibuat/diubah + 1 baris per file,
   (2) perintah verifikasi + hasil (OK/gagal + ringkasan JUJUR termasuk yang
   tidak bisa dijalankan di sandbox), (3) cara build/test, (4) blokir/asumsi.
   Jangan klaim "selesai & teruji" bila verifikasi belum dijalankan.

## PAKET 3 — Vault lama + Impor selalu sinkron (BELUM DIKERJAKAN)

Konteks: `ArsipRepository.daftarKandidatVault()` (baris ~138) dan
`deteksiVaultMilikSendiri()` (baris ~271) saat ini hanya mencakup 100 chat
teratas + SearchChats nama bawaan. `tarikDaftarKoleksi()` menggabung vault
aktif + hasil deteksi + topik + channel lama lalu `distinctBy { it.kunci }`.
`ImporViewModel` menampilkan `repo.alirKoleksiImpor()` dan sinkron masih manual.

1. `daftarKandidatVault()` / `deteksiVaultMilikSendiri()`: mencakup SEMUA
   supergroup milik user (owner/admin) — paginasi GetChats sampai habis bila
   API mendukung, atau seluruh hasil yang tersedia. FAIL-CLOSED: grup milik
   orang lain JANGAN PERNAH muncul sebagai kandidat.
2. `tarikDaftarKoleksi()`: iterasi SEMUA vault terdeteksi (bukan hanya vault
   aktif).
3. Tab Impor auto-sync: setiap dibuka → `tarikDaftarKoleksi()` delta +
   `sinkronKoleksi` tiap koleksi impor otomatis (tombol "Tarik" manual tetap
   ada); daftarkan koleksi impor yang pernah dibuka user sebagai "dikenal"
   untuk batch realtime `mulaiRealtime` (lazy subscribe); tampilkan
   "Terakhir disinkron: <waktu>" di tab Impor.
4. Unit test JVM (logika murni, tanpa Android): deteksi mencakup supergroup
   milik sendiri di luar 100 teratas; grup orang lain tidak muncul
   (fail-closed).

## TEMUAN REVIEW INDEPENDEN (wajib diperbaiki di run ini)

1. FALSE-POSITIVE Pindai Duplikat: `AturanDuplikat.kelompokkan` mengelompokkan
   juga baris berukuran 0 — dua file BERBEDA dengan nama sama yang ukurannya
   belum diketahui akan dianggap rangkap dan satu disembunyikan dari indeks
   saat "Rapikan". Perbaiki dengan cara AMAN: kecualikan baris ukuran <= 0
   dari pengelompokan otomatis, ATAU tampilkan grup berukuran-tak-pasti dengan
   peringatan eksplisit dan jangan sertakan dalam Rapikan. Jangan menebak:
   pilih yang paling aman untuk data user.
2. `AturanIdempoten.cocok()` (fungsi murni) TIDAK dipakai di jalur guard yang
   sebenarnya (yang dipakai `hitungMirip` SQL). Selaraskan: test harus menguji
   perilaku guard yang benar-benar dipakai produksi. Bila `cocok()` memang
   tidak dipakai, hapus atau pakai — jangan biarkan kode mati + test yang
   menguji kode mati.
3. Test untuk `AturanDuplikat` BELUM ADA — tulis (pengelompokan benar, rapikan
   sisakan tepat 1 per grup, baris ukuran 0 tidak ikut terrapikan).
4. Bersihkan file sampah di root repo peninggalan run lama (untracked, JANGAN
   di-commit): `BRAINSTORM_2026-10-16.md`, `BRIEF_BRAINSTORM.md`,
   `BRIEF_IMPLEMENTASI_FITUR.md`, direktori `.kotlin/`. JANGAN hapus `briefs/`.
5. Review `git status`/`git diff` akhir: pastikan tidak ada perubahan tak
   disengaja di luar paket fitur + test.

## Batasan skema & keamanan

- Butuh kolom/tabel baru → tulis migrasi Room mengikuti pola yang ada
  (proyek di v8). Bila tidak butuh, jangan sentuh skema.
- Jangan hapus pesan Telegram tanpa konfirmasi eksplisit berlapis (sudah ada
  untuk Paket 2 — pertahankan pola yang sama bila menambah aksi hapus).
- Jangan tampilkan/tebak secret. Jangan commit/push.
