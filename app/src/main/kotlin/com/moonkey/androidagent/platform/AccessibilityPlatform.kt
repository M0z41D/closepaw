package com.moonkey.androidagent.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.moonkey.androidagent.data.perception.Perceptor
import com.moonkey.androidagent.domain.models.ScreenSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * AccessibilityPlatform - Real implementation of AndroidPlatform using AccessibilityService.
 * 
 * This wraps the existing Perceptor for screen capture and provides
 * action execution via the accessibility service APIs.
 */
class AccessibilityPlatform(
    private val service: AccessibilityService
) : AndroidPlatform {
    
    companion object {
        private const val TAG = "AccessibilityPlatform"
        private const val DEFAULT_GESTURE_DURATION_MS = 100L
        private const val SWIPE_GESTURE_DURATION_MS = 300L
    }
    
    override suspend fun captureScreen(): ScreenSnapshot {
        return withContext(Dispatchers.Main) {
            val root = service.rootInActiveWindow
            Perceptor.snapshot(root)
        }
    }
    
    override suspend fun performAction(action: UIAction, snapshot: ScreenSnapshot?): ActionResult {
        return when (action) {
            is UIAction.Click -> performClick(action, snapshot)
            is UIAction.ClickAt -> performClickAt(action.x, action.y)
            is UIAction.Type -> performType(action, snapshot)
            is UIAction.Scroll -> performScroll(action)
            is UIAction.Swipe -> performSwipe(action)
            is UIAction.SystemButton -> performSystemButton(action)
            is UIAction.Wait -> performWait(action)
        }
    }
    
    override fun hasRequiredPermissions(): Boolean {
        return service.serviceInfo != null
    }
    
    override fun getCurrentPackageName(): String? {
        return try {
            service.rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get package name", e)
            null
        }
    }
    
    override fun getDisplayInfo(): DisplayInfo {
        val displayMetrics = service.resources.displayMetrics
        return DisplayInfo(
            widthPixels = displayMetrics.widthPixels,
            heightPixels = displayMetrics.heightPixels,
            density = displayMetrics.density
        )
    }
    
    // ===== Action Implementations =====
    
    private suspend fun performClick(action: UIAction.Click, snapshot: ScreenSnapshot?): ActionResult {
        if (snapshot == null) {
            return ActionResult.Failure("Snapshot required for element-based click")
        }
        
        val node = snapshot.rawMap[action.elementIndex]
            ?: return ActionResult.ElementNotFound(action.elementIndex)
        
        return clickNode(node)
    }
    
    private suspend fun clickNode(node: AccessibilityNodeInfo): ActionResult {
        // Try native click first
        if (node.isClickable) {
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (result) {
                return ActionResult.Success("Clicked via accessibility action")
            }
        }
        
        // Fallback to gesture tap
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        return performClickAt(bounds.centerX(), bounds.centerY())
    }
    
    private suspend fun performClickAt(x: Int, y: Int): ActionResult {
        return performTap(x.toFloat(), y.toFloat())
    }
    
    private suspend fun performType(action: UIAction.Type, snapshot: ScreenSnapshot?): ActionResult {
        if (snapshot == null) {
            return ActionResult.Failure("Snapshot required for type action")
        }
        
        val node = snapshot.rawMap[action.elementIndex]
            ?: return ActionResult.ElementNotFound(action.elementIndex)
        
        // Try setting text directly
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.text)
        }
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        
        return if (result) {
            ActionResult.Success("Text entered: ${action.text}")
        } else {
            // Try clicking first to focus, then set text
            clickNode(node)
            val retryResult = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (retryResult) {
                ActionResult.Success("Text entered after focus: ${action.text}")
            } else {
                ActionResult.Failure("Failed to set text on element")
            }
        }
    }
    
    private suspend fun performScroll(action: UIAction.Scroll): ActionResult {
        val display = getDisplayInfo()
        val centerX = display.widthPixels / 2f
        
        val (startY, endY) = when (action.direction) {
            ScrollDirection.DOWN -> {
                // Swipe up to scroll down
                display.heightPixels * 0.7f to display.heightPixels * 0.3f
            }
            ScrollDirection.UP -> {
                // Swipe down to scroll up
                display.heightPixels * 0.3f to display.heightPixels * 0.7f
            }
            ScrollDirection.LEFT, ScrollDirection.RIGHT -> {
                // Horizontal scroll - use center Y
                display.heightPixels * 0.5f to display.heightPixels * 0.5f
            }
        }
        
        val (startX, endX) = when (action.direction) {
            ScrollDirection.LEFT -> {
                display.widthPixels * 0.8f to display.widthPixels * 0.2f
            }
            ScrollDirection.RIGHT -> {
                display.widthPixels * 0.2f to display.widthPixels * 0.8f
            }
            else -> centerX to centerX
        }
        
        return performSwipeGesture(startX, startY, endX, endY, SWIPE_GESTURE_DURATION_MS)
    }
    
    private suspend fun performSwipe(action: UIAction.Swipe): ActionResult {
        return performSwipeGesture(
            action.startX.toFloat(),
            action.startY.toFloat(),
            action.endX.toFloat(),
            action.endY.toFloat(),
            action.durationMs
        )
    }
    
    private suspend fun performSystemButton(action: UIAction.SystemButton): ActionResult {
        val globalAction = when (action.button) {
            SystemButtonType.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            SystemButtonType.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            SystemButtonType.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
        }
        
        val result = service.performGlobalAction(globalAction)
        return if (result) {
            ActionResult.Success("System button: ${action.button}")
        } else {
            ActionResult.Failure("Failed to perform system action: ${action.button}")
        }
    }
    
    private suspend fun performWait(action: UIAction.Wait): ActionResult {
        kotlinx.coroutines.delay(action.durationMs)
        return ActionResult.Success("Waited ${action.durationMs}ms")
    }
    
    // ===== Gesture Helpers =====
    
    private suspend fun performTap(x: Float, y: Float): ActionResult {
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, DEFAULT_GESTURE_DURATION_MS))
            .build()
        
        return dispatchGesture(gesture)
    }
    
    private suspend fun performSwipeGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ): ActionResult {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        
        return dispatchGesture(gesture)
    }
    
    private suspend fun dispatchGesture(gesture: GestureDescription): ActionResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(ActionResult.Success("Gesture completed"))
                    }
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(ActionResult.Cancelled("Gesture cancelled"))
                    }
                }
            }
            
            val dispatched = service.dispatchGesture(gesture, callback, null)
            if (!dispatched) {
                continuation.resume(ActionResult.Failure("Failed to dispatch gesture"))
            }
        }
    }
}

