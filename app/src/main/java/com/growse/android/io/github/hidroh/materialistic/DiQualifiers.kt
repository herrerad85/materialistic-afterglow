/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.growse.android.io.github.hidroh.materialistic

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Hilt qualifiers replacing the old Dagger 1 @Named("...") strings.
// Annotated @Retention(BINARY) so they are usable from Java @Inject sites.

/** The Hacker News-backed [com.growse.android.io.github.hidroh.materialistic.data.ItemManager]. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class HackerNews

/**
 * The Algolia search-backed [com.growse.android.io.github.hidroh.materialistic.data.ItemManager].
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class Algolia

/**
 * The Algolia "popular"/trending-backed
 * [com.growse.android.io.github.hidroh.materialistic.data.ItemManager].
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class Popular

/** The IO [kotlinx.coroutines.CoroutineDispatcher] (consumed by the Gate-2 story-list vertical). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

/**
 * Coroutine dispatcher provider. Consumed by the Gate-2 story-list vertical
 * ([com.growse.android.io.github.hidroh.materialistic.StoryListViewModel]). Unscoped:
 * Dispatchers.IO is already a process-global singleton. @DefaultDispatcher omitted until a
 * CPU-bound site needs it.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
  @Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
