# BRIEF: Perbaiki error "Forums can be enabled in supergroups only" saat buat koleksi/topik + jelaskan fungsi vault

## Konteks
Aplikasi: Arsip Senja Android, repo di direktori ini, branch `main`.
Pengguna melaporkan error saat membuat koleksi (screenshot, 14 Okt 2026 ~12:01 WIB):
> "Gagal membuat koleksi: Vault belum mode forum dan aktivasi otomatis gagal
> (Forums can be enabled in supergroups only). Pastikan kamu pemilik grup,
> lalu pilih ulang vault di Pengaturan."

Ini adalah error BARU dari perbaikan Build #21 (commit 956e016). Sebelumnya
errornya "The chat is not a forum". Artinya: perbaikan self-healing Build #21
berjalan, tetapi menabrak dinding baru.

## Analisis akar masalah (sudah diverifikasi dari kode, jangan ulangi riset ini)
1. `ArsipRepository.pastikanTopikDenganPerbaikanForum` (ArsipRepository.kt,
   ~baris 296) menangkap galat "not a forum" dari `telegram.pastikanTopik`,
   lalu memanggil `telegram.aktifkanForum(vault)`.
2. `aktifkanForum` memanggil `TdApi.ToggleSupergroupIsForum`. Telegram
   menjawab "Forums can be enabled in supergroups only".
3. KESIMPULAN: **vault yang tersimpan saat ini BUKAN supergroup**
   (kemungkinan besar channel lama atau chat tipe lain dari versi lama
   aplikasi). ToggleSupergroupIsForum hanya berlaku untuk supergroup,
   sehingga aktivasi otomatis PASTI gagal untuk vault ini.
4. `daftarKandidatVault` sudah benar: `TelegramClient.daftarSupergroup`
   memfilter `ChatTypeSupergroup && !isChannel`. Jadi vault rusak ini TIDAK
   berasal dari pemilih grup di Pengaturan — ia adalah vault basi (stale)
   yang tersimpan sebelum validasi ada.
5. Cacat turunan: pesan error saat ini menyesatkan ("Pastikan kamu pemilik
   grup...") — padahal masalahnya bukan kepemilikan, melainkan TIPE chat
   vault yang salah. Pengguna juga bingung apa fungsi vault di Pengaturan.

## Yang harus dikerjakan
1. **Validasi tipe vault di semua jalur yang memakai vault:**
   - Tambahkan pemeriksaan tipe chat vault via `TdApi.GetChat` (atau API
     TDLib yang setara): vault sah HANYA bila `type is
     TdApi.ChatTypeSupergroup && !isChannel`.
   - Terapkan di: `pilihVault`, `pastikanTopikDenganPerbaikanForum`
     (dipakai `buatKoleksi` + `pastikanTopikKoleksi`), dan idealnya satu
     helper bersama, mis. `pastikanVaultSah(vault): Boolean`/throw.
2. **Bedakan dua kegagalan yang selama ini tercampur:**
   - Vault ADALAH supergroup tapi belum forum → aktifkan forum otomatis
     lalu coba lagi (perilaku Build #21, pertahankan).
   - Vault BUKAN supergroup → JANGAN panggil `aktifkanForum` (pasti gagal).
     Masuk ke jalur vault-tidak-sah di bawah.
3. **Pemulihan vault tidak sah (self-healing, perilaku utama yang diminta):**
   - Bila vault tersimpan bukan supergroup DAN tidak ada koleksi terdaftar
     yang memakai vault itu (cek via `koleksiDao`), anggap vault basi:
     hapus vault tersimpan, buat vault forum baru otomatis via
     `buatVaultBaru()` (nama bawaan "Arsip Senja"), lalu lanjutkan pembuatan
     koleksi/topik tanpa meminta tindakan pengguna.
   - Bila vault tersimpan bukan supergroup TAPI ada koleksi terdaftar yang
     memakainya: JANGAN buang diam-diam (akan meng orphan koleksi).
     Lempar galat yang jelas dan bisa ditindaklanjuti, mis.:
     "Vault yang tersimpan bukan grup (tipe: channel/lainnya), sehingga
     topik tidak bisa dibuat. Pilih grup lain atau buat vault baru di
     Pengaturan > Vault Telegram." Sertakan tipe chat sebenarnya bila
     tersedia dari GetChat.
4. **Perbaiki pesan error di semua jalur terkait** agar menyebut masalah
   sebenarnya (tipe vault salah) dan langkah konkret, bukan "pastikan kamu
   pemilik grup" bila bukan itu masalahnya. Pesan tetap Bahasa Indonesia.
5. **Jelaskan fungsi vault di UI Pengaturan** (pengguna bertanya "vault itu
   gunanya apa"): di bagian "Vault Telegram" di `PengaturanScreen.kt`,
   tambahkan 1–2 kalimat penjelasan, mis.: vault adalah grup Telegram
   tempat semua koleksi disimpan sebagai topik; tiap berkas yang diunggah
   disimpan sebagai pesan di dalam topik koleksinya. Tanpa vault yang sah,
   koleksi tidak bisa dibuat. Gunakan gaya bahasa yang sama dengan teks
   yang sudah ada. JANGAN tambah emoji sebagai ikon.
6. **Jangan ubah perilaku yang sudah benar:** pemilih grup tetap hanya
   menampilkan supergroup; `Buat vault baru` tetap membuat supergroup
   forum; rute upload otomatis, label, warna, tipe kategori tidak disentuh.

## Pengujian (wajib)
- Tambahkan unit test baru (di samping `KoleksiKategoriTest` yang sudah
  ada) yang membuktikan:
  a. Vault bukan supergroup + tanpa koleksi → vault basi diganti vault
     baru otomatis dan pembuatan koleksi lanjut.
  b. Vault bukan supergroup + ada koleksi terdaftar → galat jelas, vault
     lama TIDAK dibuang.
  c. Vault supergroup belum forum → aktivasi forum + coba lagi (regresi
     Build #21 tetap jalan).
- Jalankan unit test lokal bila memungkinkan (`./gradlew testDebugUnitTest`
  atau setara). Bila Gradle tidak bisa jalan di sandbox (RAM kecil), katakan
  sejujurnya di laporan dan pastikan kode setidaknya konsisten secara
  sintaks dengan pola yang sudah ada (tipe, import, nama fungsi).

## Aturan
- Bahasa Indonesia untuk semua teks pengguna.
- Tanpa emoji sebagai ikon UI.
- Perubahan minimal dan terfokus: jangan refactor file lain, jangan ubah
  skema Room, jangan ubah workflow CI.
- JANGAN commit atau push. Serahkan perubahan sebagai working tree yang
  sudah diuji; Muse yang akan commit/push setelah verifikasi independen.

## Format laporan akhir
Tulis laporan singkat: file yang diubah (dengan ringkasan per file), hasil
test (lolos/gagal + bukti perintah yang dijalankan), dan apa yang belum
terverifikasi (butuh perangkat nyata / akun Telegram pengguna).
