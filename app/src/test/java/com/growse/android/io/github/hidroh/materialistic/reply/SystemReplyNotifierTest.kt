/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric characterization of the real [SystemReplyNotifier] (E5-D8 G3). With
 * POST_NOTIFICATIONS granted, two notifiable replies post three notifications (two per-reply + one
 * group summary), each per-reply id is the stable kid-derived id, every notification carries the
 * "replies" channel and the shared group key, and the summary is flagged as the group summary.
 */
@RunWith(RobolectricTestRunner::class)
class SystemReplyNotifierTest {

  private lateinit var context: Context
  private lateinit var notificationManager: NotificationManager

  @Before
  fun setUp() {
    val application: Application = ApplicationProvider.getApplicationContext()
    context = application
    // Grant the post permission so the belt-and-suspenders guard does not short-circuit.
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  }

  @Test
  fun notify_postsPerReplyPlusSummary_withStableIdsChannelAndGroup() {
    val notifier = SystemReplyNotifier(context)

    notifier.notify(
        listOf(
            NotifiableReply(
                kidId = "101",
                parentId = "9",
                author = "alice",
                parentTitleOrText = "Show HN: a thing",
            ),
            NotifiableReply(
                kidId = "202",
                parentId = "8",
                author = "bob",
                parentTitleOrText = null,
            ),
        )
    )

    val shadow = shadowOf(notificationManager)
    // 2 per-reply + 1 summary.
    assertEquals(3, shadow.size())

    val posted = shadow.activeNotifications
    val ids = posted.map { it.id }.toSet()
    // The per-reply ids are stable, derived directly from the (numeric) kid id.
    assertTrue(ids.contains(101))
    assertTrue(ids.contains(202))

    posted.forEach { sbn ->
      assertEquals("replies", sbn.notification.channelId)
      assertEquals(GROUP_REPLIES, sbn.notification.group)
    }

    // Exactly one summary (group-summary flag set), and its id is not a per-reply id.
    val summaries = posted.filter {
      it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0
    }
    assertEquals(1, summaries.size)
    assertTrue(summaries.single().id !in setOf(101, 202))
  }

  @Test
  fun notify_withoutPermission_postsNothing() {
    shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

    SystemReplyNotifier(context)
        .notify(listOf(NotifiableReply("101", "9", author = "alice", parentTitleOrText = null)))

    assertEquals(0, shadowOf(notificationManager).size())
  }

  @Test
  @Config(sdk = [30])
  fun notify_preApi33_postsWithoutRuntimePermission() {
    // On API < 33 POST_NOTIFICATIONS is not a runtime permission, so checkSelfPermission reports
    // DENIED. The notifier must fall back to the notifications-enabled state and still post, or the
    // whole pre-33 base would silently get no reply notifications.
    shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

    SystemReplyNotifier(context)
        .notify(
            listOf(
                NotifiableReply("101", "9", author = "alice", parentTitleOrText = null),
                NotifiableReply("202", "8", author = "bob", parentTitleOrText = null),
            )
        )

    // 2 per-reply + 1 summary, despite POST_NOTIFICATIONS being "denied" at this API level.
    assertEquals(3, shadowOf(notificationManager).size())
  }

  @Test
  fun notify_emptyList_postsNothing() {
    SystemReplyNotifier(context).notify(emptyList())

    assertEquals(0, shadowOf(notificationManager).size())
  }

  private companion object {
    const val GROUP_REPLIES = "com.growse.android.io.github.hidroh.materialistic.REPLIES"
  }
}
