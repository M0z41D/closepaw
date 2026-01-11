package com.moonkey.androidagent.data.llm

import android.util.Log
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** LLMClient - Generic Wrapper for OpenAI API. */
object LLMClient {

    private const val TAG = "LLMClient"
    private var client: OpenAIClient? = null

    fun initialize(apiKey: String) {
        client = OpenAIOkHttpClient.builder().apiKey(apiKey).build()
    }

    suspend fun chat(messages: List<ChatMessage>): String {
        return withContext(Dispatchers.IO) {
            try {
                if (client == null) throw IllegalStateException("LLMClient not initialized")

                val builder = ChatCompletionCreateParams.builder().model(ChatModel.GPT_4O)

                messages.forEach { msg ->
                    when (msg.role) {
                        Role.SYSTEM -> builder.addSystemMessage(msg.content)
                        Role.USER -> builder.addUserMessage(msg.content)
                        Role.ASSISTANT -> builder.addAssistantMessage(msg.content)
                    }
                }

                val response = client!!.chat().completions().create(builder.build())
                val content = response.choices()[0].message().content().orElse("")

                Log.d(TAG, "LLM Response: $content")
                content.cleanJson()
            } catch (e: Exception) {
                Log.e(TAG, "LLM call failed", e)
                throw e
            }
        }
    }

    private fun String.cleanJson(): String {
        return this.trim().replace("```json", "").replace("```", "").trim()
    }
}
