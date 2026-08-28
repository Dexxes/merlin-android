package dev.merlin.android.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ArticleDao {

    @Query("SELECT * FROM articles")
    suspend fun getAll(): List<ArticleEntity>

    @Upsert
    suspend fun upsertAll(entities: List<ArticleEntity>)

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM articles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    @Query("DELETE FROM articles")
    suspend fun clearAll()
}
