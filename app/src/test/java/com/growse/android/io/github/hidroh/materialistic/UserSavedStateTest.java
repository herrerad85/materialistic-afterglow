/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic;

import static org.junit.Assert.assertFalse;

import java.lang.reflect.Method;
import org.junit.Test;

/**
 * Regression guard for the TransactionTooLargeException fix. A prolific profile (pg, dang) used to
 * crash because the whole loaded User, including its entire submissions array, was parceled into
 * saved instance state (the captured state:user [size=2127308], about 2.13 MB). The fix removed
 * UserActivity's onSaveInstanceState override entirely: mUser is re-fetched on recreate via the
 * existing load() path, and the activity no longer writes the User into the bundle.
 *
 * UserActivity is a Hilt entry point with no Hilt test harness in this module, and its inherited
 * onSaveInstanceState is protected in androidx.activity.ComponentActivity (a different package), so it
 * cannot be launched or invoked to assert behavior directly. This guards the mechanism of the bug:
 * the heavy-state override must stay gone. Re-adding an onSaveInstanceState override to stash the User
 * (the only way the activity could reintroduce the oversized parcel) trips this test.
 */
public class UserSavedStateTest {

  @Test
  public void userActivity_declaresNoOwnSaveInstanceState() {
    boolean declaresOverride = false;
    for (Method method : UserActivity.class.getDeclaredMethods()) {
      if (method.getName().equals("onSaveInstanceState")) {
        declaresOverride = true;
        break;
      }
    }
    assertFalse(
        "UserActivity must not override onSaveInstanceState: that path persisted the whole User and"
            + " caused the TransactionTooLargeException for prolific profiles",
        declaresOverride);
  }
}
