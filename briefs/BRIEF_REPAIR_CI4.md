# BRIEF_REPAIR_CI4 — Perbaiki test duplikat yang salah desain

Konteks: CI run 38887456246, `:app:testDebugUnitTest` gagal 1 dari 234 test:
`AturanDuplikatTest.bedaNamaUkuranTopikAtauKoleksiTidakDikelompokkan` FAILED
(AssertionError di AturanDuplikatTest.kt:107: `grup.isEmpty()`).

## Diagnosis (SUDAH DIVERIFIKASI, jangan diubah)

- `AturanDuplikat.kelompokkan()` memakai kunci
  `Triple(it.koleksiId ?: it.chatId, it.topicId, it.nama to it.ukuran)`.
  KODE PRODUKSI SUDAH BENAR — jangan diubah.
- Test yang gagal memanggil helper `buatBerkas()` dengan `chatId` berbeda
  (-1001 vs -1002) untuk menguji "beda koleksi", tetapi helper punya default
  `koleksiId: Long? = -100123L` untuk SEMUA berkas. Karena `koleksiId` tidak null,
  kunci memakai `koleksiId` (-100123, sama untuk semua) BUKAN `chatId`, sehingga
  b1 dan b5 (nama+ukuran+topik sama) benar-benar dikelompokkan → `grup.isEmpty()`
  gagal. Yang salah adalah TEST, bukan kode produksi.

## Tugas

1. Di `app/src/test/java/com/arsipsenja/data/repo/AturanDuplikatTest.kt`,
   fungsi `bedaNamaUkuranTopikAtauKoleksiTidakDikelompokkan` SAJA:
   tambahkan argumen `koleksiId = null` ke SEMUA lima pemanggilan `buatBerkas()`
   di fungsi itu. Dengan koleksiId null, kunci duplikat jatuh ke `chatId`
   (fallback desain untuk koleksi yatim) sehingga test benar-benar menguji:
   b1 vs b2 beda nama, b1 vs b3 beda ukuran, b1 vs b4 beda topik,
   b1 vs b5 beda chatId → hasil harus kosong.

2. JANGAN mengubah: kode produksi, helper `buatBerkas()` (default-nya),
   fungsi test lain, atau file test lain.

3. Jalankan `git diff` dan pastikan hanya AturanDuplikatTest.kt yang berubah,
   hanya 5 baris yang bertambah argumen `koleksiId = null`.
   JANGAN commit, JANGAN push, JANGAN membuat PR.
