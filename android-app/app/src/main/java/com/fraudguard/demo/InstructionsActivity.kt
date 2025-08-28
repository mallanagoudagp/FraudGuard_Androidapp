package com.fraudguard.demo

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

class InstructionsActivity : Activity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_instructions)

		findViewById<Button>(R.id.btnOpenAccessibility).setOnClickListener {
			startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
		}
		findViewById<Button>(R.id.btnSelectIme).setOnClickListener {
			startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
		}
		findViewById<Button>(R.id.btnNotifications).setOnClickListener {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
				intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
				startActivity(intent)
			} else {
				startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
			}
		}
	}
}
