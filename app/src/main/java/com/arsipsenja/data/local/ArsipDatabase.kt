package com.arsipsenja.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrasi 1 -> 2: dukung koleksi topik forum.
 *
 * - `koleksi`: primary key lama (chatId) tidak muat menampung banyak topik
 *   dalam satu vault, jadi tabel dibangun ulang dengan PK komposit
 *   (chatId, topicId). Data lama disalin dengan topicId = 0 (channel lama).
 * - `berkas_arsip`: tambah kolom topicId (default 0 = channel lama)
 *   + indeks komposit (koleksiId, topicId).
 *
 * Seluruh data pengguna dipertahankan; tidak ada destructive migration
 * untuk perubahan ini.
 */
val MIGRASI_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `koleksi_baru` (
                `chatId` INTEGER NOT NULL,
                `topicId` INTEGER NOT NULL,
                `nama` TEXT NOT NULL,
                `deskripsi` TEXT,
                `jumlahBerkas` INTEGER NOT NULL,
                `dibuatPada` INTEGER NOT NULL,
                `terakhirSinkron` INTEGER NOT NULL,
                PRIMARY KEY(`chatId`, `topicId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `koleksi_baru`
                (`chatId`, `topicId`, `nama`, `deskripsi`, `jumlahBerkas`, `dibuatPada`, `terakhirSinkron`)
            SELECT `chatId`, 0, `nama`, `deskripsi`, `jumlahBerkas`, `dibuatPada`, `terakhirSinkron`
            FROM `koleksi`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `koleksi`")
        db.execSQL("ALTER TABLE `koleksi_baru` RENAME TO `koleksi`")

        db.execSQL("ALTER TABLE `berkas_arsip` ADD COLUMN `topicId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `berkas_arsip` ADD COLUMN `tdThumbFileId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `berkas_arsip` ADD COLUMN `thumbPath` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_berkas_arsip_koleksiId_topicId` " +
                "ON `berkas_arsip` (`koleksiId`, `topicId`)",
        )
    }
}

/**
 * Migrasi 2 -> 3: metadata kategori kustom.
 *
 * - `koleksi.label`: chip label opsional (maks 24 karakter), null bila kosong.
 * - `koleksi.warna`: ARGB untuk dot/chip kategori, default 0 (warna tema).
 * - `koleksi.tipeFile`: CSV nama enum TipeFile untuk rute otomatis;
 *   string kosong = menangani semua tipe (kompatibel data lama).
 */
val MIGRASI_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `koleksi` ADD COLUMN `label` TEXT")
        db.execSQL("ALTER TABLE `koleksi` ADD COLUMN `warna` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `koleksi` ADD COLUMN `tipeFile` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Migrasi 3 -> 4: kunci pengaman per koleksi (gerbang privasi lokal).
 *
 * - `koleksi.kunciTipe`: TIDAK_ADA / PIN / KATA_SANDI / POLA.
 * - `koleksi.kunciGaram` / `kunciHash`: null bila tidak terkunci.
 * - `koleksi.kunciBiometrik`: 0/1, boleh dibuka pakai biometrik.
 *
 * Kolom lain tidak disentuh; seluruh data pengguna dipertahankan.
 */
val MIGRASI_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `koleksi` ADD COLUMN `kunciTipe` TEXT NOT NULL DEFAULT 'TIDAK_ADA'")
        db.execSQL("ALTER TABLE `koleksi` ADD COLUMN `kunciGaram` TEXT")
        db.execSQL("ALTER TABLE `koleksi` ADD COLUMN `kunciHash` TEXT")
        db.execSQL("ALTER TABLE `koleksi` ADD COLUMN `kunciBiometrik` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Migrasi 4 -> 5: penanda asal impor untuk tab pisah.
 *
 * - `koleksi.asalImpor`: 0 = koleksi pribadi (default untuk semua data lama),
 *   1 = hasil "Tarik dari Telegram" (hanya tab Impor).
 *
 * Data lama otomatis menjadi pribadi; tidak ada baris yang dihapus.
 */
val MIGRASI_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `koleksi` ADD COLUMN `asalImpor` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Migrasi 5 -> 6: integritas, kelompok potongan, dan rekonsiliasi.
 *
 * ## Kolom baru pada `berkas_arsip`
 *
 * Seluruhnya **additive** dengan nilai bawaan, jadi tidak ada baris yang hilang
 * dan tidak ada nilai lama yang berubah makna:
 *
 * - `hashSha256`, `grupId`, `indeksPotongan`, `jumlahPotongan`, `namaAsli`,
 *   `ukuranTotal` — cermin header caption. NULL/0 untuk arsip lama, yang
 *   memang belum punya metadata tersebut.(sum them later)
 * - `statusSinkron` — 0 = masih conforms with Telegram.
 * - `sudahDiverifikasi` — 0 = hash belum dicocokkan.
 *
 * Kolom ini sengaja dapat di-backfill: setelah sinkron penuh berikutnya,
 * setiap baris yang punya header caption terisi. Baris tanpa header (arsip
 * lama) tetap null dan diperlakukan sebagai "tidak punya hash" — bukan error.
 *
 * ## Indeks baru
 *
 * Tanpa ini, `tipe`/`ukuran`/`hashSha256`/`grupId`/`statusSinkron` hanya bisa
 * dijawab dengan pemindaian penuh tabel, dan pengurutan `nama COLLATE NOCASE`
 * tidak bisa memakai indeks (indeks bawaan BINARY).
 *
 * Ekspansi indeks `koleksiId, topicId` menjadi `koleksiId, topicId, tanggal`
 * menutup query paling sering dipakai: daftar berkas satu koleksi terurut
 * tanggal. Indeks lama tetap dipertahankan karena pola `koleksiId = ?` tanpa
 * tanggal masih dipakai filter dasbor.
 */
val MIGRASI_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `berkas_arsip` ADD COLUMN `hashSha256` TEXT")
        db.execSQL("ALTER TABLE `berkas_arsip` ADD COLUMN `grupId` TEXT")
        db.execSQL(
            "ALTER TABLE `berkas_arsip` ADD COLUMN `indeksPotongan` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE `berkas_arsip` ADD COLUMN `jumlahPotongan` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL("ALTER TABLE `berkas_arsip` ADD COLUMN `namaAsli` TEXT")
        db.execSQL(
            "ALTER TABLE `berkas_arsip` ADD COLUMN `ukuranTotal` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE `berkas_arsip` ADD COLUMN `statusSinkron` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE `berkas_arsip` ADD COLUMN `sudahDiverifikasi` INTEGER NOT NULL DEFAULT 0"
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_berkas_arsip_koleksiId_topicId_tanggal` " +
                "ON `berkas_arsip` (`koleksiId`, `topicId`, `tanggal`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_berkas_arsip_tipe` ON `berkas_arsip` (`tipe`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_berkas_arsip_ukuran` ON `berkas_arsip` (`ukuran`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_berkas_arsip_hashSha256` " +
                "ON `berkas_arsip` (`hashSha256`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_berkas_arsip_grupId` ON `berkas_arsip` (`grupId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_berkas_arsip_statusSinkron` " +
                "ON `berkas_arsip` (`statusSinkron`)"
        )
    }
}
val MIGRASI_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `upload_queue_pause` (
                `workId` TEXT NOT NULL,
                `isPaused` INTEGER NOT NULL DEFAULT 0,
                `priority` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`workId`)
            )
            """.trimIndent()
        )
    }
}

// Menambahkan kolom parameter enqueue (uri, koleksiId, topicId) ke tabel jeda
// agar "lanjutkan seketika" bisa membangun ulang WorkRequest tanpa membaca inputData.
val MIGRASI_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `upload_queue_pause` ADD COLUMN `uri` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `upload_queue_pause` ADD COLUMN `koleksiId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `upload_queue_pause` ADD COLUMN `topicId` INTEGER NOT NULL DEFAULT 0")
    }
}

// Menambahkan tabel log operasi tertunda (sinkron tahan banting, Paket B1)
// dan log konflik (resolusi sinkron dua arah, Paket B2).
val MIGRASI_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `operasi_tertunda` (
                `id` TEXT NOT NULL,
                `jenis` TEXT NOT NULL,
                `targetId` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `dibuatPada` INTEGER NOT NULL,
                `percobaan` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `log_konflik` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `targetId` TEXT NOT NULL,
                `jenis` TEXT NOT NULL,
                `pemenang` TEXT NOT NULL,
                `alasan` TEXT NOT NULL,
                `dicatatPada` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

@Database(
    entities = [
        BerkasArsipEntity::class,
        KoleksiEntity::class,
        TautanEntity::class,
        UploadQueuePauseEntity::class,
        OperasiTertundaEntity::class,
        LogKonflikEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class ArsipDatabase : RoomDatabase() {

    abstract fun berkasDao(): BerkasArsipDao
    abstract fun koleksiDao(): KoleksiDao
    abstract fun tautanDao(): TautanDao
    abstract fun uploadQueuePauseDao(): UploadQueuePauseDao
    abstract fun operasiTertundaDao(): OperasiTertundaDao
    abstract fun logKonflikDao(): LogKonflikDao

    companion object {
        @Volatile
        private var instance: ArsipDatabase? = null

        fun get(context: Context): ArsipDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArsipDatabase::class.java,
                    "arsip-senja.db",
                )
                    .addMigrations(
                        MIGRASI_1_2,
                        MIGRASI_2_3,
                        MIGRASI_3_4,
                        MIGRASI_4_5,
                        MIGRASI_5_6,
                        MIGRASI_6_7,
                        MIGRASI_7_8,
                        MIGRASI_8_9,
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
