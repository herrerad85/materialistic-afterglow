/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import android.content.Context
import com.growse.android.io.github.hidroh.materialistic.data.MaterialisticDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Exposes the reply-tracking DAOs off the existing [MaterialisticDatabase] singleton (E5-D5). The
 * database is still its own manual singleton, so this provides the DAOs from `getInstance` rather
 * than rewriting database provisioning. The reply poller (G2) injects these.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReplyDataModule {

  @Provides
  @Singleton
  fun provideReplySeenDao(@ApplicationContext context: Context): ReplySeenDao =
      MaterialisticDatabase.getInstance(context).replySeenDao

  @Provides
  @Singleton
  fun provideReplyPollStateDao(@ApplicationContext context: Context): ReplyPollStateDao =
      MaterialisticDatabase.getInstance(context).replyPollStateDao
}
