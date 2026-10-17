package com.arsipsenja.data.telegram

import android.content.Context
import android.os.Build
import android.util.Log
import com.arsipsenja.BuildConfig
import com.arsipsenja.data.local.PengaturanStore
import com.arsipsenja.data.repo.AturanDeteksiVault
import com.arsipsenja.data.repo.PeranPenggunaChat
import com.arsipsenja.data.repo.PenjagaVault
import com.arsipsenja.util.TipeFile
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Pembungkus TDLib (org.drinkless.tdlib.Client) untuk Arsip Senja.
 *
 * Satu instance untuk seluruh proses: mengelola klien, alur otorisasi,
 * pemetaan update, serta operasi berkas (kirim/terima) dengan progres.
 */
class TelegramClient(
    private val context: Context,
    private val pengaturan: PengaturanStore,
) {

    private val tag = "TelegramClient"

    private val _status = MutableStateFlow<StatusOtorisasi>(StatusOtorisasi.Menyiapkan)
    val status: StateFlow<StatusOtorisasi> = _status.asStateFlow()

    private val _siap = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Dipancarkan setiap kali otorisasi mencapai status siap. */
    val siap: SharedFlow<Unit> = _siap.asSharedFlow()

    private val _progres = MutableSharedFlow<ProgresBerkas>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val progresBerkas: SharedFlow<ProgresBerkas> = _progres.asSharedFlow()

    /**
     * Replay agar [tungguTerkirim] yang mulai mengoleksi belakangan tidak
     * kehilangan updateMessageSendSucceeded yang sudah terbit duluan —
     * tanpa ini, pesan yang sebenarnya sudah terkirim dianggap timeout
     * (30 mnt) lalu worker retry dan MENGIRIM ULANG berkas yang sama
     * (duplikat di Telegram + indeks ganda).
     */
    private val _pesanTerkirim = MutableSharedFlow<PesanTerkirim>(
        replay = 64,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val pesanTerkirim: SharedFlow<PesanTerkirim> = _pesanTerkirim.asSharedFlow()

    private val _pesanBaru = MutableSharedFlow<TdApi.Message>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val pesanBaru: SharedFlow<TdApi.Message> = _pesanBaru.asSharedFlow()

    /**
     * (chatId, messageId) setiap kali pesan dihapus di Telegram.
     *
     * Tanpa handler ini indeks lokal berbohong permanen: berkas tetap
     * ditampilkan padahal sudah tidak ada di vault, dan mengunduhnya
     * akan retry tanpa henti.
     */
    private val _pesanDihapus = MutableSharedFlow<Pair<Long, Long>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val pesanDihapus: SharedFlow<Pair<Long, Long>> = _pesanDihapus.asSharedFlow()

    /**
     * Pesan yang diedit (caption berubah atau dokumen diganti). Dibawa penuh
     * agar indeks lokal bisa diselaraskan tanpa menebak.
     */
    private val _pesanDiubah = MutableSharedFlow<TdApi.Message>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val pesanDiubah: SharedFlow<TdApi.Message> = _pesanDiubah.asSharedFlow()

    private val _koneksi = MutableStateFlow(false)
    /**
     * True bila TDLib melaporkan ConnectionStateReady. Dipakai mesin
     * realtime untuk memicu sinkron ringan saat koneksi pulih.
     */
    val koneksi: StateFlow<Boolean> = _koneksi.asStateFlow()

    private val _judulChatBerubah = MutableSharedFlow<Pair<Long, String>>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    /** (chatId, judulBaru) setiap UpdateChatTitle — untuk refresh nama lokal. */
    val judulChatBerubah: SharedFlow<Pair<Long, String>> = _judulChatBerubah.asSharedFlow()

    private val _infoTopikBerubah = MutableSharedFlow<Triple<Long, Int, String>>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    /**
     * (chatId, forumTopicId, namaBaru) setiap UpdateForumTopicInfo —
     * untuk refresh nama koleksi yang sudah dikenal. Tidak pernah
     * membuat koleksi baru dari topik asing.
     */
    val infoTopikBerubah: SharedFlow<Triple<Long, Int, String>> =
        _infoTopikBerubah.asSharedFlow()

    private val _galat = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val galat: SharedFlow<String> = _galat.asSharedFlow()

    @Volatile
    private var klien: Client? = null

    // ---------------------------------------------------------------------
    // Lifecycle klien
    // ---------------------------------------------------------------------

    /** Siapkan klien bila kredensial sudah ada. Aman dipanggil berulang. */
    @Synchronized
    fun mulai() {
        if (klien != null) return
        if (!pengaturan.adaKredensial()) {
            _status.value = StatusOtorisasi.MenungguKredensial
            return
        }
        mulaiDenganKredensial()
    }

    /**
     * Mulai ulang klien dari awal: tutup yang lama bila ada, lalu buka baru.
     *
     * [mulai] sengaja tidak melakukan ini — begitu klien ada, ia keluar
     * lebih dulu, sehingga tombol "Coba lagi" di layar login tidak melakukan
     * apa pun (klien sudah non-null). Method inilah yang dipanggil tombol itu.
     */
    @Synchronized
    fun mulaiUlang() {
        klien?.let { kirimTutup(); klien = null }
        if (!pengaturan.adaKredensial()) {
            _status.value = StatusOtorisasi.MenungguKredensial
            return
        }
        mulaiDenganKredensial()
    }

    /** Simpan kredensial lalu mulai klien (dipanggil dari layar login). */
    @Synchronized
    fun simpanKredensialDanMulai(apiId: Int, apiHash: String) {
        pengaturan.simpanKredensial(apiId, apiHash)
        klien?.let {
            kirimTutup()
            klien = null
        }
        mulaiDenganKredensial()
    }

    private fun mulaiDenganKredensial() {
        _status.value = StatusOtorisasi.Menyiapkan
        try {
            muatPustakaNatif()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(tag, "Pustaka TDLib gagal dimuat", e)
            _status.value = StatusOtorisasi.Galat(
                "Pustaka TDLib tidak bisa dimuat di perangkat ini (${e.message})",
            )
            return
        }

        val penanganUpdate = Client.ResultHandler { obj -> tanganiUpdate(obj) }
        val penanganKecuali = Client.ExceptionHandler { e ->
            Log.w(tag, "Kecualian pada update TDLib: ${e.message}")
        }
        klien = Client.create(penanganUpdate, penanganKecuali, penanganKecuali)
        Log.i(tag, "Klien TDLib dibuat")
    }

    private fun muatPustakaNatif() {
        if (pustakaDimuat) return
        synchronized(this) {
            if (pustakaDimuat) return
            try {
                // OpenSSL dipakai libtdjni; dimuat dulu agar resolusi simbol rapi.
                System.loadLibrary("cryptox")
                System.loadLibrary("sslx")
            } catch (e: UnsatisfiedLinkError) {
                Log.i(tag, "OpenSSL sudah termuat otomatis (${e.message})")
            }
            System.loadLibrary("tdjni")
            pustakaDimuat = true
        }
    }

    /** Putus sesi di Telegram (keluar) — kredensial lokal tetap tersimpan. */
    fun keluar() {
        val k = klien ?: run {
            // Klien sudah tertutup: basis data tidak lagi dipakai, jadi aman
            // dihapus bersama kuncinya. Tanpa ini, kunci lama tertinggal dan
            // basis data terenkripsi tidak dapat dibuka pada sesi berikutnya.
            pengaturan.hapusRahasiaSesi { hapusBerkasTdlib() }
            _status.value = StatusOtorisasi.Keluar
            return
        }
        _status.value = StatusOtorisasi.Keluar
        k.send(TdApi.LogOut(), null)
    }

    /**
     * Hapus seluruh basis data dan cache TDLib.
     *
     * Dipanggil tepat sebelum kuncinya dihapus. Mengembalikan false bila
     * direktori masih ada sehingga pemanggil bisa membatalkan penghapusan
     * kunci.
     */
    private fun hapusBerkasTdlib(): Boolean {
        val akar = File(context.filesDir, "tdlib")
        if (!akar.exists()) return true
        return try {
            akar.deleteRecursively() && !akar.exists()
        } catch (e: Exception) {
            Log.w(tag, "Gagal menghapus berkas TDLib: ${e.message}")
            false
        }
    }

    private fun kirimTutup() {
        klien?.send(TdApi.Close(), null)
    }

    // ---------------------------------------------------------------------
    // Update TDLib
    // ---------------------------------------------------------------------

    private fun tanganiUpdate(obj: TdApi.Object?) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> tanganiOtorisasi(obj.authorizationState)
            is TdApi.UpdateFile -> {
                val f = obj.file ?: return
                _progres.tryEmit(
                    ProgresBerkas(
                        fileId = f.id,
                        path = f.local?.path.orEmpty(),
                        ukuran = when {
                            f.expectedSize > 0L -> f.expectedSize
                            else -> f.size
                        },
                        terkirim = f.remote?.uploadedSize ?: 0L,
                        terunduh = f.local?.downloadedSize ?: 0L,
                        selesaiUnggah = f.remote?.isUploadingCompleted == true,
                        selesaiUnduh = f.local?.isDownloadingCompleted == true,
                    ),
                )
            }
            is TdApi.UpdateMessageSendSucceeded -> {
                _pesanTerkirim.tryEmit(PesanTerkirim(obj.message, obj.oldMessageId))
            }
            is TdApi.UpdateNewMessage -> _pesanBaru.tryEmit(obj.message)
            is TdApi.UpdateDeleteMessages -> {
                val zul = obj.messageIds ?: return
                for (id in zul) _pesanDihapus.tryEmit(obj.chatId to id)
            }
            // `UpdateMessageEdited` hanya membawa chatId/messageId/editDate —
            // tidak ada isi pesan. Isi terbaru harus diambil ulang; tanpa itu
            // caption yang diubah di Telegram akan tetap stale selamanya.
            is TdApi.UpdateMessageEdited ->
                ambilUlangPesan(obj.chatId, obj.messageId)
            is TdApi.UpdateMessageContent ->
                ambilUlangPesan(obj.chatId, obj.messageId)
            is TdApi.UpdateConnectionState ->
                _koneksi.value = obj.state is TdApi.ConnectionStateReady
            is TdApi.UpdateChatTitle ->
                _judulChatBerubah.tryEmit(obj.chatId to obj.title)
            is TdApi.UpdateForumTopicInfo -> {
                val info = obj.info ?: return
                _infoTopikBerubah.tryEmit(
                    Triple(info.chatId, info.forumTopicId, info.name.orEmpty()),
                )
            }
            else -> Unit
        }
    }

    /**
 * Minta isi terbaru satu pesan lalu pancarkan ke [_pesanDiubah].
 *
 * Dipanggil dari thread update TDLib, jadi tidak boleh suspend. Galat
 * diabaikan dengan sengaja: gagal mengambil ulang bukan kondisi yang perlu
 * dilaporkan pengguna — penyelarasan manual tetap bisa menutup kesenjangan.
 */
private fun ambilUlangPesan(chatId: Long, messageId: Long) {
    val k = klien ?: return
    k.send(
        TdApi.GetMessage(chatId, messageId),
        { obj ->
            // GetMessage adalah Function<Message>, jadi balikannya langsung
            // objek pesan — bukan Amplop yang memuat `.message`.
            val pesan = obj as? TdApi.Message ?: return@send
            _pesanDiubah.tryEmit(pesan)
        },
    )
}

private fun tanganiOtorisasi(state: TdApi.AuthorizationState?) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> kirimParameter()
            is TdApi.AuthorizationStateWaitPhoneNumber -> _status.value = StatusOtorisasi.MenungguNomor
            is TdApi.AuthorizationStateWaitCode -> {
                val info = state.codeInfo
                _status.value = StatusOtorisasi.MenungguKode(
                    petunjuk = petunjukKode(info?.type),
                    hitungMundur = info?.timeout ?: 0,
                )
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                _status.value = StatusOtorisasi.MenungguSandi(state.passwordHint)
            }
            is TdApi.AuthorizationStateWaitRegistration -> {
                _status.value = StatusOtorisasi.MenungguRegistrasi(state.termsOfService?.text?.text)
            }
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                _status.value = StatusOtorisasi.MenungguPerangkatLain
            }
            is TdApi.AuthorizationStateReady -> {
                _status.value = StatusOtorisasi.Siap
                _siap.tryEmit(Unit)
            }
            is TdApi.AuthorizationStateLoggingOut,
            is TdApi.AuthorizationStateClosing,
            is TdApi.AuthorizationStateClosed,
            -> {
                _status.value = StatusOtorisasi.Keluar
                if (state is TdApi.AuthorizationStateClosed) {
                    klien = null
                }
            }
            is TdApi.AuthorizationStateWaitEmailAddress,
            is TdApi.AuthorizationStateWaitEmailCode,
            is TdApi.AuthorizationStateWaitPremiumPurchase,
            -> {
                _status.value = StatusOtorisasi.Galat(
                    "Metode masuk ini belum didukung oleh Arsip Senja.",
                )
            }
            else -> Unit
        }
    }

    private fun petunjukKode(tipe: TdApi.AuthenticationCodeType?): String = when (tipe) {
        is TdApi.AuthenticationCodeTypeSms -> "SMS ke nomormu"
        is TdApi.AuthenticationCodeTypeTelegramMessage -> "Pesan Telegram di perangkat lain"
        is TdApi.AuthenticationCodeTypeCall -> "Panggilan telepon"
        is TdApi.AuthenticationCodeTypeFlashCall -> "Panggilan kilat"
        is TdApi.AuthenticationCodeTypeMissedCall -> "Panggilan tak terjawab"
        is TdApi.AuthenticationCodeTypeSmsWord -> "SMS kata sandi"
        is TdApi.AuthenticationCodeTypeSmsPhrase -> "SMS frasa sandi"
        is TdApi.AuthenticationCodeTypeFragment -> "Kode dari fragment"
        is TdApi.AuthenticationCodeTypeFirebaseAndroid -> "Verifikasi Google"
        is TdApi.AuthenticationCodeTypeFirebaseIos -> "Verifikasi Google"
        else -> "Kode verifikasi"
    }

    private fun kirimParameter() {
        val apiId = pengaturan.apiId()
        val apiHash = pengaturan.apiHash()
        if (apiId == null || apiHash.isNullOrBlank()) {
            _status.value = StatusOtorisasi.MenungguKredensial
            return
        }
        val akar = File(context.filesDir, "tdlib")
        val parameter = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = File(akar, "database").absolutePath
            filesDirectory = File(akar, "files").absolutePath
            databaseEncryptionKey = pengaturan.kunciDatabaseTdlib()
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            this.apiId = apiId
            this.apiHash = apiHash
            systemLanguageCode = "id"
            deviceModel = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
            systemVersion = Build.VERSION.RELEASE.orEmpty()
            applicationVersion = BuildConfig.VERSION_NAME
        }
        kirimTanpaHasil(parameter) { pesan ->
            Log.e(tag, "SetTdlibParameters gagal: $pesan")
            _status.value = StatusOtorisasi.Galat(
                "TDLib menolak parameter: $pesan",
            )
        }
    }

    // ---------------------------------------------------------------------
    // Perintah dasar
    // ---------------------------------------------------------------------

    /**
     * Kirim permintaan dan tunggu hasilnya; galat TDLib menjadi [TelegramException].
     *
     * ## Kenapa ada batas waktu
     *
     * Tanpa batas waktu, permintaan yang tidak pernah dijawab (perangkat sedang
     * offline, TDLib membuang permintaan) membuat coroutine menggantung
     * selamanya. Di layar login hal ini mematikan seluruh form karena flag
     * "sibuk" tidak pernah kembali false; di sinkron, mutex terkunci permanen
     * dan tidak ada sinkron berikutnya yang bisa berjalan.
     *
     * Timeout dibungkus menjadi [TelegramException] biasa agar pemanggil tidak
     * perlu membedakan jenis galat.
     */
    suspend fun <T : TdApi.Object> kirim(
        fn: TdApi.Function<T>,
        batasWaktu: Long = BATAS_KIRIM_MS,
    ): T {
        val k = klien ?: throw TelegramException("TDLib belum siap")
        return try {
            withTimeout(batasWaktu) {
                suspendCancellableCoroutine { kontinu ->
                    k.send(
                        fn,
                        { obj ->
                            when {
                                obj is TdApi.Error ->
                                    kontinu.resumeWith(Result.failure(TelegramException(obj)))
                                !kontinu.isActive -> Unit
                                else -> {
                                    @Suppress("UNCHECKED_CAST")
                                    kontinu.resumeWith(Result.success(obj as T))
                                }
                            }
                        },
                        { e -> kontinu.resumeWith(Result.failure(e)) },
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw TelegramException(
                pesan = "Permintaan Telegram melewati batas waktu ${batasWaktu / 1000} detik",
                kode = 0,
                cause = e,
            )
        }
    }

    private fun kirimTanpaHasil(
        fn: TdApi.Function<*>,
        padaGalat: (String) -> Unit = { _galat.tryEmit(it) },
    ) {
        val k = klien ?: return
        k.send(fn, { obj ->
            if (obj is TdApi.Error) padaGalat(obj.message ?: "galat ${obj.code}")
        })
    }

    /** Jalankan permintaan sinkron (hanya untuk fungsi yang didukung TDLib). */
    fun <T : TdApi.Object> eksekusi(fn: TdApi.Function<T>): T = try {
        Client.execute(fn)
    } catch (e: Client.ExecutionException) {
        throw TelegramException(e.error)
    }

    // ---------------------------------------------------------------------
    // Otorisasi
    // ---------------------------------------------------------------------

    suspend fun setNomorTelepon(nomor: String) {
        kirim(
            TdApi.SetAuthenticationPhoneNumber().apply {
                phoneNumber = nomor.trim()
                settings = TdApi.PhoneNumberAuthenticationSettings().apply {
                    allowFlashCall = false
                    allowMissedCall = false
                    isCurrentPhoneNumber = true
                    hasUnknownPhoneNumber = false
                    allowSmsRetrieverApi = false
                    authenticationTokens = emptyArray()
                }
            },
        )
    }

    suspend fun cekKode(kode: String) {
        kirim(TdApi.CheckAuthenticationCode().apply { code = kode.trim() })
    }

    suspend fun cekSandi(sandi: String) {
        kirim(TdApi.CheckAuthenticationPassword().apply { password = sandi })
    }

    suspend fun daftarkanAkun(namaDepan: String, namaBelakang: String) {
        kirim(
            TdApi.RegisterUser().apply {
                firstName = namaDepan.trim()
                lastName = namaBelakang.trim()
            },
        )
    }

    /** Tunggu sampai status menjadi siap (dipakai setelah submit kredensial). */
    suspend fun tungguSampaiSiap() {
        status.first { it is StatusOtorisasi.Siap || it is StatusOtorisasi.Galat || it is StatusOtorisasi.Keluar }
    }

    // ---------------------------------------------------------------------
    // Koleksi (channel) & pesan
    // ---------------------------------------------------------------------

    suspend fun daftarChannel(): List<TdApi.Chat> {
        val daftar = kirim(
            TdApi.GetChats().apply {
                chatList = TdApi.ChatListMain()
                limit = 100
            },
        )
        return daftar.chatIds.toList()
            .mapNotNull { id ->
                try {
                    kirim(TdApi.GetChat().apply { chatId = id })
                } catch (e: Exception) {
                    Log.w(tag, "Gagal memuat chat $id", e)
                    null
                }
            }
            .filter { it.type is TdApi.ChatTypeSupergroup }
    }

    suspend fun buatChannel(nama: String, deskripsi: String): TdApi.Chat =
        kirim(
            TdApi.CreateNewSupergroupChat().apply {
                title = nama.trim()
                isChannel = true
                isForum = false
                this.description = deskripsi.trim()
                location = null
                messageAutoDeleteTime = 0
                forImport = false
            },
        )

    suspend fun ubahJudul(chatId: Long, judul: String) {
        kirim(TdApi.SetChatTitle().apply {
            this.chatId = chatId
            title = judul.trim()
        })
    }

    suspend fun ubahDeskripsi(chatId: Long, deskripsi: String) {
        kirim(TdApi.SetChatDescription().apply {
            this.chatId = chatId
            description = deskripsi.trim()
        })
    }

    suspend fun tinggalkanChannel(chatId: Long) {
        kirim(TdApi.LeaveChat().apply { this.chatId = chatId })
    }

    // ---------------------------------------------------------------------
    // Vault & topik forum
    // ---------------------------------------------------------------------

    /** Ambil info chat via GetChat (dipakai untuk validasi tipe vault). */
    suspend fun ambilChat(chatId: Long): TdApi.Chat =
        kirim(TdApi.GetChat().apply { this.chatId = chatId })

    /**
     * True bila chat adalah supergroup biasa (bukan channel): satu-satunya
     * tipe yang sah sebagai vault (bisa menampung topik forum).
     * Aturan murninya di [PenjagaVault.grupBaruSah] agar bisa diuji unit.
     */
    fun adalahVaultSah(chat: TdApi.Chat): Boolean {
        val tipe = chat.type
        return PenjagaVault.grupBaruSah(
            tipe is TdApi.ChatTypeSupergroup,
            (tipe as? TdApi.ChatTypeSupergroup)?.isChannel ?: false,
        )
    }

    /**
     * Chat yang layak menjadi kandidat vault saat **deteksi**.
     *
     * Lebih longgar dari [adalahVaultSah]: channel milik sendiri juga
     * diterima karena aplikasi mendukung channel sebagai koleksi.
     */
    fun layakDeteksiVault(chat: TdApi.Chat): Boolean {
        val tipe = chat.type
        return PenjagaVault.layakDeteksi(
            tipe is TdApi.ChatTypeSupergroup,
            (tipe as? TdApi.ChatTypeSupergroup)?.isChannel ?: false,
        )
    }

    /**
     * Label Bahasa Indonesia untuk tipe chat, dipakai di pesan galat vault
     * agar pengguna tahu masalah sebenarnya (tipe salah, bukan kepemilikan).
     * Perbandingan nama kelas dipakai agar tak bergantung pada semua varian
     * TdApi.ChatType saat kompilasi.
     */
    fun labelTipeChat(chat: TdApi.Chat): String {
        val tipe = chat.type
        if (tipe is TdApi.ChatTypeSupergroup) {
            return if (tipe.isChannel) "channel" else "grup"
        }
        return when (tipe?.javaClass?.simpleName) {
            "ChatTypeBasicGroup" -> "grup biasa"
            "ChatTypePrivate" -> "chat pribadi"
            "ChatTypeSecret" -> "chat rahasia"
            else -> "tipe lain"
        }
    }

    /** Info ringkas satu topik forum di dalam grup vault. */
    data class InfoTopik(
        val chatId: Long,
        val topicId: Int,
        val nama: String,
        val umum: Boolean,
        val tertutup: Boolean,
    )

    /** Id pengguna sendiri via GetMe, di-cache selama hidup client. */
    @Volatile
    private var idSaya: Long? = null

    suspend fun sayaId(): Long =
        idSaya ?: kirim(TdApi.GetMe()).id.also { idSaya = it }

    private fun peranDariStatus(status: TdApi.ChatMemberStatus?): PeranPenggunaChat = when (status) {
        is TdApi.ChatMemberStatusCreator -> PeranPenggunaChat.PEMILIK
        is TdApi.ChatMemberStatusAdministrator -> PeranPenggunaChat.ADMIN
        is TdApi.ChatMemberStatusMember -> PeranPenggunaChat.ANGGOTA
        else -> PeranPenggunaChat.TAK_DIKETAHUI
    }

    /**
     * True bila pengguna sendiri adalah PEMILIK atau ADMIN grup ini.
     * Dipakai untuk menyaring "Pilih grup lama" agar hanya menampilkan
     * grup milik sendiri, bukan grup orang lain yang diikuti.
     *
     * Nama kelas TDLib diverifikasi dari AAR dependensi (capullo-tech,
     * commit 11850ef): GetChatMember memakai `memberId: MessageSender`,
     * bukan userId mentah — jadi anggota dinyatakan via MessageSenderUser.
     * Pendekatan fail-closed: bila gagal diperiksa atau bukan owner/admin,
     * kembalikan false.
     */
    suspend fun apakahPemilik(chatId: Long): Boolean {
        val anggota = try {
            kirim(
                TdApi.GetChatMember().apply {
                    this.chatId = chatId
                    memberId = TdApi.MessageSenderUser(sayaId())
                },
            )
        } catch (e: Exception) {
            Log.w(tag, "Gagal memeriksa status anggota untuk chat $chatId", e)
            return false
        }
        return AturanDeteksiVault.adalahPemilikAtauAdmin(peranDariStatus(anggota.status))
    }

    /**
     * Peran pengguna di sebuah chat (Paket A4 — badge Pemilik/Admin di
     * pemilih grup). Fail-closed: bila status tak bisa dibaca, kembalikan
     * TAK_DIKETAHUI agar pemanggil bisa menyembunyikannya.
     */
    suspend fun peranDiChat(chatId: Long): PeranPenggunaChat {
        val anggota = try {
            kirim(
                TdApi.GetChatMember().apply {
                    this.chatId = chatId
                    memberId = TdApi.MessageSenderUser(sayaId())
                },
            )
        } catch (e: Exception) {
            Log.w(tag, "Gagal membaca peran untuk chat $chatId", e)
            return PeranPenggunaChat.TAK_DIKETAHUI
        }
        return peranDariStatus(anggota.status)
    }

    /**
     * Supergroup (bukan channel) MILIK SENDIRI (owner/admin) dari seluruh obrolan —
     * kandidat vault. Melakukan paginasi GetChats / LoadChats hingga tuntas agar grup lama
     * di luar 100 teratas tetap terdeteksi. Grup yang gagal diperiksa kepemilikannya
     * dikeluarkan (fail-closed) + dicatat ke log; bila SEMUA pengecekan
     * gagal, galat dilempar agar UI menampilkan "Gagal memuat grup: ..."
     * bukan daftar kosong yang menyesatkan.
     */
    suspend fun daftarSupergroup(): List<TdApi.Chat> {
        val supergroup = muatSemuaSupergroup()
        var gagalPeriksa = 0
        val milikSendiri = supergroup.filter { chat ->
            try {
                apakahPemilik(chat.id)
            } catch (e: Exception) {
                gagalPeriksa++
                Log.w(tag, "Gagal memeriksa kepemilikan grup ${chat.id}", e)
                false
            }
        }
        if (supergroup.isNotEmpty() && gagalPeriksa == supergroup.size) {
            throw TelegramException(
                "Gagal memeriksa kepemilikan grup. Coba lagi.",
                0,
            )
        }
        return milikSendiri
    }

    /**
     * Supergroup milik sendiri BESERTA peran (owner/admin) — untuk pemilih
     * grup manual (Paket A4). Berbeda dengan [daftarSupergroup] yang hanya
     * mengembalikan grup milik pemilik (dipakai alur otomatis yang butuh
     * jaminan kepemilikan penuh), fungsi ini juga menyertakan grup tempat
     * pengguna berstatus admin, dan pemanggil memfilter via
     * [AturanDeteksiVault.saringKandidat] agar grup orang lain tetap
     * disembunyikan total.
     */
    suspend fun daftarSupergroupDenganPeran(): List<Pair<TdApi.Chat, PeranPenggunaChat>> {
        val supergroup = muatSemuaSupergroup()
        val hasil = supergroup.mapNotNull { chat ->
            val peran = try {
                peranDiChat(chat.id)
            } catch (e: Exception) {
                Log.w(tag, "Gagal memeriksa peran grup ${chat.id}", e)
                PeranPenggunaChat.TAK_DIKETAHUI
            }
            if (!AturanDeteksiVault.adalahPemilikAtauAdmin(peran)) null else chat to peran
        }
        if (supergroup.isNotEmpty() && hasil.isEmpty()) {
            Log.w(tag, "Semua grup tersaring peran — kemungkinan semua pemeriksaan gagal.")
        }
        return hasil
    }

    /**
     * Muat SELURUH supergroup/channel yang layak dideteksi (tanpa filter
     * kepemilikan). Diekstrak dari [daftarSupergroup] agar logika paginasi
     * hanya ada di satu tempat.
     */
    private suspend fun muatSemuaSupergroup(): List<TdApi.Chat> {
                val semuaChatId = linkedSetOf<Long>()
                var putaran = 0
                while (putaran < 50) {
                    putaran++
                    val adaLagi = try {
                        kirim(
                            TdApi.LoadChats().apply {
                                chatList = TdApi.ChatListMain()
                                limit = 100
                            },
                        )
                        true
                    } catch (e: Exception) {
                        // TDLib melempar 404 ketika seluruh obrolan sudah termuat
                        false
                    }

                    val batch = try {
                        kirim(
                            TdApi.GetChats().apply {
                                chatList = TdApi.ChatListMain()
                                limit = 100
                            },
                        )
                    } catch (e: Exception) {
                        null
                    }

                    if (batch == null || batch.chatIds.isEmpty()) {
                        if (!adaLagi) break
                    } else {
                        val sebelum = semuaChatId.size
                        semuaChatId.addAll(batch.chatIds.toList())
                        if (semuaChatId.size == sebelum && !adaLagi) break
                    }

                    if (!adaLagi) break
                }

                if (semuaChatId.isEmpty()) {
                    val fallback = runCatching {
                        kirim(
                            TdApi.GetChats().apply {
                                chatList = TdApi.ChatListMain()
                                limit = 100
                            },
                        )
                    }.getOrNull()
                    if (fallback != null) {
                        semuaChatId.addAll(fallback.chatIds.toList())
                    }
                }
        return semuaChatId
            .mapNotNull { id ->
                try {
                    kirim(TdApi.GetChat().apply { chatId = id })
                } catch (e: Exception) {
                    Log.w(tag, "Gagal memuat chat $id", e)
                    null
                }
            }
            .filter { layakDeteksiVault(it) }
    }

    /**
     * Cari chat berdasarkan nama (SearchChats menjangkau SELURUH daftar
     * chat, bukan hanya 100 teratas seperti GetChats). Dipakai deteksi
     * vault bernama sama yang sudah lama tidak aktif.
     */
    suspend fun cariChat(nama: String): List<TdApi.Chat> {
        val daftar = kirim(
            TdApi.SearchChats().apply {
                query = nama.trim()
                limit = 50
            },
        )
        return daftar.chatIds.toList()
            .mapNotNull { id ->
                try {
                    kirim(TdApi.GetChat().apply { chatId = id })
                } catch (e: Exception) {
                    Log.w(tag, "Gagal memuat chat $id", e)
                    null
                }
            }
            .filter { layakDeteksiVault(it) }
    }

    /**
     * Buat grup vault forum baru bernama [nama], dalam DUA tahap:
     *
     * 1. Buat supergroup BIASA (`isForum = false`, `isChannel = false`),
     *    lalu verifikasi hasilnya benar-benar grup via GetChat.
     * 2. Aktifkan mode forum via ToggleSupergroupIsForum, lalu verifikasi
     *    lagi.
     *
     * Satu tahap tidak bisa dipakai: terbukti di perangkat (Build #26 dan
     * #27) bahwa kombinasi `isForum = true` dalam satu panggilan
     * CreateNewSupergroupChat menghasilkan CHANNEL, bukan grup forum —
     * meski skema TDLib menyebut isChannel "diabaikan bila forum dibuat".
     * Memisahkan pembuatan grup dan pengaktifan forum menghindari
     * kombinasi yang rusak itu; ToggleSupergroupIsForum sendiri sudah
     * terbukti jalan di jalur pilih-grup-lama / self-healing.
     *
     * Bila tahap 1 menghasilkan channel, lempar galat jelas dan JANGAN
     * aktifkan. Bila tahap 2 gagal, grup biasanya tetap terbentuk sebagai
     * grup biasa dan bisa dipakai lewat "Pilih grup lama" (forum
     * diaktifkan saat dipilih).
     */
    suspend fun buatVault(nama: String): TdApi.Chat {
        val chat = kirim(
            TdApi.CreateNewSupergroupChat().apply {
                title = nama.trim()
                isForum = false
                isChannel = false
                description = ""
                location = null
                messageAutoDeleteTime = 0
                forImport = false
            },
        )
        val grup = ambilChat(chat.id)
        if (!adalahVaultSah(grup)) {
            throw TelegramException(
                PenjagaVault.pesanBuatVaultChannel(labelTipeChat(grup)),
                0,
            )
        }
        aktifkanForum(grup.id)
        val forum = ambilChat(chat.id)
        if (!adalahVaultSah(forum)) {
            throw TelegramException(
                PenjagaVault.pesanBuatVaultChannel(labelTipeChat(forum)),
                0,
            )
        }
        return forum
    }

    /**
     * Aktifkan mode forum pada supergroup lama (yang belum forum), agar bisa
     * menampung topik. Membutuhkan hak pemilik grup.
     *
     * @throws TelegramException bila gagal (mis. bukan pemilik, atau grup
     * diskusi yang memang tidak bisa jadi forum).
     */
    suspend fun aktifkanForum(chatId: Long) {
        kirim(
            TdApi.ToggleSupergroupIsForum().apply {
                supergroupId = grupIdDariChat(chatId)
                isForum = true
                hasForumTabs = false
            },
        )
    }

    /**
     * Ubah chatId supergroup (-100xxx) menjadi supergroupId (xxx) untuk API
     * yang memakai identifier supergroup, mis. [ToggleSupergroupIsForum].
     */
    fun grupIdDariChat(chatId: Long): Long {
        require(chatId <= -1_000_000_000_000L) { "Bukan chat supergroup: $chatId" }
        return -chatId - 1_000_000_000_000L
    }

    /** Seluruh topik forum di satu chat; paginasi ditangani otomatis. */
    suspend fun daftarTopik(chatId: Long): List<InfoTopik> {
        val hasil = mutableListOf<InfoTopik>()
        var offsetDate = 0
        var offsetMessageId = 0L
        var offsetForumTopicId = 0
        do {
            val halaman = kirim(
                TdApi.GetForumTopics().apply {
                    this.chatId = chatId
                    query = ""
                    this.offsetDate = offsetDate
                    this.offsetMessageId = offsetMessageId
                    this.offsetForumTopicId = offsetForumTopicId
                    limit = 100
                },
            )
            halaman.topics?.forEach { topik ->
                val info = topik.info ?: return@forEach
                hasil += InfoTopik(
                    chatId = chatId,
                    topicId = info.forumTopicId,
                    nama = info.name,
                    umum = info.isGeneral,
                    tertutup = info.isClosed,
                )
            }
            offsetDate = halaman.nextOffsetDate
            offsetMessageId = halaman.nextOffsetMessageId
            offsetForumTopicId = halaman.nextOffsetForumTopicId
        } while (offsetForumTopicId != 0)
        return hasil
    }

    /**
     * Cari topik berdasar nama (case-insensitive); buat otomatis bila belum ada.
     * Inilah jalur yang dipakai unggah: kategori yang hilang dibuat sendiri.
     */
    suspend fun pastikanTopik(chatId: Long, nama: String): InfoTopik {
        val bersih = nama.trim()
        require(bersih.isNotEmpty()) { "Nama topik kosong." }
        daftarTopik(chatId)
            .firstOrNull { it.nama.equals(bersih, ignoreCase = true) }
            ?.let { return it }
        return buatTopik(chatId, bersih)
    }

    /** Buat topik forum baru dan kembalikan infonya. */
    suspend fun buatTopik(chatId: Long, nama: String): InfoTopik {
        val bersih = nama.trim()
        require(bersih.isNotEmpty()) { "Nama topik kosong." }
        val info = kirim(
            TdApi.CreateForumTopic().apply {
                this.chatId = chatId
                this.name = bersih
                isNameImplicit = false
                icon = TdApi.ForumTopicIcon().apply {
                    color = WARNA_TOPIK
                    customEmojiId = 0L
                }
            },
        )
        return InfoTopik(chatId, info.forumTopicId, info.name, info.isGeneral, info.isClosed)
    }

    /** Ganti nama topik forum. */
    suspend fun ubahTopik(chatId: Long, topicId: Int, nama: String) {
        kirim(
            TdApi.EditForumTopic().apply {
                this.chatId = chatId
                this.forumTopicId = topicId
                this.name = nama.trim()
                editIconCustomEmoji = false
                iconCustomEmojiId = 0L
            },
        )
    }

    /** Hapus topik forum beserta seluruh pesannya. */
    suspend fun hapusTopik(chatId: Long, topicId: Int) {
        kirim(
            TdApi.DeleteForumTopic().apply {
                this.chatId = chatId
                this.forumTopicId = topicId
            },
        )
    }

    /** Riwayat pesan di dalam satu topik forum. */
    suspend fun riwayatTopik(
        chatId: Long,
        topicId: Int,
        dariMessageId: Long,
        batas: Int,
    ): List<TdApi.Message> {
        val hasil = kirim(
            TdApi.GetForumTopicHistory().apply {
                this.chatId = chatId
                this.forumTopicId = topicId
                fromMessageId = dariMessageId
                offset = 0
                limit = batas
            },
        )
        return hasil.messages?.toList().orEmpty()
    }

    suspend fun riwayat(chatId: Long, dariMessageId: Long, batas: Int): List<TdApi.Message> {
        val hasil = kirim(
            TdApi.GetChatHistory().apply {
                this.chatId = chatId
                fromMessageId = dariMessageId
                offset = 0
                limit = batas
                onlyLocal = false
            },
        )
        return hasil.messages?.toList().orEmpty()
    }

    // ---------------------------------------------------------------------
    // Unggah
    // ---------------------------------------------------------------------

    /**
     * Kirim satu berkas lokal ke koleksi. [tipe] menentukan jenis konten
     * pesan (foto/video/audio/dokumen) agar pratinjau Telegram tetap rapi.
     * [topicId] != 0 berarti kirim ke dalam topik forum (message thread).
     */
    suspend fun kirimBerkas(
        chatId: Long,
        path: String,
        tipe: TipeFile,
        caption: String,
        topicId: Int = 0,
    ): TdApi.Message {
        // Nama lokal agar tidak tertutup (shadow) oleh field `caption`
        // milik TdApi di dalam blok apply di bawah.
        val teks = caption
        val isi = when (tipe) {
            TipeFile.GAMBAR -> TdApi.InputMessagePhoto().apply {
                photo = TdApi.InputFileLocal().apply { this.path = path }
                width = 0
                height = 0
                this.caption = teksCaption(teks)
            }
            TipeFile.VIDEO -> TdApi.InputMessageVideo().apply {
                video = TdApi.InputFileLocal().apply { this.path = path }
                duration = 0
                width = 0
                height = 0
                supportsStreaming = true
                this.caption = teksCaption(teks)
            }
            TipeFile.AUDIO -> TdApi.InputMessageAudio().apply {
                audio = TdApi.InputFileLocal().apply { this.path = path }
                duration = 0
                title = teks
                performer = null
                this.caption = teksCaption(teks)
            }
            else -> TdApi.InputMessageDocument().apply {
                document = TdApi.InputFileLocal().apply { this.path = path }
                this.caption = teksCaption(teks)
            }
        }
        return kirim(
            TdApi.SendMessage().apply {
                this.chatId = chatId
                this.topicId =
                    if (topicId != 0) TdApi.MessageTopicForum(topicId) else null
                replyTo = null
                options = null
                replyMarkup = null
                inputMessageContent = isi
            },
        )
    }

    private fun teksCaption(teks: String): TdApi.FormattedText =
        TdApi.FormattedText().apply {
            text = teks
            entities = emptyArray()
        }

    /**
     * Tunggu unggah [fileId] mencapai 100% dengan memantau UpdateFile.
     * Progres perantara bisa disimak dari [progresBerkas] sebelum memanggil ini.
     */
    suspend fun tungguUnggah(fileId: Int, batasMillis: Long = BATAS_UNGGAH): ProgresBerkas =
        withTimeout(batasMillis) {
            progresBerkas.first { it.fileId == fileId && it.selesaiUnggah }
        }

    /** Tunggu pesan ber-[messageId] benar-benar terkirim (unggah selesai). */
    suspend fun tungguTerkirim(messageId: Long, batasMillis: Long = BATAS_UNGGAH): PesanTerkirim =
        withTimeout(batasMillis) {
            pesanTerkirim.first {
                it.pesan.id == messageId || it.idLama == messageId
            }
        }

    // ---------------------------------------------------------------------
    // Unduh
    // ---------------------------------------------------------------------

    /** Minta TDLib mengunduh berkas; hasilnya dipantau lewat [progresBerkas]. */
    suspend fun mintaUnduh(fileId: Int, prioritas: Int = 32): TdApi.File =
        kirim(
            TdApi.DownloadFile().apply {
                this.fileId = fileId
                priority = prioritas
                offset = 0
                limit = 0
                synchronous = false
            },
        )

    /**
     * Unduh berkas sampai selesai. Progres dapat dipantau dengan menyaring
     * [progresBerkas] berdasarkan fileId sebelum memanggil fungsi ini.
     */
    suspend fun unduh(fileId: Int): TdApi.File = coroutineScope {
        val menunggu = async {
            progresBerkas.first { it.fileId == fileId && it.selesaiUnduh }
        }
        val awal = mintaUnduh(fileId)
        if (awal.local?.isDownloadingCompleted == true) {
            menunggu.cancel()
            return@coroutineScope awal
        }
        menunggu.await()
        kirim(TdApi.GetFile().apply { this.fileId = fileId })
    }

    /** Ambil metadata berkas TDLib (misalnya untuk menyegarkan ukuran bila masih 0). */
    suspend fun ambilFile(fileId: Int): TdApi.File =
        kirim(TdApi.GetFile().apply { this.fileId = fileId })

    /** Ambil pesan tertentu dari Telegram (untuk konfirmasi status pengiriman). */
    suspend fun ambilPesan(chatId: Long, messageId: Long): TdApi.Message =
        kirim(TdApi.GetMessage(chatId, messageId))

    /** Hapus satu atau beberapa pesan dari chat Telegram. */
    suspend fun hapusPesan(chatId: Long, messageIds: List<Long>, untukSemua: Boolean = true) {
        kirim<TdApi.Ok>(
            TdApi.DeleteMessages().apply {
                this.chatId = chatId
                this.messageIds = messageIds.toLongArray()
                this.revoke = untukSemua
            },
        )
    }

    // ---------------------------------------------------------------------
    // Tautan
    // ---------------------------------------------------------------------

    suspend fun tautanPesan(chatId: Long, messageId: Long, dalamThread: Boolean = false): String {
        val hasil = kirim(
            TdApi.GetMessageLink().apply {
                this.chatId = chatId
                this.messageId = messageId
                mediaTimestamp = 0
                pollOptionId = null
                forAlbum = false
                inMessageThread = dalamThread
            },
        )
        return hasil.link
    }

    suspend fun buatTautanUndangan(chatId: Long, nama: String): TdApi.ChatInviteLink =
        kirim(
            TdApi.CreateChatInviteLink().apply {
                this.chatId = chatId
                this.name = nama
                expirationDate = 0
                memberLimit = 0
                createsJoinRequest = false
            },
        )

    suspend fun cabutTautan(chatId: Long, inviteLink: String): TdApi.ChatInviteLinks =
        kirim(
            TdApi.RevokeChatInviteLink().apply {
                this.chatId = chatId
                this.inviteLink = inviteLink
            },
        )

    companion object {
        private const val BATAS_UNGGAH = 30L * 60L * 1000L

        /**
         * Batas waktu default satu permintaan biasa (bukan unggah/unduh
         * yang memang lambat). Cukup untuk operasi normal; mencegah
         * goroutine menggantung permanen saat perangkat offline.
         */
        private const val BATAS_KIRIM_MS = 45_000L

        /** Warna ikon topik forum (RGB, aksen amber khas Arsip Senja). */
        private const val WARNA_TOPIK = 0xE8A33D

        @Volatile
        private var pustakaDimuat = false
    }
}
