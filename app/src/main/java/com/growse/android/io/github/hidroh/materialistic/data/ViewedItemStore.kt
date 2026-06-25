/*
 * Copyright (c) 2018 Ha Duy Trung
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

package com.growse.android.io.github.hidroh.materialistic.data

import androidx.annotation.WorkerThread
import com.growse.android.io.github.hidroh.materialistic.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Data store tracking which items have been viewed (read). */
@Singleton
class ViewedItemStore
@Inject
constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val cache: LocalCache,
) {

  private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

  /**
   * Returns whether an item has been viewed. A synchronous cache read (annotated [WorkerThread]);
   * the caller is responsible for keeping it off the main thread.
   */
  @WorkerThread
  fun isViewed(itemId: String?): Boolean =
      if (itemId.isNullOrEmpty()) false else cache.isViewed(itemId)

  /**
   * Marks an item as already being viewed (fire-and-forget, off the main thread).
   *
   * @param itemId item ID that has been viewed
   */
  fun view(itemId: String?) {
    if (itemId.isNullOrEmpty()) return
    scope.launch { cache.setViewed(itemId) }
  }
}
