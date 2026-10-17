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
import java.util.Calendar
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

    /**
     * Hasil [buatAmanah] — dipakai UI untuk memberi tahu pengguna bahwa
     * rahasia tidak lagi tersimpan terenkripsi.
     */
    @Volatile
    var tersimpanTanpaEnkripsi: Boolean = false
        private set

    /**
     * Buka penyimpanan rahasia.
     *
     * ## Kenapa tidak ada fallback diam-diam lagi
     *
     * Versi lama menangkap `Exception` lalu jatuh ke `SharedPreferences`
     * biasa. Fallback itu memegang `api_hash` **dan kunci enkripsi database
     * TDLib** — jadi satu kegagalan Keystore diam-diam mengubah satu-satunya
     * database terenkripsi aplikasi menjadi database yang kuncinya tersimpan
     * dalam bentuk polos, tanpa satu pesan pun ke pengguna.
     *
     * Sekarang kegagalan Keystore tidak ditoleransi untuk rahasia: proses
     * melaporkan kondisi lewat [tersimpanTanpaEnkripsi] supaya UI bisa
     * memperingatkan pengguna, dan aplikasi refuses to proceed diam-diam.
     */
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
        // Ditandai dan dilaporkan, bukan disembunyikan. Penyimpanan polos
        // hanya dipakai agar data lama tidak hilang total, dan hanya untuk
        // preferensi non-rahasia.
        tersimpanTanpaEnkripsi = true
        Log.e(TAG, "Keystore gagal; rahasia tidak dapat dienkripsi: ${e.message}")
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

    /** Toggle "Hanya unggah via Wi-Fi" — menahan transfer di jaringan seluler. */
    val hanyaWifi: Flow<Boolean> = store.data.map { p -> p[KUNCI_HANYA_WIFI] ?: false }

    suspend fun setHanyaWifi(aktif: Boolean) {
        store.edit { it[KUNCI_HANYA_WIFI] = aktif }
    }

    // ---- Jeda otomatis antrean unggah (Paket B3) ----

    /**
     * True bila unggahan dijeda otomatis saat baterai di bawah
     * [AturanJedaOtomatis.AMBANG_BATERAI_RENDAH] persen. Default true.
     */
    val jedaBateraiRendah: Flow<Boolean> =
        store.data.map { p -> p[KUNCI_JEDA_BATERAI] ?: true }

    suspend fun setJedaBateraiRendah(aktif: Boolean) {
        store.edit { it[KUNCI_JEDA_BATERAI] = aktif }
    }

    /**
     * True bila unggahan dijeda otomatis saat memakai data seluler.
     * Default false — perilaku ini sudah tercakup toggle "Hanya WiFi",
     * toggle ini untuk pengguna yang ingin jeda seluler TANPA membatasi
     * ke WiFi saja (mis. tethering).
     */
    val jedaDataSeluler: Flow<Boolean> =
        store.data.map { p -> p[KUNCI_JEDA_SELULER] ?: false }

    suspend fun setJedaDataSeluler(aktif: Boolean) {
        store.edit { it[KUNCI_JEDA_SELULER] = aktif }
    }

    // ---- Hasil cek kesehatan vault berkala (Paket A2) ----

    /** Waktu cek kesehatan vault terakhir (epoch ms); null bila belum pernah. */
    val vaultDicekPada: Flow<Long?> = store.data.map { p -> p[KUNCI_VAULT_DICEK] }

    /** Hasil cek terakhir; null bila belum pernah dicek. */
    val vaultSehatTerakhir: Flow<Boolean?> =
        store.data.map { p -> p[KUNCI_VAULT_SEHAT] }

    /** Penyebab masalah dari cek terakhir; null bila sehat/belum dicek. */
    val vaultMasalahTerakhir: Flow<String?> =
        store.data.map { p -> p[KUNCI_VAULT_MASALAH] }

    suspend fun simpanHasilCekVault(sehat: Boolean, masalah: String?, dicekPada: Long) {
        store.edit {
            it[KUNCI_VAULT_SEHAT] = sehat
            it[KUNCI_VAULT_DICEK] = dicekPada
            if (masalah != null) it[KUNCI_VAULT_MASALAH] = masalah
            else it.remove(KUNCI_VAULT_MASALAH)
        }
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
    @Synchronized
    fun kunciDatabaseTdlib(): ByteArray {
        // Menolak terus, bukan menyimpan kunci dalam bentuk polos.
        //
        // Bila Keystore tidak tersedia, kunci yang tadinya tersimpan
        // terenkripsi sudah tidak dapat dibaca sama sekali — basis data
        // TDLib sudah terlanjur tidak bisa dibuka apa pun yang terjadi.
        // Menulis kunci baru ke penyimpanan polos hanya menghasilkan kunci
        // yang bisa dibaca siapa pun yang punya berkas aplikasi, yaitu
        // membatalkan enkripsi tanpa memberi hasil apa pun.
        check(!tersimpanTanpaEnkripsi) {
            "Keystore tidak tersedia: kunci database TDLib tidak dapat " +
                "disimpan dengan aman. Keluar sesi lalu masuk ulang untuk " +
                "membangun ulang basis data lokal."
        }
        val tersimpan = amanah.getString(KUNCI_DB, null)
        if (tersimpan != null) {
            return Base64.decode(tersimpan, Base64.NO_WRAP)
        }
        val kunci = ByteArray(32).also { SecureRandom().nextBytes(it) }
        // `commit()` bukan `apply()`: apply menulis di latar belakang, jadi
        // dua pemanggil pada-cold start bisa sama-sama membuat kunci berbeda
        // lalu TDLib memakai yang satu sementara yang tersimpan adalah yang
        // lain. Basis data pun terkunci permanen.
        amanah.edit()
            .putString(KUNCI_DB, Base64.encodeToString(kunci, Base64.NO_WRAP))
            .commit()
        return kunci
    }

    /**
     * Hapus kunci enkripsi database TDLib.
     *
     * Kunci **harus** selalu dihapus bersamaan dengan basis datanya. Kalau
     * kuncinya hilang tapi `tdlib/database` masih ada, TDLib tidak dapat
     * mendekripsi basis data lama: [kunciDatabaseTdlib] akan membuat kunci
     * baru, `setTdlibParameters` ditolak dengan "Wrong database encryption
     * key", lalu TDLib mengulang WaTdlibParameters tanpa henti. Arsip menjadi
     * tidak dapat dipulihkan selamanya.
     *
     * [hapusBasisData] harus menghapus direktori `tdlib/database`; pemanggil
     * yang paling tahu letaknya. Bila ia mengembalikan false, kunci **tidak**
     * dihapus agar kedua sisi tetap konsisten.
     */
    @Synchronized
    fun hapusRahasiaSesi(hapusBasisData: () -> Boolean = { true }): Boolean {
        // Urutan penting: basis data dihapus **dulu**, baru kuncinya.
        // Bila urutannya dibalik dan penghapusan basis data gagal, kunci
        // sudah hilang sementara datanya masih ada — persis kondisi yang
        // membuat arsip tidak dapat dipulihkan selamanya.
        if (!hapusBasisData()) {
            Log.w(TAG, "Basis data TDLib gagal dihapus; kunci dipertahankan")
            return false
        }
        // `commit()` disengaja: apply() bersifat asinkron sehingga kunci
        // yang sempat dikembalikan ke TDLib bisa berbeda dari yang
        // tersimpan — TOCTOU yang berakhir dengan basis data terkunci.
        amanah.edit().remove(KUNCI_DB).commit()
        return true
    }
    
    /** Menyimpan tanggal ketika kartu Senja Memories ditolak. */
    suspend fun setSenjaMemoriesDismissed() {
        store.edit {
            val calendar = Calendar.getInstance()
            val tahun = calendar.get(Calendar.YEAR)
            val bulan = calendar.get(Calendar.MONTH) + 1 // January is 0
            val hari = calendar.get(Calendar.DAY_OF_MONTH)
            val tanggal = "$tahun-%02d-%02d".format(bulan, hari)
            it[KUNCI_SENJA_MEMORIES_DISMISSED_DATE] = tanggal
        }
    }
    
    /** Mengembalikan true jika kartu Senja Memories telah ditolak untuk hari ini. */
    suspend fun isSenjaMemoriesDismissed(): Boolean {
        val tanggalDisimpan = store.data.first()[KUNCI_SENJA_MEMORIES_DISMISSED_DATE]
        if (tanggalDisimpan == null) return false
        val calendar = Calendar.getInstance()
        val tahunSekarang = calendar.get(Calendar.YEAR)
        val bulanSekarang = calendar.get(Calendar.MONTH) + 1
        val hariSekarang = calendar.get(Calendar.DAY_OF_MONTH)
        val tanggalSekarang = "$tahunSekarang-%02d-%02d".format(bulanSekarang, hariSekarang)
        return tanggalDisimpan == tanggalSekarang
    }

    private companion object {
        const val TAG = "PengaturanStore"
        val KUNCI_TAMPILAN = stringPreferencesKey("tampilan_dasbor")
        val KUNCI_URUT = stringPreferencesKey("urut_berkas")
        val KUNCI_ARAH = stringPreferencesKey("arah_urut")
        val KUNCI_BIOMETRIK = booleanPreferencesKey("biometrik_aktif")
        val KUNCI_VAULT = longPreferencesKey("vault_chat_id")
        val KUNCI_SINKRON_TERAKHIR = longPreferencesKey("sinkron_terakhir")
        val KUNCI_JEDA_BATERAI = booleanPreferencesKey("jeda_baterai_rendah")
        val KUNCI_JEDA_SELULER = booleanPreferencesKey("jeda_data_seluler")
        val KUNCI_VAULT_DICEK = longPreferencesKey("vault_dicek_pada")
        val KUNCI_VAULT_SEHAT = booleanPreferencesKey("vault_sehat_terakhir")
        val KUNCI_VAULT_MASALAH = stringPreferencesKey("vault_masalah_terakhir")
        val KUNCI_GAGAL_DILAPORKAN = stringSetPreferencesKey("notifikasi_gagal_unggah")
        val KUNCI_RUTE_OTOMATIS = booleanPreferencesKey("rute_otomatis")
        val KUNCI_HANYA_WIFI = booleanPreferencesKey("hanya_wifi")
        const val KUNCI_API_ID = "telegram_api_id"
        const val KUNCI_API_HASH = "telegram_api_hash"
        const val KUNCI_DB = "kunci_db_tdlib"
        val KUNCI_SENJA_MEMORIES_DISMISSED_DATE = stringPreferencesKey("senja_memories_dismissed_date")
    }
}
