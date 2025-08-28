package com.fraudguard.demo

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class HoneytrapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_honeytrap)

        findViewById<TextView>(R.id.title).text = getString(R.string.honeytrap_title)
        findViewById<TextView>(R.id.subtitle).text = getString(R.string.honeytrap_subtitle)

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            HoneytrapLogger.log(this, "exit", null)
            finish()
        }

        HoneytrapLogger.log(this, "opened", intent?.extras?.toString())
    }
}


