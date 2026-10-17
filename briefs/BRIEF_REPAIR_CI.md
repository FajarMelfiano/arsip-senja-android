# BRIEF REPAIR — Perbaiki 9 compile error (CI run 38885262575 gagal)

## KONTEKS
PR #3 (branch `fitur/duplikat-vault-impor`) gagal CI pada task `:app:compileDebugKotlin`
dengan 9 error. Tugasmu: perbaiki SEMUA error kompilasi di bawah, TIDAK menambah
fitur, TIDAK mengubah perilaku. Jangan sentuh file lain kecuali yang disebut.

## ATURAN AGEN (wajib, dari ~/workspace/opencode/AGENTS.md)
- Matikan Plan Mode. Muat skill `brainstorming` bila perlu merancang (tidak perlu di sini).
- Jangan push / PR / merge / deploy. Hanya perbaiki kode di working tree.
- Jangan menambah emoji ke UI. Jangan mengubah skema database.
- Verifikasi akhir: `./gradlew compileDebugKotlin` HARUS sukses di akhir
  (Catatan: Gradle daemon gagal IPC di sandbox ini — bila daemon tidak bisa
  jalan, verifikasi cukup dengan inspeksi kode cermat: pastikan setiap
  referensi yang diperbaiki benar-benar ada di codebase, lalu laporkan bahwa
  kompilasi lokal tidak dapat dijalankan di lingkungan ini dan delegasikan ke CI).
- Akhiri dengan LAPORAN AKHIR: daftar file yang diubah + barisnya, dan status verifikasi.

## PERBAIKAN (5 lokasi, 9 error)

### 1. ArsipRepository.kt baris ~121 — typealias bersarang tidak didukung
```kotlin
    /** Kandidat grup vault untuk dipilih pengguna di Pengaturan. */
    typealias KandidatVault = com.arsipsenja.data.repo.KandidatVault
```
HAPUS kedua baris di atas (KDoc + typealias). Alasan: `ArsipRepository.kt` dan
`AturanDeteksiVault.kt` berada di package yang SAMA (`com.arsipsenja.data.repo`),
sehingga `KandidatVault` sudah langsung terlihat tanpa alias. Pastikan tidak ada
sisa referensi ke alias yang dihapus (semua pemakaian `KandidatVault` tetap valid
karena merujuk ke data class asli).

### 2. ArsipRepository.kt baris ~762 — referensi tak dikenal
```kotlin
            } else if (PenandaImpor.adalahImpor(entitasKoleksi)) {
```
Fungsi `adalahImpor` TIDAK ADA di `PenandaImpor`
(`app/src/main/java/com/arsipsenja/data/local/PenandaImpor.kt`).
Ganti baris itu menjadi:
```kotlin
            } else if (entitasKoleksi.asalImpor) {
```
(`KoleksiEntity.asalImpor: Boolean` menandai koleksi impor; `entitasKoleksi`
sudah non-null di cabang ini.)

### 3. ArsipRepository.kt baris ~1666-1668 (fun pastikanUkuran) — referensi tak dikenal
```kotlin
        val ukuranSegar = runCatching {
            telegram.ambilFile(b.tdFileId).ukuranTotal()
        }.getOrNull() ?: 0L
```
`TdApi.File` TIDAK punya `ukuranTotal()`; ukuran ada di field `size: Long`.
Ganti menjadi:
```kotlin
        val ukuranSegar = runCatching {
            telegram.ambilFile(b.tdFileId).size
        }.getOrNull() ?: 0L
```

### 4. UploadWorker.kt baris ~194-196 — masalah yang sama
```kotlin
                        val ukuranSegar = runCatching {
                            telegram.ambilFile(mentah.tdFileId).ukuranTotal()
                        }.getOrNull() ?: 0L
```
Ganti `.ukuranTotal()` menjadi `.size` (field `TdApi.File.size: Long`).

### 5. PengaturanScreen.kt baris ~791 — token warna tak dikenal
```kotlin
                                color = WarnaArsip.Latar,
```
`WarnaArsip` TIDAK punya `Latar`. Token yang tersedia antara lain:
`Kanvas, KanvasDalam, Veil, Permukaan, Permukaan2, Permukaan3, GarisRambut,
GarisKuat, Amber, ..., Tinta, Tinta2, Tinta3, Bahaya`
(lihat `app/src/main/java/com/arsipsenja/ui/theme/Color.kt`).
Ganti `WarnaArsip.Latar` menjadi `WarnaArsip.Permukaan` — konsisten dengan
kartu/surface lain di layar ini (cek pemakaian `WarnaArsip.Permukaan` di file
yang sama untuk memastikan konsistensi).

## SELESAI
Laporkan: 5 lokasi diperbaiki, file+baris yang diubah, dan hasil verifikasi
kompilasi (atau alasan bila kompilasi lokal tak dapat dijalankan).
