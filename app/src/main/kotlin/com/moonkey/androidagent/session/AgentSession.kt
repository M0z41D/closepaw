package com.moonkey.androidagent.session

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.moonkey.androidagent.platform.AccessibilityPlatform
import com.moonkey.androidagent.platform.AndroidPlatform
import com.moonkey.androidagent.protocol.*
import com.moonkey.androidagent.service.AgentOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * AgentSession - Manages the lifecycle of an agent execution.
 * 
 * This is the main entry point for controlling the agent. It:
 * - Receives Operations (Op) via submit()
 * - Emits Events (AgentEvent) via the events Flow
 * - Maintains session state via the state StateFlow
 * 
 * **Phase 5 Implementation**: Now supports SessionServices for full DI.
 * - Can be created with SessionServices for new orchestration (Phase 6)
 * - Falls back to legacy AgentOrchestrator for backward compatibility
 * - Services include: ToolRegistry, ToolRouter, AgentRegistry, HistoryManager, PolicyEngine
 */
class AgentSession private constructor(
    val sessionId: SessionId,
    private val config: SessionConfig,
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val services: SessionServices? = null
) {
    companion object {
        private const val TAG = "AgentSession"
        
        /**
         * Create a new AgentSession (legacy mode without SessionServices).
         * 
         * This maintains backward compatibility with Phase 2 implementation.
         */
        fun create(
            config: SessionConfig,
            service: AccessibilityService,
            scope: CoroutineScope
        ): AgentSession {
            return AgentSession(
                sessionId = SessionId.generate(),
                config = config,
                service = service,
                scope = scope,
                services = null
            )
        }
        
        /**
         * Create a new AgentSession with full SessionServices support.
         * 
         * This is the preferred way to create sessions for Phase 6+ orchestration.
         * 
         * @param config Session configuration
         * @param service AccessibilityService for platform access
         * @param scope CoroutineScope for async operations
         * @return AgentSession with SessionServices initialized
         */
        suspend fun createWithServices(
            config: SessionConfig,
            service: AccessibilityService,
            scope: CoroutineScope
        ): AgentSession {
            val platform: AndroidPlatform = AccessibilityPlatform(service)
            val services = SessionServices.create(config, platform)
            
            return AgentSession(
                sessionId = SessionId.generate(),
                config = config,
                service = service,
                scope = scope,
                services = services
            )
        }
        
        /**
         * Create a new AgentSession with pre-created SessionServices.
         * 
         * Useful for testing or custom service configuration.
         */
        fun createWithServices(
            config: SessionConfig,
            service: AccessibilityService,
            scope: CoroutineScope,
            services: SessionServices
        ): AgentSession {
            return AgentSession(
                sessionId = SessionId.generate(),
                config = config,
                service = service,
                scope = scope,
                services = services
            )
        }
    }
    
    /**
     * Get the SessionServices if available.
     * 
     * Returns null if session was created without services (legacy mode).
     */
    fun getServices(): SessionServices? = services
    
    /**
     * Check if this session has SessionServices available.
     */
    fun hasServices(): Boolean = services != null
    
    // ===== State =====
    
    private val _state = MutableStateFlow<SessionState>(SessionState.Created)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    
    // ===== Events =====
    
    private val eventChannel = Channel<AgentEvent>(Channel.BUFFERED)
    val events: Flow<AgentEvent> = eventChannel.receiveAsFlow()
    
    // ===== Bridge to Legacy Orchestrator =====
    
    private var legacyOrchestrator: AgentOrchestrator? = null
    private var currentGoal: String = ""
    
    /**
     * Submit an operation to the session.
     * 
     * This is thread-safe and can be called from any thread.
     */
    suspend fun submit(op: Op) {
        Log.d(TAG, "Received Op: $op (current state: ${_state.value})")
        
        when (op) {
            is Op.Start -> handleStart(op)
            is Op.Pause -> handlePause()
            is Op.Resume -> handleResume()
            is Op.Interrupt -> handleInterrupt()
            is Op.Shutdown -> handleShutdown()
            is Op.UserInput -> handleUserInput(op)
            is Op.Approve -> handleApproval(op)
        }
    }
    
    /**
     * Emit a status update as an event.
     * 
     * This is the bridge method that converts legacy status callbacks to events.
     */
    internal fun emitStatus(status: String, emoji: String? = null) {
        scope.launch {
            emit(AgentEvent.StatusUpdate(
                sessionId = sessionId,
                timestamp = now(),
                status = status,
                emoji = emoji
            ))
        }
    }
    
    // ===== Operation Handlers =====
    
    private suspend fun handleStart(op: Op.Start) {
        if (_state.value != SessionState.Created) {
            emit(AgentEvent.SessionError(
                sessionId = sessionId,
                timestamp = now(),
                error = AgentError.InvalidStateError(
                    message = "Cannot start: session already started",
                    currentState = _state.value.toString(),
                    attemptedOperation = "Start"
                )
            ))
            return
        }
        
        currentGoal = op.goal
        _state.value = SessionState.Running
        
        emit(AgentEvent.SessionStarted(
            sessionId = sessionId,
            timestamp = now(),
            goal = op.goal
        ))
        
        // Create and start the legacy orchestrator
        legacyOrchestrator = AgentOrchestrator(
            service = service,
            scope = scope,
            statusListener = { status -> emitStatus(status) }
        )
        
        legacyOrchestrator?.start(op.goal)
        
        Log.i(TAG, "Session started: $sessionId, goal: ${op.goal}")
    }
    
    private suspend fun handlePause() {
        if (_state.value != SessionState.Running) {
            Log.w(TAG, "Cannot pause: not running (state: ${_state.value})")
            return
        }
        
        _state.value = SessionState.Paused
        legacyOrchestrator?.pause()
        
        emit(AgentEvent.SessionPaused(
            sessionId = sessionId,
            timestamp = now()
        ))
        
        Log.i(TAG, "Session paused: $sessionId")
    }
    
    private suspend fun handleResume() {
        if (_state.value != SessionState.Paused) {
            Log.w(TAG, "Cannot resume: not paused (state: ${_state.value})")
            return
        }
        
        _state.value = SessionState.Running
        legacyOrchestrator?.resume()
        
        emit(AgentEvent.SessionResumed(
            sessionId = sessionId,
            timestamp = now()
        ))
        
        Log.i(TAG, "Session resumed: $sessionId")
    }
    
    private suspend fun handleInterrupt() {
        if (_state.value != SessionState.Running) {
            Log.w(TAG, "Cannot interrupt: not running (state: ${_state.value})")
            return
        }
        
        // For Phase 2, interrupt just stops and we stay in Running state
        // In future phases, this will abort the current turn but keep session alive
        Log.i(TAG, "Interrupt requested (Phase 2: treated as stop)")
        handleShutdown()
    }
    
    private suspend fun handleShutdown() {
        Log.i(TAG, "Shutting down session: $sessionId")
        
        val previousState = _state.value
        _state.value = SessionState.Shutdown
        
        // Stop legacy orchestrator
        legacyOrchestrator?.stop()
        legacyOrchestrator = null
        
        // Cleanup SessionServices if available
        services?.cleanup()
        
        emit(AgentEvent.SessionCompleted(
            sessionId = sessionId,
            timestamp = now(),
            result = null,
            reason = if (previousState == SessionState.Running || previousState == SessionState.Paused) {
                CompletionReason.USER_STOPPED
            } else {
                CompletionReason.INTERRUPTED
            }
        ))
        
        // Close event channel
        eventChannel.close()
    }
    
    private suspend fun handleUserInput(op: Op.UserInput) {
        // Phase 2: User input not yet supported in legacy orchestrator
        Log.w(TAG, "UserInput not yet supported in Phase 2: ${op.text}")
        emitStatus("User input not yet supported")
    }
    
    private suspend fun handleApproval(op: Op.Approve) {
        // Phase 5: Forward approval to ToolRouter if services are available
        if (services != null) {
            services.toolRouter.resolveApproval(op.actionId, op.decision)
            Log.d(TAG, "Resolved approval: ${op.actionId} -> ${op.decision}")
        } else {
            // Legacy mode: Approval not supported
            Log.w(TAG, "Approval not supported in legacy mode: ${op.actionId} -> ${op.decision}")
            emitStatus("Approval not yet supported")
        }
    }
    
    // ===== Helpers =====
    
    private suspend fun emit(event: AgentEvent) {
        try {
            eventChannel.send(event)
            Log.d(TAG, "Emitted event: ${event::class.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to emit event: $event", e)
        }
    }
    
    private fun now(): Long = System.currentTimeMillis()
}

