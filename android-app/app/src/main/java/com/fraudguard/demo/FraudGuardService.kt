package com.fraudguard.demo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import app.FusionEngine
import com.fraudguard.demo.db.AppDb
import com.fraudguard.demo.db.ScoreLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FraudGuardService : Service() {

	private val channelId = "fraudguard_channel"
	private val alertsChannelId = "fraudguard_alerts"
	private val alertsMediumChannelId = "fraudguard_alerts_medium"
	private val alertsHighChannelId = "fraudguard_alerts_high"
	private val honeytrapChannelId = "fraudguard_trap"
	private val notifId = 1001
	private val handler = Handler(Looper.getMainLooper())
	private var running = false
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var lastAlertLevel: String? = null
	private var lastAlertTimeMs: Long = 0
	private var lastAlertScore: Double = 0.0
	private val minAlertIntervalMs: Long = 60_000 // 60s debounce
	private val minScoreDeltaToAlert: Double = 0.10 // require +0.10 jump
	private var highRiskStreak: Int = 0
	private var lastTrapTimeMs: Long = 0
	private val minTrapIntervalMs: Long = 120_000 // 2 min between trap notifications

	override fun onCreate() {
		super.onCreate()
		Agents.loadState(this)
		createChannel()
		// Do NOT call startForeground to avoid crashes on devices missing permission; we'll post
		// a normal ongoing notification from our tick loop.
		Agents.startAll()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (!running) {
			running = true
			handler.post(tick)
		}
		return START_STICKY
	}

	private val tick = object : Runnable {
		override fun run() {
			// Fuse current scores and update notification text
			val touch = Agents.touch.result?.score ?: 0.0
			val typing = Agents.typing.result?.score ?: 0.0
			val usage = Agents.usage.result.score
			val fused = FusionEngine().fuseScores(touch, typing, usage)
			val text = "Risk: ${fused.riskLevel} • ${"%.2f".format(fused.finalScore)}"
			val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			// Post a normal notification (not foreground) so we don't require FOREGROUND_SERVICE
			nm.notify(notifId, buildNotification(text))
			maybeAlert(fused)
			maybeTriggerHoneytrap(fused)
			maybeTriggerHoneytrapOnTamper()
			// Log to Room
			scope.launch {
				val dao = AppDb.get(this@FraudGuardService).scoreLogDao()
				dao.insert(
					ScoreLog(
						timestamp = System.currentTimeMillis(),
						touch = Agents.touch.result?.score,
						typing = Agents.typing.result?.score,
						usage = Agents.usage.result.score,
						fused = fused.finalScore,
						risk = fused.riskLevel.name
					)
				)
			}
			Agents.saveState(this@FraudGuardService)
			if (running) handler.postDelayed(this, 2000)
		}
	}

	private fun createChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			val ch = NotificationChannel(channelId, "FraudGuard", NotificationManager.IMPORTANCE_LOW)
			ch.description = "FraudGuard background scoring"
			nm.createNotificationChannel(ch)

			val alerts = NotificationChannel(alertsChannelId, "Fraud Alerts", NotificationManager.IMPORTANCE_HIGH)
			alerts.description = "Alerts for elevated risk"
			nm.createNotificationChannel(alerts)

			val medium = NotificationChannel(alertsMediumChannelId, "Fraud Alerts (Medium)", NotificationManager.IMPORTANCE_DEFAULT)
			medium.enableVibration(true)
			medium.vibrationPattern = longArrayOf(0, 150, 100, 150)
			nm.createNotificationChannel(medium)

			val high = NotificationChannel(alertsHighChannelId, "Fraud Alerts (High)", NotificationManager.IMPORTANCE_HIGH)
			high.enableVibration(true)
			high.vibrationPattern = longArrayOf(0, 300, 150, 300, 150, 300)
			nm.createNotificationChannel(high)

			val trap = NotificationChannel(honeytrapChannelId, "Session Verification", NotificationManager.IMPORTANCE_HIGH)
			trap.description = "Verify session (honeytrap)"
			nm.createNotificationChannel(trap)
		}
	}

	private fun buildNotification(text: String): Notification {
		val builder = NotificationCompat.Builder(this, channelId)
			.setContentTitle("FraudGuard running")
			.setContentText(text)
			.setSmallIcon(android.R.drawable.ic_lock_idle_lock)
			.setOngoing(true)
		return builder.build()
	}

	override fun onDestroy() {
		running = false
		handler.removeCallbacksAndMessages(null)
		Agents.saveState(this)
		super.onDestroy()
	}

	private fun maybeAlert(fused: FusionEngine.FusionResult) {
		val level = fused.riskLevel.name
		// Only alert on Medium/High transitions
		val shouldAlert = (fused.riskLevel == FusionEngine.RiskLevel.MEDIUM || fused.riskLevel == FusionEngine.RiskLevel.HIGH)
		if (!shouldAlert) { lastAlertLevel = null; return }

		val now = System.currentTimeMillis()
		val intervalOk = now - lastAlertTimeMs >= minAlertIntervalMs
		val levelChanged = lastAlertLevel != level
		val scoreJumped = fused.finalScore - lastAlertScore >= minScoreDeltaToAlert
		if (!(levelChanged || scoreJumped) || !intervalOk) return

		lastAlertLevel = level
		lastAlertTimeMs = now
		lastAlertScore = fused.finalScore

		// Quick actions
		val verifyIntent = Intent(this, SettingsActivity::class.java)
		val verifyPI = androidx.core.app.PendingIntentCompat.getActivity(this, 2001, verifyIntent, 0, false)

		val dashboardIntent = Intent(this, DashboardActivity::class.java)
		val dashPI = androidx.core.app.PendingIntentCompat.getActivity(this, 2002, dashboardIntent, 0, false)

		val channelForLevel = when (fused.riskLevel) {
			FusionEngine.RiskLevel.HIGH -> alertsHighChannelId
			FusionEngine.RiskLevel.MEDIUM -> alertsMediumChannelId
			else -> alertsChannelId
		}

		val builder = NotificationCompat.Builder(this, channelForLevel)
			.setSmallIcon(android.R.drawable.stat_sys_warning)
			.setContentTitle("Fraud risk: ${fused.riskLevel}")
			.setContentText("Score ${"%.2f".format(fused.finalScore)} — tap to review")
			.setPriority(if (fused.riskLevel == FusionEngine.RiskLevel.HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
			.setAutoCancel(true)
			.addAction(0, "Review dashboard", dashPI)
			.addAction(0, "Verify settings", verifyPI)

		val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		nm.notify(3000 + fused.riskLevel.ordinal, builder.build())
	}

	private fun maybeTriggerHoneytrap(fused: FusionEngine.FusionResult) {
		val prefs = getSharedPreferences("fraudguard", MODE_PRIVATE)
		val enabled = prefs.getBoolean("enable_honeytrap", false)
		if (!enabled) { highRiskStreak = 0; return }

		if (fused.riskLevel == FusionEngine.RiskLevel.HIGH) {
			highRiskStreak += 1
		} else {
			highRiskStreak = 0
		}

		val now = System.currentTimeMillis()
		val intervalOk = now - lastTrapTimeMs >= minTrapIntervalMs
		if (highRiskStreak < 3 || !intervalOk) return

		lastTrapTimeMs = now
		highRiskStreak = 0

		HoneytrapLogger.log(this, "notify", "score=${"%.3f".format(fused.finalScore)} level=${fused.riskLevel}")

		val intent = Intent(this, HoneytrapActivity::class.java)
		intent.putExtra("score", fused.finalScore)
		intent.putExtra("level", fused.riskLevel.name)
		val pi = androidx.core.app.PendingIntentCompat.getActivity(this, 5001, intent, 0, false)

		val builder = NotificationCompat.Builder(this, honeytrapChannelId)
			.setSmallIcon(android.R.drawable.stat_sys_warning)
			.setContentTitle("Verify session")
			.setContentText("Security check required — tap to continue")
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setAutoCancel(true)
			.setContentIntent(pi)

		val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		nm.notify(6001, builder.build())
	}

	private fun maybeTriggerHoneytrapOnTamper() {
		val prefs = getSharedPreferences("fraudguard", MODE_PRIVATE)
		val enabled = prefs.getBoolean("enable_honeytrap", false)
		if (!enabled) return
		val now = System.currentTimeMillis()
		val intervalOk = now - lastTrapTimeMs >= minTrapIntervalMs
		if (!intervalOk) return
		val res = TamperDetector.check(this)
		if (!res.tampered) return
		lastTrapTimeMs = now
		HoneytrapLogger.log(this, "tamper_notify", "debug=${res.debuggable} adb=${res.adbEnabled} emu=${res.emulator} suspicious=${res.suspiciousBuild} magiskApp=${res.magiskAppPresent} su=${res.suBinaryPresent} magiskPaths=${res.magiskPathsPresent}")
		val intent = Intent(this, HoneytrapActivity::class.java)
		intent.putExtra("tamper", true)
		val pi = androidx.core.app.PendingIntentCompat.getActivity(this, 5002, intent, 0, false)
		val builder = NotificationCompat.Builder(this, honeytrapChannelId)
			.setSmallIcon(android.R.drawable.stat_sys_warning)
			.setContentTitle("Security check required")
			.setContentText("Potential tamper detected — tap to continue")
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setAutoCancel(true)
			.setContentIntent(pi)
		val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		nm.notify(6002, builder.build())
	}

	override fun onBind(intent: Intent?): IBinder? = null
}


