/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import android.content.Context
import com.growse.android.io.github.hidroh.materialistic.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Hilt wiring for the AI summary feature (E7 G3). Mirrors the reply package's split-module shape:
 * an abstract @Binds module for the provider implementation plus an object @Provides module for the
 * dedicated network stack. The existing SecretStore binding lives in AiSecretModule (G2) and is not
 * touched here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiSummaryBindModule {

  @Binds
  @Singleton
  abstract fun bindLlmSummaryProvider(impl: SelectableSummaryProvider): LlmSummaryProvider
}

/**
 * Qualifies the AI-only OkHttpClient so it never collides with the HN-side network bindings.
 * Defined here (not in the shared DiQualifiers) because it is specific to this feature group.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AiHttpClient

/** Qualifies the Gemini-only OkHttpClient (separate auth: x-goog-api-key). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class GeminiHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AiNetworkModule {

  // Dedicated OkHttp/Retrofit for the Anthropic API. SEPARATE from the shared HN cache client
  // (NetworkModule): no shared cache, no connectivity/cache interceptors, and NO logging
  // interceptor. The request body is the user's thread text and the only added header is the BYO
  // key (read per-call from SecretStore by the auth interceptor), so logging it would leak both.
  @Provides
  @Singleton
  @AiHttpClient
  fun provideAiOkHttpClient(secretStore: SecretStore): OkHttpClient =
      OkHttpClient.Builder()
          .addInterceptor(AnthropicAuthInterceptor(secretStore))
          .callTimeout(60, TimeUnit.SECONDS)
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(60, TimeUnit.SECONDS)
          .build()

  @Provides
  @Singleton
  fun provideAnthropicService(@AiHttpClient client: OkHttpClient): AnthropicService =
      Retrofit.Builder()
          .baseUrl("https://api.anthropic.com/")
          .callFactory(client)
          .addConverterFactory(GsonConverterFactory.create())
          .build()
          .create(AnthropicService::class.java)

  // Dedicated Gemini client/service, separate from the Anthropic stack so each keeps its own auth
  // (Gemini: x-goog-api-key). Same no-cache, no-logging posture as the Anthropic client.
  @Provides
  @Singleton
  @GeminiHttpClient
  fun provideGeminiOkHttpClient(secretStore: SecretStore): OkHttpClient =
      OkHttpClient.Builder()
          .addInterceptor(GeminiAuthInterceptor(secretStore))
          .callTimeout(60, TimeUnit.SECONDS)
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(60, TimeUnit.SECONDS)
          .build()

  @Provides
  @Singleton
  fun provideGeminiService(@GeminiHttpClient client: OkHttpClient): GeminiService =
      Retrofit.Builder()
          .baseUrl("https://generativelanguage.googleapis.com/")
          .callFactory(client)
          .addConverterFactory(GsonConverterFactory.create())
          .build()
          .create(GeminiService::class.java)

  // Reads the user's selected provider from preferences per call, so a settings change takes effect
  // on the next summary (SelectableSummaryProvider routes on this).
  @Provides
  @Singleton
  fun provideAiProviderSelector(@ApplicationContext context: Context): AiProviderSelector =
      AiProviderSelector {
        Preferences.getAiProvider(context)
      }
}
