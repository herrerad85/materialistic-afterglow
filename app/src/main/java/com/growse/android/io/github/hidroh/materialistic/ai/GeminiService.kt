/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit interface for the Gemini generateContent API. The model id lives in the path
 * (models/{model}:generateContent), so the provider passes the full relative URL via [Url] rather
 * than a @Path (keeps the ':generateContent' verb out of path-segment encoding). Returns
 * [retrofit2.Response] so the provider can read the HTTP code and error body itself.
 */
interface GeminiService {

  @POST
  suspend fun generateContent(
      @Url url: String,
      @Body request: GeminiRequest,
  ): retrofit2.Response<GeminiResponse>
}
