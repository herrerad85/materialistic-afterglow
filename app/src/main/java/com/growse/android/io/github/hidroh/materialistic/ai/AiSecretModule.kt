/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the AI [SecretStore] seam (E7-D3) to the Keystore + Tink implementation. The store is
 * isolated behind the interface so the crypto impl is swappable and consumer tests can bind a fake.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiSecretModule {
  @Binds @Singleton abstract fun bindSecretStore(impl: KeystoreSecretStore): SecretStore
}

/**
 * Entry point so a non-injected `PreferenceFragment` (the AI settings screen) can reach the
 * singleton [SecretStore] via `EntryPointAccessors`, mirroring how the reply-notification settings
 * reach their controller.
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AiSecretEntryPoint {
  fun secretStore(): SecretStore
}
