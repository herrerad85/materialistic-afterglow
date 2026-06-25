/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the reply-notification seams (E5-D7): the poll source, the opt-in read, and the
 * notification port. The implementations are swappable; tests bind fakes directly without this
 * module. [ReplyNotifier] is bound to [SystemReplyNotifier] (real grouped system notifications,
 * G3).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReplyNotificationBindModule {

  @Binds
  @Singleton
  abstract fun bindReplyDataSource(impl: HackerNewsReplyDataSource): ReplyDataSource

  @Binds @Singleton abstract fun bindReplyNotifier(impl: SystemReplyNotifier): ReplyNotifier

  @Binds
  @Singleton
  abstract fun bindReplyNotificationToggle(
      impl: PreferencesReplyNotificationToggle
  ): ReplyNotificationToggle
}

/** Provides the process [WorkManager] for the scheduler facade. */
@Module
@InstallIn(SingletonComponent::class)
object ReplyWorkManagerModule {

  @Provides
  @Singleton
  fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
      WorkManager.getInstance(context)
}
