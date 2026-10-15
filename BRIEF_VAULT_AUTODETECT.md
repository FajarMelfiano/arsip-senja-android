# Brief: Status vault basi + auto-detect vault yang sudah ada + Pilih grup lama hanya grup milik sendiri

## Aturan agen (wajib dibaca dulu, bukan ditempel belakangan)
- Baca `~/workspace/opencode/AGENTS.md` (manual operasi: skill-router, fase P0–P5,
  anti-slop, format laporan) dan `AGENTS.md` di repo ini.
- Jalankan playbook skill-router: cari 2–4 sudut query, baca SKILL.md yang cocok,
  muat SEMUA skill yang relevan (skill-router, phased-execution, debugging,
  systematic-debugging, android-dev, android-jetpack-compose-expert, verification,
  code-review, copywriting-id, clean-code, kotlin — minimal yang relevan, jangan
  satu skill saja).
- Kerja dalam fase P0→P5 dengan micro-verification gate tiap fase. Format laporan
  akhir wajib: Active Skills / Current Phase / Deep Work / Micro-Verification /
  Next Phase Readiness.
- Bahasa Indonesia untuk semua teks UI/pesan. Tanpa emoji sebagai ikon (100% ikon
  Lucide yang sudah ada di `ArsipIcons`). Palet `WarnaArsip`, judul Fraunces,
  isi JakartaSans. Anti AI slop: setiap baris UI punya hierarki dan tujuan jelas.
- JANGAN commit atau push. JANGAN ubah perilaku benar lainnya (rute upload, label,
  warna, tipe, kunci koleksi, kunci biometrik level aplikasi, skema selain yang
  disebut di sini).

## Konteks (sudah diverifikasi dari kode, bukan asumsi)
Pengguna melaporkan dua hal setelah Build #24/25 (commit `500d4b5`):
1. Ia sudah menekan "Buat vault baru", tapi status di Pengaturan tetap
   "Bermasalah". Maunya: aplikasi auto-detect kalau vault yang sehat sudah ada,
   tanpa perlu ketuk Perbaiki.
2. "Pilih grup lama" menampilkan grup orang lain. Maunya: hanya grup yang
   pengguna buat sendiri (ia pemiliknya).

Fakta kode yang SUDAH diverifikasi (cek ulang sendiri sebelum mengubah):
- `PengaturanScreen.kt` (~baris 95, 223): `namaVaultAktif = infoVault?.nama`,
  `siap = infoVault?.forumSiap == true`, `adaVault = vaultAktif != null`. TIDAK
  ada `LaunchedEffect` refresh di layar ini — `vaultAktif`/`infoVault` hanya
  dimuat sekali di `PengaturanViewModel.init` (`muatVaultAktif()` +
  `muatInfoVault()`). Bila ViewModel bertahan di back-stack navigasi, kembali ke
  Pengaturan menampilkan status BASI. Ini hipotesis terkuat untuk keluhan (1):
  vault baru dibuat lewat jalur lain (mis. tombol "Buat vault baru & coba lagi"
  di Kelola koleksi via `DasborViewModel.buatVaultBaruDanCobaLagi`, yang tidak
  menyentuh `PengaturanViewModel`), lalu Pengaturan masih menampilkan vault lama.
  Buktikan atau bantah hipotesis ini di P0 dengan membaca `ArsipNavHost` +
  cara ViewModel dibuat/di-scope; bila ada penyebab lain yang lebih kuat,
  laporkan dan perbaiki penyebab itu.
- `PengaturanViewModel.muatInfoVault()` memakai
  `runCatching { repo.infoVaultAktif() }.getOrNull()` — bila pengecekan gagal
  (transien), `_infoVault` jadi null → layar menampilkan "Bermasalah" TANPA
  penjelasan. Kegagalan pengecekan tidak boleh disamarkan menjadi "Bermasalah".
- `ArsipRepository.pastikanVault()` = `vaultSekarang() ?: buatVaultBaru()` —
  bila belum ada vault tersimpan, langsung membuat grup BARU tanpa memeriksa
  dulu apakah grup vault sehat milik pengguna sudah ada di Telegram.
- `ArsipRepository.buatVaultBaru(nama)` langsung memanggil
  `telegram.buatVault(nama)` — tidak memeriksa apakah supergroup forum milik
  pengguna dengan nama yang sama sudah ada → membuat duplikat.
- `TelegramClient.daftarSupergroup()` (`TelegramClient.kt` ~457): `GetChats`
  (ChatListMain, limit 100) + `GetChat` per id, filter hanya
  `ChatTypeSupergroup && !isChannel`. TIDAK ada filter kepemilikan — grup orang
  lain yang diikuti pengguna ikut tampil di "Pilih grup lama". TIDAK ada
  pemakaian `GetMe` / `GetChatMember` di `TelegramClient` saat ini.

## P0 — Diagnosis (verifikasi, jangan langsung lompat ke P3)
1. Konfirmasi/ bantah hipotesis status basi di atas (scope ViewModel Pengaturan,
   jalur pembuatan vault lain yang tidak me-refresh Pengaturan).
2. Telusuri `infoVaultAktif()` + `daftarTopik()`: apa yang terjadi pada grup
   forum yang BARU dibuat (propagasi Telegram)? Apakah perlu retry?
3. Tentukan cara mendapatkan id pengguna sendiri via TDLib (`TdApi.GetMe`) dan
   status keanggotaan via `TdApi.GetChatMember(chatId, userId)` →
   `TdApi.ChatMemberStatusCreator` = pemilik. Periksa nama kelas TDLib yang
   tepat di dependensi proyek (jangan tebak dari ingatan).

## P1 — UX (teks persis, Bahasa Indonesia)
- Status vault tidak pernah menampilkan "Bermasalah" tanpa alasan. Bila
  pengecekan gagal: pertahankan info terakhir + tampilkan pesan
  "Gagal memeriksa status vault: <alasan>. Coba buka lagi Pengaturan."
- Dialog "Pilih grup lama": tambah keterangan "Hanya grup yang kamu buat
  (kamu pemiliknya)." Bila daftar kosong: empty state "Belum ada grup
  milikmu — buat vault baru." (bukan daftar kosong diam).
- Auto-detect yang sukses harus memberi tahu pengguna lewat `pesanVault`:
  mis. `Vault "Arsip Senja" terdeteksi otomatis dan dijadikan aktif.`
- Buat vault baru yang menemukan vault lama bernama sama: pesan
  `Vault "Arsip Senja" sudah ada, dipakai lagi.` (bukan diam memakai ulang).

## P2 — Aturan auto-detect & auto-adopt (tulis aturan ini persis)
Fungsi baru, mis. `ArsipRepository.deteksiVaultMilikSendiri(): List<KandidatVault>`
atau setara: daftar supergroup MILIK SENDIRI (lihat P3) yang forum-siap
(`daftarTopik` sukses). Dipakai di tiga tempat:
1. **Buka Pengaturan** (refresh tiap tampil, lihat P3a): bila vault tersimpan
   TIDAK SAH (bukan supergroup, mis. channel) dan ada kandidat milik sendiri
   yang forum-siap → auto-adopt bila (a) ada yang nama-nya cocok persis
   "Arsip Senja" (case-insensitive, trim), ATAU (b) hanya ada SATU kandidat
   forum-siap. Bila ada beberapa kandidat tanpa kecocokan nama → JANGAN
   menebak; pertahankan status Bermasalah + pesan menyebut jumlah kandidat:
   "Vault rusak. Ditemukan N grup milikmu yang siap dipakai — pilih salah
   satu di "Pilih grup lama"." Pengecualian keamanan: auto-adopt HANYA bila
   vault tersimpan tidak sah DAN (tidak ada koleksi terdaftar yang memakai
   vault lama itu ATAU semua koleksinya bertopicId == 0). Bila vault lama
   dipakai koleksi bertopik → jangan auto-adopt; pesan jelas seperti aturan
   lama (tombol aksi di Kelola koleksi tetap berlaku).
2. **`pastikanVault()`** (jalur buat koleksi / upload): bila belum ada vault
   tersimpan → deteksi dulu; bila ada kandidat (aturan nama-cocok / satu-satunya
   seperti di atas) → pakai itu, JANGAN membuat grup baru. Bila tidak ada →
   buat baru seperti sekarang.
3. **`buatVaultBaru(nama)`**: sebelum `CreateNewSupergroupChat`, cari di
   grup milik sendiri yang forum-siap dengan nama sama (case-insensitive,
   trim) → bila ketemu, jadikan aktif + kembalikan id-nya (dedupe, tanpa
   membuat grup baru). Bila tidak ketemu → buat baru seperti sekarang.

## P3 — Implementasi
### P3a. Status tidak basi
- `PengaturanScreen`: tambah `LaunchedEffect(Unit)` (atau mekanisme setara
  yang benar untuk layar ini) yang memanggil `vm.muatVaultAktif()` +
  `vm.muatInfoVault()` setiap kali layar Pengaturan ditampilkan. `init`
  ViewModel tetap dipertahankan sebagai pemuatan pertama.
- `PengaturanViewModel.muatInfoVault()`: bila pengecekan gagal, PERTAHANKAN
  `_infoVault` lama dan isi `_pesanVault` dengan alasan gagalnya. Jangan
  menimpa info lama dengan null.
- Setelah `buatVaultBaru` / `pilihVault` / `perbaikiVault` sukses, status
  di-refresh (sebagian sudah ada — verifikasi semuanya, tambahkan yang
  kurang). Pertimbangkan retry terbatas (maks 2x, jeda ~1,5 dtk) untuk cek
  forum tepat setelah vault baru dibuat (propagasi Telegram), lalu laporkan
  bila retry dipakai.

### P3b. Filter hanya grup milik sendiri
- `TelegramClient`: tambah `sayaId()` (cache hasil `TdApi.GetMe`) dan
  `apakahPemilik(chatId: Long): Boolean` via
  `TdApi.GetChatMember(chatId, sayaId)` → true hanya bila
  `memberStatus is TdApi.ChatMemberStatusCreator`. Bila pengecekan gagal
  untuk satu grup → grup itu DIKELUARKAN + log (fail-closed, sesuai maunya
  pengguna: hanya tampilkan yang pasti milik sendiri). Bila SEMUA pengecekan
  gagal → lempar galat agar UI menampilkan "Gagal memuat grup: ..." (bukan
  daftar kosong yang menyesatkan).
- `TelegramClient.daftarSupergroup()`: kembalikan HANYA supergroup milik
  sendiri (filter tipe dulu, lalu filter pemilik). `daftarKandidatVault()`
  otomatis ikut tersaring karena memakai fungsi ini. JANGAN mengubah filter
  tipe yang sudah ada. Perhatikan performa: cek pemilik hanya untuk chat
  yang lolos filter tipe (bukan untuk 100 chat mentah).
- `DialogPilihGrup` di `PengaturanScreen.kt`: tambah keterangan pemilik +
  empty state seperti P1.

### P3c. Auto-detect (aturan P2)
- Implementasikan deteksi + auto-adopt di `ArsipRepository` sesuai P2
  (Pengaturan-refresh, `pastikanVault`, `buatVaultBaru` dedupe).
- `PengaturanViewModel`: sesudah auto-adopt, `pesanVault` berisi pesan
  deteksi otomatis (P1) dan `_vaultAktif` + `_infoVault` diperbarui.
- JANGAN mengubah: jalur `pilihVault` manual (tetap seperti sekarang),
  kebijakan `pulihkanVaultTakSah`, rute upload/label/warna/tipe, kunci koleksi,
  kunci biometrik level aplikasi, skema Room.
- Pelajaran Build #24 yang tetap berlaku: setiap subkelas baru wajib dicek
  deklarasi induknya (`open`/`abstract`/`sealed`) — jangan ulangi cacat
  final-class `TelegramException`.

## P4 — Verifikasi
- Unit test baru (jangan hapus test lama): (1) aturan auto-adopt — nama cocok
  → adopt; satu-satunya kandidat → adopt; beberapa kandidat tanpa nama cocok
  → jangan adopt; vault lama dipakai koleksi bertopik → jangan adopt;
  (2) dedupe nama buat vault baru (case-insensitive + trim);
  (3) `muatInfoVault` gagal → info lama dipertahankan (bila memungkinkan
  dengan fake repo; bila tidak, trace manual + catat batasannya);
  (4) filter pemilik — modelkan aturan creator-only di helper murni bila
  memungkinkan agar bisa diuji tanpa TDLib; bila cek TDLib tidak bisa
  diuji unit, catat batasannya + buktikan via review kode.
- Jalankan `./gradlew testDebugUnitTest` bila memungkinkan. Bila Gradle
  tidak jalan di sandbox (sudah 3x terbukti: daemon mati / tidak ada JDK),
  katakan sejujurnya — JANGAN klaim test lolos. Cek keseimbangan
  kurung/parens/bracket semua file baru/diubah.
- CI GitHub adalah gerbang verifikasi (pola proyek ini).

## P5 — Laporan
Format wajib: Active Skills / Current Phase / Deep Work / Micro-Verification /
Next Phase Readiness. Sebutkan file diubah/dibuat, hasil test + bukti perintah
(atau pengakuan jujur bila tidak jalan), apa yang belum terverifikasi, dan
cara uji perangkat: (1) buat vault baru lewat Kelola koleksi → buka Pengaturan
→ status harus langsung Forum siap tanpa ketuk Perbaiki; (2) simpan vault
channel rusak + punya satu grup forum milik sendiri → buka Pengaturan →
harus auto-adopt + pesan deteksi; (3) buka Pilih grup lama → hanya grup
buatan sendiri yang tampil, grup orang lain hilang; (4) buat vault baru
dengan nama yang sama dengan vault lama milik sendiri → tidak ada duplikat,
pesan "sudah ada, dipakai lagi".
