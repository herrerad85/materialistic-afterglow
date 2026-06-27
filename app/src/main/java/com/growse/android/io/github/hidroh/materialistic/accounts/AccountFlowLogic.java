/*
 * Copyright (c) 2015 Ha Duy Trung
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.growse.android.io.github.hidroh.materialistic.accounts;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.List;

import com.growse.android.io.github.hidroh.materialistic.AlertDialogBuilder;
import com.growse.android.io.github.hidroh.materialistic.LoginActivity;
import com.growse.android.io.github.hidroh.materialistic.R;
import com.growse.android.io.github.hidroh.materialistic.reply.ReplyNotificationScheduler;

/**
 * The account/login flow, extracted from AppUtils. Explicit-parameter static logic (no service
 * locator): the {@link ReplyNotificationScheduler} is passed in rather than self-sourced via
 * {@code EntryPointAccessors}. Hilt-managed callers go through the injected {@code AccountFlow} seam;
 * adapters and other plain call sites call these methods directly with explicit dependencies.
 */
public final class AccountFlowLogic {

    private AccountFlowLogic() {}

    /**
     * Displays UI to allow user to login
     * If no accounts exist in user's device, regardless of login status, prompt to login again
     * If 1 or more accounts in user's device, and already logged in, prompt to update password
     * If 1 or more accounts in user's device, and logged out, show account chooser
     * @param context activity context
     * @param alertDialogBuilder dialog builder
     */
    public static void showLogin(Context context, AlertDialogBuilder alertDialogBuilder,
                                 AccountSession session, ReplyNotificationScheduler scheduler) {
        if (session.savedAccounts().isEmpty()) { // no saved accounts -> login screen
            context.startActivity(new Intent(context, LoginActivity.class));
        } else if (session.getActiveUsername() != null) { // active but action failed -> re-login
            context.startActivity(new Intent(context, LoginActivity.class));
        } else { // logged out, saved accounts exist -> choose one
            showAccountChooser(context, alertDialogBuilder, session, scheduler);
        }
    }

    public static void showAccountChooser(final Context context, AlertDialogBuilder alertDialogBuilder,
                                          AccountSession session,
                                          final ReplyNotificationScheduler replyNotificationScheduler) {
        List<SavedAccount> accounts = session.savedAccounts();
        String activeUsername = session.getActiveUsername();
        final String[] items = new String[accounts.size()];
        int checked = -1;
        for (int i = 0; i < accounts.size(); i++) {
            String accountName = accounts.get(i).getUsername();
            items[i] = accountName;
            if (TextUtils.equals(accountName, activeUsername)) {
                checked = i;
            }
        }
        int initialSelection = checked;
        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
            private int selection = initialSelection;

            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        if (selection < 0) {
                            break;
                        }
                        session.setActive(items[selection]);
                        // E5-D3: new active account -> seed/reconcile reply polling now, not at the
                        // next periodic wakeup. reconcile() is idempotent and the single source of truth.
                        replyNotificationScheduler.reconcile();
                        Toast.makeText(context,
                                context.getString(R.string.welcome, items[selection]),
                                Toast.LENGTH_SHORT)
                                .show();
                        dialog.dismiss();
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        Intent intent = new Intent(context, LoginActivity.class);
                        intent.putExtra(LoginActivity.EXTRA_ADD_ACCOUNT, true);
                        context.startActivity(intent);
                        dialog.dismiss();
                        break;
                    case DialogInterface.BUTTON_NEUTRAL:
                        if (selection < 0) {
                            break;
                        }
                        session.removeAccount(items[selection]);
                        // E5-D3: removing the active account clears the session -> reconcile() cancels
                        // its periodic work; removing a non-active one is a no-op reconcile.
                        replyNotificationScheduler.reconcile();
                        dialog.dismiss();
                        break;
                    default:
                        selection = which;
                        break;
                }
            }
        };
        alertDialogBuilder
                .init(context)
                .setTitle(R.string.choose_account)
                .setSingleChoiceItems(items, checked, clickListener)
                .setPositiveButton(android.R.string.ok, clickListener)
                .setNegativeButton(R.string.add_account, clickListener)
                .setNeutralButton(R.string.remove_account, clickListener)
                .show();
    }
}
