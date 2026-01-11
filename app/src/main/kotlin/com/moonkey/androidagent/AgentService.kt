package com.moonkey.androidagent

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.moonkey.androidagent.data.llm.LLMClient
import com.moonkey.androidagent.service.AgentOrchestrator
import com.moonkey.androidagent.service.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * AgentService - The entry point for the Accessibility Service. Orchestrates the agent execution
 * via AgentOrchestrator.
 */
class AgentService : AccessibilityService() {

    companion object {
        private const val TAG = "AgentService"

        @Volatile
        var instance: AgentService? = null
            private set

        var statusCallback: ((String) -> Unit)? = null
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var orchestrator: AgentOrchestrator? = null
    private var overlayManager: OverlayManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AgentService connected")
        updateStatus("Accessibility Service connected")

        // Initialize Orchestrator
        orchestrator = AgentOrchestrator(this, scope) { status -> updateStatus(status) }

        // Initialize OverlayManager
        overlayManager =
                OverlayManager(
                        context = this,
                        onStop = { stopAgent() },
                        onPause = { orchestrator?.pause() },
                        onResume = { orchestrator?.resume() }
                )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We poll the screen, no reactive event handling needed for MVP
    }

    override fun onInterrupt() {
        Log.w(TAG, "AgentService interrupted")
    }

    override fun onDestroy() {
        orchestrator?.stop()
        overlayManager?.hide()
        super.onDestroy()
        instance = null
        scope.cancel()
    }

    private fun updateStatus(status: String) {
        Log.d(TAG, status)
        statusCallback?.invoke(status)
        overlayManager?.updateStatus(status)
    }

    /** Run the agent loop - called from MainActivity */
    fun runAgent(goal: String, apiKey: String, maxSteps: Int = 20) {
        // Initialize LLM with API Key
        LLMClient.initialize(apiKey)

        updateStatus("🚀 Starting agent for goal: $goal")
        overlayManager?.show()
        orchestrator?.start(goal)
    }

    fun stopAgent() {
        orchestrator?.stop()
        overlayManager?.hide()
        updateStatus("🛑 Agent stopped")
    }
}
