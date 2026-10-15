# Rencana manual Build #31 — Realtime event-driven (tanpa OpenCode)

**Status:** RENCANA, bukan kode. Disusun 2026-10-14 ~17:55 WIB setelah run11
OpenCode timeout tanpa output (OPENCODE_EXIT:124, `git diff` kosong, HEAD tetap
`8352b7d` = Build #30). Dipakai bila user memilih jalur **manual**; bila user
memilih luncurkan ulang OpenCode, abaikan file ini.

**Sumber:** `BRIEF_REALTIME.md` (scope penuh) + pembacaan kode langsung
14 Okt 2026. Semua acuan file/l baris diverifikasi ke HEAD `8352b7d`.

## Fakta dasar yang sudah diverifikasi (bukan asumsi)

1. `TelegramClient.tanganiUpdate` (`data/telegram/TelegramClient.kt:154-181`):
   `UpdateNewMessage` → `_pesanBaru.tryEmit(...)`, `UpdateConnectionState` → `Unit`
   (dibuang). Tidak ada konsumen `pesanBaru` di luar `TelegramClient`.
2. `ArsipRepository` (`data/repo/ArsipRepository.kt`, 1193 baris) **tidak punya
   `CoroutineScope`** — kolektor realtime tidak bisa "tinggal dicolok" di repo.
   Scope harus dimiliki `AppContainer` (`ArsipSenjaApp.kt`) dan dimulai di
   `mulaiSesi()`.
3. `lifecycle-process` (`ProcessLifecycleOwner`) **tidak ada** di dependensi
   (`app/build.gradle.kts:101-133`, `gradle/libs.versions.toml`). Opsi foreground
   tanpa dependensi baru: `MainActivity.onResume()` → repo. Jangan tambah
   dependensi hanya untuk ini.
4. `PengaturanStore` **tidak punya** kunci `terakhirSinkron` — dan **tidak perlu
   ditambah**. `KoleksiEntity.terakhirSinkron` per koleksi sudah ada dan sudah
   di-update `sinkronKoleksi()` (`ArsipRepository.kt:412-419`). Indikator global
   "Terakhir diperbarui HH:mm" = MAX(`terakhirSinkron`) dari Flow koleksi.
   → Tanpa migrasi Room.
5. `StatusOtorisasi` (`data/telegram/StatusOtorisasi.kt`) hanya status OTORISASI
   (Menyiapkan/Siap/Galat/…). Tidak ada status KONEKSI (Menghubungkan/Tersambung).
   Status koneksi butuh `StateFlow` baru di `TelegramClient` dari
   `UpdateConnectionState`.
6. Jalur ingest yang bisa dipakai ulang: `tarikRiwayatBaru()`
   (`ArsipRepository.kt:421-471`) — dedupe via `BerkasArsipEntity.kunci(chatId,
   messageId)` (`"$chatId:$messageId"`), konversi via `keBerkasMentah()` →
   `keEntity()` (`data/telegram/PemetaTelegram.kt`). Per-pesan ingest harus
   memakai helper yang SAMA (ekstrak dari `tarikRiwayatBaru`, jangan duplikasi).
7. `BerkasMentah` (`PemetaTelegram.kt:8-23`) tidak membawa `topicId` — pemetaan
   pesan → koleksi `(chatId, topicId)` adalah pekerjaan Slice 0/3, bukan gratis.

## Satu pertanyaan penentu (harus terjawab di Slice 0, sebelum Slice 3)

**Dari field mana `topicId` diturunkan dari `TdApi.Message`?**
Kandidat kuat (TDLib ≥1.7): `message.messageThreadId` (Long; = topicId untuk
pesan topik) + `message.isTopicMessage` (Boolean). AAR TDLib tidak ada di cache
lokal sandbox ini, jadi **belum terbukti** untuk build TDLib yang dipakai
(`gradle/libs.versions.toml`: `tdlib = 11850efeb5791d4fd386c0eeec1ebce4c5080435`).
Probe: file scratch yang mereferensikan kedua field → biar CI yang menjawab
lewat kompilasi (lokal Gradle mustahil: RAM). **Bila field tidak ada**,
fallback Slice 3: pesan di vault chat → debounce → `tarikRiwayatBaru` hanya
untuk topik-topik yang dikenal di vault itu (dedupe membuat sebagian besar
menjadi no-op); JANGAN pernah membuat koleksi dari chat tak dikenal.

## Irisan (satu irisan = satu turn kerja, satu commit, satu verifikasi CI)

### Slice 0 — Probe + fondasi (tanpa mengubah perilaku)
- Probe field `messageThreadId` / `isTopicMessage` (scratch, hapus setelah CI
  menjawab). Catat hasilnya di file ini.
- Tambah `StateFlow` status koneksi di `TelegramClient` dari
  `UpdateConnectionState` (Connecting/Ready/… → `Menghubungkan`/`Tersambung`).
- Verifikasi: `git diff --check`, kurung per file = 0.

### Slice 1 — Logika murni + unit test (tanpa TDLib, tanpa UI)
- File baru `data/repo/AturanRealtime.kt`: filter pesan (hanya `(chatId, topicId)`
  yang dikenal; chat asing → abaikan), dedupe id `"$chatId:$messageId"`,
  aturan debounce (jendela 1–3 dtk, pilih angka + alasan tertulis) dan ambang
  sinkron (60 dtk, sesuai brief).
- Test baru `AturanRealtimeTest.kt`: (a) pesan topik dikenal lolos,
  (b) chat asing ditolak, (c) duplikat ditolak, (d) flapping koneksi →
  satu sinkron, (e) foreground < ambang → tidak sinkron.
- Ini irisan paling aman dan bisa jalan DULUAN, apa pun hasil Slice 0.

### Slice 2 — Konsumen `pesanBaru` di repository
- `AppContainer` memiliki `CoroutineScope(SupervisorJob + Dispatchers.IO)`,
  dimulai di `mulaiSesi()`; `ArsipRepository.mulaiPengamatRealtime(scope)`.
- Konsumen: kumpulkan `pesanBaru` (debounce Slice 1) → filter ke koleksi yang
  dikenal (pribadi + impor, dari `koleksiDao`) → ingest per pesan via helper
  bersama dengan `tarikRiwayatBaru` (dedupe + pertahankan metadata lokal
  label/warna/tipe/kunci). JANGAN tarik seluruh riwayat per pesan.
- `UpdateConnectionState` → Ready: sinkron ringan tertunda + debounce, hanya
  bila terakhir sinkron > ambang. Jangan spam saat flapping.
- Update chat/topik relevan (`UpdateChatTitle`, `UpdateForumTopicInfo`,
  dst. — hanya yang TERBUKTI ada di TDLib ini saat kompilasi): refresh entri
  terkait saja, bukan full resync.

### Slice 3 — Trigger foreground
- `MainActivity.onResume()` → `repo.cekSinkronRingan()`: sinkron ringan sekali
  bila terakhir sinkron > ambang. Satu pilihan saja (ON_RESUME), sesuai
  temuan dependensi di atas. Jelaskan pilihan di laporan.

### Slice 4 — Indikator UI (jujur, Bahasa Indonesia, Lucide)
- Dasbor + Pengaturan: indikator "Menyinkronkan…" saat sinkron berjalan +
  "Terakhir diperbarui HH:mm" dari MAX(`terakhirSinkron`) + status koneksi.
- Kalimat batasan tampil di UI: "Sinkron otomatis aktif saat aplikasi berjalan".
  Jangan klaim realtime 24 jam.
- Tombol Sinkron/Tarik TETAP ADA. Upload JANGAN diubah.

### Slice 5 — Verifikasi + rilis (gerbang CI, seperti build-build sebelumnya)
- `git diff --check` bersih; grep TODO/FIXME/XXX/placeholder di `app/src` kosong.
- `./gradlew testDebugUnitTest` via CI (lokal tidak bisa); 6 skenario brief
  (a)–(f) dinilai dari kode, jujur bila belum di perangkat.
- Uji perangkat (user): kirim berkas dari perangkat lain ke topik → muncul
  sendiri tanpa tekan Sinkron; vault diubah/dihapus di Telegram → status ikut
  berubah.
- Larangan keras (dari brief): JANGAN commit/push sebelum verifikasi Muse;
  JANGAN ubah workflow CI / keystore / `versionCode` / secret; JANGAN hapus
  data Telegram/koleksi/berkas lokal otomatis; JANGAN polling agresif /
  foreground service permanen / periodic boros; JANGAN buat koleksi dari chat
  tak dikenal; JANGAN klaim terbukti di perangkat sebelum user menguji.

## Yang TIDAK berubah dari brief
Scope, ambang yang belum diputuskan (debounce 1–3 dtk, ambang 60 dtk — pilih +
uji di Slice 1), 6 skenario verifikasi, dan format laporan (Active Skills /
Current Phase / Deep Work / Micro-Verification / Next Phase Readiness).
