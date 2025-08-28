package com.fraudguard.demo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager

class SettingsActivity : Activity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_settings)
		val prefs = getSharedPreferences("fraudguard", MODE_PRIVATE)
		findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
			startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
		}
		findViewById<Button>(R.id.btnSelectIME).setOnClickListener {
			startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
		}
		findViewById<Button>(R.id.btnAppNotifications).setOnClickListener {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
				intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
				startActivity(intent)
			} else {
				startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
			}
		}

		val switchHoneytrap = findViewById<CheckBox>(R.id.switchHoneytrap)
		switchHoneytrap.isChecked = prefs.getBoolean("enable_honeytrap", false)
		switchHoneytrap.setOnCheckedChangeListener { _, isChecked ->
			prefs.edit().putBoolean("enable_honeytrap", isChecked).apply()
		}

		val btnForce = findViewById<Button>(R.id.btnForceHoneytrap)
		btnForce.setOnClickListener {
			// Force-open the honeytrap for DEV/testing
			HoneytrapLogger.log(this, "force_open", null)
			startActivity(Intent(this, HoneytrapActivity::class.java))
		}
		// Only show in debug builds
		if (!BuildConfig.DEBUG) {
			btnForce.visibility = android.view.View.GONE
		}

		findViewById<Button>(R.id.btnGrantFilePerms).setOnClickListener {
			if (android.os.Build.VERSION.SDK_INT <= 32) {
				if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
					ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 9001)
				} else {
					android.widget.Toast.makeText(this, "Permission already granted", android.widget.Toast.LENGTH_SHORT).show()
				}
			} else {
				android.widget.Toast.makeText(this, "Not required on Android 13+", android.widget.Toast.LENGTH_SHORT).show()
			}
		}
	}
}
