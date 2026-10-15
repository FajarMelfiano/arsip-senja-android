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

# BRIEF run10 — Build #30: Guard Upload + Retry Berpesan + Tarik Multi-Sumber + Tab Pisah Impor Telegram

## Perintah sesi (WAJIB — dari user langsung)

Kerjakan dalam **SATU sesi yang sama**: buat **planning lengkap dulu**, lalu
implementasikan planning itu sampai selesai dalam run ini juga. Jangan hanya
menulis rencana tanpa kode, jangan juga langsung coding tanpa planning.
Planning ditulis di awal log/output run (P0–P2: pemahaman, desain tab pisah,
desain data, risiko), lalu P3 implementasi, P4 verifikasi, P5 readiness.
Satu sesi, satu run, planning + implementasi.

## Konteks proyek

- Repo: `~/workspace/arsip-senja-android`, branch `main`, HEAD `b6a4c3d`
  (Build #29, sudah CI sukses + release).
- Aplikasi: Arsip Senja Android native (Kotlin + Jetpack Compose + Room +
  TDLib + WorkManager). `applicationId com.arsipsenja`, debug suffix `.debug`.
- Model mental (JANGAN diubah): Vault = satu grup Telegram forum yang
  menampung semua koleksi. Koleksi = satu topik di dalam vault (atau satu
  channel lama, `topicId = 0`, kompatibel ke belakang). Berkas disimpan sebagai
  pesan Telegram di akun user sendiri, bukan di server lain.
- Bahasa UI: Bahasa Indonesia. Ikon: 100% Lucide (via `ArsipIcons`), TANPA
  emoji sebagai ikon/chrome UI.

## P0 — Diagnosis yang sudah terbukti (JANGAN ulangi diagnosis dari nol)

### A. Upload gagal total — akar: guard `koleksiId <= 0`
- ChatId grup/supergroup Telegram selalu NEGATIF (`-100...`). Dibuktikan
  `TelegramClient.grupIdDariChat` (`require(chatId <= -1_000_000_000_000L)`).
- Guard lama di `UploadWorker.kerjakan()` menolak semua tujuan sah.
- Bukti perangkat Build #29: antrean menampilkan pesan
  "Tujuan unggah tidak sah — pilih ulang koleksi." — artinya pesan galat
  Build #29 sudah jalan, sekaligus membuktikan akar guard ini.
- Jalur: `UnggahViewModel.mulaiUnggah` → `ArsipRepository.mulaiUnggah` /
  `unggahOtomatis` → `UploadWorker` (WorkManager, tag `antrean-unggah`).

### B. Upload gagal per-potongan tanpa pesan
- Kode lama: catch per-potongan → `Log` + notifikasi + `gagal = true` →
  `Result.retry()` tanpa outputData. Setelah retry habis, FAILED generik
  tanpa alasan.

### C. Tarik tidak semua terdeteksi + berkas tidak ikut
- `ArsipRepository.tarikDaftarKoleksi()` lama: bila vault aktif ada → hanya
  `tarikTopikVault(vault)`; bila tidak ada → hanya `tarikChannelLama()`.
  Topik di grup forum lama lain TIDAK pernah ditarik bila vault aktif ada.
- `tarikTopikVault` / `tarikChannelLama` hanya membuat/memperbarui
  `KoleksiEntity` (pertahankan metadata lokal), TIDAK memanggil
  `sinkronKoleksi` — jumlah berkas tetap 0 sampai Sinkron ditekan.
- `tarikRiwayatBaru`: `HALAMAN_RIWAYAT = 100`, `BATAS_HALAMAN = 5`
  (maks 500 pesan/koleksi/sinkron), dedupe via `berkasDao.semuaId()`.

### D. Keputusan user — TAB PISAH (mengikat, supersedes asumsi gabung)
Kutipan user: "maksudnya aku maunya tuh kepisah, kan aku mencet tarik dari
telegram nah itu kan ke import semua kan grup yang ada kalo aku sinkron, nah
itu beda tab dengan file file pribadiku".
Artinya: hasil "Tarik dari Telegram" (semua grup yang ke-import saat sinkron)
harus berada di **tab TERPISAH** dari file-file pribadi. Impor TIDAK boleh
dicampur ke daftar koleksi pribadi.

## Keadaan working tree saat run dimulai (VERIFIKASI dulu, jangan asumsi)

- `git status`: `M` pada 2 file, sisanya bersih (brief-brief lama untracked,
  abaikan).
- `git diff --stat`: `ArsipRepository.kt` 33 insertions,
  `UploadWorker.kt` 27 insertions.
- `UploadWorker.kt` — edit SUDAH ADA dan VALID, dipertahankan sebagai titik
  awal (jangan tulis ulang buta):
  - import `PenjagaVault`; field `percobaanKe = params.runAttemptCount`;
  - guard jadi `getLong(K_KOLEKSI, 0L)` + `if (koleksiId == 0L)` + komentar
    chatId negatif; `var pesanGagalTerakhir`; catch per-potongan isi
    `pesanGagalTerakhir = PenjagaVault.terjemahkanGalatForum(...)`;
  - akhir: sukses → `Result.success()`; gagal + `percobaanKe < 2` →
    `Result.retry()`; gagal + percobaan terakhir →
    `gagalDenganPesan(pesanGagalTerakhir ?: "Unggah gagal — coba lagi.")`;
  - companion `MAKS_PERCOBAAN_GAGAL = 2`.
  - Tugasmu: VERIFIKASI edit ini (baca file, cek logika retry +
    keseimbangan kurung + tidak ada duplikasi), pertahankan bila benar,
    perbaiki minimal bila ada cacat. Jangan revert tanpa alasan.
- `ArsipRepository.kt` — edit `tarikDaftarKoleksi()` gabungan SUDAH ADA tapi
  **BATAL / BERTENTANGAN dengan keputusan tab pisah** (menggabung vault aktif
  + `deteksiVaultMilikSendiri()` + `tarikChannelLama()` + `sinkronKoleksi`
  per koleksi ke satu daftar yang sama). Tugasmu: KERJAKAN ULANG fungsi ini
  menjadi desain tab pisah di bawah. Jangan commit/pertahankan versi gabungan
  apa adanya. Logika multi-sumber + sinkron + isolasi gagal per-sumber dari
  edit gabungan itu BOLEH dipakai ulang, tapi outputnya harus masuk tab
  impor, bukan daftar pribadi.

## P1 — Desain tab pisah (planning wajib sebelum coding)

Pilih SATU desain berikut, beri alasan di planning, lalu implementasikan
penuh (bukan setengah):

- **Opsi A (disarankan): tab bawah ke-6 baru "Impor Telegram".**
  Tambah rute + item di `ArsipNavHost.kt` (saat ini 5 item: Dasbor, Unggah,
  Tautan, Pohon, Pengaturan; label satu baris 11.sp, `saveState/restoreState`
  + `launchSingleTop`). Layar + ViewModel sendiri untuk tab impor.
- **Opsi B: pemisahan di dalam Dasbor** (tab internal "Pribadi" vs
  "Impor Telegram" di atas daftar koleksi Dasbor).
  Hanya pilih ini bila terbukti lebih ergonomis + tidak merusak layout Dasbor
  yang ada. Jelaskan trade-off-nya di planning.

Apapun opsinya, yang WAJIB terpenuhi:
1. Koleksi hasil Tarik dari Telegram TIDAK muncul di daftar koleksi pribadi
   (Dasbor "Koleksi di akunmu", filter koleksi, Katalog/Pohon bila memakai
   daftar yang sama — pisahkan sumbernya, jangan hanya sembunyikan label).
2. Tab impor menampilkan: daftar koleksi impor (nama, asal: nama grup/channel
   + tipe forum/channel, jumlah berkas), daftar berkas tiap koleksi impor
   (buka detail, pratinjau, unduh, bagikan — pakai komponen yang sama dengan
   berkas pribadi bila memungkinkan), tombol Tarik/Sinkron impor, dan state
   kosong/loading/galat yang dirancang (bukan afterthought).
3. File pribadi tetap di tempatnya, tidak berpindah, tidak hilang, tidak
   duplikat. Koleksi yang SUDAH dikenal sebagai pribadi (kunci `chatId,
   topicId` sama) tidak boleh duplikat sebagai impor.
4. Tombol "Tarik dari Telegram" yang ada di Dasbor tetap ada dan memicu
   impor ke tab impor (pesan sukses menyebut jumlah impor + arahkan/petunjuk
   ke tab impor). Jangan buat tombol tarik kedua yang membingungkan.

## P2 — Desain data (planning wajib sebelum coding)

- Penanda asal impor WAJIB persisten dan eksplisit. Cara yang disarankan:
  tambah kolom `asalImpor: Boolean` (default `false`) atau kolom `sumber`
  ke `KoleksiEntity` + **migrasi Room** (versi naik, migrasi teruji —
  JANGAN `fallbackToDestructiveMigration`, JANGAN hapus data lokal).
  Bila memilih desain lain (mis. tabel terpisah), jelaskan di planning
  kenapa lebih aman + buktikan tidak ada kehilangan metadata lokal
  (label/warna/tipe/kunci dipertahankan seperti `tarikTopikVault` lama).
- `tarikDaftarKoleksi()` baru: kembalikan HANYA koleksi impor (bertanda
  impor), dari gabungan sumber: vault aktif + semua vault forum-siap milik
  sendiri (`deteksiVaultMilikSendiri()`, dedupe per chatId) +
  `tarikChannelLama()`. Tiap sumber dibungkus `runCatching` — gagal satu
  sumber tidak membatalkan yang lain. Tiap koleksi impor yang ditemukan
  langsung `sinkronKoleksi(kunci)` (per koleksi `runCatching`).
  Koleksi pribadi TIDAK disentuh oleh fungsi ini.
- `DasborViewModel.tarikDaftarKoleksi()`: sesuaikan pesan + state agar
  mengarah ke tab impor (jumlah impor, bukan "koleksi di akunmu" yang
  ambigu). Galat via `PenjagaVault.terjemahkanGalatForum`.
- Upload: JANGAN ubah alur upload selain verifikasi guard + retry berpesan
  yang sudah ada. Upload ke koleksi pribadi tetap ke topik koleksi itu.
  Upload ke koleksi impor: di luar cakupan — jangan tambahkan jalur upload
  ke impor tanpa desain (tulis sebagai batasan di laporan bila belum ada).
- Kunci koleksi: berkas milik koleksi impor yang terkunci tetap mengikuti
  gating kunci yang sama seperti berkas pribadi (jangan bocorkan pratinjau
  koleksi terkunci).

## P3 — Implementasi (cakupan)

1. Verifikasi + pertahankan perbaikan `UploadWorker.kt` (guard + retry
   berpesan). Tambah unit test bila ada aturan murni yang bisa diuji
   tanpa WorkManager (mis. terjemahan galat sudah ada di `PenjagaVaultTest` —
   jangan duplikasi tanpa nilai).
2. Rework `tarikDaftarKoleksi()` → impor bertanda + multi-sumber +
   sinkron per koleksi (lihat P2).
3. Migrasi Room + penanda asal impor (bila pakai kolom baru).
4. Tab impor: rute + layar + ViewModel + pemisahan daftar pribadi vs impor
   di semua tempat yang memakai `alirKoleksi()` / `repo.koleksi` (Dasbor,
   filter, Pohon, Unggah — audit satu per satu, jangan asumsi).
5. String UI Bahasa Indonesia. Ikon Lucide saja. Hierarki visual jelas,
   state kosong/loading/galat dirancang. Warna/tipografi pakai token yang
   ada (`WarnaArsip`, `Fraunces`/`JakartaSans`).
6. JANGAN ubah: keystore, workflow CI (`.github/workflows/`), `versionCode`,
   kredensial/secret. JANGAN commit/push — Muse yang verifikasi +
   commit/push. JANGAN hapus data Telegram user otomatis, JANGAN hapus
   koleksi/berkas lokal otomatis, JANGAN hapus channel/grup Telegram user.

## P4 — Verifikasi (bukti nyata, bukan klaim)

- Cek keseimbangan kurung tiap file Kotlin yang diubah (harus 0 selisih —
  laporkan angkanya per file).
- `git diff --stat` + review diff penuh sendiri sebelum menyerahkan.
- Unit test: jalankan `./gradlew testDebugUnitTest` bila `java` tersedia;
  bila tidak tersedia di sandbox (sudah diketahui: `JAVA_HOME is not set`,
  tanpa `java`/`javac`), JANGAN klaim lolos — laporkan apa adanya bahwa
  CI GitHub adalah gerbang verifikasi, dan sebutkan test baru yang
  ditambahkan + apa yang diuji.
- Skenario yang harus bisa dijelaskan dari kode (tanpa perangkat):
  (a) upload ke koleksi Gambar dengan chatId negatif → lolos guard;
  (b) gagal per-potongan percobaan 1 → retry; percobaan terakhir →
  FAILED dengan pesan;
  (c) Tarik dengan vault aktif + 1 grup forum lama lain + 1 channel lama →
  ketiganya masuk tab impor, tidak ada di daftar pribadi, tidak duplikat;
  (d) Tarik kedua kalinya → tidak duplikat, metadata lokal dipertahankan;
  (e) koleksi pribadi tetap tampil di Dasbor, file pribadi tetap di tabnya.
- Bila ada yang tidak bisa diverifikasi tanpa perangkat/akun Telegram user,
  tulis eksplisit sebagai "belum terbukti di perangkat".

## P5 — Readiness

- Laporan akhir WAJIB format: Active Skills / Current Phase / Deep Work /
  Micro-Verification / Next Phase Readiness + (sesuai AGENTS.md) daftar file
  diubah + verifikasi + cara menjalankan + blokir/asumsi.
- Sebutkan: apakah migrasi Room naik versi berapa → berapa, apakah ada
  perilaku upload ke impor (ada/tidak + alasan), dan instruksi uji perangkat
  untuk user (upload sekali; Tarik → cek tab impor; cek file pribadi tetap
  di tabnya).

## Larangan keras

- Jangan klaim selesai tanpa bukti. Jangan klaim upload/tarik/tab pisah
  terbukti jalan di perangkat sebelum user mengujinya.
- Jangan mencetak secret/token/OTP. Jangan membaca file secret kecuali
  tugas membutuhkannya.
- Jangan menambahkan `TODO` tersembunyi / placeholder malas.
