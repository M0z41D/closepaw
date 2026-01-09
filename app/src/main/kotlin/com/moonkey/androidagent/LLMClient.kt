package com.moonkey.androidagent

import android.util.Log
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LLMClient - Wrapper for OpenAI API using official SDK.
 * Mirrors the LLM decision logic from kernel.py.
 */
object LLMClient {

    private const val TAG = "LLMClient"
    
    private var client: OpenAIClient? = null

    private val SYSTEM_PROMPT = """
You are an Android Driver Agent. Your job is to achieve the user's goal by navigating the UI.

You will receive:
1. The User's GOAL.
2. A SCREEN JSON list of UI elements with: index, text, id, class, bounds, center, clickable, editable, scrollable.

You must output ONLY a valid JSON object with your next action.

Available Actions:
- {"action": "tap", "target": {"by": "index", "value": N}, "reason": "Why you are tapping"}
- {"action": "type", "target": {"by": "index", "value": N}, "text": "text to type", "reason": "Why you are typing"}
- {"action": "scroll", "target": {"by": "index", "value": N}, "direction": "down|up", "reason": "Why you are scrolling"}
- {"action": "back", "reason": "Go back"}
- {"action": "home", "reason": "Go to home screen"}
- {"action": "wait", "ms": 1200, "reason": "Wait for loading"}
- {"action": "done", "reason": "Task complete"}

Rules:
- Use the element's "index" to target UI elements
- Only output valid JSON, no markdown or explanation
- If you can't find what you need, try scrolling or going back
- When the goal is achieved, output the "done" action

Example Output:
{"action": "tap", "target": {"by": "index", "value": 5}, "reason": "Clicking the Settings button"}
""".trimIndent()

    fun initialize(apiKey: String) {
        client = OpenAIOkHttpClient.builder()
            .apiKey(apiKey)
            .build()
    }

    suspend fun nextAction(goal: String, screenJson: String, history: List<String>): String {
        return withContext(Dispatchers.IO) {
            try {
                val historyText = if (history.isNotEmpty()) {
                    "\n\nPrevious actions:\n" + history.takeLast(6).joinToString("\n")
                } else ""

                val userContent = "GOAL: $goal\n\nSCREEN:\n$screenJson$historyText"

                val params = ChatCompletionCreateParams.builder()
                    .model(ChatModel.GPT_4O)
                    .addSystemMessage(SYSTEM_PROMPT)
                    .addUserMessage(userContent)
                    .build()

                val response = client!!.chat().completions().create(params)

                val content = response.choices()[0].message().content().orElse("")
                Log.d(TAG, "LLM Response: $content")
                
                // Clean up response (remove markdown if present)
                content.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
            } catch (e: Exception) {
                Log.e(TAG, "LLM call failed", e)
                // Fallback: wait action
                """{"action": "wait", "ms": 1200, "reason": "LLM error: ${e.message}"}"""
            }
        }
    }
}
