package com.flipphoneguy.totp;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * Tracks foreground state so the password lock can be cleared whenever the
 * user actually leaves the app (HOME, recents, switching apps), without
 * tripping when sub-activities run (camera, file picker, installer).
 *
 * Mechanism: each activity calls {@link #onUserLeaveHint()} from its own
 * onUserLeaveHint, which Android only fires on real user-initiated departure.
 * We flip a flag, and on the next activity start (anywhere in our process)
 * we clear the cached password. {@link MainActivity#onResume} then sees no
 * active password and re-prompts.
 */
public class App extends Application {

    private static volatile boolean sUserLeft = false;
    private static int sStartedCount = 0;

    public static void onUserLeaveHint() { sUserLeft = true; }

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) {
                sStartedCount++;
                if (sUserLeft) {
                    EntryStore.clearActivePassword();
                    sUserLeft = false;
                }
            }
            @Override public void onActivityStopped(Activity a) {
                if (sStartedCount > 0) sStartedCount--;
            }

            // Unused
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }
}
