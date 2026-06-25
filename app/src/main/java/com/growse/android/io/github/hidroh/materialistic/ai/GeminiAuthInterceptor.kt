/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the Gemini auth header to each outbound request. Mirrors [AnthropicAuthInterceptor]: the
 * key is read from [SecretStore] at intercept() time (never cached on the singleton, never logged),
 * and a keyless request throws [IOException] rather than leaving the device without credentials.
 * Gemini authenticates with the x-goog-api-key header (not anthropic's x-api-key).
 */
class GeminiAuthInterceptor(private val secretStore: SecretStore) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val key = secretStore.getApiKey()
    if (key.isNullOrBlank()) {
      throw IOException("AI API key not set")
    }
    val authed =
        chain
            .request()
            .newBuilder()
            .header("x-goog-api-key", key)
            .header("content-type", "application/json")
            .build()
    return chain.proceed(authed)
  }
}
