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
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for [diffNewStories] , the extracted "N new stories" / promoted diff. Items are sorted
 * by rank in the adapter, so each list here is already in display order; identity is
 * [Item.getLongId].
 */
class NewStoriesDiffTest {

  private fun item(id: Long): Item =
      mockk(relaxed = true) {
        every { getLongId() } returns id
        every { getId() } returns id.toString()
      }

  @Test
  fun `first load (empty previous) counts nothing as new`() {
    val result = diffNewStories(emptyList(), listOf(item(1), item(2)))

    assertTrue(result.added.isEmpty())
    assertTrue(result.promoted.isEmpty())
  }

  @Test
  fun `unchanged list counts nothing`() {
    val result = diffNewStories(listOf(item(1), item(2)), listOf(item(1), item(2)))

    assertTrue(result.added.isEmpty())
    assertTrue(result.promoted.isEmpty())
  }

  @Test
  fun `a new story is added`() {
    val newStory = item(3)
    val result = diffNewStories(listOf(item(1), item(2)), listOf(item(1), item(2), newStory))

    assertEquals(1, result.added.size)
    assertEquals(3L, result.added[0].getLongId())
    assertTrue(result.promoted.isEmpty())
  }

  @Test
  fun `several new stories are all counted`() {
    // Two fresh stories debut at the top; the count must not stop at the first of the run.
    val result =
        diffNewStories(listOf(item(1), item(2)), listOf(item(3), item(4), item(1), item(2)))

    assertEquals(2, result.added.size)
    assertEquals(setOf(3L, 4L), result.added.map { it.getLongId() }.toSet())
  }

  @Test
  fun `a promoted story records ranks climbed`() {
    // id 2 climbs from index 1 to index 0; id 1 slips down.
    val result = diffNewStories(listOf(item(1), item(2)), listOf(item(2), item(1)))

    assertTrue(result.added.isEmpty())
    assertEquals(mapOf("2" to 1), result.promoted)
  }
}
