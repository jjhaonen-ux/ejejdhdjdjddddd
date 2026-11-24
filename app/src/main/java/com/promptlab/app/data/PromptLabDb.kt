package com.promptlab.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** عنصر سجل: الأصل مستقل عن النتيجة، والتعديل اليدوي لا يمحو الأصل (FR-11/FR-12) */
@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val original: String,
    val result: String,
    val mode: String,
    val taskType: String,
    val score: Int,
    val createdAt: Long,
    val favorite: Boolean = false,
)

/** مسودة واحدة تُحفظ تلقائيًا وتُستعاد بعد القتل والتدوير (NFR-04) */
@Entity(tableName = "draft")
data class Draft(
    @PrimaryKey val id: Int = 1,
    val text: String,
    val updatedAt: Long,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun all(): Flow<List<HistoryItem>>

    @Insert
    suspend fun insert(item: HistoryItem): Long

    @Update
    suspend fun update(item: HistoryItem)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAll()
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM draft WHERE id = 1")
    suspend fun get(): Draft?

    @Query("SELECT * FROM draft WHERE id = 1")
    fun observe(): Flow<Draft?>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: Draft)
}

@Database(entities = [HistoryItem::class, Draft::class], version = 1, exportSchema = false)
abstract class PromptLabDb : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun draftDao(): DraftDao

    companion object {
        @Volatile private var instance: PromptLabDb? = null

        fun get(context: Context): PromptLabDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PromptLabDb::class.java,
                    "promptlab.db",
                ).build().also { instance = it }
            }
    }
}
