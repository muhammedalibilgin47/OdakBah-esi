package com.tiyzi.odakbahcesi.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Bir odak seansı kaydı. Bahçedeki her bitki bir seanstır.
 * success = false ise bitki solmuştur (🥀).
 */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startAt: Long,      // epoch ms
    val endAt: Long,        // epoch ms
    val minutes: Int,       // fiilen çalışılan dakika
    val subject: String,    // Matematik, Fizik ...
    val plant: String,      // PlantType.name
    val success: Boolean,
)

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY endAt DESC")
    fun all(): Flow<List<Session>>

    @Insert
    suspend fun insert(session: Session): Long

    @Query("SELECT * FROM sessions")
    suspend fun allOnce(): List<Session>

    @Insert
    suspend fun insertAll(list: List<Session>)

    @Query("DELETE FROM sessions")
    suspend fun clear()
}

@Database(entities = [Session::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "odak_bahcesi.db"
                ).build().also { instance = it }
            }
    }
}
