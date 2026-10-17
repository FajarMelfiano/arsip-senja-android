# BRIEF REPAIR 2 — Perbaiki referensi KandidatVault di UI (CI run 38886130802 gagal)

## KONTEKS
PR #3 (branch `fitur/duplikat-vault-impor`). Repair sebelumnya menghapus
`typealias KandidatVault` yang bersarang di dalam class `ArsipRepository`
(Kotlin tidak mendukung typealias bersarang). Ternyata dua file UI merujuk ke
`ArsipRepository.KandidatVault` — semua error CI sekarang berasal dari referensi
rusak ini (termasuk error-error turunan di PengaturanScreen.kt baris 115, 662+).

Data class aslinya ada di:
`app/src/main/java/com/arsipsenja/data/repo/AturanDeteksiVault.kt`
(package `com.arsipsenja.data.repo`, deklarasi: `data class KandidatVault(...)`).

## ATURAN AGEN (wajib, dari ~/workspace/opencode/AGENTS.md)
- Matikan Plan Mode. Jangan push / PR / merge / deploy — hanya perbaiki working tree.
- Jangan menambah fitur, jangan mengubah perilaku, jangan menambah emoji, jangan ubah skema DB.
- Akhiri dengan LAPORAN AKHIR: file+baris yang diubah, status verifikasi
  (kompilasi lokal tak dapat dijalankan di sandbox ini karena Gradle daemon
  gagal IPC — cukup inspeksi cermat bahwa semua referensi kini valid, lalu
  delegasikan ke CI).

## PERBAIKAN

### 1. `app/src/main/java/com/arsipsenja/ui/viewmodel/PengaturanViewModel.kt`
- Tambahkan import: `import com.arsipsenja.data.repo.KandidatVault`
  (sejajarkan dengan import `com.arsipsenja.data.repo.*` yang sudah ada).
- Baris ~58: `MutableStateFlow<List<ArsipRepository.KandidatVault>>(emptyList())`
  → `MutableStateFlow<List<KandidatVault>>(emptyList())`
- Baris ~59: `val kandidatVault: StateFlow<List<ArsipRepository.KandidatVault>>`
  → `val kandidatVault: StateFlow<List<KandidatVault>>`
- Pastikan tidak ada lagi kemunculan string `ArsipRepository.KandidatVault` di file ini.

### 2. `app/src/main/java/com/arsipsenja/ui/screens/PengaturanScreen.kt`
- Tambahkan import: `import com.arsipsenja.data.repo.KandidatVault`
  (di kelompok import `com.arsipsenja.*`).
- Baris ~604: `kandidat: List<ArsipRepository.KandidatVault>,`
  → `kandidat: List<KandidatVault>,`
- Pastikan tidak ada lagi kemunculan string `ArsipRepository.KandidatVault` di file ini.

### 3. Verifikasi silang (WAJIB sebelum selesai)
- `grep -rn "ArsipRepository.KandidatVault" app/src` HARUS kosong.
- `grep -rn "typealias KandidatVault" app/src` HARUS kosong (alias sudah dihapus permanen).
- `data class KandidatVault` tetap SATU definisi di `AturanDeteksiVault.kt`.
- Fungsi `repo.daftarKandidatVault()` di `ArsipRepository` mengembalikan
  `List<KandidatVault>` (data class asli, package sama — tanpa import tambahan).

Catatan: error CI di baris 115 (`firstOrNull { it.chatId ... }`) dan baris 662+
(`chatId`, `nama`, `forumSiap` tak dikenal) adalah EFEK DOMINO dari tipe
`List<ArsipRepository.KandidatVault>` yang rusak — semuanya sembuh otomatis
setelah dua perbaikan di atas, tidak perlu disentuh satu per satu.
