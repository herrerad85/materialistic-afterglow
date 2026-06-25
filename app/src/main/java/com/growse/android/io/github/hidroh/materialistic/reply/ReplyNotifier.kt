/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

/**
 * A reply ready to be surfaced to the user (E5-D4). Plain data so the poller and tests never depend
 * on Android notification APIs. [parentTitleOrText] is best-effort context for the notification
 * body (G3 fills it from the parent payload).
 */
data class NotifiableReply(
    val kidId: String,
    val parentId: String,
    val author: String,
    val parentTitleOrText: String?,
)

/**
 * The notification port (E5-D7). The poller hands it the filtered, notifiable replies; the real
 * system-notification posting (grouped per-reply + summary, the Replies channel, tap-to-thread)
 * lives in [SystemReplyNotifier]. Kept an interface so unit tests assert on a fake without touching
 * NotificationManager.
 */
interface ReplyNotifier {
  fun notify(replies: List<NotifiableReply>)
}
