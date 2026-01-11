package com.moonkey.androidagent.domain.agents

import com.moonkey.androidagent.data.llm.ChatMessage
import com.moonkey.androidagent.data.llm.LLMClient
import com.moonkey.androidagent.data.llm.Role
import com.moonkey.androidagent.data.perception.Perceptor
import com.moonkey.androidagent.domain.models.ManagerResult
import com.moonkey.androidagent.domain.models.ScreenSnapshot
import com.moonkey.androidagent.domain.state.InfoPool
import org.json.JSONObject

class Manager : Agent<ManagerResult> {

    override suspend fun think(scope: InfoPool, context: ScreenSnapshot): ManagerResult {
        val prompt = buildPrompt(scope, context)

        val messages =
                listOf(ChatMessage(Role.SYSTEM, SYSTEM_PROMPT), ChatMessage(Role.USER, prompt))

        val response = LLMClient.chat(messages)
        return parseResponse(response)
    }

    private fun buildPrompt(scope: InfoPool, context: ScreenSnapshot): String {
        val sb = StringBuilder()
        sb.append("### User Request ###\n${scope.instruction}\n\n")

        if (scope.plan.isEmpty()) {
            sb.append(
                    "### Task ###\nMake a high-level plan to achieve the request. The screen context is provided below.\n"
            )
        } else {
            sb.append("### Current Plan ###\n${scope.plan}\n\n")
            if (scope.actionHistory.isNotEmpty()) {
                sb.append("### History ###\nLast Action: ${scope.actionHistory.lastOrNull()}\n")
            }
            sb.append(
                    "### Completed Subgoal ###\n${scope.currentSubgoal}\n\n"
            ) // Renamed in InfoPool logic, but kept concept.
        }

        if (scope.errorFlagPlan) {
            sb.append(
                    "### WARNING ###\nThe executor is stuck. Please revise the plan to try a different approach.\n\n"
            )
        }

        // Screen Context
        val screenJson = Perceptor.toPromptJson(context)
        sb.append("### Screen Context ###\n$screenJson\n\n")

        return sb.toString()
    }

    private fun parseResponse(json: String): ManagerResult {
        return try {
            val obj = JSONObject(json)
            ManagerResult(
                    thought = obj.optString("thought", ""),
                    plan = obj.optString("plan", ""),
                    completedSubgoal = obj.optString("completed_subgoal", "No completed subgoal.")
            )
        } catch (e: Exception) {
            ManagerResult("Error parsing JSON: $json", "Finished", "")
        }
    }

    companion object {
        val SYSTEM_PROMPT =
                """
            You are a Manager Agent for an Android phone.
            Your goal is to break down user instructions into a high-level plan.
            
            Output strictly in JSON format:
            {
                "thought": "Your reasoning here",
                "plan": "1. step one\n2. step two...",
                "completed_subgoal": "What has been done so far (e.g. 'Opened Settings')"
            }
            
            Guidelines:
            1. If the task is finished, set plan to "Finished".
            2. If starting, list all steps.
            3. Use the Screen Context to verify if steps are completed.
        """.trimIndent()
    }
}
