/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

/** What a single comment-navigation key press should do (#49). */
enum class CommentNavAction {
  /** Move to the previous row (the comment above, leaving the current sub-thread). */
  SELECT_PREVIOUS,
  /** Move to the next row (the comment below, or out of a collapsed/childless node). */
  SELECT_NEXT,
  /** Move to the resolved sibling/child neighbour the current comment points at. */
  SELECT_NEIGHBOUR,
  /** Expand the current comment's collapsed children before descending into them. */
  EXPAND,
  /** Do nothing (no left-ward parent to move to). */
  NONE,
}

/**
 * Pure comment-thread navigation decision (#49), extracted from
 * SinglePageItemRecyclerViewAdapter.getNextPosition.
 *
 * Given the press direction, whether the current comment has a sibling/child neighbour in that
 * direction, and whether it is expanded, decide which move to make. The adapter still owns
 * resolving a neighbour id to its row index and performing the actual selection / expansion; this
 * only encodes the parent/child/sibling branching so it can be characterized without a
 * RecyclerView.
 */
object CommentNavigation {
  @JvmStatic
  fun nextAction(direction: Int, hasNeighbour: Boolean, isExpanded: Boolean): CommentNavAction =
      when (direction) {
        Navigable.DIRECTION_UP ->
            if (hasNeighbour) CommentNavAction.SELECT_NEIGHBOUR
            else CommentNavAction.SELECT_PREVIOUS
        Navigable.DIRECTION_DOWN ->
            if (hasNeighbour) CommentNavAction.SELECT_NEIGHBOUR else CommentNavAction.SELECT_NEXT
        Navigable.DIRECTION_LEFT ->
            if (hasNeighbour) CommentNavAction.SELECT_NEIGHBOUR else CommentNavAction.NONE
        Navigable.DIRECTION_RIGHT ->
            when {
              !hasNeighbour -> CommentNavAction.SELECT_NEXT
              isExpanded -> CommentNavAction.SELECT_NEIGHBOUR
              else -> CommentNavAction.EXPAND
            }
        else -> CommentNavAction.NONE
      }
}
