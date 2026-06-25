/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for the Anthropic Messages API. Returns [retrofit2.Response] (not a bare
 * [AnthropicResponse]) so the provider can inspect the HTTP code and read the error body itself,
 * rather than depending on Retrofit raising an HttpException it would have to unwrap.
 */
interface AnthropicService {

  @POST("v1/messages")
  suspend fun messages(@Body request: AnthropicRequest): retrofit2.Response<AnthropicResponse>
}
