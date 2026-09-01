# Google Gemini (Vertex AI) — integration notes

This document explains the simple client-side API-key based Gemini integration added in
`feature/add-gemini-provider` branch.

WARNING: Storing API keys in the mobile app is not recommended for production. The app
now supports a GEMINI provider that reads an API key from the in-app credential store
(AuthStore). If you plan to ship to users, prefer a server-side service account approach
(see recommended flow in docs).

Quick start (client-side API key)

1. Obtain an API key for Google Generative Models (if supported) or create an API key in
   Google Cloud Console for the relevant Vertex AI Generative API. Place the key in the
   app's Settings -> LLM API Key field (the UI exposes provider-specific API key input).

2. In the app settings, pick model "gemini-pro" from the model picker (or add your own
   model id). The client will call the Generative Models REST endpoint using the stored
   API key.

3. Example environment variable name used by dev tooling: GEMINI_API_KEY

Developer notes

- Code added:
  - app/src/main/kotlin/ai/closepaw/llm/GeminiClient.kt — minimal Gemini client using API-key based requests to the
    Generative Language REST endpoint (v1beta2). It maps the app's ResponseInputItem -> prompt text and
    returns text output as ResponsesResult.
  - app/src/main/kotlin/ai/closepaw/llm/LLMProvider.kt — new LLMProvider.GEMINI enum and display label.
  - app/src/main/kotlin/ai/closepaw/llm/LLMClientFactory.kt — registers GeminiClient for GEMINI provider.
  - app/src/main/assets/llm_models.json — example model entry `gemini-pro`.

- The Gemini client implemented here is intentionally lightweight and conservative. The
  Generative Models API has multiple request/response shapes and streaming options; adapt
  the implementation to your chosen model and endpoint.

Security

- Do NOT commit real API keys to the repository.
- For production, create a server-side proxy that holds a Google Cloud service account
  and calls Vertex AI on behalf of the app. The app then authenticates to your backend.

