package com.example.demoapplication.database

import androidx.room.*

@Database(
    entities = [CachedElection::class, CachedCandidate::class],
    version = 1,
    exportSchema = false
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun electionCacheDao(): ElectionCacheDao
    abstract fun candidateCacheDao(): CandidateCacheDao

    companion object {
        @Volatile
        private var INSTANCE: CacheDatabase? = null

        fun getInstance(context: android.content.Context): CacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CacheDatabase::class.java,
                    "offline_cache"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Entity(tableName = "cached_elections")
data class CachedElection(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val status: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_candidates")
data class CachedCandidate(
    @PrimaryKey val id: String,
    val electionId: String,
    val name: String,
    val party: String,
    val symbol: String
)

@Dao
interface ElectionCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertElection(election: CachedElection)

    @Query("SELECT * FROM cached_elections")
    suspend fun getAllElections(): List<CachedElection>

    @Query("DELETE FROM cached_elections")
    suspend fun clearAll()
}

@Dao
interface CandidateCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidate(candidate: CachedCandidate)

    @Query("SELECT * FROM cached_candidates WHERE electionId = :electionId")
    suspend fun getCandidatesForElection(electionId: String): List<CachedCandidate>
}