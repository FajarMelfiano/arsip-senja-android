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

# BRIEF run11 — Build #31: Semuanya sangat realtime beneran (event-driven, hemat baterai)

Repo: `~/workspace/arsip-senja-android`, branch `main`. HEAD saat brief ditulis:
`8352b7d` (Build #30 selesai penuh, CI sukses, release fresh). Kerja di atas HEAD
ini. Satu sesi planning + implementasi, seperti run-run sebelumnya.

Perintah user (dikutip apa adanya): "buat semuanya sangat realtime beneran".
Artinya: tidak ada lagi status basi, tidak ada lagi tombol Sinkron sebagai
satu-satunya jalan — perubahan di Telegram harus muncul sendiri di aplikasi
begitu terjadi, selama aplikasi berjalan. Tanpa polling boros, tanpa klaim
realtime 24 jam yang tidak benar.

## P0 — Diagnosis (temuan Muse dari pembacaan kode, VERIFIKASI sebelum coding)

### Yang SUDAH realtime (jangan dirusak, jangan ditulis ulang)
- `DasborViewModel`: `berkas` / `koleksi` / `statistik` = Room Flow
  (`alirKoleksiPribadi()`, `alirStatistikPribadi()`, `alirBerkas()`).
- `ImporViewModel.impor` = `alirKoleksiImpor()` Flow.
- `UnggahViewModel.antrean` = `WorkManager.getWorkInfosByTagFlow(TAG_UNGGAH)`;
  progres transfer dari `telegram.progresBerkas` (SharedFlow).
- `PengaturanViewModel`: `infoVault` di-refresh tiap kali Pengaturan ditampilkan
  (`LaunchedEffect` → `muatInfoVault()`).

### Gap 1 — TERBESAR: `pesanBaru` (UpdateNewMessage) TANPA KONSUMEN
- `TelegramClient` sudah meng-emit `pesanBaru` (`UpdateNewMessage` →
  `_pesanBaru.tryEmit(obj.message)`), tetapi grep membuktikan TIDAK ADA
  konsumen di luar `TelegramClient` (yang dipakai pihak lain hanya
  `progresBerkas`, `pesanTerkirim` tidak dipakai, `pesanBaru` tidak dipakai).
- Akibat: berkas baru di Telegram (dikirim dari perangkat lain, di-upload di
  luar aplikasi, diteruskan ke topik) TIDAK muncul sampai user menekan
  Sinkron manual.

### Gap 2 — Sinkron hanya manual
- Pemanggil sinkron hanya: `DasborViewModel` (sinkron / sinkronTerpilih),
  `PengaturanViewModel.sinkronSemua()`, `ImporViewModel` (sinkron / tarik),
  dan sesudah unggah. Tidak ada auto-sync saat pesan baru, saat aplikasi
  kembali foreground, atau saat koneksi pulih.
- `TelegramClient.tanganiUpdate`: `UpdateConnectionState` → `Unit` (diabaikan).

### Gap 3 — Status vault hanya segar saat Pengaturan dibuka
- Tidak bereaksi bila chat vault berubah di Telegram (judul diubah, forum
  dimatikan, grup dihapus) selama aplikasi berjalan.

### Gap 4 — Topik/koleksi baru dari Telegram tidak terdeteksi
- Topik yang dibuat dari Telegram langsung tidak muncul sampai Tarik manual.

## P1 — Desain realtime (event-driven + debounce + indikator)

1. **Konsumen `pesanBaru` di repository (SATU tempat, jangan di tiap ViewModel):**
   filter hanya pesan di chat/topik koleksi yang SUDAH dikenal (pribadi +
   impor). Pesan di chat tak dikenal DIABAIKAN — jangan pernah membuat
   koleksi liar dari chat asing. Debounce/kumpulkan pesan beruntun (jendela
   singkat 1–3 detik, tentukan angkanya + uji). Ingest per pesan lewat jalur
   yang sama dengan sinkron: dedupe id `"$chatId:$messageId"`, pertahankan
   metadata lokal (label/warna/tipe/kunci). JANGAN tarik seluruh riwayat tiap
   pesan masuk.
2. **`UpdateConnectionState` → Ready:** sinkron ringan tertunda + debounce
   (hanya bila terakhir sinkron sudah melewati ambang, mis. 60 detik —
   tentukan + uji). Jangan spam saat koneksi flapping putus-pulih berulang.
3. **Update chat/topik relevan** (`UpdateChatTitle`, `UpdateForumTopicInfo`,
   dan update sejenis yang TERBUKTI ada di TDLib ini — cek, jangan asumsikan):
   refresh entri terkait saja, bukan full resync.
4. **Aplikasi kembali foreground** (pilih SATU: `ProcessLifecycleOwner`
   ON_START atau ON_RESUME di `MainActivity`, jelaskan pilihanmu): cek
   sinkron ringan sekali bila terakhir sinkron sudah melewati ambang.
5. **UI jujur dan hidup:** indikator "Menyinkronkan…" saat sinkron berjalan +
   "Terakhir diperbarui HH:mm" di Dasbor + Pengaturan; status koneksi
   (Menghubungkan / Tersambung) bila relevan. String Bahasa Indonesia, ikon
   Lucide, token `WarnaArsip` / `Fraunces` / `JakartaSans`.
6. **Hemat baterai (WAJIB — HP user sering low-batt):** event-driven +
   debounce. JANGAN polling tiap detik/menit. JANGAN foreground service
   permanen. JANGAN WorkManager periodic agresif. Satu jaring pengaman
   periodic ringan (interval minimal WorkManager 15 menit) BOLEH hanya bila
   dibuktikan perlu + berhenti bila tidak ada perubahan; bila event-driven
   cukup, JANGAN tambah periodic — putuskan dengan alasan tertulis.
7. **Batasan jujur (tulis di laporan + tampilkan di UI):** TDLib client hanya
   hidup saat proses aplikasi berjalan — realtime penuh saat aplikasi dibuka /
   proses hidup; saat aplikasi dimatikan total, sinkron tertunda sampai
   dibuka lagi. Tampilkan mis. "Sinkron otomatis aktif saat aplikasi
   berjalan". Jangan klaim realtime 24 jam.

## P2 — Arsitektur
- Logika realtime di `ArsipRepository` (satu tempat); ViewModel hanya collect
  Flow. Jangan duplikasi ingest di tiap ViewModel.
- Pemisahan pribadi/impor tetap lewat `PenandaImpor`; gating kunci tetap
  (koleksi terkunci tidak dibuka otomatis oleh sinkron).
- Upload JANGAN diubah kecuali bila perlu untuk konsistensi status — bila
  diubah, jelaskan alasannya.
- Timestamp terakhir sinkron: simpan di `PengaturanStore` atau field repo
  (pilih, jangan tambah migrasi Room bila tidak perlu; bila perlu migrasi,
  naikkan versi + `ALTER` eksplisit, JANGAN `fallbackToDestructiveMigration`).
- Tombol Sinkron/Tarik TETAP ADA sebagai jalan manual. Jangan hapus.

## P3 — Implementasi
- Konsumen `pesanBaru` + debounce + ingest per pesan (filter + dedupe).
- Handler `UpdateConnectionState` Ready + handler update chat/topik terkait.
- Trigger foreground (ON_START / ON_RESUME, satu pilihan).
- Indikator "Menyinkronkan…" + "Terakhir diperbarui HH:mm" + status koneksi.
- Pisahkan logika murni (filter pesan, dedupe, aturan debounce/ambang) agar
  bisa di-unit-test tanpa TDLib.

## P4 — Verifikasi
- Cek kurung per file Kotlin yang diubah = 0; `git diff --check` bersih;
  grep TODO/FIXME/XXX/placeholder di `app/src` kosong.
- `git diff --stat` + review diff sendiri sebelum menyerahkan.
- `./gradlew testDebugUnitTest` bila `java` ada — bila tidak ada, JANGAN klaim
  lolos, CI adalah gerbang.
- Test baru untuk filter pesan / dedupe / aturan debounce-ambang.
- 6 skenario (nilai dari kode, jujur bila belum di perangkat):
  (a) pesan baru di topik dikenal → berkas muncul tanpa tekan Sinkron;
  (b) pesan di chat tak dikenal → diabaikan, tidak ada koleksi liar;
  (c) pesan duplikat → tidak dobel;
  (d) koneksi putus-pulih berulang → satu sinkron ringan, bukan spam;
  (e) kembali foreground → refresh bila perlu, tidak spam bila baru sinkron;
  (f) vault diubah/dihapus di Telegram → status vault ikut berubah.

## P5 — Readiness
- Laporan format wajib: Active Skills / Current Phase / Deep Work /
  Micro-Verification / Next Phase Readiness.
- Cantumkan: ambang debounce + ambang sinkron yang dipilih + alasannya;
  batasan realtime (proses hidup) + kalimat UI-nya; instruksi uji perangkat
  (kirim berkas dari perangkat lain ke topik → lihat muncul sendiri).

## LARANGAN KERAS
- JANGAN commit/push — Muse yang verifikasi + commit/push.
- JANGAN ubah workflow CI / keystore / `versionCode` / secret.
- JANGAN hapus data Telegram user otomatis; JANGAN hapus koleksi/berkas
  lokal otomatis; JANGAN buat koleksi dari chat tak dikenal.
- JANGAN polling agresif / foreground service permanen / periodic boros.
- Jangan klaim selesai tanpa bukti; jangan klaim terbukti di perangkat
  sebelum user menguji; jangan cetak secret/token/OTP; jangan tambah TODO
  tersembunyi / placeholder malas.
