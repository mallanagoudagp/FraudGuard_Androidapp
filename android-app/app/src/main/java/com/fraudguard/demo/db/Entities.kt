package com.fraudguard.demo.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "score_logs")
data class ScoreLog(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val timestamp: Long,
	val touch: Double?,
	val typing: Double?,
	val usage: Double,
	val fused: Double,
	val risk: String
)


@Entity(tableName = "trap_logs")
data class TrapLog(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val timestamp: Long,
	val event: String,
	val meta: String?
)

