/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the offline-read policy seam (#22/#25) to its live-device implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OfflineModule {
  @Binds
  @Singleton
  abstract fun bindOfflineReadPolicy(impl: DefaultOfflineReadPolicy): OfflineReadPolicy
}
