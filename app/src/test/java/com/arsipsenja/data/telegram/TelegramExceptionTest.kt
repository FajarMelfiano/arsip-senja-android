package com.arsipsenja.data.telegram

import com.arsipsenja.util.KebijakanCobaLagi
import com.arsipsenja.util.KeputusanCobaLagi
import com.google.common.truth.Truth.assertThat
import org.drinkless.tdlib.TdApi
import org.junit.Test

/**
 * Uji pemetaan galat TDLib menjadi keputusan percobaan ulang.
 *
 * Ini yang menghentikan unduhan berkas yang pesannya sudah dihapus: dulu
 * `Result.retry()` dipanggil tanpa syarat apa pun, sehingga berkas yang mustahil
 * diunduh tetap dicoba selamanya.
 */
class TelegramExceptionTest {

    @Test
    fun `galat 401 unauthorized dihentikan agar pengguna diminta masuk ulang`() {
        val e = TelegramException(TdApi.Error(401, "Unauthorized"))
        assertThat(e.kode).isEqualTo(401)
        assertThat(e.permanen).isTrue()
        assertThat(e.jedaMilidetik()).isEqualTo(0L)
    }

    @Test
    fun `galat 403 forbidden dihentikan`() {
        assertThat(TelegramException(TdApi.Error(403, "Forbidden")).permanen).isTrue()
    }

    @Test
    fun `flood wait dibaca jeda dari teks pesan dan ditunda`() {
        // Binding yang dipakai tidak mengekspos parameters["retry_after"],
        // jadi durasi diambil dari teks pesan.
        val e = TelegramException(TdApi.Error(420, "FLOOD_WAIT_42"))
        assertThat(e.retryAfterDetik).isEqualTo(42)
        assertThat(e.permanen).isFalse()
        assertThat(e.jedaMilidetik()).isEqualTo(42_000L)
    }

    @Test
    fun `flood wait tanpa angka memakai jeda bawaan`() {
        val e = TelegramException(TdApi.Error(420, "Too Many Requests"))
        assertThat(e.retryAfterDetik).isNull()
        assertThat(e.jedaMilidetik()).isEqualTo(60_000L)
    }

    @Test
    fun `durasi ridiculous ditolak agar tidak salah baca sebuah id`() {
        // Angka besar di pesan bisa berupa id obrolan, bukan durasi.
        assertThat(TelegramException.ambilDurasiTunggu("chat 1234567890123 gagal")).isNull()
    }

    @Test
    fun `pesan kosong tidak menghasilkan durasi`() {
        assertThat(TelegramException.ambilDurasiTunggu(null)).isNull()
        assertThat(TelegramException.ambilDurasiTunggu("")).isNull()
        assertThat(TelegramException.ambilDurasiTunggu("   ")).isNull()
    }

    @Test
    fun `galat 500 memakai backoff yang tumbuh per percobaan`() {
        val e = TelegramException(TdApi.Error(500, "Internal Server Error"))
        val pertama = e.jedaMilidetik(percobaan = 0)
        val kedua = e.jedaMilidetik(percobaan = 1)
        assertThat(e.permanen).isFalse()
        assertThat(kedua).isGreaterThan(pertama)
    }

    @Test
    fun `galat yang menyertakan sebab tetap menyimpan cause`() {
        val sebab = IllegalStateException("akar masalah")
        val e = TelegramException("gagal", 0, sebab)
        assertThat(e.cause).isSameInstanceAs(sebab)
    }

    @Test
    fun `keputusan konsisten dengan kebijakan retry terpusat`() {
        val e = TelegramException(TdApi.Error(420, "FLOOD_WAIT_7"))
        assertThat(e.keputusan()).isEqualTo(KebijakanCobaLagi.putuskan(420, 7))
    }

    @Test
    fun `galat non-TDLib tanpa kode dianggap sementara`() {
        val e = TelegramException("masalah jaringan")
        assertThat(e.kode).isEqualTo(0)
        assertThat(e.keputusan(percobaan = 2))
            .isEqualTo(KeputusanCobaLagi.Backoff(2))
    }
}