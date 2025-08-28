package com.fraudguard.demo

import android.content.Context
import com.fraudguard.demo.db.AppDb
import com.fraudguard.demo.db.TrapLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object HoneytrapLogger {
    private val ioScope = CoroutineScope(Dispatchers.IO)

    fun log(context: Context, event: String, meta: String? = null) {
        val appCtx = context.applicationContext
        ioScope.launch {
            try {
                AppDb.get(appCtx).trapLogDao().insert(
                    TrapLog(
                        timestamp = System.currentTimeMillis(),
                        event = event,
                        meta = meta
                    )
                )
                // Optionally send to isolated honeytrap endpoint if configured
                val prefs = appCtx.getSharedPreferences("fraudguard", Context.MODE_PRIVATE)
                if (prefs.getBoolean("honeytrap_upload", false)) {
                    HoneytrapApi.sendEvent(appCtx, event, meta)
                }
            } catch (_: Throwable) {
                // Intentionally ignore logging failures to avoid impacting UX
            }
        }
    }
}


