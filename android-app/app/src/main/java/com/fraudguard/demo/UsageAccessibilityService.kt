package com.fraudguard.demo

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import android.content.Intent
import app.UsageAgent

class UsageAccessibilityService : AccessibilityService() {
    private val TAG = "UsageAccessibilityService"

    override fun onServiceConnected() {
        super.onServiceConnected()
        UsageBridge.service = this
        Log.d(TAG, "UsageAccessibilityService connected")
        // Ensure background scoring runs while accessibility is enabled
        startService(Intent(this, FraudGuardService::class.java))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg.isNotEmpty()) {
                    val prev = UsageBridge.lastPackage
                    if (prev == null || prev != pkg) {
                        Log.d(TAG, "Window/event change: $pkg (prev=$prev)")
                        UsageBridge.agent?.onAppSwitch(prev ?: pkg, pkg)
                        UsageBridge.lastPackage = pkg
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "UsageAccessibilityService interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        UsageBridge.service = null
        Log.d(TAG, "UsageAccessibilityService destroyed")
    }
}

object UsageBridge {
    @Volatile var agent: UsageAgent? = null
    @Volatile var service: AccessibilityService? = null
    @Volatile var lastPackage: String? = null
}


