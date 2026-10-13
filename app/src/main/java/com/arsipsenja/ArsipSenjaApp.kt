package com.arsipsenja

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkManager
import com.arsipsenja.data.local.ArsipDatabase
import com.arsipsenja.data.local.PengaturanStore
import com.arsipsenja.data.repo.ArsipRepository
import com.arsipsenja.data.telegram.TelegramClient
import com.arsipsenja.data.work.Notifikasi

/**
 * Kontainer dependensi manual (tanpa Hilt) — satu instance per proses.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val pengaturan: PengaturanStore by lazy { PengaturanStore(appContext) }

    val database: ArsipDatabase by lazy {
        Room.databaseBuilder(appContext, ArsipDatabase::class.java, "arsip-senja.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val telegramClient: TelegramClient by lazy {
        TelegramClient(appContext, pengaturan)
    }

    val arsipRepository: ArsipRepository by lazy {
        ArsipRepository(appContext, database, telegramClient, pengaturan, workManager)
    }

    /** WorkManager proses ini — untuk pemantauan & pembatalan antrean dari UI. */
    val workManager: WorkManager
        get() = WorkManager.getInstance(appContext)

    fun mulaiSesi() {
        Notifikasi.siapkan(appContext)
        telegramClient.mulai()
    }
}

/**
 * Kelas aplikasi: memegang [AppContainer] dan menyiapkan kanal notifikasi.
 */
class ArsipSenjaApp : Application(), Configuration.Provider {

    lateinit var wadah: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        PenangkapCrash.pasang(this)
        instance = this
        wadah = AppContainer(this)
        try {
            wadah.mulaiSesi()
        } catch (e: Exception) {
            // Satu subsistem gagal tidak boleh membunuh seluruh aplikasi.
            android.util.Log.e("ArsipSenjaApp", "mulaiSesi gagal", e)
        }
    }

    companion object {
        lateinit var instance: ArsipSenjaApp
            private set

        fun dari(context: Context): ArsipSenjaApp =
            context.applicationContext as ArsipSenjaApp
    }
}
