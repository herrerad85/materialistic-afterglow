/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

/** In-memory [SecretStore] for consumer tests (no Keystore/Tink), E7-D8. */
class FakeSecretStore : SecretStore {
  private var apiKey: String? = null

  override fun putApiKey(value: String) {
    apiKey = value
  }

  override fun getApiKey(): String? = apiKey

  override fun hasApiKey(): Boolean = !apiKey.isNullOrEmpty()

  override fun clearApiKey() {
    apiKey = null
  }
}
