package com.moonkey.androidagent.orchestration.v3

import android.util.Log
import com.moonkey.androidagent.domain.agents.Executor
import com.moonkey.androidagent.domain.agents.Manager
import com.moonkey.androidagent.domain.agents.Reflector
import com.moonkey.androidagent.domain.models.AgentAction
import com.moonkey.androidagent.domain.models.ScreenSnapshot
import com.moonkey.androidagent.orchestration.AgentOrchestration
import com.moonkey.androidagent.orchestration.CancellationReason
import com.moonkey.androidagent.orchestration.CancellationSignal
import com.moonkey.androidagent.orchestration.EventEmitter
import com.moonkey.androidagent.orchestration.OrchestrationConfig
import com.moonkey.androidagent.platform.ScrollDirection
import com.moonkey.androidagent.platform.SystemButtonType
import com.moonkey.androidagent.platform.UIAction
import com.moonkey.androidagent.protocol.AgentEvent
import com.moonkey.androidagent.session.SessionServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * MobileV3Orchestration - Multi-agent orchestration following Mobile-Agent-v3 pattern.
 * 
 * Agents:
 * - Manager: High-level planning and goal decomposition
 * - Executor: Action selection based on current screen state
 * - Reflector: Verifies action outcomes by comparing before/after states
 * 
 * Loop structure:
 * 1. PERCEPTION: Capture current screen state
 * 2. REFLECTION: (if history exists) Verify previous action's effect
 * 3. PLANNING: (if needed) Get/update plan from Manager
 * 4. EXECUTION: Select and execute action via Executor
 * 5. SETTLING: Wait for UI to stabilize
 * 
 * Features:
 * - Cooperative pause/resume
 * - Cancellation-aware
 * - Event emission for UI updates
 * - Uses SessionServices for all dependencies
 */
class MobileV3Orchestration(
    private val config: OrchestrationConfig,
    private val services: SessionServices,
    private val eventEmitter: EventEmitter,
    private val cancellationSignal: CancellationSignal
) : AgentOrchestration {
    
    companion object {
        private const val TAG = "MobileV3Orchestration"
    }
    
    // Agents (using existing domain implementations)
    private val manager = Manager()
    private val executor = Executor()
    private val reflector = Reflector()
    
    // State
    private val executionState = SessionExecutionState(instruction = config.goal)
    
    // Cooperative pause
    private val pauseState = MutableStateFlow(false)
    
    // Interrupt flag (for mid-turn interruption)
    private var interruptRequested = false
    
    // Stop flag
    private var stopRequested = false
    
    // ===== AgentOrchestration Interface =====
    
    override suspend fun run() {
        Log.i(TAG, "Starting MobileV3Orchestration for goal: ${config.goal}")
        emitStatus("🚀 Starting agent for: ${config.goal}")
        
        var previousSnapshot: ScreenSnapshot? = null
        
        while (shouldContinue()) {
            // Check cooperative pause
            checkPause()
            
            // Check for cancellation
            if (cancellationSignal.isCompleted || stopRequested) {
                Log.i(TAG, "Cancellation/stop detected, exiting loop")
                break
            }
            
            // Check for interrupt
            if (interruptRequested) {
                Log.i(TAG, "Interrupt requested, resetting for next turn")
                interruptRequested = false
                previousSnapshot = null // Reset to start fresh
                continue
            }
            
            // Check max turns
            if (executionState.turnCount >= config.maxTurns) {
                Log.w(TAG, "Max turns (${config.maxTurns}) reached")
                emitStatus("⚠️ Max turns reached")
                cancellationSignal.complete(CancellationReason.MaxTurnsReached)
                break
            }
            
            // Execute one turn
            val turnResult = executeTurn(previousSnapshot)
            
            when (turnResult) {
                is TurnResult.Continue -> {
                    // Wait for UI to settle
                    delay(config.actionDelayMs)
                    // Use the before-action snapshot for next turn's reflection
                    // This allows comparing "before action" vs "after action (next turn's capture)"
                    previousSnapshot = turnResult.beforeSnapshot
                    executionState.recordAction(turnResult.action)
                }
                is TurnResult.Finished -> {
                    Log.i(TAG, "Task finished: ${turnResult.reason}")
                    emitStatus("✅ Task completed: ${turnResult.reason}")
                    cancellationSignal.complete(CancellationReason.GoalAchieved)
                    break
                }
                is TurnResult.Error -> {
                    Log.e(TAG, "Turn error: ${turnResult.message}")
                    executionState.recordError(turnResult.message)
                    if (!turnResult.recoverable) {
                        emitStatus("❌ Fatal error: ${turnResult.message}")
                        cancellationSignal.complete(CancellationReason.Error(turnResult.message))
                        break
                    }
                    emitStatus("⚠️ Error (retrying): ${turnResult.message}")
                    delay(config.actionDelayMs) // Wait before retry
                }
                TurnResult.Interrupted -> {
                    Log.i(TAG, "Turn interrupted")
                    previousSnapshot = null
                    continue
                }
                TurnResult.Cancelled -> {
                    Log.i(TAG, "Turn cancelled")
                    break
                }
            }
        }
        
        Log.i(TAG, "MobileV3Orchestration finished after ${executionState.turnCount} turns")
    }
    
    override suspend fun pause() {
        Log.d(TAG, "Pause requested")
        pauseState.value = true
        emitStatus("⏸️ Paused")
    }
    
    override suspend fun resume() {
        Log.d(TAG, "Resume requested")
        pauseState.value = false
        emitStatus("▶️ Resuming...")
    }
    
    override suspend fun interrupt() {
        Log.d(TAG, "Interrupt requested")
        interruptRequested = true
    }
    
    override suspend fun stop() {
        Log.d(TAG, "Stop requested")
        stopRequested = true
        pauseState.value = false // Unblock if paused
    }
    
    // ===== Turn Execution =====
    
    /**
     * Execute a single turn of the agent loop.
     */
    private suspend fun executeTurn(previousSnapshot: ScreenSnapshot?): TurnResult {
        val turnId = "turn-${executionState.turnCount}"
        
        try {
            // 1. PERCEPTION
            emitTurnPhase(turnId, TurnPhase.PERCEPTION)
            emitStatus("👀 Scanning screen...")
            val currentSnapshot = services.platform.captureScreen()
            emitScreenCaptured(currentSnapshot)
            
            // Check for interrupt/cancel
            if (interruptRequested) return TurnResult.Interrupted
            if (cancellationSignal.isCompleted || stopRequested) return TurnResult.Cancelled
            
            // 2. REFLECTION (if we have history)
            if (previousSnapshot != null && executionState.actionHistory.isNotEmpty()) {
                val lastAction = executionState.getLastAction()
                if (lastAction != null && lastAction !is AgentAction.FinishAction) {
                    emitTurnPhase(turnId, TurnPhase.REFLECTION)
                    emitStatus("🤔 Verifying last action...")
                    
                    val outcome = reflector.validate(previousSnapshot, currentSnapshot, lastAction)
                    executionState.recordOutcome(outcome)
                    
                    emitThought("reflector", "Outcome: ${outcome.description}")
                    Log.d(TAG, "Reflection outcome: $outcome")
                }
            }
            
            // Check for interrupt/cancel
            if (interruptRequested) return TurnResult.Interrupted
            if (cancellationSignal.isCompleted || stopRequested) return TurnResult.Cancelled
            
            // 3. PLANNING (if needed)
            if (executionState.shouldReplan()) {
                // Check if already finished
                if (executionState.plan.contains("Finished", ignoreCase = true) && 
                    executionState.getLastAction() is AgentAction.FinishAction) {
                    Log.i(TAG, "Task already marked as finished")
                    return TurnResult.Finished("Goal achieved")
                }
                
                emitTurnPhase(turnId, TurnPhase.PLANNING)
                emitStatus("🧠 Planning...")
                
                // Create InfoPool-like context for Manager
                val infoPool = createInfoPoolContext()
                val result = manager.think(infoPool, currentSnapshot)
                
                executionState.updatePlan(result.plan, result.completedSubgoal)
                emitThought("manager", "Plan: ${result.plan.take(100)}...")
                Log.d(TAG, "New plan: ${result.plan}")
                
                if (result.plan.equals("Finished", ignoreCase = true)) {
                    return TurnResult.Finished("Manager determined task is complete")
                }
            }
            
            // Check for interrupt/cancel
            if (interruptRequested) return TurnResult.Interrupted
            if (cancellationSignal.isCompleted || stopRequested) return TurnResult.Cancelled
            
            // 4. EXECUTION
            emitTurnPhase(turnId, TurnPhase.EXECUTION)
            emitStatus("💡 Executing...")
            
            val infoPool = createInfoPoolContext()
            val action = executor.think(infoPool, currentSnapshot)
            
            emitThought("executor", "Action: ${formatAction(action)}")
            Log.d(TAG, "Action decided: $action")
            
            // Handle action
            return when (action) {
                is AgentAction.FinishAction -> {
                    TurnResult.Finished(action.reason ?: "Executor marked done")
                }
                is AgentAction.InvalidAction -> {
                    TurnResult.Error(action.reason ?: "Invalid action", recoverable = true)
                }
                else -> {
                    // Execute the action
                    val uiAction = convertToUIAction(action)
                    if (uiAction != null) {
                        val result = services.platform.performAction(uiAction, currentSnapshot)
                        Log.d(TAG, "Action result: $result")
                        emitStatus("✓ ${formatAction(action)}")
                    }
                    // Return with the BEFORE snapshot so next turn can compare
                    // before (currentSnapshot) vs after (next turn's capture)
                    TurnResult.Continue(action, beforeSnapshot = currentSnapshot)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Turn execution failed", e)
            return TurnResult.Error(e.message ?: "Unknown error", recoverable = true)
        }
    }
    
    // ===== Helper Methods =====
    
    /**
     * Check if we should continue the loop.
     */
    private fun shouldContinue(): Boolean {
        return !executionState.isFinished && 
               !stopRequested && 
               !cancellationSignal.isCompleted
    }
    
    /**
     * Check and handle pause state.
     */
    private suspend fun checkPause() {
        if (pauseState.value) {
            // Wait until resumed
            pauseState.first { !it }
        }
    }
    
    /**
     * Create InfoPool-like context for existing agents.
     * 
     * This bridges the new SessionExecutionState to the existing
     * Agent implementations that expect InfoPool.
     */
    private fun createInfoPoolContext(): com.moonkey.androidagent.domain.state.InfoPool {
        return com.moonkey.androidagent.domain.state.InfoPool(
            instruction = executionState.instruction,
            plan = executionState.plan,
            currentSubgoal = executionState.currentSubgoal,
            actionHistory = executionState.actionHistory.toMutableList(),
            outcomes = executionState.outcomes.toMutableList(),
            errorDescriptions = executionState.errorDescriptions.toMutableList(),
            textMemory = executionState.memory,
            errorFlagPlan = executionState.errorFlagPlan
        )
    }
    
    /**
     * Convert AgentAction to platform UIAction.
     */
    private fun convertToUIAction(action: AgentAction): UIAction? {
        if (action !is AgentAction.AtomicAction) return null
        
        return when (action.type) {
            "click" -> action.elementId?.let { UIAction.Click(it) }
            "type" -> {
                val elementId = action.elementId ?: return null
                val text = action.text ?: return null
                UIAction.Type(elementId, text)
            }
            "scroll" -> {
                val direction = when (action.direction?.lowercase()) {
                    "up" -> ScrollDirection.UP
                    "down" -> ScrollDirection.DOWN
                    "left" -> ScrollDirection.LEFT
                    "right" -> ScrollDirection.RIGHT
                    else -> ScrollDirection.DOWN
                }
                UIAction.Scroll(direction)
            }
            "system" -> {
                val button = when (action.button?.lowercase()) {
                    "back" -> SystemButtonType.BACK
                    "home" -> SystemButtonType.HOME
                    "recents" -> SystemButtonType.RECENTS
                    else -> return null
                }
                UIAction.SystemButton(button)
            }
            "wait" -> UIAction.Wait(1000)
            else -> null
        }
    }
    
    /**
     * Format action for display.
     */
    private fun formatAction(action: AgentAction): String {
        return when (action) {
            is AgentAction.AtomicAction -> {
                when (action.type) {
                    "click" -> "Click element ${action.elementId}"
                    "type" -> "Type '${action.text?.take(20)}...' into element ${action.elementId}"
                    "scroll" -> "Scroll ${action.direction}"
                    "system" -> "Press ${action.button}"
                    "wait" -> "Wait"
                    else -> action.type
                }
            }
            is AgentAction.FinishAction -> "Finished: ${action.reason}"
            is AgentAction.InvalidAction -> "Invalid: ${action.reason}"
        }
    }
    
    // ===== Event Emission =====
    
    private suspend fun emitStatus(status: String) {
        eventEmitter(AgentEvent.StatusUpdate(
            sessionId = config.sessionId,
            timestamp = System.currentTimeMillis(),
            status = status
        ))
    }
    
    private suspend fun emitTurnPhase(turnId: String, phase: TurnPhase) {
        eventEmitter(AgentEvent.TurnStarted(
            sessionId = config.sessionId,
            timestamp = System.currentTimeMillis(),
            turnId = turnId,
            turnNumber = executionState.turnCount,
            phase = com.moonkey.androidagent.protocol.TurnPhase.valueOf(phase.name)
        ))
    }
    
    private suspend fun emitThought(agent: String, thought: String) {
        eventEmitter(AgentEvent.AgentThinking(
            sessionId = config.sessionId,
            timestamp = System.currentTimeMillis(),
            agentName = agent,
            thought = thought
        ))
    }
    
    private suspend fun emitScreenCaptured(snapshot: ScreenSnapshot) {
        eventEmitter(AgentEvent.ScreenCaptured(
            sessionId = config.sessionId,
            timestamp = System.currentTimeMillis(),
            elementCount = snapshot.elements.size,
            packageName = services.platform.getCurrentPackageName(),
            activityName = null
        ))
    }
}

