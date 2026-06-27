/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.accounts

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountModule {
  @Binds abstract fun bindAccountSession(impl: AndroidAccountSession): AccountSession
}

/** Activity-scoped because the account flow needs the activity-scoped [AlertDialogBuilder]. */
@Module
@InstallIn(ActivityComponent::class)
abstract class AccountFlowModule {
  @Binds abstract fun bindAccountFlow(impl: DefaultAccountFlow): AccountFlow
}
