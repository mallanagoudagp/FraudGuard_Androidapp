package com.fraudguard.demo

import android.app.Activity
import android.os.Bundle
import android.util.Log

class TouchScoreActivity : Activity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// Example feature vector (replace with real gesture features, standardized order)
		val features = FloatArray(16) { i -> (i + 1) / 100f } // dummy 16-dim

		val result = AssetsScoring.scoreOnce(this, features)
		if (result != null) {
			Log.i("FraudGuard", "Result: score=${result.score} mse=${result.mse} thr=${result.threshold}")
		} else {
			Log.w("FraudGuard", "Scoring returned null")
		}
		// No UI; finish quickly
		finish()
	}
}

