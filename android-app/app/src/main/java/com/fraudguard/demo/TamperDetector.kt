package com.fraudguard.demo

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

object TamperDetector {
    data class Result(
        val debuggable: Boolean,
        val adbEnabled: Boolean,
        val emulator: Boolean,
        val suspiciousBuild: Boolean,
        val magiskAppPresent: Boolean,
        val suBinaryPresent: Boolean,
        val magiskPathsPresent: Boolean,
        val tampered: Boolean
    )

    fun check(context: Context): Result {
        val appDebuggable = try {
            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Throwable) { false }

        val adb = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Throwable) { false }

        val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.lowercase().contains("vbox") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")

        val suspicious = Build.TAGS?.contains("test-keys") == true ||
                Build.HARDWARE?.contains("goldfish") == true ||
                Build.HARDWARE?.contains("ranchu") == true

        // Magisk heuristics
        val magiskPackages = arrayOf(
            "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk",
            "org.meowcat.edxposed.manager",
            "com.topjohnwu.superuser"
        )
        val pm: PackageManager = context.packageManager
        val hasMagiskApp = try {
            magiskPackages.any { pkg ->
                try { pm.getPackageInfo(pkg, 0); true } catch (_: Throwable) { false }
            }
        } catch (_: Throwable) { false }

        val suPaths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/app/Superuser.apk"
        )
        val suPresent = try {
            suPaths.any { path -> java.io.File(path).exists() }
        } catch (_: Throwable) { false }

        val magiskPaths = arrayOf(
            "/sbin/.magisk",
            "/data/adb/magisk",
            "/data/adb/modules",
            "/cache/magisk.log"
        )
        val magiskDirPresent = try {
            magiskPaths.any { path -> java.io.File(path).exists() }
        } catch (_: Throwable) { false }

        val tampered = appDebuggable || adb || isEmulator || suspicious || hasMagiskApp || suPresent || magiskDirPresent

        // Optional deeper checks if file access is granted (pre-Android 13)
        val hasRead = try {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
        if (hasRead) {
            try {
                // which su
                val whichPaths = arrayOf("/system/xbin/which", "/system/bin/which")
                val which = whichPaths.firstOrNull { java.io.File(it).exists() }
                if (which != null) {
                    val p = Runtime.getRuntime().exec(arrayOf(which, "su"))
                    val out = p.inputStream.bufferedReader().readText()
                    if (out.contains("/su") || out.contains("/bin/su") || out.contains("/xbin/su")) {
                        return Result(appDebuggable, adb, isEmulator, suspicious, true, true, true, true)
                    }
                }
            } catch (_: Throwable) { }

            try {
                // mounts check
                val mounts = java.io.File("/proc/mounts")
                if (mounts.exists()) {
                    val txt = mounts.readText()
                    if (txt.contains("magisk") || txt.contains("su overlay") || txt.contains("overlayfs")) {
                        return Result(appDebuggable, adb, isEmulator, suspicious, hasMagiskApp, suPresent, true, true)
                    }
                }
            } catch (_: Throwable) { }
        }
        return Result(appDebuggable, adb, isEmulator, suspicious, hasMagiskApp, suPresent, magiskDirPresent, tampered)
    }
}


