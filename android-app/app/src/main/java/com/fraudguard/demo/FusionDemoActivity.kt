package com.fraudguard.demo

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.Button
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.KeyEvent
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.chip.Chip
import app.FusionEngine
import android.content.Intent
import app.TouchAgent
import app.TypingAgent
import app.UsageAgent
import android.provider.Settings

class FusionDemoActivity : Activity() {
    private val TAG = "FraudGuard"
    private var uiRunning = false
    private val uiHandler = Handler(Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_fusion_demo)
        val tvTouch = findViewById<TextView>(R.id.touchScore)
        val tvTyping = findViewById<TextView>(R.id.typingScore)
        val tvFusion = findViewById<TextView>(R.id.fusion)
        val tvUsage = findViewById<TextView>(R.id.usageScore)
        val btnRun = findViewById<Button>(R.id.btnRun)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val edit = findViewById<EditText>(R.id.editText)
        val chipTouch = findViewById<Chip>(R.id.chipTouch)
        val chipTyping = findViewById<Chip>(R.id.chipTyping)
        val chipUsage = findViewById<Chip>(R.id.chipUsage)
        val warmupPanel = findViewById<View>(R.id.warmupPanel)
        val mainPanel = findViewById<View>(R.id.mainPanel)
        val btnSubmitTyping = findViewById<Button>(R.id.btnSubmitTyping)
        val btnReset = findViewById<Button>(R.id.btnReset)
        val btnDashboard = findViewById<Button>(R.id.btnOpenDashboard)
        val btnSettings = findViewById<Button>(R.id.btnOpenSettings)
        val btnStartService = findViewById<Button>(R.id.btnStartService)
        val btnTestAccessibility = findViewById<Button>(R.id.btnTestAccessibility)
        val rootScroll = findViewById<View>(R.id.rootScroll)
        
        // Add overlay controls dynamically using Start Service button long press
        btnStartService.setOnLongClickListener {
            // Request overlay permission if needed and start overlay service
            if (!android.provider.Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant 'Draw over other apps' to capture touches globally", Toast.LENGTH_LONG).show()
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(intent)
            } else {
                startService(Intent(this, OverlayTouchService::class.java))
                Toast.makeText(this, "Global touch capture ON", Toast.LENGTH_SHORT).show()
            }
            true
        }
        btnStop.setOnLongClickListener {
            stopService(Intent(this, OverlayTouchService::class.java))
            Toast.makeText(this, "Global touch capture OFF", Toast.LENGTH_SHORT).show()
            true
        }

        // 1) Start agents
        val touch = Agents.touch
        val typing = Agents.typing
        val usage = Agents.usage

        // Restore persisted baselines
        val prefs = getSharedPreferences("fraudguard", MODE_PRIVATE)
        prefs.getString("touch_state", null)?.let {
            try {
                val parts = it.split(",")
                val s = TouchAgent.State()
                s.baselineAvgVelocity = parts[0].toDouble()
                s.baselineAvgVelocityVar = parts[1].toDouble()
                s.baselinePeakVelocity = parts[2].toDouble()
                s.baselinePeakVelocityVar = parts[3].toDouble()
                s.baselinePathDeviation = parts[4].toDouble()
                s.baselinePathDeviationVar = parts[5].toDouble()
                s.baselineTapDuration = parts[6].toDouble()
                s.baselineTapDurationVar = parts[7].toDouble()
                s.baselineJitter = parts[8].toDouble()
                s.baselineJitterVar = parts[9].toDouble()
                s.baselinePressureProfile = parts[10].toDouble()
                s.baselinePressureProfileVar = parts[11].toDouble()
                s.totalGestures = parts[12].toInt()
                s.isInWarmup = parts[13].toBoolean()
                touch.applyState(s)
            } catch (_: Exception) {}
        }
        prefs.getString("typing_state", null)?.let {
            try {
                val parts = it.split(",")
                val s = TypingAgent.State()
                s.baselineDwellMean = parts[0].toDouble()
                s.baselineDwellVariance = parts[1].toDouble()
                s.baselineFlightMean = parts[2].toDouble()
                s.baselineFlightVariance = parts[3].toDouble()
                s.baselineBackspaceRate = parts[4].toDouble()
                s.totalKeystrokes = parts[5].toInt()
                s.isInWarmup = parts[6].toBoolean()
                typing.applyState(s)
            } catch (_: Exception) {}
        }
        prefs.getString("usage_state", null)?.let {
            try {
                val parts = it.split(",")
                val s = UsageAgent.State()
                s.baselineLaunchRatePerMin = parts[0].toDouble()
                s.baselineSwitchRatePerMin = parts[1].toDouble()
                s.baselineAvgSessionMs = parts[2].toDouble()
                s.baselineSessionVar = parts[3].toDouble()
                s.totalSessions = parts[4].toInt()
                s.inWarmup = parts[5].toBoolean()
                usage.applyState(s)
            } catch (_: Exception) {}
        }
        touch.start()
        typing.start()
        usage.start()
        UsageBridge.agent = usage

        // 2) Capture real-time touch events from the root scroll view and allow normal scrolling
        rootScroll.setOnTouchListener { _, ev ->
            if (!uiRunning) return@setOnTouchListener false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val idx = ev.actionIndex
                    val id = ev.getPointerId(idx)
                    touch.onTouchDown(id, ev.getX(idx), ev.getY(idx), ev.getPressure(idx), ev.getSize(idx))
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until ev.pointerCount) {
                        val id = ev.getPointerId(i)
                        touch.onTouchMove(id, ev.getX(i), ev.getY(i), ev.getPressure(i), ev.getSize(i))
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    val idx = ev.actionIndex
                    val id = ev.getPointerId(idx)
                    touch.onTouchUp(id, ev.getX(idx), ev.getY(idx), ev.getPressure(idx), ev.getSize(idx))
                }
            }
            // Return false so ScrollView can continue handling scrolling
            false
        }

        val updateUi = Runnable {
            val touchRes = touch.result
            val typingRes = typing.result
            val usageRes = usage.result
            val fusion = FusionEngine()
            val fused = fusion.fuseScores(touchRes?.score, typingRes?.score, usageRes.score)
            
            // Display scores with better formatting
            tvTouch.text = "Touch score: ${if (touchRes?.score != null) "%.3f".format(touchRes.score) else "--"}"
            tvTyping.text = "Typing score: ${if (typingRes?.score != null) "%.3f".format(typingRes.score) else "--"}"
            tvUsage.text = "Usage score: ${"%.3f".format(usageRes.score)}"
            tvFusion.text = "Fusion: ${"%.3f".format(fused.finalScore)} (level ${fused.riskLevel})"

            // Improved warmup detection with lower thresholds
            val touchWarm = touchRes == null || touchRes.explanations.contains("insufficient data for analysis") || touch.state.totalGestures < 5
            val typingWarm = typingRes == null || typingRes.explanations.contains("insufficient data for analysis") || typing.state.totalKeystrokes < 5
            val usageWarm = usageRes.explanations.contains("insufficient data for analysis") || usage.state.totalSessions < 2

            // Auto-complete warmup when enough data is collected
            if (touch.state.totalGestures >= 5 && touch.state.isInWarmup) {
                touch.state.isInWarmup = false
                Log.d(TAG, "Touch warmup auto-completed")
            }
            if (typing.state.totalKeystrokes >= 5 && typing.state.isInWarmup) {
                typing.state.isInWarmup = false
                Log.d(TAG, "Typing warmup auto-completed")
            }
            if (usage.state.totalSessions >= 2 && usage.state.inWarmup) {
                usage.state.inWarmup = false
                Log.d(TAG, "Usage warmup auto-completed")
            }

            chipTouch.text = if (touchWarm) "Touch: ${touch.state.totalGestures}/5" else "Touch: Ready"
            chipTyping.text = if (typingWarm) "Typing: ${typing.state.totalKeystrokes}/5" else "Typing: Ready"
            chipUsage.text = if (usageWarm) "Usage: ${usage.state.totalSessions}/2" else "Usage: Ready"
            
            // Enable submit button when typing has enough data
            btnSubmitTyping.isEnabled = typing.state.totalKeystrokes >= 5

            // Simple color accent via enabled state
            chipTouch.isEnabled = !touchWarm
            chipTyping.isEnabled = !typingWarm
            chipUsage.isEnabled = !usageWarm

            // Onboarding gate: show warmup panel until all ready
            val allReady = !touchWarm && !typingWarm && !usageWarm
            val wasWarmupVisible = warmupPanel.visibility == View.VISIBLE
            warmupPanel.visibility = if (allReady) View.GONE else View.VISIBLE
            mainPanel.visibility = View.VISIBLE
            
            // Show toast when warmup completes
            if (wasWarmupVisible && allReady) {
                Toast.makeText(this, "Warmup completed! All agents are ready.", Toast.LENGTH_LONG).show()
            }
            
            Log.d(TAG, "Touch: ${touch.state.totalGestures} gestures, Typing: ${typing.state.totalKeystrokes} keystrokes, Usage: ${usage.state.totalSessions} sessions")
        }

        // 3) Periodically refresh UI with live scores while running
        val tick = object : Runnable {
            override fun run() {
                updateUi.run()
                if (uiRunning) uiHandler.postDelayed(this, 500)
                // Persist baselines periodically when running
                val prefs = getSharedPreferences("fraudguard", MODE_PRIVATE)
                val ts = touch.state
                val ty = typing.state
                val us = usage.state
                prefs.edit()
                    .putString(
                        "touch_state",
                        listOf(
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
                        ).joinToString(",")
                    )
                    .putString(
                        "typing_state",
                        listOf(
                            ty.baselineDwellMean,
                            ty.baselineDwellVariance,
                            ty.baselineFlightMean,
                            ty.baselineFlightVariance,
                            ty.baselineBackspaceRate,
                            ty.totalKeystrokes,
                            ty.isInWarmup
                        ).joinToString(",")
                    )
                    .putString(
                        "usage_state",
                        listOf(
                            us.baselineLaunchRatePerMin,
                            us.baselineSwitchRatePerMin,
                            us.baselineAvgSessionMs,
                            us.baselineSessionVar,
                            us.totalSessions,
                            us.inWarmup
                        ).joinToString(",")
                    )
                    .apply()
            }
        }

        btnRun.setOnClickListener {
            if (!uiRunning) {
                uiRunning = true
                uiHandler.post(tick)
                Log.d(TAG, "Demo started")
            }
        }

        btnStop.setOnClickListener {
            uiRunning = false
            uiHandler.removeCallbacksAndMessages(null)
            Log.d(TAG, "Demo stopped")
        }

        // 4) Forward key events from EditText to TypingAgent
        edit.setOnKeyListener { _, keyCode, event ->
            val isDown = event.action == KeyEvent.ACTION_DOWN
            // Filter out unknowns to reduce noise
            if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                typing.onKeyEvent(isDown, keyCode, 0.0f)
                Log.d(TAG, "Key event: $keyCode, down: $isDown, total: ${typing.state.totalKeystrokes}")
            }
            false // allow normal EditText handling
        }
        
        // 4b) Also capture typing from the EditText text changes
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0) {
                    for (i in 0 until count) {
                        val char = s?.get(start + i) ?: continue
                        val keyCode = when (char) {
                            ' ' -> KeyEvent.KEYCODE_SPACE
                            '\n' -> KeyEvent.KEYCODE_ENTER
                            else -> KeyEvent.KEYCODE_A + (char.uppercaseChar() - 'A')
                        }
                        typing.onKeyEvent(true, keyCode, 0.0f)
                        typing.onKeyEvent(false, keyCode, 0.0f)
                        Log.d(TAG, "Text change: $char, keyCode: $keyCode, total: ${typing.state.totalKeystrokes}")
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // 5) Submit typing data button
        btnSubmitTyping.setOnClickListener {
            if (typing.state.totalKeystrokes >= 5) {
                typing.state.isInWarmup = false
                Toast.makeText(this, "Typing data submitted! Typing agent is now ready.", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Typing data submitted, warmup completed")
            } else {
                Toast.makeText(this, "Need at least 5 keystrokes to submit typing data", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 6) Reset baselines button
        btnReset.setOnClickListener {
            touch.resetBaseline()
            typing.resetBaseline()
            usage.resetBaseline()
            prefs.edit()
                .remove("touch_state")
                .remove("typing_state")
                .remove("usage_state")
                .apply()
            Log.d(TAG, "Baselines reset")
        }

        btnDashboard.setOnClickListener { 
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
        }
        btnSettings.setOnClickListener { 
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        val btnInstructions = findViewById<Button>(R.id.btnOpenInstructions)
        btnInstructions.setOnClickListener {
            startActivity(Intent(this, InstructionsActivity::class.java))
        }

        btnStartService.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, FraudGuardService::class.java))
            } else {
                startService(Intent(this, FraudGuardService::class.java))
            }
        }
        
        // 7) Test Accessibility: open settings if service not active, else send test event
        btnTestAccessibility.setOnClickListener {
            if (UsageBridge.service == null) {
                Toast.makeText(this, "Opening Accessibility settings — enable FraudGuard Demo", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                usage.onAppSwitch("com.test.app", "com.fraudguard.demo")
                Toast.makeText(this, "Test app switch event sent to UsageAgent", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Test app switch event sent")
            }
        }
        
        // Initial UI update
        updateUi.run()
    }
    
    override fun onResume() {
        super.onResume()
        if (uiRunning) {
            uiHandler.postDelayed(object : Runnable {
                override fun run() {
                    if (uiRunning) {
                        findViewById<TextView>(R.id.touchScore).text = "Touch score: ${Agents.touch.result?.score?.let { "%.3f".format(it) } ?: "--"}"
                        findViewById<TextView>(R.id.typingScore).text = "Typing score: ${Agents.typing.result?.score?.let { "%.3f".format(it) } ?: "--"}"
                        findViewById<TextView>(R.id.usageScore).text = "Usage score: ${"%.3f".format(Agents.usage.result.score)}"
                        uiHandler.postDelayed(this, 500)
                    }
                }
            }, 500)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Don't stop the demo when app goes to background, just pause UI updates
    }
}
