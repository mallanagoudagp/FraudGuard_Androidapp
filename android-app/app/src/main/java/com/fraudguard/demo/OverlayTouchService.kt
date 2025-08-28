package com.fraudguard.demo

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import app.TouchAgent

class OverlayTouchService : Service() {
	private var wm: WindowManager? = null
	private var puckView: ImageView? = null
	private var screenW: Int = 0
	private var screenH: Int = 0
	private val handler = Handler()
	private var lastTapTime = 0L

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		wm = getSystemService(WINDOW_SERVICE) as WindowManager
		val dm = DisplayMetrics()
		wm?.defaultDisplay?.getMetrics(dm)
		screenW = dm.widthPixels
		screenH = dm.heightPixels
		addPuck()
	}

	override fun onDestroy() {
		removePuck()
		super.onDestroy()
	}

	private fun dp(value: Float): Int = TypedValue.applyDimension(
		TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
	).toInt()

	private fun addPuck() {
		if (puckView != null) return
		val size = dp(36f)
		val lp = WindowManager.LayoutParams(
			size,
			size,
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
				WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
			else
				WindowManager.LayoutParams.TYPE_PHONE,
			WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
				WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
			PixelFormat.TRANSLUCENT
		)
		lp.gravity = Gravity.TOP or Gravity.START
		lp.x = screenW - size - dp(8f)
		lp.y = dp(140f)

		val iv = ImageView(this)
		iv.setImageResource(R.drawable.puck_bg)
		iv.alpha = 0.5f
		iv.contentDescription = "FraudGuard touch puck"

		var startX = 0f
		var startY = 0f
		var origX = 0
		var origY = 0
		iv.setOnTouchListener { _, ev ->
			val now = System.currentTimeMillis()
			val touch = Agents.touch
			when (ev.actionMasked) {
				MotionEvent.ACTION_DOWN -> {
					// Double-tap to show if hidden
					if (iv.alpha < 0.26f && now - lastTapTime < 300) {
						iv.alpha = 0.5f
					}
					lastTapTime = now
					startX = ev.rawX
					startY = ev.rawY
					origX = lp.x
					origY = lp.y
					touch.onTouchDown(0, ev.rawX, ev.rawY, 1.0f, 1.0f)
					scheduleAutoHide(iv)
				}
				MotionEvent.ACTION_MOVE -> {
					val dx = (ev.rawX - startX).toInt()
					val dy = (ev.rawY - startY).toInt()
					lp.x = (origX + dx).coerceIn(0, screenW - size)
					lp.y = (origY + dy).coerceIn(0, screenH - size)
					wm?.updateViewLayout(iv, lp)
					touch.onTouchMove(0, ev.rawX, ev.rawY, 1.0f, 1.0f)
					scheduleAutoHide(iv)
				}
				MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
					touch.onTouchUp(0, ev.rawX, ev.rawY, 1.0f, 1.0f)
					// Edge snap
					val snapLeft = 0
					val snapRight = screenW - size
					lp.x = if (lp.x < screenW / 2) snapLeft + dp(4f) else snapRight - dp(4f)
					wm?.updateViewLayout(iv, lp)
					scheduleAutoHide(iv)
				}
			}
			true
		}

		puckView = iv
		wm?.addView(puckView, lp)
		scheduleAutoHide(iv)
	}

	private fun scheduleAutoHide(iv: ImageView) {
		handler.removeCallbacksAndMessages(null)
		handler.postDelayed({ iv.alpha = 0.25f }, 5000)
	}

	private fun removePuck() {
		puckView?.let { v -> wm?.removeView(v) }
		puckView = null
	}
}
