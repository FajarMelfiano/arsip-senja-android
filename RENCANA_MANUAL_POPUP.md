# RENCANA MANUAL — Popup penamaan koleksi per tipe + semua status jadi popup
# DRAFT — JANGAN kirim sebelum run12b diputuskan gagal. Bila run12b sukses, hapus diam-diam.
# Basis: HEAD 8352b7d (Build #30). Brief: BRIEF_POPUP_PENAMAAN_STATUS.md (tetap jadi acuan P1).
# Tidak ada kode diubah oleh rencana ini — hanya rencana.

## Kenapa rencana ini ada
Run11 (realtime) dan run12 (popup) sama-sama mati timeout tanpa output
(OPENCODE_EXIT:124, log 71 byte, git diff kosong). Run12b adalah percobaan
ketiga untuk popup. Bila run12b gagal dengan pola yang sama, jangan menunggu
stall keempat — kerjakan manual dari rencana ini. Keputusan relaunch-vs-manual
tetap milik user; rencana ini hanya membuat jalur manual bisa mulai langsung.

## Temuan terverifikasi vs HEAD 8352b7d (bukan asumsi dari brief)
1. `BilahPesan` dipakai 8 Screen + definisinya (grep, 2026-10-14):
   Dasbor (2 situs: baris 137 + 875 di LembarKoleksi), Unggah, Berkas,
   Tautan, Impor, Pengaturan (2 situs: 115 + 313), Login. Semua wajib pindah
   ke PopupStatus; sesudah itu hapus BilahPesan.kt bila 0 pemakaian tersisa.
2. `_pesan: StateFlow<String?>` ada di 6 ViewModel: Dasbor (56), Unggah (44),
   Berkas (70), Tautan (30), Impor (54), Pengaturan `_pesanVault` (63).
   LoginScreen memakai BilahPesan tapi TIDAK punya `_pesan` sendiri yang
   terlihat di grep ViewModel — cek LoginViewModel saat eksekusi (kemungkinan
   pesan login mengalir lewat state lain; jangan asumsikan polanya sama).
3. `DasborViewModel.buatKoleksi` (249) punya mesin retry: `permintaanTerakhir`
   + `buatVaultBaruDanCobaLagi()` (mengulang buat dengan data form yang sama).
   Loop per-tipe WAJIB mempertahankan ini PER ITEM antrean (satu
   PermintaanBuat per tipe), bukan satu permintaan global — kalau tidak,
   tombol "Buat vault baru & coba lagi" di popup galat akan mengulang tipe
   yang salah / mengulang semua tipe.
4. `ArsipRepository.buatKoleksi` (576) mengabaikan `nama` sebagai nama koleksi
   bila topik dipakai ulang: `nama = topik.nama` (topik yang dikembalikan
   `pastikanTopikDenganPerbaikanForum`). Jadi alur Buat per-tipe: untuk tiap
   tipe, panggil repo.buatKoleksi(namaDariPopup, ..., tipe=setOf(tipeItu)).
   Bila nama popup = nama topik lama → terikat ke topik lama (benar, sesuai
   brief). Bila nama diubah → topik baru. JANGAN menulis nama koleksi
   langsung ke DAO untuk melewati ini — satu jalur tulis tetap repo.
5. DUA titik masuk Tarik, bukan satu — keduanya butuh antrean penamaan:
   `DasborViewModel` tarik (~208, memanggil repo.tarikDaftarKoleksi) dan
   `ImporViewModel` tarik (~94). Brief P0 menyebut tarikDaftarKoleksi saja;
   jangan sampai hanya Dasbor yang dapat popup penamaan sementara tab Impor
   tetap memakai nama topik diam-diam.
6. `repo.tarikDaftarKoleksi()` (473) mengembalikan `List<KoleksiEntity>` TANPA
   info mana yang baru. Deteksi "baru tercakup" = bandingkan kunci
   (chatId, topicId) sebelum vs sesudah di ViewModel (snapshot sebelum tarik),
   ATAU ubah repo mengembalikan info baru — pilih snapshot di ViewModel dulu
   (perubahan repo paling kecil, tanpa mengubah signature yang dipakai
   tempat lain; cek dulu pemakai tarikDaftarKoleksi lain sebelum mengubah
   signature).
7. `repo.ubahKoleksi` (872) SUDAH ADA — pakai untuk rename hasil Tarik
   (nama baru + field lain dipertahankan). Jangan buat fungsi rename baru
   kecuali ubahKoleksi terbukti tidak bisa (baca dulu signature-nya).
8. `TipeFile.entries` urutan: GAMBAR, VIDEO, AUDIO, DOKUMEN, ARSIP, LAINNYA
   (util/TipeFile.kt) — urutan antrean popup Buat mengikuti ini persis.
9. `LembarKoleksi` di DasborScreen.kt:695; field "Nama koleksi baru" baris
   804; tombol "Buat koleksi" baris 866. Form bersama (deskripsi, label,
   warna, kunci, multi-select tipe) dipertahankan; hanya field nama dihapus.
10. Perhatian Build #30 (dari brief, tetap berlaku): field Room dengan
    `@ColumnInfo(defaultValue)` WAJIB juga punya default Kotlin bila
    konstruktor dipakai tanpa nilai itu (pelajaran nomorKatalog).

## Slice (urutan eksekusi manual)
- Slice 0 — Tipe `PesanStatus(nada, teks, aksiUlang)` + `PopupStatus.kt`
  (AlertDialog, token WarnaArsip/Fraunces/JakartaSans, ikon Lucide, tombol
  Mengerti, aksi kedua opsional "Buat vault baru & coba lagi") + unit test
  logika (nada dari ViewModel, tutup → null, pesan baru menimpa). Murni,
  tanpa Compose/TDLib di logikanya. Bisa jalan paralel dengan Slice 1.
- Slice 1 — `PopupNamaKoleksi.kt` + mesin antrean murni (item: tipe/prefill/
  konteks; aksi Simpan/Lewati/Batalkan-semua; validasi kosong + duplikat
  terhadap daftar nama koleksi yang ada, kecuali nama topik yang dipakai
  ulang itu sendiri) + unit test antrean (simpan→lanjut, lewati→lanjut tanpa
  simpan, batal→berhenti, sudah tersimpan tetap).
- Slice 2 — Migrasi `_pesan` → `PesanStatus` di 6 ViewModel + ganti semua
  BilahPesan di 8 Screen ke PopupStatus (termasuk error LembarKoleksi,
  pesanVault Pengaturan, galat kandidat, galat login). Pindahkan aksi
  "Buat vault baru & coba lagi" (DasborViewModel._tawarkanVaultBaru:241)
  menjadi tombol aksi kedua popup galat terkait. Teks galat per baris di
  antrean Unggah TETAP (kontekstual, bukan status). Toast "Disalin"
  CrashActivity dibiarkan.
- Slice 3 — Rombak LembarKoleksi: hapus field nama (804), tambah ringkasan
  "N koleksi akan dibuat, satu per tipe"; tap Buat → antrean penamaan per
  tipe yang dicentang → per item panggil repo.buatKoleksi dengan
  tipe=setOf(tipeItu) + PermintaanBuat per item; sesudah antrean habis →
  popup status ringkasan. "Buatkan topik" (perbaikiTopikHilang) TANPA popup
  penamaan, lalu popup status hasilnya.
- Slice 4 — Tarik di KEDUA titik masuk (Dasbor + Impor): snapshot kunci
  sebelum tarik → entri baru → antrean penamaan (prefill nama topik/chat)
  → Simpan = ubahKoleksi rename, Lewati = pertahankan nama topik,
  Batalkan = sisanya pakai nama topik. Entri sudah tercakup: tanpa popup,
  nama tidak pernah ditimpa. Tarik tidak pernah terblokir popup (tutup layar
  di tengah antrean → sisa tetap tersimpan dengan nama topik).
- Slice 5 — Popup FAILED terminal antrean Unggah (sekali per pekerjaan, dari
  UploadWorker outputData, bukan per percobaan retry) + login via PopupStatus.
- Slice 6 — Verifikasi + gerbang rilis: kurung per file = 0 selisih,
  `git diff --check` bersih, grep TODO/FIXME/XXX/placeholder/lorem di
  app/src/main + app/src/test kosong, grep BilahPesan sisa = 0; test unit
  baru (antrean, baru-tercakup dari kunci (chatId,topicId) bukan nama,
  PesanStatus); 6 skenario brief P4 (a–f) dibaca dari kode dan ditulis di
  laporan. JANGAN commit/push sebelum verifikasi Muse; JANGAN ubah CI /
  keystore / versionCode / secret; JANGAN sentuh realtime Build #31.

## Larangan (dari brief, dibawa utuh)
JANGAN ubah PK KoleksiEntity; JANGAN migrasi Room; JANGAN ubah logika upload /
vault / kunci / pemisahan pribadi-impor; JANGAN buat koleksi dari chat tak
dikenal; JANGAN hapus data Telegram/koleksi/berkas otomatis; JANGAN tambah
TODO/placeholder/emoji-ikon; JANGAN klaim terbukti di perangkat sebelum user
menguji; JANGAN cetak secret/token/OTP.
