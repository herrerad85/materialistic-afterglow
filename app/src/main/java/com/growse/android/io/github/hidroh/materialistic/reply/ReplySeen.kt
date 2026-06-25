/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * The reply-detection baseline (ADR-0004): one row per already-observed direct child ([kidId]) of
 * one of the signed-in user's own items ([parentId]). Scoped by [username] so switching accounts
 * can't cross-contaminate or re-flood. A kid id is globally unique on Hacker News and has exactly
 * one parent, so the primary key is (username, kid_id); [parentId] is carried only so the baseline
 * can be pruned by the recent-item window.
 */
@Entity(tableName = "reply_seen", primaryKeys = ["username", "kid_id"])
data class ReplySeen(
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "parent_id") val parentId: String,
    @ColumnInfo(name = "kid_id") val kidId: String,
)
