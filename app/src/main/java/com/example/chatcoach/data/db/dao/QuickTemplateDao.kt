package com.example.chatcoach.data.db.dao

import androidx.room.*
import com.example.chatcoach.data.db.entity.QuickTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickTemplateDao {
    @Query("SELECT * FROM quick_templates ORDER BY usageCount DESC")
    fun getAllTemplates(): Flow<List<QuickTemplate>>

    @Query("SELECT * FROM quick_templates WHERE category = :category ORDER BY usageCount DESC")
    fun getByCategory(category: String): Flow<List<QuickTemplate>>

    @Query("SELECT * FROM quick_templates WHERE id = :id")
    suspend fun getById(id: Long): QuickTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: QuickTemplate): Long

    @Update
    suspend fun update(template: QuickTemplate)

    @Delete
    suspend fun delete(template: QuickTemplate)

    @Query("UPDATE quick_templates SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsage(id: Long)

    @Query("SELECT COUNT(*) FROM quick_templates")
    suspend fun getCount(): Int
}
