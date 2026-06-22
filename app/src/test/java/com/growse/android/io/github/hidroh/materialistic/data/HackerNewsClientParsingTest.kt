/*
 * Copyright (c) 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.data

import com.google.gson.Gson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Pure-JVM characterization of how the HN Firebase API item JSON deserializes into
 * [HackerNewsItem]. Retrofit wires a default [Gson] (GsonConverterFactory.create()), so parsing the
 * captured fixtures with a plain Gson reproduces production decoding.
 *
 * Assertions target the pure-JVM getters only (no android.text.TextUtils path).
 */
class HackerNewsClientParsingTest {

  private fun load(name: String): String =
      checkNotNull(javaClass.classLoader!!.getResourceAsStream(name)) { "missing fixture $name" }
          .bufferedReader()
          .use { it.readText() }

  @Test
  fun parsesStoryItemFields() {
    val item = Gson().fromJson(load("hn_item_story.json"), HackerNewsItem::class.java)

    assertEquals(8863L, item.longId)
    assertEquals("8863", item.id)
    assertEquals("My YC app: Dropbox - Throw away your USB drive", item.title)
    assertEquals("http://www.getdropbox.com/u/2/screencast.html", item.rawUrl)
    assertEquals("dhouston", item.by)
    assertEquals(1175714200L, item.time)
    assertEquals(111, item.score)
    assertEquals(71, item.descendants)
    assertEquals("story", item.rawType)
    assertArrayEquals(longArrayOf(8952L, 9224L, 8917L, 8884L), item.kids)
    // descendants (71) wins over kids.length (4) for the displayed comment count.
    assertEquals(71, item.kidCount)
    assertFalse(item.isDeleted)
    assertFalse(item.isDead)
  }

  @Test
  fun parsesCommentItemFields() {
    val item = Gson().fromJson(load("hn_item_comment.json"), HackerNewsItem::class.java)

    assertEquals(2921983L, item.longId)
    assertEquals("norvig", item.by)
    assertEquals(1314211127L, item.time)
    assertEquals("comment", item.rawType)
    assertEquals("2921506", item.parent)
    assertEquals(
        "Aw shucks, guys ... you make me blush with your compliments.",
        item.text,
    )
    // The field initializer `descendants = -1` does NOT run: Gson instantiates via Unsafe
    // (no constructor/initializers), so an absent JSON field leaves the JVM default 0.
    assertEquals(0, item.descendants)
    // descendants (0) is not > 0, so the comment count falls back to kids.length.
    assertEquals(2, item.kidCount)
    assertArrayEquals(longArrayOf(2922097L, 2922429L), item.kids)
  }
}
