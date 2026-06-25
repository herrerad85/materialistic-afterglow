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
 * Attaches Anthropic auth headers to each outbound request. The key is read from [SecretStore] at
 * intercept() time (not cached on the OkHttp/Retrofit instance) so a key change made by the user is
 * picked up on the next call and the key never lives on a long-lived singleton.
 *
 * If no key is set we throw [IOException] rather than send a keyless request, so no request without
 * credentials ever leaves the device (defense in depth; the provider also pre-checks). Never log
 * the key.
 */
class AnthropicAuthInterceptor(private val secretStore: SecretStore) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val key = secretStore.getApiKey()
    if (key.isNullOrBlank()) {
      throw IOException("AI API key not set")
    }
    // Never log the key: it is attached as a header only, on the per-call request.
    val authed =
        chain
            .request()
            .newBuilder()
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .build()
    return chain.proceed(authed)
  }
}
