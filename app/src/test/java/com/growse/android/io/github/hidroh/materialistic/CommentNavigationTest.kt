/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM characterization of the comment-navigation decision (#49), extracted from
 * SinglePageItemRecyclerViewAdapter.getNextPosition. Covers every direction and neighbour /
 * expansion combination so parent / child / sibling moves keep producing the same rows. No Android
 * runtime is touched.
 */
class CommentNavigationTest {

  @Test
  fun up_neighbourSelectsSiblingElsePreviousRow() {
    assertEquals(
        CommentNavAction.SELECT_NEIGHBOUR,
        CommentNavigation.nextAction(
            Navigable.DIRECTION_UP,
            hasNeighbour = true,
            isExpanded = false,
        ),
    )
    assertEquals(
        CommentNavAction.SELECT_PREVIOUS,
        CommentNavigation.nextAction(
            Navigable.DIRECTION_UP,
            hasNeighbour = false,
            isExpanded = false,
        ),
    )
  }

  @Test
  fun down_neighbourSelectsSiblingElseNextRow() {
    assertEquals(
        CommentNavAction.SELECT_NEIGHBOUR,
        CommentNavigation.nextAction(
            Navigable.DIRECTION_DOWN,
            hasNeighbour = true,
            isExpanded = false,
        ),
    )
    assertEquals(
        CommentNavAction.SELECT_NEXT,
        CommentNavigation.nextAction(
            Navigable.DIRECTION_DOWN,
            hasNeighbour = false,
            isExpanded = false,
        ),
    )
  }

  @Test
  fun left_neighbourSelectsParentElseDoesNothing() {
    assertEquals(
        CommentNavAction.SELECT_NEIGHBOUR,
        CommentNavigation.nextAction(
            Navigable.DIRECTION_LEFT,
            hasNeighbour = true,
            isExpanded = false,
        ),
    )
    assertEquals(
        CommentNavAction.NONE,
        CommentNavigation.nextAction(
            Navigable.DIRECTION_LEFT,
            hasNeighbour = false,
            isExpanded = false,
        ),
    )
  }

  @Test
  fun right_noChildMovesToNextRow() {
    assertEquals(
        CommentNavAction.SELECT_NEXT,
        CommentNavigation.nextAction(
            Navigable.DIRECTION_RIGHT,
            hasNeighbour = false,
            isExpanded = false,
        ),
    )
  }

  @Test
  fun right_childSelectsWhenExpandedElseExpands() {
    assertEquals(
        CommentNavAction.SELECT_NEIGHBOUR,
        CommentNavigation.nextAction(
            Navigable.DIRECTION_RIGHT,
            hasNeighbour = true,
            isExpanded = true,
        ),
    )
    assertEquals(
        CommentNavAction.EXPAND,
        CommentNavigation.nextAction(
            Navigable.DIRECTION_RIGHT,
            hasNeighbour = true,
            isExpanded = false,
        ),
    )
  }

  @Test
  fun unknownDirectionDoesNothing() {
    assertEquals(
        CommentNavAction.NONE,
        CommentNavigation.nextAction(-1, hasNeighbour = true, isExpanded = true),
    )
  }
}
