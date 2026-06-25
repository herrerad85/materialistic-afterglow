/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

/**
 * Gson DTOs for the Gemini generateContent API (POST v1beta/models/{model}:generateContent). The
 * wire keys are camelCase, so the property names match and need no @SerializedName. Default Gson
 * omits null fields, so a [GeminiContent] with a null role serializes as just {parts:[...]} (the
 * shape systemInstruction expects).
 */
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent?,
    val generationConfig: GeminiGenerationConfig,
)

data class GeminiContent(val role: String?, val parts: List<GeminiPart>)

data class GeminiPart(val text: String?)

data class GeminiGenerationConfig(val maxOutputTokens: Int)

/**
 * Tolerant response shape: every field nullable so a malformed or error body parses without a Gson
 * exception and the provider decides what is missing. A 2xx body carries [candidates]; a safety
 * block carries [promptFeedback].blockReason with no candidate text; a non-2xx body carries
 * [error].
 */
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?,
    val promptFeedback: GeminiPromptFeedback?,
    val error: GeminiError?,
)

data class GeminiCandidate(val content: GeminiContent?, val finishReason: String?)

data class GeminiPromptFeedback(val blockReason: String?)

data class GeminiError(val code: Int?, val message: String?, val status: String?)
