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
package com.growse.android.io.github.hidroh.materialistic.widget

import com.growse.android.io.github.hidroh.materialistic.data.Item

/** What changed between the previously rendered story list and a freshly loaded one. */
data class NewStoriesResult(
    /** Stories present in the new list but not the previous one (the "N new stories" count). */
    val added: List<Item>,
    /** id -> ranks climbed, for stories that moved up the list (promoted). */
    val promoted: Map<String, Int>,
)

/**
 * Pure diff of [oldItems] (previously rendered) vs [newItems] (freshly loaded), extracted from
 * `StoryRecyclerViewAdapter` so the "N new stories" / promoted logic is testable without a
 * RecyclerView. Both lists are in display order (the adapter sorts by rank). Identity is
 * [Item.getLongId]; the promoted key is [Item.getId].
 *
 * A story is *added* if it is absent from the previous list, and *promoted* by however many places
 * it climbed (a story displaced downward by an insert is not promoted). An empty previous list is
 * the first load for the section, so nothing counts as new (no first-load false positives).
 */
fun diffNewStories(oldItems: List<Item>, newItems: List<Item>): NewStoriesResult {
  if (oldItems.isEmpty()) {
    return NewStoriesResult(emptyList(), emptyMap())
  }
  val oldIndexById = HashMap<Long, Int>(oldItems.size)
  oldItems.forEachIndexed { index, item -> oldIndexById[item.getLongId()] = index }
  val added = ArrayList<Item>()
  val promoted = HashMap<String, Int>()
  newItems.forEachIndexed { newIndex, item ->
    val oldIndex = oldIndexById[item.getLongId()]
    if (oldIndex == null) {
      added.add(item)
    } else if (oldIndex > newIndex) {
      promoted[item.getId()] = oldIndex - newIndex
    }
  }
  return NewStoriesResult(added, promoted)
}
