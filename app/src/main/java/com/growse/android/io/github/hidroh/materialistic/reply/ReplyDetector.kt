/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

/**
 * One of the user's own recent submitted items and the ids of its current direct children.
 *
 * [titleOrText] is best-effort context for the notification body (the story title; comment parents
 * have no title and stay null). Defaulted so existing call sites and tests keep compiling.
 */
data class PolledParent(
    val parentId: String,
    val kidIds: List<String>,
    val titleOrText: String? = null,
)

/** A newly observed direct child of a polled parent, not yet in the seen baseline. */
data class NewKid(val parentId: String, val kidId: String)

/** A fetched candidate child, carrying just enough to decide whether it is a notifiable reply. */
data class CandidateChild(
    val kidId: String,
    val parentId: String,
    val author: String?,
    val dead: Boolean,
    val deleted: Boolean,
)

/**
 * Outcome of one diff pass.
 *
 * @property newKids every newly observed kid id, persisted into the baseline regardless of seeding.
 * @property notifyCandidates the subset eligible to become notifications, empty on the silent
 *   first-run seed.
 */
data class DetectionResult(val newKids: List<NewKid>, val notifyCandidates: List<NewKid>)

/**
 * Pure reply detection (ADR-0004). No Android, no Room, no network: the worker (G2) supplies the
 * polled parents and the persisted baseline, then acts on the result. "A reply to you" is a
 * *direct* child of one of your own recent items, never a grandchild, so the detector only ever
 * looks at each parent's own kids.
 */
object ReplyDetector {

  /**
   * Diff each polled parent's current direct kids against the seen baseline.
   *
   * On the first poll for a user ([isSeeded] == false) every current kid is recorded as newly seen
   * but nothing is offered for notification (the silent seed, E5-D2): enabling the feature must not
   * dump a notification for every reply that already existed. After seeding, every kid not in
   * [seenKidIds] is both recorded and offered as a notify candidate.
   */
  fun detect(
      parents: List<PolledParent>,
      seenKidIds: Set<String>,
      isSeeded: Boolean,
  ): DetectionResult {
    val newKids = parents.flatMap { parent ->
      parent.kidIds
          .asSequence()
          .filter { it !in seenKidIds }
          .distinct()
          .map { NewKid(parent.parentId, it) }
          .toList()
    }
    return DetectionResult(
        newKids = newKids,
        notifyCandidates = if (isSeeded) newKids else emptyList(),
    )
  }

  /**
   * Of the fetched candidate children, keep only genuine replies worth notifying: not authored by
   * the user themselves, with a real author, and neither dead nor deleted (E5-D2). Hacker News
   * usernames are case-sensitive, so self-reply detection is an exact match.
   */
  fun notifiable(candidates: List<CandidateChild>, username: String): List<CandidateChild> =
      candidates.filter { child ->
        !child.dead && !child.deleted && !child.author.isNullOrBlank() && child.author != username
      }
}
