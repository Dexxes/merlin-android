package dev.merlin.android.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ImageCacheIndexDao {

    @Query("SELECT * FROM image_cache_index WHERE articleId = :articleId")
    suspend fun get(articleId: Int): ImageCacheIndexEntity?

    @Upsert
    suspend fun upsert(entity: ImageCacheIndexEntity)

    @Query("DELETE FROM image_cache_index WHERE articleId = :articleId")
    suspend fun delete(articleId: Int)

    @Query("DELETE FROM image_cache_index")
    suspend fun clear()
}
