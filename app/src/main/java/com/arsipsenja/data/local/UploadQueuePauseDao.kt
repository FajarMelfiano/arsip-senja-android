package com.arsipsenja.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadQueuePauseDao {

    /**
     * Menyimpan atau memperbarui status jeda dan prioritas untuk pekerjaan unggah.
     * Jika sudah ada, akan diperbarui; jika tidak, akan disisipkan.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pauseEntity: UploadQueuePauseEntity)

    /**
     * Mendapatkan status jeda dan prioritas untuk pekerjaan unggah tertentu.
     * Mengembalikan null jika tidak ada entri (pekerjaan belum pernah diantrekan).
     */
    @Query("SELECT * FROM upload_queue_pause WHERE workId = :workId")
    suspend fun getPauseStatus(workId: String): UploadQueuePauseEntity?

    /**
     * Mengubah status jeda untuk pekerjaan unggah tertentu.
     */
    @Update
    suspend fun updatePauseStatus(pauseEntity: UploadQueuePauseEntity)

    /**
     * Mengamati status jeda secara reaktif untuk UI antrean.
     * Null bila pekerjaan belum pernah dicatat (dianggap tidak jeda).
     */
    @Query("SELECT isPaused FROM upload_queue_pause WHERE workId = :workId")
    fun amatiJeda(workId: String): Flow<Int?>

    /** Daftar workId yang ditandai prioritas — untuk pengurutan antrean. */
    @Query("SELECT workId FROM upload_queue_pause WHERE priority = 1")
    fun amatiPrioritas(): Flow<List<String>>
}