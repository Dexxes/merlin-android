package dev.merlin.android.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlights WHERE articleId = :articleId")
    suspend fun get(articleId: Int): HighlightEntity?

    @Upsert
    suspend fun upsert(entity: HighlightEntity)

    @Query("DELETE FROM highlights WHERE articleId = :articleId")
    suspend fun delete(articleId: Int)

    @Query("DELETE FROM highlights")
    suspend fun clear()
}
