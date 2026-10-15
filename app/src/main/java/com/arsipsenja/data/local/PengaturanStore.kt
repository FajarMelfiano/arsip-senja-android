package com.arsipsenja.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.arsipsenja.util.ArahUrut
import com.arsipsenja.util.TampilanDasbor
import com.arsipsenja.util.UrutBerkas
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStorePengaturan by preferencesDataStore(name = "pengaturan")

/**
 * Pengaturan tampilan (DataStore) + rahasia Telegram (EncryptedSharedPreferences).
 * api_id/api_hash diinput pengguna dari my.telegram.org — tidak pernah di-hardcode.
 */
class PengaturanStore(private val context: Context) {

    private val store = context.dataStorePengaturan

    private val amanah: SharedPreferences by lazy { buatAmanah() }

    private fun buatAmanah(): SharedPreferences = try {
        val kunciUtama = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "rahasia_arsip",
            kunciUtama,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Keystore bisa gagal di emulator lama; tetap jalan dengan prefs biasa
        // agar pengguna tidak terkunci keluar — bukan untuk menyimpan rahasia jangka panjang.
        Log.w(TAG, "Keystore tidak tersedia, memakai prefs biasa: ${e.message}")
        context.getSharedPreferences("rahasia_arsip_darurat", Context.MODE_PRIVATE)
    }

    // ---- Tampilan ----

    val tampilanDasbor: Flow<TampilanDasbor> = store.data.map { p ->
        when (p[KUNCI_TAMPILAN]) {
            "daftar" -> TampilanDasbor.DAFTAR
            else -> TampilanDasbor.KISI
        }
    }

    val urutBerkas: Flow<UrutBerkas> = store.data.map { p ->
        p[KUNCI_URUT].let { s -> UrutBerkas.entries.firstOrNull { it.name == s } ?: UrutBerkas.TANGGAL }
    }

    val arahUrut: Flow<ArahUrut> = store.data.map { p ->
        p[KUNCI_ARAH].let { s -> ArahUrut.entries.firstOrNull { it.name == s } ?: ArahUrut.TURUN }
    }

    val biometrikAktif: Flow<Boolean> = store.data.map { p -> p[KUNCI_BIOMETRIK] ?: false }

    /**
     * chatId grup vault (supergroup forum) pilihan pengguna; null bila belum dipilih.
     * Seluruh koleksi topik tinggal di vault ini.
     */
    val vaultChatId: Flow<Long?> = store.data.map { p -> p[KUNCI_VAULT] }

    suspend fun setVaultChatId(chatId: Long?) {
        store.edit {
            if (chatId == null) it.remove(KUNCI_VAULT) else it[KUNCI_VAULT] = chatId
        }
    }

    suspend fun vaultSekarang(): Long? = vaultChatId.first()

    suspend fun setTampilan(tampilan: TampilanDasbor) {
        store.edit { it[KUNCI_TAMPILAN] = tampilan.name }
    }

    suspend fun setUrutan(urut: UrutBerkas, arah: ArahUrut) {
        store.edit {
            it[KUNCI_URUT] = urut.name
            it[KUNCI_ARAH] = arah.name
        }
    }

    suspend fun setBiometrik(aktif: Boolean) {
        store.edit { it[KUNCI_BIOMETRIK] = aktif }
    }

    /** Toggle "Rute otomatis (ikut kategori)" di layar Unggah — persisten antar buka aplikasi. */
    val ruteOtomatis: Flow<Boolean> = store.data.map { p -> p[KUNCI_RUTE_OTOMATIS] ?: false }

    suspend fun setRuteOtomatis(aktif: Boolean) {
        store.edit { it[KUNCI_RUTE_OTOMATIS] = aktif }
    }

    suspend fun perluBiometrik(): Boolean = biometrikAktif.first()

    suspend fun tampilanSekarang(): TampilanDasbor = tampilanDasbor.first()

    // ---- Sinkron terakhir (untuk debounce sinkron otomatis) ----

    /** Waktu sinkron terakhir yang berhasil (epoch ms); null bila belum pernah. */
    val aliranSinkronTerakhir: Flow<Long?> =
        store.data.map { p -> p[KUNCI_SINKRON_TERAKHIR] }

    suspend fun sinkronTerakhir(): Long? = aliranSinkronTerakhir.first()

    suspend fun setSinkronTerakhir(ms: Long) {
        store.edit { it[KUNCI_SINKRON_TERAKHIR] = ms }
    }

    // ---- Galat unggah yang sudah dilaporkan ----

    /**
     * ID pekerjaan unggah yang popup galatnya sudah pernah ditampilkan.
     * Disimpan persisten agar galat lama tidak dibuka ulang setiap layar
     * Unggah dibuka (ViewModel tab bisa dibuat ulang saat navigasi).
     */
    val aliranGagalDilaporkan: Flow<Set<String>> =
        store.data.map { p -> p[KUNCI_GAGAL_DILAPORKAN] ?: emptySet() }

    suspend fun tandaiGagalDilaporkan(id: String) {
        store.edit { p ->
            val semua = (p[KUNCI_GAGAL_DILAPORKAN] ?: emptySet()) + id
            // Batasi agar tidak tumbuh tanpa batas (cukup 200 terakhir).
            p[KUNCI_GAGAL_DILAPORKAN] = if (semua.size > 200) {
                semua.toList().takeLast(200).toSet()
            } else {
                semua
            }
        }
    }

    // ---- Rahasia Telegram ----

    fun apiId(): Int? = amanah.getString(KUNCI_API_ID, null)?.toIntOrNull()

    fun apiHash(): String? = amanah.getString(KUNCI_API_HASH, null)

    fun adaKredensial(): Boolean = apiId() != null && !apiHash().isNullOrBlank()

    fun simpanKredensial(apiId: Int, apiHash: String) {
        amanah.edit()
            .putString(KUNCI_API_ID, apiId.toString())
            .putString(KUNCI_API_HASH, apiHash.trim())
            .apply()
    }

    /**
     * Kunci enkripsi basis data TDLib; dibuat sekali dan disimpan di Keystore.
     */
    fun kunciDatabaseTdlib(): ByteArray {
        val tersimpan = amanah.getString(KUNCI_DB, null)
        if (tersimpan != null) {
            return Base64.decode(tersimpan, Base64.NO_WRAP)
        }
        val kunci = ByteArray(32).also { SecureRandom().nextBytes(it) }
        amanah.edit()
            .putString(KUNCI_DB, Base64.encodeToString(kunci, Base64.NO_WRAP))
            .apply()
        return kunci
    }

    /** Hapus sesi lokal (kredensial API tetap disimpan agar login ulang mudah). */
    fun hapusRahasiaSesi() {
        amanah.edit().remove(KUNCI_DB).apply()
    }

    private companion object {
        const val TAG = "PengaturanStore"
        val KUNCI_TAMPILAN = stringPreferencesKey("tampilan_dasbor")
        val KUNCI_URUT = stringPreferencesKey("urut_berkas")
        val KUNCI_ARAH = stringPreferencesKey("arah_urut")
        val KUNCI_BIOMETRIK = booleanPreferencesKey("biometrik_aktif")
        val KUNCI_VAULT = longPreferencesKey("vault_chat_id")
        val KUNCI_SINKRON_TERAKHIR = longPreferencesKey("sinkron_terakhir")
        val KUNCI_GAGAL_DILAPORKAN = stringSetPreferencesKey("notifikasi_gagal_unggah")
        val KUNCI_RUTE_OTOMATIS = booleanPreferencesKey("rute_otomatis")
        const val KUNCI_API_ID = "telegram_api_id"
        const val KUNCI_API_HASH = "telegram_api_hash"
        const val KUNCI_DB = "kunci_db_tdlib"
    }
}
