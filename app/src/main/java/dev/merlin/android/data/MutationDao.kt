package dev.merlin.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MutationDao {

    @Query("SELECT * FROM pending_mutations")
    suspend fun getAll(): List<PendingMutationEntity>

    @Insert
    suspend fun insert(entity: PendingMutationEntity)

    @Insert
    suspend fun insertAll(entities: List<PendingMutationEntity>)

    @Query("DELETE FROM pending_mutations WHERE articleId = :articleId")
    suspend fun deleteByArticle(articleId: Int)

    @Query("DELETE FROM pending_mutations WHERE articleId = :articleId AND kind = :kind")
    suspend fun deleteByArticleAndKind(articleId: Int, kind: PendingMutationKind)

    @Query("DELETE FROM pending_mutations")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM pending_mutations")
    suspend fun count(): Int
}
