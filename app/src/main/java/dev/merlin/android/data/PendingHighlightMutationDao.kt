package dev.merlin.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingHighlightMutationDao {

    @Query("SELECT * FROM pending_highlight_mutations")
    suspend fun getAll(): List<PendingHighlightMutationEntity>

    @Insert
    suspend fun insert(entity: PendingHighlightMutationEntity)

    @Insert
    suspend fun insertAll(entities: List<PendingHighlightMutationEntity>)

    @Query("DELETE FROM pending_highlight_mutations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM pending_highlight_mutations WHERE tempId = :tempId AND articleId = :articleId AND kind = 'CREATE' LIMIT 1")
    suspend fun findPendingCreateByTempId(tempId: String, articleId: Int): PendingHighlightMutationEntity?

    @Query("DELETE FROM pending_highlight_mutations")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM pending_highlight_mutations")
    suspend fun count(): Int
}
