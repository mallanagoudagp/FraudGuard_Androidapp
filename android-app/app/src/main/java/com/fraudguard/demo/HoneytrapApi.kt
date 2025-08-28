package com.fraudguard.demo

import android.content.Context
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL

object HoneytrapApi {
    fun sendEvent(context: Context, event: String, meta: String?): Boolean {
        return try {
            val base = context.getString(R.string.honeytrap_base_url)
            val url = URL(base)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                // Do not attach app auth tokens; this is intentionally isolated
            }
            val body = JSONObject()
                .put("ts", System.currentTimeMillis())
                .put("event", event)
                .put("meta", meta ?: JSONObject.NULL)
                .put("device", android.os.Build.MODEL)
                .put("sdk", android.os.Build.VERSION.SDK_INT)
                .toString()
            BufferedOutputStream(conn.outputStream).use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Throwable) {
            false
        }
    }
}


