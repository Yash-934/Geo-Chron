package com.geochron.atlas.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val nameHi: String,
    val nameEn: String,
    val category: String,
    val infoHi: String,
    val infoEn: String,
    val factHi: String,
    val dateLabel: String,
    val source: String,
    val scope: String,
    val lon: Double,
    val lat: Double,
    val savedAt: Long
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
    val placeId: String?,
    val updatedAt: Long
)

@Entity(tableName = "recents")
data class RecentEntity(
    @PrimaryKey val id: String,
    val nameHi: String,
    val nameEn: String,
    val scope: String,
    val viewedAt: Long
)

@Entity(tableName = "quiz_progress")
data class QuizProgressEntity(
    @PrimaryKey val mode: String,
    val bestScore: Int = 0,
    val gamesPlayed: Int = 0,
    val totalCorrect: Int = 0,
    val totalAsked: Int = 0,
    val wrongIds: String = "",       // comma-joined place ids for the revision list
    val updatedAt: Long = 0
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val at: Long
)

@Dao
interface StudyDao {
    @Query("SELECT * FROM bookmarks ORDER BY savedAt DESC")
    fun bookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT id FROM bookmarks")
    fun bookmarkIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(b: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun removeBookmark(id: String)

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun notes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(n: NoteEntity)

    @Delete
    suspend fun deleteNote(n: NoteEntity)

    @Query("SELECT * FROM recents ORDER BY viewedAt DESC LIMIT 20")
    fun recents(): Flow<List<RecentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRecent(r: RecentEntity)

    @Query("SELECT * FROM quiz_progress")
    fun quizProgress(): Flow<List<QuizProgressEntity>>

    @Query("SELECT * FROM quiz_progress WHERE mode = :mode")
    suspend fun progressFor(mode: String): QuizProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(p: QuizProgressEntity)

    @Query("SELECT * FROM search_history ORDER BY at DESC LIMIT 15")
    fun searchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert
    suspend fun addSearch(s: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}

@Database(
    entities = [BookmarkEntity::class, NoteEntity::class, RecentEntity::class, QuizProgressEntity::class, SearchHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun dao(): StudyDao

    companion object {
        @Volatile private var instance: StudyDatabase? = null
        fun get(context: Context): StudyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, StudyDatabase::class.java, "geochron_study.db")
                .build().also { instance = it }
        }
    }
}

/** Thin repository over the Room DAO used by the ViewModel. */
class StudyRepository(private val dao: StudyDao) {
    val bookmarks = dao.bookmarks()
    val bookmarkIds = dao.bookmarkIds()
    val notes = dao.notes()
    val recents = dao.recents()
    val quizProgress = dao.quizProgress()
    val searchHistory = dao.searchHistory()

    suspend fun toggleBookmark(place: PlaceInfo, currentlyBookmarked: Boolean) {
        if (currentlyBookmarked) dao.removeBookmark(place.id)
        else dao.addBookmark(
            BookmarkEntity(
                id = place.id, nameHi = place.nameHi, nameEn = place.nameEn,
                category = place.categoryEn, infoHi = place.infoHi, infoEn = place.infoEn,
                factHi = place.factHi, dateLabel = place.dateLabel, source = place.source,
                scope = place.scope, lon = place.lon, lat = place.lat,
                savedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addRecent(place: PlaceInfo) {
        dao.addRecent(RecentEntity(place.id, place.nameHi, place.nameEn, place.scope, System.currentTimeMillis()))
    }

    suspend fun saveNote(title: String, text: String, placeId: String?) {
        dao.saveNote(NoteEntity(title = title, text = text, placeId = placeId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNote(note: NoteEntity) = dao.deleteNote(note)

    suspend fun recordSearch(query: String) {
        if (query.isNotBlank()) dao.addSearch(SearchHistoryEntity(query = query.trim(), at = System.currentTimeMillis()))
    }

    suspend fun clearSearchHistory() = dao.clearSearchHistory()

    suspend fun recordQuizResult(mode: String, score: Int, asked: Int, wrongIds: List<String>) {
        val old = dao.progressFor(mode)
        val mergedWrong = ((old?.wrongIds?.split(",") ?: emptyList()) + wrongIds)
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().takeLast(60)
        dao.saveProgress(
            QuizProgressEntity(
                mode = mode,
                bestScore = maxOf(old?.bestScore ?: 0, score),
                gamesPlayed = (old?.gamesPlayed ?: 0) + 1,
                totalCorrect = (old?.totalCorrect ?: 0) + score,
                totalAsked = (old?.totalAsked ?: 0) + asked,
                wrongIds = mergedWrong.joinToString(","),
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
