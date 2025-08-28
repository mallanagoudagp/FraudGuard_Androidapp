package com.fraudguard.demo.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreLogDao {
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insert(log: ScoreLog)

	@Query("SELECT * FROM score_logs ORDER BY timestamp DESC LIMIT 500")
	fun recent(): Flow<List<ScoreLog>>
}


@Dao
interface TrapLogDao {
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insert(log: TrapLog)

	@Query("SELECT * FROM trap_logs ORDER BY timestamp DESC LIMIT 500")
	fun recent(): Flow<List<TrapLog>>
}


