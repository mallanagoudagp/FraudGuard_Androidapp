package com.fraudguard.demo.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScoreLog::class, TrapLog::class], version = 2)
abstract class AppDb : RoomDatabase() {
	abstract fun scoreLogDao(): ScoreLogDao
	abstract fun trapLogDao(): TrapLogDao

	companion object {
		@Volatile private var INSTANCE: AppDb? = null
		fun get(context: Context): AppDb = INSTANCE ?: synchronized(this) {
			INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "fraudguard.db").fallbackToDestructiveMigration().build().also { INSTANCE = it }
		}
	}
}


