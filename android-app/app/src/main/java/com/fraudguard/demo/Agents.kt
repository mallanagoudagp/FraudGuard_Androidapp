package com.fraudguard.demo

import android.content.Context
import app.TouchAgent
import app.TypingAgent
import app.UsageAgent

object Agents {
	val touch: TouchAgent = TouchAgent()
	val typing: TypingAgent = TypingAgent()
	val usage: UsageAgent = UsageAgent()

	fun startAll() {
		touch.start()
		typing.start()
		usage.start()
	}

	fun stopAll() {
		touch.stop()
		typing.stop()
		usage.stop()
	}

	fun loadState(context: Context) {
		val prefs = context.getSharedPreferences("fraudguard", Context.MODE_PRIVATE)
		prefs.getString("touch_state", null)?.let {
			try {
				val p = it.split(",")
				val s = TouchAgent.State()
				s.baselineAvgVelocity = p[0].toDouble()
				s.baselineAvgVelocityVar = p[1].toDouble()
				s.baselinePeakVelocity = p[2].toDouble()
				s.baselinePeakVelocityVar = p[3].toDouble()
				s.baselinePathDeviation = p[4].toDouble()
				s.baselinePathDeviationVar = p[5].toDouble()
				s.baselineTapDuration = p[6].toDouble()
				s.baselineTapDurationVar = p[7].toDouble()
				s.baselineJitter = p[8].toDouble()
				s.baselineJitterVar = p[9].toDouble()
				s.baselinePressureProfile = p[10].toDouble()
				s.baselinePressureProfileVar = p[11].toDouble()
				s.totalGestures = p[12].toInt()
				s.isInWarmup = p[13].toBoolean()
				touch.applyState(s)
			} catch (_: Exception) {}
		}
		prefs.getString("typing_state", null)?.let {
			try {
				val p = it.split(",")
				val s = TypingAgent.State()
				s.baselineDwellMean = p[0].toDouble()
				s.baselineDwellVariance = p[1].toDouble()
				s.baselineFlightMean = p[2].toDouble()
				s.baselineFlightVariance = p[3].toDouble()
				s.baselineBackspaceRate = p[4].toDouble()
				s.totalKeystrokes = p[5].toInt()
				s.isInWarmup = p[6].toBoolean()
				typing.applyState(s)
			} catch (_: Exception) {}
		}
		prefs.getString("usage_state", null)?.let {
			try {
				val p = it.split(",")
				val s = UsageAgent.State()
				s.baselineLaunchRatePerMin = p[0].toDouble()
				s.baselineSwitchRatePerMin = p[1].toDouble()
				s.baselineAvgSessionMs = p[2].toDouble()
				s.baselineSessionVar = p[3].toDouble()
				s.totalSessions = p[4].toInt()
				s.inWarmup = p[5].toBoolean()
				usage.applyState(s)
			} catch (_: Exception) {}
		}
	}

	fun saveState(context: Context) {
		val prefs = context.getSharedPreferences("fraudguard", Context.MODE_PRIVATE)
		val ts = touch.state
		val ty = typing.state
		val us = usage.state
		prefs.edit()
			.putString("touch_state", listOf(
				ts.baselineAvgVelocity,
				ts.baselineAvgVelocityVar,
				ts.baselinePeakVelocity,
				ts.baselinePeakVelocityVar,
				ts.baselinePathDeviation,
				ts.baselinePathDeviationVar,
				ts.baselineTapDuration,
				ts.baselineTapDurationVar,
				ts.baselineJitter,
				ts.baselineJitterVar,
				ts.baselinePressureProfile,
				ts.baselinePressureProfileVar,
				ts.totalGestures,
				ts.isInWarmup
			).joinToString(","))
			.putString("typing_state", listOf(
				ty.baselineDwellMean,
				ty.baselineDwellVariance,
				ty.baselineFlightMean,
				ty.baselineFlightVariance,
				ty.baselineBackspaceRate,
				ty.totalKeystrokes,
				ty.isInWarmup
			).joinToString(","))
			.putString("usage_state", listOf(
				us.baselineLaunchRatePerMin,
				us.baselineSwitchRatePerMin,
				us.baselineAvgSessionMs,
				us.baselineSessionVar,
				us.totalSessions,
				us.inWarmup
			).joinToString(","))
			.apply()
	}
}
