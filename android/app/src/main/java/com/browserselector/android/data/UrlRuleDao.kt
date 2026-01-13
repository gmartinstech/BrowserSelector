package com.browserselector.android.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.browserselector.android.model.UrlRule
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for UrlRule entities.
 */
@Dao
interface UrlRuleDao {

    @Query("SELECT * FROM url_rules ORDER BY priority DESC, createdAt DESC")
    fun getAllRules(): Flow<List<UrlRule>>

    @Query("SELECT * FROM url_rules ORDER BY priority DESC, createdAt DESC")
    suspend fun getAllRulesList(): List<UrlRule>

    @Query("SELECT * FROM url_rules WHERE id = :id")
    suspend fun getRule(id: Int): UrlRule?

    @Query("SELECT * FROM url_rules WHERE pattern = :pattern LIMIT 1")
    suspend fun getRuleByPattern(pattern: String): UrlRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: UrlRule): Long

    @Update
    suspend fun updateRule(rule: UrlRule)

    @Delete
    suspend fun deleteRule(rule: UrlRule)

    @Query("DELETE FROM url_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Int)

    @Query("SELECT COUNT(*) FROM url_rules")
    suspend fun getRuleCount(): Int

    @Query("SELECT MAX(priority) FROM url_rules")
    suspend fun getMaxPriority(): Int?

    @Query("UPDATE url_rules SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: Int, priority: Int)
}
