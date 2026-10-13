package com.arsipsenja.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BerkasArsipEntity::class, KoleksiEntity::class, TautanEntity::class],
    version = 1,
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
                    // Indeks arsip selalu bisa dibangun ulang dari Telegram.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
