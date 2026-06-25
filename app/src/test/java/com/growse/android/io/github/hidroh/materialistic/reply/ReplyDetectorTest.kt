/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for [ReplyDetector], the correctness core of reply detection (ADR-0004). */
class ReplyDetectorTest {

  // --- detect(): silent first-run seed -------------------------------------------------------

  @Test
  fun `unseeded first run records every current kid but notifies nothing`() {
    val parents = listOf(PolledParent("p1", listOf("k1", "k2")), PolledParent("p2", listOf("k3")))

    val result = ReplyDetector.detect(parents, seenKidIds = emptySet(), isSeeded = false)

    // All three are recorded as the baseline...
    assertEquals(setOf("k1", "k2", "k3"), result.newKids.map { it.kidId }.toSet())
    // ...but the silent seed offers nothing for notification.
    assertTrue(result.notifyCandidates.isEmpty())
  }

  // --- detect(): post-seed diffing -----------------------------------------------------------

  @Test
  fun `first real reply after an empty seed is both recorded and notifiable`() {
    // Seeded earlier with k1,k2; a new kid k3 has since appeared on p1.
    val parents = listOf(PolledParent("p1", listOf("k1", "k2", "k3")))

    val result = ReplyDetector.detect(parents, seenKidIds = setOf("k1", "k2"), isSeeded = true)

    assertEquals(listOf(NewKid("p1", "k3")), result.newKids)
    assertEquals(listOf(NewKid("p1", "k3")), result.notifyCandidates)
  }

  @Test
  fun `nothing new when every current kid is already seen`() {
    val parents = listOf(PolledParent("p1", listOf("k1", "k2")))

    val result = ReplyDetector.detect(parents, seenKidIds = setOf("k1", "k2"), isSeeded = true)

    assertTrue(result.newKids.isEmpty())
    assertTrue(result.notifyCandidates.isEmpty())
  }

  @Test
  fun `new kids are detected across multiple parents and carry their parent`() {
    val parents =
        listOf(PolledParent("p1", listOf("k1", "kNew1")), PolledParent("p2", listOf("kNew2")))

    val result = ReplyDetector.detect(parents, seenKidIds = setOf("k1"), isSeeded = true)

    assertEquals(
        setOf(NewKid("p1", "kNew1"), NewKid("p2", "kNew2")),
        result.notifyCandidates.toSet(),
    )
  }

  @Test
  fun `a kid duplicated within a parent payload is recorded once`() {
    val parents = listOf(PolledParent("p1", listOf("kNew", "kNew")))

    val result = ReplyDetector.detect(parents, seenKidIds = emptySet(), isSeeded = true)

    assertEquals(listOf(NewKid("p1", "kNew")), result.newKids)
  }

  // --- notifiable(): child-payload filtering -------------------------------------------------

  @Test
  fun `notifiable keeps a genuine reply by another author`() {
    val candidates = listOf(child("k1", author = "someone_else"))

    assertEquals(candidates, ReplyDetector.notifiable(candidates, username = "me"))
  }

  @Test
  fun `notifiable drops self-replies, dead, deleted and authorless children`() {
    val candidates =
        listOf(
            child("self", author = "me"),
            child("dead", author = "x", dead = true),
            child("deleted", author = "x", deleted = true),
            child("blank", author = ""),
            child("nullauthor", author = null),
            child("keep", author = "you"),
        )

    val kept = ReplyDetector.notifiable(candidates, username = "me").map { it.kidId }

    assertEquals(listOf("keep"), kept)
  }

  @Test
  fun `notifiable self-reply check is case-sensitive (HN usernames are)`() {
    // "Me" is a different HN account from "me", so a reply by "Me" is a real reply.
    val candidates = listOf(child("k1", author = "Me"))

    assertEquals(candidates, ReplyDetector.notifiable(candidates, username = "me"))
  }

  private fun child(
      kidId: String,
      author: String?,
      dead: Boolean = false,
      deleted: Boolean = false,
  ) =
      CandidateChild(
          kidId = kidId,
          parentId = "p1",
          author = author,
          dead = dead,
          deleted = deleted,
      )
}
