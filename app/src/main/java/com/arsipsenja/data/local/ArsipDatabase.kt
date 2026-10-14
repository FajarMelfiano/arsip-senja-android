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

@Database(
    entities = [BerkasArsipEntity::class, KoleksiEntity::class, TautanEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ArsipDatabase : RoomDatabase() {

    abstract fun berkasDao(): BerkasArsipDao
    abstract fun koleksiDao(): KoleksiDao
    abstract fun tautanDao(): TautanDao

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
                    .addMigrations(MIGRASI_1_2)
                    // Indeks arsip selalu bisa dibangun ulang dari Telegram.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
