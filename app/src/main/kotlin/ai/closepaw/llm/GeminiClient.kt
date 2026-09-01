package ai.closepaw.llm

import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseInputItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Simple Gemini client that uses an API key to call Google's Generative Models REST API.
 *
 * WARNING: Storing API keys in the client is not recommended for production. Prefer a
 * server-side service account approach. This implementation is intentionally minimal and
 * designed to integrate with the existing LLMClient interface used by the app.
 */
class GeminiClient(
    private val apiKeySupplier: () -> String,
    private val baseUrl: String? = null,
) : LLMClient() {

    override suspend fun chatWithTools(
        systemPrompt: String,
        inputItems: List<ResponseInputItem>,
        tools: List<FunctionTool>,
        model: String,
        maxOutputTokens: Long?
    ): ResponsesResult = withContext(Dispatchers.IO) {
        // Build a simple prompt from systemPrompt + inputs
        val promptBuilder = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            promptBuilder.append(systemPrompt).append("\n\n")
        }
        inputItems.forEach { item ->
            // ResponseInputItem has role/content; we approximate by concatenating
            promptBuilder.append(item.role).append(": ")
            // Try to extract text candidate if available
            val content = try {
                item.content?.getString(0) ?: ""
            } catch (e: Exception) {
                // Fallback: toString
                item.content?.toString() ?: ""
            }
            promptBuilder.append(content).append("\n")
        }

        val prompt = promptBuilder.toString()

        // Endpoint: use explicit baseUrl if provided, otherwise use GenAI endpoint v1beta2
        // We append ?key=APIKEY for API-key-based access.
        val apiKey = apiKeySupplier()
        val modelId = if (model.isNotBlank()) model else "gemini-pro"
        val endpoint = when {
            !baseUrl.isNullOrBlank() -> baseUrl
            else -> "https://generativelanguage.googleapis.com/v1beta2/models/$modelId:generate"
        }
        val urlStr = if (apiKey.isNotBlank()) "$endpoint?key=$apiKey" else endpoint
        val url = URL(urlStr)

        val bodyJson = JSONObject().apply {
            put("prompt", JSONObject().apply { put("text", prompt) })
            maxOutputTokens?.let { put("maxOutputTokens", it) }
        }

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
        }

        try {
            conn.outputStream.use { it.write(bodyJson.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val respText = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                throw Exception("HTTP $code: $err")
            }

            // Parse candidate text if present
            val respJson = JSONObject(respText)
            val candidate = when {
                respJson.has("candidates") -> respJson.getJSONArray("candidates").optJSONObject(0)?.optString("output", null)
                respJson.has("output") -> respJson.optString("output", null)
                respJson.has("content") -> respJson.optString("content", null)
                else -> null
            }

            val text = candidate ?: respJson.toString()
            val responseId = UUID.randomUUID().toString()
            return@withContext ResponsesResult(textContent = text, toolCalls = emptyList(), responseId = responseId)
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    override fun chatWithToolsStreaming(
        systemPrompt: String,
        inputItems: List<ResponseInputItem>,
        tools: List<FunctionTool>,
        model: String
    ): Flow<LLMStreamEvent> = flow {
        // For simplicity, implement streaming as a single non-streaming call that emits
        // created, a single TextDelta, and Completed.
        emit(LLMStreamEvent.Created(responseId = "gemini-${System.currentTimeMillis()}"))
        val result = try {
            val res = runCatching {
                // Reuse the synchronous implementation
                kotlinx.coroutines.runBlocking {
                    chatWithTools(systemPrompt, inputItems, tools, model)
                }
            }
            res.getOrThrow()
        } catch (e: Exception) {
            emit(LLMStreamEvent.Failed(e.message ?: "Gemini request failed"))
            return@flow
        }
        result.textContent?.let { emit(LLMStreamEvent.TextDelta(it)) }
        emit(LLMStreamEvent.Completed)
    }

}
