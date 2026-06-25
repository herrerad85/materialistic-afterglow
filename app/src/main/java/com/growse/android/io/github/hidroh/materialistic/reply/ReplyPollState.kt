/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.reply

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The per-username seeded marker (ADR-0004 / E5-D5). The *existence* of a row is what marks a
 * username as seeded, never "the baseline has zero rows", since a user with no replies legitimately
 * has an empty baseline and would otherwise look perpetually unseeded and swallow their first real
 * reply. Written only after a successful poll; [lastPolledAt] doubles as a debug timestamp.
 */
@Entity(tableName = "reply_poll_state")
data class ReplyPollState(
    @PrimaryKey @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "last_polled_at") val lastPolledAt: Long,
)
