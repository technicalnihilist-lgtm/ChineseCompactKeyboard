package com.example.chinesecompactkeyboard.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "clipboard_entries")
data class ClipboardEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long
)

/** Words the user actually picked, used to re-rank candidates. */
@Entity(tableName = "user_words")
data class UserWord(
    @PrimaryKey val word: String,
    val freq: Int,
    val updatedAt: Long
)

/** Learned word-to-word transitions, used for context predictions. */
@Entity(tableName = "user_bigrams", primaryKeys = ["prev", "next"])
data class UserBigram(
    val prev: String,
    val next: String,
    val freq: Int,
    val updatedAt: Long
)

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_entries ORDER BY createdAt DESC, id DESC")
    suspend fun all(): List<ClipboardEntry>

    @Query("SELECT * FROM clipboard_entries ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun newest(): ClipboardEntry?

    @Query("SELECT COUNT(*) FROM clipboard_entries")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entry: ClipboardEntry): Long

    @Query("DELETE FROM clipboard_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM clipboard_entries")
    suspend fun clear()

    @Query("SELECT id FROM clipboard_entries ORDER BY createdAt DESC, id DESC LIMIT -1 OFFSET :keep")
    suspend fun idsBeyond(keep: Int): List<Long>

    @Query("DELETE FROM clipboard_entries WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Transaction
    suspend fun trimTo(limit: Int) {
        val extra = idsBeyond(limit)
        if (extra.isNotEmpty()) deleteIds(extra)
    }
}

@Dao
interface LearningDao {
    @Query("SELECT * FROM user_words ORDER BY freq DESC LIMIT :limit")
    suspend fun topWords(limit: Int): List<UserWord>

    @Query("SELECT * FROM user_words")
    suspend fun allWords(): List<UserWord>

    @Query("SELECT * FROM user_bigrams ORDER BY freq DESC LIMIT :limit")
    suspend fun topBigrams(limit: Int): List<UserBigram>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWords(words: List<UserWord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBigrams(bigrams: List<UserBigram>)

    @Query("DELETE FROM user_words")
    suspend fun clearWords()

    @Query("DELETE FROM user_bigrams")
    suspend fun clearBigrams()
}

@Database(
    entities = [ClipboardEntry::class, UserWord::class, UserBigram::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
    abstract fun learningDao(): LearningDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chinese_keyboard.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
