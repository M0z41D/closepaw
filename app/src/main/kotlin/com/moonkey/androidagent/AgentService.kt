package com.moonkey.androidagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * AgentService - The kernel that runs the agent loop. Mirrors the structure of kernel.py from
 * android-action-kernel.
 */
class AgentService : AccessibilityService() {

    companion object {
        private const val TAG = "AgentService"

        // Singleton reference for MainActivity to access
        @Volatile
        var instance: AgentService? = null
            private set

        var statusCallback: ((String) -> Unit)? = null
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRunning = false
    private val history = mutableListOf<String>()
    private var lastElements: List<Sanitizer.Element> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AgentService connected")
        updateStatus("Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle events in real-time for MVP
        // The agent loop polls when needed
    }

    override fun onInterrupt() {
        Log.w(TAG, "AgentService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
    }

    private fun updateStatus(status: String) {
        Log.d(TAG, status)
        statusCallback?.invoke(status)
    }

    /** Run the agent loop - called from MainActivity */
    fun runAgent(goal: String, apiKey: String, maxSteps: Int = 20) {
        if (isRunning) {
            updateStatus("Agent is already running")
            return
        }

        LLMClient.initialize(apiKey)
        history.clear()
        isRunning = true

        scope.launch {
            updateStatus("🚀 Starting agent for goal: $goal")

            try {
                repeat(maxSteps) { step ->
                    if (!isRunning) return@launch

                    updateStatus("--- Step ${step + 1} ---")

                    // 1. Perception
                    updateStatus("👀 Scanning screen...")
                    val root = rootInActiveWindow
                    lastElements = Sanitizer.snapshot(root)
                    val screenJson = Sanitizer.toJson(lastElements)
                    updateStatus("Found ${lastElements.size} elements")

                    // 2. Reasoning
                    updateStatus("🧠 Thinking...")
                    val actionJson = LLMClient.nextAction(goal, screenJson, history)

                    // 3. Parse and Execute
                    updateStatus("💡 Action: $actionJson")
                    val result = execute(actionJson)
                    history.add("step=${step + 1} action=$actionJson result=$result")

                    // Check for done
                    try {
                        val action = JSONObject(actionJson)
                        if (action.optString("action") == "done") {
                            updateStatus("✅ Goal achieved: ${action.optString("reason")}")
                            isRunning = false
                            return@launch
                        }
                    } catch (e: Exception) {
                        /* ignore parse errors */
                    }

                    // Wait for UI to settle
                    delay(1000)
                }

                updateStatus("⏱️ Max steps reached")
            } catch (e: Exception) {
                updateStatus("❌ Error: ${e.message}")
                Log.e(TAG, "Agent loop error", e)
            } finally {
                isRunning = false
            }
        }
    }

    fun stopAgent() {
        isRunning = false
        updateStatus("🛑 Agent stopped")
    }

    private suspend fun execute(actionJson: String): String {
        return try {
            val action = JSONObject(actionJson)
            val actionType = action.getString("action")

            when (actionType) {
                "tap" -> executeTap(action)
                "type" -> executeType(action)
                "scroll" -> executeScroll(action)
                "back" -> executeBack()
                "home" -> executeHome()
                "wait" -> executeWait(action)
                "done" -> "done"
                else -> "unknown action: $actionType"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Execute error", e)
            "error: ${e.message}"
        }
    }

    private fun executeTap(action: JSONObject): String {
        val target = action.getJSONObject("target")
        val index = target.getInt("value")

        val element = lastElements.getOrNull(index) ?: return "error: element $index not found"

        // Try node click first
        val node = element.node
        if (node != null && node.isClickable) {
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) return "clicked element $index via node"
        }

        // Fallback to coordinate tap
        return dispatchTap(element.center[0].toFloat(), element.center[1].toFloat())
    }

    private fun executeType(action: JSONObject): String {
        val target = action.getJSONObject("target")
        val index = target.getInt("value")
        val text = action.getString("text")

        val element = lastElements.getOrNull(index) ?: return "error: element $index not found"

        val node = element.node ?: return "error: node reference lost"

        // Focus the element first
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // Set text
        val bundle =
                Bundle().apply {
                    putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            text
                    )
                }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)

        return if (success) "typed '$text' in element $index" else "error: failed to type"
    }

    private fun executeScroll(action: JSONObject): String {
        val target = action.getJSONObject("target")
        val index = target.getInt("value")
        val direction = action.optString("direction", "down")

        val element = lastElements.getOrNull(index) ?: return "error: element $index not found"

        val node = element.node ?: return "error: node reference lost"

        val scrollAction =
                if (direction == "up") {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }

        val success = node.performAction(scrollAction)
        return if (success) "scrolled $direction on element $index" else "error: scroll failed"
    }

    private fun executeBack(): String {
        val success = performGlobalAction(GLOBAL_ACTION_BACK)
        return if (success) "pressed back" else "error: back failed"
    }

    private fun executeHome(): String {
        val success = performGlobalAction(GLOBAL_ACTION_HOME)
        return if (success) "pressed home" else "error: home failed"
    }

    private suspend fun executeWait(action: JSONObject): String {
        val ms = action.optLong("ms", 1200)
        delay(ms)
        return "waited ${ms}ms"
    }

    private fun dispatchTap(x: Float, y: Float): String {
        val path = Path().apply { moveTo(x, y) }

        val gesture =
                GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                        .build()

        dispatchGesture(gesture, null, null)
        return "tapped at ($x, $y)"
    }
}
