/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import com.google.gson.annotations.SerializedName

/**
 * Gson DTOs for the Anthropic Messages API (POST /v1/messages). @SerializedName maps the snake_case
 * wire keys to idiomatic Kotlin properties; the request is non-streaming (a short summary keeps it
 * simple).
 */
data class AnthropicRequest(
    val model: String,
    @SerializedName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<AnthropicMessage>,
)

data class AnthropicMessage(val role: String, val content: String)

/**
 * Tolerant response shape: every field is nullable so a malformed or error body parses without a
 * Gson exception, and the provider decides what is missing. A non-2xx body carries [error]; a 2xx
 * body carries [content].
 */
data class AnthropicResponse(
    val content: List<AnthropicContentBlock>?,
    @SerializedName("stop_reason") val stopReason: String?,
    val error: AnthropicError?,
)

data class AnthropicContentBlock(val type: String?, val text: String?)

data class AnthropicError(val type: String?, val message: String?)
