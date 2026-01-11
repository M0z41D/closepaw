package com.moonkey.androidagent.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.moonkey.androidagent.data.perception.Perceptor
import com.moonkey.androidagent.domain.agents.Executor
import com.moonkey.androidagent.domain.agents.Manager
import com.moonkey.androidagent.domain.agents.Reflector
import com.moonkey.androidagent.domain.models.AgentAction
import com.moonkey.androidagent.domain.models.ScreenSnapshot
import com.moonkey.androidagent.domain.state.InfoPool
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AgentOrchestrator(
        private val service: AccessibilityService,
        private val scope: CoroutineScope,
        private val statusListener: (String) -> Unit
) {
    private val TAG = "AgentOrchestrator"

    private val manager = Manager()
    private val executor = Executor()
    private val reflector = Reflector()
    private val dispatcher = ActionDispatcher(service)

    private var job: Job? = null
    private var isPaused = AtomicBoolean(false)

    fun start(goal: String) {
        stop()
        job =
                scope.launch(Dispatchers.Default) {
                    try {
                        runLoop(goal)
                    } catch (e: Exception) {
                        Log.e(TAG, "Agent Loop Crashed", e)
                    }
                }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun pause() {
        isPaused.set(true)
        statusListener("⏸️ Paused")
    }

    fun resume() {
        isPaused.set(false)
        statusListener("▶️ Resuming...")
    }

    private suspend fun runLoop(goal: String) {
        val infoPool = InfoPool(instruction = goal)
        var previousSnapshot: ScreenSnapshot? = null
        var lastAction: AgentAction? = null

        Log.i(TAG, "Starting Agent Loop for goal: $goal")
        statusListener("Starting Agent for: $goal")

        while (scope.isActive) {
            if (isPaused.get()) {
                delay(500)
                continue
            }

            // 1. Perception
            statusListener("👀 Scanning Screen...")
            // Ensure we are on main thread to get rootInActiveWindow if needed, or depends on
            // Android API safety.
            // rootInActiveWindow is thread-safe call but returns Node bound to thread?
            // Usually safest to fetch root, then simplify.
            // But Perceptor needs to traverse. Traversing nodes acts on the Main Thread usually?
            // Actually AccessibilityNodeInfo is parcelable/transferable but calls are IPC.
            val root = service.rootInActiveWindow
            val currentSnapshot = Perceptor.snapshot(root)

            // 2. Reflection
            if (previousSnapshot != null &&
                            lastAction != null &&
                            lastAction !is AgentAction.FinishAction
            ) {
                statusListener("🤔 Verifying last action...")
                val outcome = reflector.validate(previousSnapshot!!, currentSnapshot, lastAction)
                infoPool.recordOutcome(outcome)
                Log.d(TAG, "Reflection Outcome: $outcome")

                // If B (Failed Backtrack), we might want to trigger immediate backtrack logic or
                // just let Manager handle it via InfoPool error flags.
                // For MVP, we let Manager see the error logs.
            }

            // 3. Planning
            // Trigger Manager if:
            // a) Plan is empty (Start)
            // b) Previous action was Finish/Done (Subgoal completed?) - wait, executor outputs
            // "Done" action
            // c) Error flag is set
            // d) Executor says "finished subgoal"

            val shouldPlan =
                    infoPool.plan.isEmpty() ||
                            infoPool.errorFlagPlan ||
                            (lastAction is AgentAction.FinishAction) // Subgoal finished?

            // Actually, we should check if current plan is "Finished".
            // If plan is "Finished" and verified, we are done.
            if ("Finished" in infoPool.plan && lastAction is AgentAction.FinishAction) {
                Log.i(TAG, "Task Goal Reached!")
                break
            }

            if (shouldPlan) {
                Log.d(TAG, "Planning...")
                statusListener("🧠 Planning...")
                val result = manager.think(infoPool, currentSnapshot)
                infoPool.plan = result.plan
                infoPool.currentSubgoal = result.completedSubgoal // Update history? Manager returns
                // "completed_subgoal" string to log.

                // Update history with completed subgoal if not empty
                // Logic: Manager says "I completed X".

                Log.d(TAG, "New Plan: ${infoPool.plan}")

                if (result.plan == "Finished") {
                    Log.i(TAG, "Manager decided task is finished.")
                    break
                }
            }

            // 4. Execution
            Log.d(TAG, "Executing...")
            statusListener("💡 Executing...")
            val action = executor.think(infoPool, currentSnapshot)
            Log.d(TAG, "Action Decided: $action")

            if (action is AgentAction.FinishAction) {
                // Executor believes subgoal is done or task is done.
                // If Executor says "Done", next loop 'shouldPlan' will be true (via lastAction
                // type)
                // We don't dispatch anything for FinishAction usually, unless it has reason.
            } else if (action is AgentAction.InvalidAction) {
                // Log error
                infoPool.errorDescriptions.add(action.reason ?: "Invalid Action")
            } else {
                dispatcher.perform(action, currentSnapshot)
            }

            // 5. Update State
            lastAction = action
            previousSnapshot = currentSnapshot
            infoPool.actionHistory.add(action) // Add to history

            delay(2000) // Wait for UI to settle
        }
    }
}
