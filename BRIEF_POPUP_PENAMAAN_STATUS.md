# Header wajib untuk SETIAP brief OpenCode — tempel di atas brief, jangan pernah dihapus

> **BACA DULU SEBELUM CODING:** baca `~/workspace/opencode/AGENTS.md` sampai habis,
> lalu ikuti aturan di bawah. Brief yang tidak menyertakan header ini TIDAK SAH
> — hentikan dan minta brief yang lengkap, jangan langsung coding.

## ATURAN SKILL AGENT — WAJIB

1. **Skill-router dulu.** Untuk setiap tugas substantif, cari skill lewat
   `grep -ri "<kata kunci>" ~/workspace/skills/INDEX.md` dan direktori skill,
   lalu **baca file `SKILL.md`-nya** sebelum memakai skill itu.
   Muat **3–10+ skill yang relevan per fase** (P0–P5). Tugas besar normalnya
   memakai 10+ skill aktif sekaligus.
2. **Fase P0–P5 + gate verifikasi mikro tiap fase.** P0 ada di brief (Muse);
   OpenCode mengeksekusi P1–P4 (+ P5 readiness). Jangan lompat ke implementasi
   (P3) bila fondasi (P0/P1/P2) belum lolos gate. Format laporan wajib:
   Active Skills / Current Phase / Deep Work / Micro-Verification /
   Next Phase Readiness.
3. **Deklarasikan skill yang dimuat.** Di awal log run, tulis daftar skill yang
   benar-benar dibaca (nama + path `SKILL.md`). Jangan klaim skill yang hanya
   di-`grep` tapi tidak dibaca.
4. **Just-in-time loading.** Kebutuhan baru di tengah fase → muat skill yang
   relevan DULU, baru lanjut. Tulis deklarasi tambahan di log saat itu terjadi.
5. **Anti-slop.** Tolak output generik: layout template, placeholder malas,
   boilerplate tanpa penanganan edge-case. Setiap baris UI harus punya hierarki
   dan tujuan visual yang jelas.
6. **Verifikasi dengan bukti nyata** (test / screenshot / bundle yang benar-benar
   diunduh) sebelum klaim selesai. Jangan klaim selesai tanpa bukti.

## Cek akhir sebelum menyerahkan run

- [ ] `AGENTS.md` dibaca
- [ ] Daftar skill yang dimuat tertulis di log (dengan bukti baca `SKILL.md`)
- [ ] Semua fase P0–P5 dilewati dengan gate-nya
- [ ] Bukti verifikasi dilampirkan (bukan sekadar klaim)

---
Asal: perbaikan 14 Okt 2026 — run pertama situs BIBD dikirim tanpa header ini,
OpenCode hanya memuat 1 skill (accessibility) dan langsung coding, melanggar
aturan tetap. Run kedua dengan header ini memuat 14 SKILL.md dan lolos semua gate.

---

# BRIEF — Build berikutnya: Popup penamaan koleksi per tipe + SEMUA pesan status jadi popup

Repo: `~/workspace/arsip-senja-android`, branch `main`. Basis: HEAD `8352b7d`
(Build #30 selesai penuh). Working tree saat brief ditulis hanya berisi file
brief untracked (jangan commit file brief apa pun, termasuk
`RENCANA_MANUAL_REALTIME.md` yang sudah ada — itu bukan bagian tugas ini).
Satu sesi: planning + implementasi. JANGAN commit/push — Muse yang verifikasi +
commit/push.

## Perintah user (dikutip apa adanya, sudah dikonfirmasi)

> "oh gini flownya, setiap bikij topik baru atau sudah ada topiknya tapi
> koleksinya ga mencakup semua itu akan muncul popup penamaan nama koleksi,
> dan itu sendiri sendiri. gambar koleksi sendiri, dokumen sendiri dan lain
> lain. nah dan juga semua pesan status sukses, gagal, error atau apapun itu
> muncul error nah wajib dibuat pop up aja biar keliatan"

Interpretasi yang SUDAH dikonfirmasi user: (1) popup penamaan nama koleksi
muncul setiap bikin topik baru DAN setiap topik sudah ada tapi belum tercakup
koleksi; satu popup per koleksi, bergantian, pisah per tipe (Gambar sendiri,
Dokumen sendiri, dst). (2) SEMUA pesan status (sukses, gagal, error, apa pun)
wajib jadi popup agar kelihatan.

## P0 — Keadaan sekarang (TERVERIFIKASI dari pembacaan kode, cek lagi sebelum coding)

**A. Buat koleksi sekarang: SATU form → SATU topik + SATU koleksi multi-tipe.**
- `DasborScreen.LembarKoleksi` (~baris 695): satu form — "Nama koleksi baru"
  + deskripsi + label + warna + multi-select tipe (default SEMUA tipe
  dicentang) + kunci. Satu tombol "Buat koleksi".
- `DasborViewModel.buatKoleksi(nama, ..., tipe)` → `ArsipRepository.buatKoleksi`
  → SATU `pastikanTopikDenganPerbaikanForum(vault, nama)` → SATU
  `KoleksiEntity` dengan `tipeFile` = CSV tipe yang dipilih (kosong = semua tipe).
- Bila topik bernama sama SUDAH ADA, `pastikanTopik` memakai ulang topik itu;
  nama koleksi = `topik.nama`; metadata lokal lama dipertahankan. TIDAK ada
  popup penamaan untuk topik yang dipakai ulang — ini yang berubah.
- `KoleksiEntity` PK komposit `(chatId, topicId)` → SATU koleksi per topik.
  Konsekuensi: "satu koleksi per tipe" HANYA bisa berarti satu TOPIK per tipe
  (tipe berbeda = topik berbeda). JANGAN ubah PK, JANGAN migrasi Room.

**B. Tarik sekarang: nama koleksi = nama topik, tanpa popup.**
- `ArsipRepository.tarikDaftarKoleksi()` → `tarikTopikVault` +
  `tarikChannelLama` → tiap topik jadi `KoleksiEntity` dengan
  `nama = topik.nama` (atau judul chat), langsung `simpanSemua`, tanpa
  bertanya ke user. Entri baru masuk tab Impor (`asalImpor = true`).
- Definisi "tercakup" yang dipakai brief ini: ada `KoleksiEntity` dengan
  `(chatId, topicId)` yang sama. Topik tanpa entri itu = BELUM tercakup.

**C. Status sekarang: strip tipis (BilahPesan), BUKAN popup.**
- `ui/components/BilahPesan.kt`: strip amber di bawah header + tombol tutup.
  Dipakai di Dasbor (termasuk error di `LembarKoleksi`), Unggah, Berkas,
  Tautan, Impor, Pengaturan (`pesanVault`).
- Tiap ViewModel punya `_pesan: StateFlow<String?>` (Dasbor, Unggah, Berkas,
  Tautan, Impor) / `_pesanVault` (Pengaturan); Screen collect + tampilkan
  `BilahPesan`; tutup via `habiskanPesan()`. Nada ditentukan Screen dari
  substring ("gagal"/"Gagal") — rapuh, ikut diperbaiki (lihat P1).
- Antrean Unggah: teks galat per baris di `UnggahScreen.BarisAntrean`
  (dari `UploadWorker` outputData) — itu TEKS KONTEKSTUAL di daftar, BUKAN
  pesan status; TETAP dipertahankan (lihat P1).
- `TipeFile` (`util/TipeFile.kt`): GAMBAR, VIDEO, AUDIO, DOKUMEN, ARSIP,
  LAINNYA. `LoginScreen` punya teks status sendiri; `CrashActivity` punya
  Toast "Disalin" (sepele, di luar cakupan).

**D. Di LUAR cakupan (JANGAN dikerjakan):** realtime Build #31
(konsumen `pesanBaru`, auto-sync, dsb) — JANGAN implementasikan di run ini.
JANGAN ubah logika upload, logika vault, pemisahan pribadi/impor, gating kunci,
atau `tarikDaftarKoleksi` selain yang diperintahkan di P1.

## P1 — Desain (W AJIB dipenuhi persis; bila ada konflik, tanya di laporan, jangan menebak diam-diam)

### 1. Popup penamaan (`PopupNamaKoleksi`) — SATU komponen dipakai Buat + Tarik
- Dialog modal (bukan bottom sheet): judul "Beri nama koleksi", chip tipe
  ("Untuk tipe: Gambar"), field Nama (wajib, tidak boleh kosong), teks
  pembantu yang menyebut konteksnya ("Topik baru di vault" atau
  "Topik 'X' dari Telegram belum punya koleksi").
- Tombol: **[Lewati]** (kiri/ sekunder) **[Simpan]** (primer). Plus
  **[Batalkan semua]** (teks kecil) saat popup adalah bagian dari antrean
  > 1 item — mengehentikan sisa antrean, yang sudah tersimpan TETAP tersimpan.
- Nama awal (prefill): untuk Buat = label tipe ("Gambar", "Dokumen", dst);
  untuk Tarik = nama topik Telegram apa adanya. User boleh ubah bebas.
- Validasi: nama kosong → tombol Simpan nonaktif + pesan validasi inline di
  dalam popup (bukan popup baru). Nama duplikat dengan koleksi lain yang
  sudah ada → TOLAK dengan pesan jelas di dalam popup ("Nama sudah dipakai
  koleksi lain"), jangan menimpa koleksi lain. Nama sama dengan topik yang
  dipakai ulang itu sendiri = BOLEH (bukan duplikat).
- Antrean popup: SATU popup tampil dalam satu waktu. Antrean diproses
  berurutan; Simpan/Lewati → popup berikutnya; Batalkan semua → antrean
  dibuang. JANGAN menumpuk dialog.

### 2. Alur BUAT koleksi (LembarKoleksi dirombak)
- Form tetap ada untuk pengaturan BERSAMA: deskripsi, label, warna, kunci,
  dan pilihan tipe (checkbox multi, default semua tipe — pertahankan).
- Field "Nama koleksi baru" DIHAPUS dari form — nama kini ditanya lewat
  popup penamaan per tipe. Ganti field itu dengan ringkasan:
  "N koleksi akan dibuat, satu per tipe — namanya ditanya satu per satu."
- Tap "Buat koleksi" → antrean penamaan = satu item per tipe yang dicentang,
  urutan mengikuti urutan `TipeFile.entries` (Gambar, Video, Audio, Dokumen,
  Arsip, Lainnya). Untuk tiap item: popup → Simpan → buat SATU topik +
  SATU koleksi untuk tipe itu (pengaturan bersama dipakai untuk semuanya).
- Lewati = tipe itu tidak dibuatkan koleksi. Batalkan semua = berhenti,
  yang sudah dibuat tetap ada. Sesudah antrean habis → popup status
  "N koleksi dibuat" (atau "Tidak ada koleksi dibuat" bila semua dilewati /
  dibatalkan — tetap popup, bukan strip).
- Kasus topik sudah ada tapi belum tercakup: bila nama yang disimpan user
  sama dengan nama topik yang sudah ada, pakai ulang topik itu (perilaku
  `pastikanTopik` yang sudah ada) dan koleksi terikat ke topik tersebut.
  Bila nama diubah → topik baru dibuat dengan nama baru.
- "Buatkan topik" (`perbaikiTopikHilang`) TIDAK memakai popup penamaan:
  koleksinya sudah punya nama; perbaiki diam-diam seperti sekarang, lalu
  popup status hasilnya.

### 3. Alur TARIK (Dasbor "Tarik dari Telegram" + tab Impor "Tarik")
- Sesudah `tarikDaftarKoleksi()` selesai, hitung entri yang BARU tercakup
  (tidak ada `KoleksiEntity` untuk `(chatId, topicId)` itu SEBELUM tarik —
  repo wajib menyediakan cara membedakannya; pilih cara paling bersih,
  mis. repo mengembalikan mana yang baru, atau ViewModel membandingkan
  sebelum/sesudah; JANGAN andalkan nama untuk membedakan).
- Untuk tiap entri BARU (topik vault maupun channel lama, `topicId` boleh 0):
  popup penamaan berurutan, prefill = nama topik/chat. Simpan → ganti nama
  koleksi itu (pertahankan SEMUA field lain: label, warna, tipe, kunci,
  asalImpor). Lewati → nama topik dipertahankan. Batalkan semua →
  sisanya memakai nama topik.
- Entri yang SUDAH tercakup sebelum tarik: TIDAK ADA popup (namanya sudah
  milik user). Tarik TIDAK PERNAH menghapus / menimpa nama koleksi yang
  sudah ada. Tarik TIDAK PERNAH terblokir popup: bila user menutup layar di
  tengah antrean, sisa entri tetap tersimpan dengan nama topik.
- Pesan hasil tarik tetap lewat popup status (lihat §4).

### 4. Popup status (`PopupStatus`) — SATU komponen untuk SEMUA pesan status
- Komponen baru `ui/components/PopupStatus.kt`, `AlertDialog`-based, token
  `WarnaArsip` / `Fraunces` / `JakartaSans`, ikon Lucide (bukan emoji):
  judul + pesan + tombol. Tiga varian: SUKSES (ikon centang, aksen Sukses),
  GALAT (ikon alert, aksen Bahaya), INFO (ikon info, aksen netral).
- GANTI SEMUA pemakaian `BilahPesan` untuk pesan status di: Dasbor
  (termasuk error di `LembarKoleksi`), Unggah, Berkas, Tautan, Impor,
  Pengaturan (`pesanVault` + galat kandidat). Sesudah diganti, HAPUS
  `BilahPesan.kt` bila tidak ada pemakaian tersisa (cek dengan grep);
  jangan sisakan komponen mati.
- Sumber kebenaran nada: ViewModel, BUKAN substring di Screen. Ubah
  `_pesan: StateFlow<String?>` menjadi state pesan berjenis, mis.
  `StateFlow<PesanStatus?>` dengan `PesanStatus(nada, teks, aksiUlang: Boolean)`
  — atau desain setara. Screen TIDAK BOLEH lagi menebak nada dari kata
  "gagal". Tiap situs yang mengisi `_pesan` tentukan nadanya di titik
  pengisian (sukses → SUKSES, catch → GALAT, info → INFO).
- Perilaku: popup modal, WAJIB ditutup manual via tombol **[Mengerti]**.
  TIDAK ada auto-dismiss. Satu popup dalam satu waktu; pesan baru saat
  popup terbuka → ganti isi popup (latest wins), jangan menumpuk.
  Tutup → `habiskanPesan()`.
- Aksi khusus yang HILANG dari strip harus PINDAH ke popup: tombol
  "Buat vault baru & coba lagi" (`tawarkanVaultBaru` di Dasbor) menjadi
  tombol aksi kedua di popup galat terkait ("Buat vault baru & coba lagi"),
  BUKAN strip terpisah.
- Antrean Unggah: teks galat per baris TETAP di daftar. TAMBAH: saat sebuah
  pekerjaan mencapai FAILED terminal, tampilkan popup status galat berisi
  alasan dari `UploadWorker` outputData (sekali per pekerjaan, BUKAN per
  percobaan retry). Sukses upload massal: ringkasan tetap lewat popup
  status Unggah seperti pesan lainnya. Progres transfer TIDAK berubah.
- Login: galat login juga lewat `PopupStatus` (konsisten). Toast "Disalin"
  di `CrashActivity` dibiarkan (bukan pesan status alur).

## P2 — Arsitektur
- Logika antrean penamaan + penentuan "baru tercakup" + tipe
  `PesanStatus` dipisahkan sebagai logika murni (file util / ViewModel
  helper) agar bisa di-unit-test tanpa Compose/TDLib. Screen hanya
  render antrean + popup.
- Satu-satunya sumber penamaan: `PopupNamaKoleksi`. JANGAN duplikasi
  form nama di dua tempat. Satu-satunya sumber status: `PopupStatus`.
  JANGAN ada lagi `BilahPesan` untuk status.
- JANGAN ubah PK `KoleksiEntity`, JANGAN migrasi Room, JANGAN ubah
  `tarikDaftarKoleksi` selain mengembalikan info "baru" (atau padanannya),
  JANGAN ubah logika upload / vault / kunci / pemisahan pribadi-impor.
- `ubahKoleksi` yang sudah ada dipakai untuk rename hasil Tarik (nama baru
  + field lain dipertahankan). Bila perlu fungsi rename khusus, buat di
  repo dengan satu tanggung jawab, jangan duplikasi logika simpan.

## P3 — Implementasi
1. `PopupStatus.kt` + `PopupNamaKoleksi.kt` (baru) + tipe `PesanStatus`.
2. Ubah `_pesan` di Dasbor/Unggah/Berkas/Tautan/Impor/Pengaturan ke
   `PesanStatus`; ubah semua Screen ke `PopupStatus`; pindahkan aksi
   "Buat vault baru & coba lagi" ke popup galat.
3. Rombak `LembarKoleksi`: hapus field nama, tambah ringkasan antrean,
   antrean penamaan per tipe → buat per tipe via repo.
4. Alur Tarik: deteksi entri baru → antrean penamaan → rename via repo.
5. Popup FAILED terminal antrean Unggah (sekali per pekerjaan).
6. Login via `PopupStatus`.
7. Hapus `BilahPesan.kt` bila tak terpakai; perbarui semua Screen yang
   mengimpornya.

## P4 — Verifikasi (W AJIB, jangan klaim tanpa bukti)
- Cek kurung per file Kotlin yang diubah = 0 selisih; `git diff --check`
  bersih; grep `TODO|FIXME|XXX|placeholder|lorem` di
  `app/src/main/java` + `app/src/test` kosong; grep `BilahPesan` sisa = 0
  (atau hanya dihapusnya file bila masih ada pemakaian non-status yang
  dijelaskan di laporan).
- `./gradlew testDebugUnitTest` bila `java` ada; bila tidak ada, tulis
  jelas "tidak dijalankan di sandbox, CI adalah gerbang" — JANGAN klaim lolos.
- Test baru (unit, logika murni): antrean penamaan (simpan → lanjut,
  lewati → lanjut tanpa simpan, batal → berhenti, sudah tersimpan tetap),
  deteksi "baru tercakup" (entri baru vs sudah ada, dibedakan dari kunci
  `(chatId, topicId)` bukan nama), `PesanStatus` (nada dari ViewModel,
  tutup → null, pesan baru menimpa).
- 6 skenario (baca dari kode, tulis di laporan mana yang terbaca):
  (a) Buat dengan 2 tipe dicentang → 2 popup berurutan → 2 topik + 2 koleksi,
  masing-masing satu tipe. (b) Buat dengan nama sama seperti topik yang
  sudah ada tapi belum tercakup → popup prefill nama topik → Simpan →
  koleksi terikat ke topik lama, tidak ada topik ganda. (c) Tarik menemukan
  2 topik baru → 2 popup; Lewati yang kedua → nama topik dipertahankan.
  (d) Batalkan semua di tengah antrean → yang sudah dibuat/rename tetap,
  sisanya tidak berubah. (e) Gagal buat koleksi → popup GALAT berisi alasan
  + tombol aksi vault bila relevan; tidak ada strip. (f) Sukses sinkron →
  popup SUKSES; tombol Mengerti menutup; tidak ada strip.

## P5 — Readiness (format laporan wajib)
Active Skills / Current Phase / Deep Work / Micro-Verification /
Next Phase Readiness + daftar file diubah + bukti verifikasi + perilaku
baru (Buat per tipe, Tarik penamaan, status popup) + instruksi uji
perangkat (buat koleksi 2 tipe; tarik topik lama; gagalkan satu aksi
sengaja, mis. nama duplikat; cek tidak ada strip tersisa).

## Larangan keras
- JANGAN commit/push — Muse yang verifikasi + commit/push.
- JANGAN ubah workflow CI / keystore / `versionCode` / secret.
- JANGAN hapus data Telegram user otomatis; JANGAN hapus koleksi/berkas
  lokal otomatis; JANGAN buat koleksi dari chat tak dikenal (penamaan
  hanya untuk hasil Tarik yang sudah dikenal + topik baru dari Buat).
- JANGAN implementasikan realtime Build #31 di run ini.
- JANGAN tambah TODO tersembunyi / placeholder malas / emoji sebagai ikon.
- JANGAN klaim selesai tanpa bukti; jangan klaim terbukti di perangkat
  sebelum user menguji; jangan cetak secret/token/OTP.
- Perhatian Build #30: field Room dengan `@ColumnInfo(defaultValue)` WAJIB
  juga punya default Kotlin bila konstruktor dipakai tanpa nilai itu
  (pelajaran dari gagal kompilasi test `PenandaImporTest` — `nomorKatalog`).
