package com.moonkey.androidagent.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayManager(
        private val context: AccessibilityService,
        private val onStop: () -> Unit,
        private val onPause: () -> Unit,
        private val onResume: () -> Unit
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: ViewGroup? = null
    private var statusText: TextView? = null
    private var isPaused = false

    fun show() {
        if (overlayView != null) return

        val params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        else WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        val layout =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(0x99000000.toInt()) // Semi-transparent black
                    setPadding(16, 16, 16, 16)
                }

        statusText =
                TextView(context).apply {
                    text = "Agent Ready"
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 14f
                }
        layout.addView(statusText)

        val buttonsLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val pauseButton =
                Button(context).apply {
                    text = "Pause"
                    setOnClickListener {
                        if (isPaused) {
                            text = "Pause"
                            isPaused = false
                            onResume()
                        } else {
                            text = "Resume"
                            isPaused = true
                            onPause()
                        }
                    }
                }

        val stopButton =
                Button(context).apply {
                    text = "Stop"
                    setOnClickListener { onStop() }
                }

        buttonsLayout.addView(pauseButton)
        buttonsLayout.addView(stopButton)
        layout.addView(buttonsLayout)

        windowManager.addView(layout, params)
        overlayView = layout
    }

    fun hide() {
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
    }

    fun updateStatus(status: String) {
        // Run on UI thread just in case, though AccessibilityService runs on main thread usually
        statusText?.post { statusText?.text = status }
    }
}
