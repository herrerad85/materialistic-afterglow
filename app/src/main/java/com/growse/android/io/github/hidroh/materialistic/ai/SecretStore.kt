/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

/**
 * Stores the user's bring-your-own LLM API key encrypted at rest, Android Keystore backed. Never
 * plain prefs.
 */
interface SecretStore {
  fun putApiKey(value: String)

  fun getApiKey(): String?

  fun hasApiKey(): Boolean

  fun clearApiKey()
}
