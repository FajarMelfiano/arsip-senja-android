# BRIEF_REPAIR_CI3 — Perbaiki AturanDuplikatTest (missing constructor params)

Konteks: CI run 38886708381 gagal pada `:app:compileDebugUnitTestKotlin`:
```
e: AturanDuplikatTest.kt:40:9 No value passed for parameter 'deskripsi'.
e: AturanDuplikatTest.kt:40:9 No value passed for parameter 'dibuatPada'.
```

Penyebab: `BerkasArsipEntity` (`app/src/main/java/com/arsipsenja/data/local/BerkasArsipEntity.kt`)
memiliki dua parameter konstruktor tanpa default: `val deskripsi: String?` dan
`val dibuatPada: Long`. Test helper `buatBerkas()` di AturanDuplikatTest tidak
mengisi keduanya.

## Tugas

1. Di `BerkasArsipEntity.kt`, beri nilai default aman:
   - `deskripsi: String? = null`
   - `dibuatPada: Long = 0L`
   JANGAN mengubah nama, tipe, urutan, atau annotation Room kolom mana pun.
   (Default Kotlin tidak mengubah skema DB → tidak perlu migrasi.)

2. Setelah edit, jalankan `git diff` dan pastikan hanya 2 baris yang berubah.
   JANGAN commit, JANGAN push, JANGAN membuat PR. Hanya ubah working tree.

3. Laporkan: file yang diubah + jumlah baris diff + alasan aman terhadap Room.
