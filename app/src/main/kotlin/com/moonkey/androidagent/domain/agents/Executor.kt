package com.moonkey.androidagent.domain.agents

import com.moonkey.androidagent.data.llm.ChatMessage
import com.moonkey.androidagent.data.llm.LLMClient
import com.moonkey.androidagent.data.llm.Role
import com.moonkey.androidagent.data.perception.Perceptor
import com.moonkey.androidagent.domain.models.AgentAction
import com.moonkey.androidagent.domain.models.ScreenSnapshot
import com.moonkey.androidagent.domain.state.InfoPool
import org.json.JSONObject

class Executor : Agent<AgentAction> {

    override suspend fun think(scope: InfoPool, context: ScreenSnapshot): AgentAction {
        val prompt = buildPrompt(scope, context)

        val messages =
                listOf(ChatMessage(Role.SYSTEM, SYSTEM_PROMPT), ChatMessage(Role.USER, prompt))

        val response = LLMClient.chat(messages)
        return parseResponse(response)
    }

    private fun buildPrompt(scope: InfoPool, context: ScreenSnapshot): String {
        val sb = StringBuilder()
        sb.append("### User Request ###\n${scope.instruction}\n\n")
        sb.append("### Current Plan ###\n${scope.plan}\n\n")

        // Extract current subgoal logic (taking first line of plan)
        val currentSubgoal = scope.plan.lines().firstOrNull() ?: scope.instruction
        sb.append("### Current Subgoal ###\n$currentSubgoal\n\n")

        val screenJson = Perceptor.toPromptJson(context)
        sb.append("### Screen Context ###\n$screenJson\n\n")

        return sb.toString()
    }

    private fun parseResponse(json: String): AgentAction {
        return try {
            val obj = JSONObject(json)
            val actionType = obj.optString("action")
            val reason = obj.optString("reason")

            when (actionType) {
                "click" ->
                        AgentAction.AtomicAction(
                                type = "click",
                                elementId = obj.optInt("element_id"),
                                reason = reason
                        )
                "type" ->
                        AgentAction.AtomicAction(
                                type = "type",
                                elementId = obj.optInt("element_id"),
                                text = obj.optString("text"),
                                reason = reason
                        )
                "scroll" ->
                        AgentAction.AtomicAction(
                                type = "scroll",
                                direction = obj.optString("direction"),
                                reason = reason
                        )
                "system" ->
                        AgentAction.AtomicAction(
                                type = "system",
                                button = obj.optString("button"),
                                reason = reason
                        )
                "answer" -> AgentAction.FinishAction(reason = "Answer: ${obj.optString("text")}")
                "wait" -> AgentAction.AtomicAction(type = "wait", reason = reason)
                "done" -> AgentAction.FinishAction(reason = reason)
                else -> AgentAction.InvalidAction("Unknown action type: $actionType")
            }
        } catch (e: Exception) {
            AgentAction.InvalidAction("Error parsing JSON: ${e.message}")
        }
    }

    companion object {
        val SYSTEM_PROMPT =
                """
            You are an Executor Agent for Android.
            Your goal is to perform the NEXT ATOMIC ACTION to fulfill the Current Subgoal.
            
            Available Actions:
            - {"action": "click", "element_id": 12, "reason": "..."}
            - {"action": "type", "element_id": 12, "text": "hello", "reason": "..."}
            - {"action": "scroll", "direction": "up/down/left/right", "reason": "..."}
            - {"action": "system", "button": "back/home/enter", "reason": "..."}
            - {"action": "answer", "text": "final answer", "reason": "..."}  (If task requests an answer)
            - {"action": "done", "reason": "..."} (If subgoal is visibly finished)
            
            Rules:
            1. Use 'element_id' from Screen Context.
            2. To scroll down (to see bottom), direction="down".
            3. Output strictly valid JSON.
        """.trimIndent()
    }
}
