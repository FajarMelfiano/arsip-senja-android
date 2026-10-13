package com.arsipsenja.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TipeFileTest {

    @Test
    fun `mime gambar video audio`() {
        assertThat(TipeFile.dariMime("image/png", "a.png")).isEqualTo(TipeFile.GAMBAR)
        assertThat(TipeFile.dariMime("video/mp4", "a.mp4")).isEqualTo(TipeFile.VIDEO)
        assertThat(TipeFile.dariMime("audio/mpeg", "a.mp3")).isEqualTo(TipeFile.AUDIO)
        assertThat(TipeFile.dariMime("IMAGE/JPEG", "a.jpg")).isEqualTo(TipeFile.GAMBAR)
    }

    @Test
    fun `mime arsip dan dokumen`() {
        assertThat(TipeFile.dariMime("application/zip", "a.zip")).isEqualTo(TipeFile.ARSIP)
        assertThat(TipeFile.dariMime("application/x-7z-compressed", "a.7z")).isEqualTo(TipeFile.ARSIP)
        assertThat(TipeFile.dariMime("application/pdf", "a.pdf")).isEqualTo(TipeFile.DOKUMEN)
        assertThat(TipeFile.dariMime("text/plain", "a.txt")).isEqualTo(TipeFile.DOKUMEN)
    }

    @Test
    fun `mime kosong jatuh ke ekstensi`() {
        assertThat(TipeFile.dariMime("", "lagu.flac")).isEqualTo(TipeFile.AUDIO)
        assertThat(TipeFile.dariMime(null, "video.mkv")).isEqualTo(TipeFile.VIDEO)
        assertThat(TipeFile.dariMime(null, "tanpa_ekstensi")).isEqualTo(TipeFile.LAINNYA)
    }

    @Test
    fun `mime tak dikenal memakai ekstensi`() {
        assertThat(TipeFile.dariMime("application/x-custom", "arsip.tar")).isEqualTo(TipeFile.ARSIP)
        assertThat(TipeFile.dariMime("application/x-custom", "data.xyz")).isEqualTo(TipeFile.LAINNYA)
    }

    @Test
    fun `ekstensi case insensitive dan berantakan`() {
        assertThat(TipeFile.dariEkstensi("Foto.JPG")).isEqualTo(TipeFile.GAMBAR)
        assertThat(TipeFile.dariEkstensi(".bashrc")).isEqualTo(TipeFile.LAINNYA)
        assertThat(TipeFile.dariEkstensi("arsip.tar.gz")).isEqualTo(TipeFile.ARSIP)
    }

    @Test
    fun `pemotongan nama berkas aman`() {
        assertThat(namaBerkas("/storage/emulated/0/Download/rapor.pdf")).isEqualTo("rapor.pdf")
        assertThat(namaBerkas("tanpath")).isEqualTo("tanpath")
        assertThat(ekstensi("laporan akhir.DOCX")).isEqualTo("docx")
        assertThat(ekstensi("tanpa-titik")).isEqualTo("")
        assertThat(ekstensi("akhirdot.")).isEqualTo("")
    }
}
