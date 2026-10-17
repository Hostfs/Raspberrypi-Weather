package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_readings ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentReadings(limit: Int = 100): Flow<List<WeatherReadingEntity>>

    @Query("SELECT * FROM weather_readings WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getReadingsSince(sinceTimestamp: Long): Flow<List<WeatherReadingEntity>>

    @Query("SELECT * FROM weather_readings ORDER BY timestamp DESC LIMIT 1")
    fun getLatestReading(): Flow<WeatherReadingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: WeatherReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(readings: List<WeatherReadingEntity>)

    @Query("SELECT COUNT(*) FROM weather_readings")
    fun getReadingCount(): Flow<Int>

    @Query("DELETE FROM weather_readings WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOldReadings(cutoffTimestamp: Long): Int

    @Query("DELETE FROM weather_readings")
    suspend fun clearAll()
}
