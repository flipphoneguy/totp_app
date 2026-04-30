package com.flipphoneguy.totp;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * Tracks foreground state so the password lock can be cleared whenever the
 * user actually leaves the app (HOME, recents, switching apps), without
 * tripping when a sub-intent the app launched (camera, file picker,
 * installer) covers our window.
 *
 * Mechanism:
 *   - Started-activity counter (sStartedCount). Activity transitions inside
 *     our process keep the count >= 1. When the count goes to 0, every
 *     activity in our process is stopped, i.e. we're in the background.
 *   - Sub-intent counter (sSubIntents). Activities call
 *     {@link #beginSubIntent()} before launching another app's activity for
 *     result, and {@link #endSubIntent()} on result. While that count is
 *     non-zero, we don't treat a 0 started-count as the user leaving — they
 *     just used the camera, picker, etc.
 */
public class App extends Application {

    private static int sStartedCount = 0;
    private static int sSubIntents = 0;
    private static boolean sBackgrounded = false;

    public static void beginSubIntent() { sSubIntents++; }
    public static void endSubIntent() {
        if (sSubIntents > 0) sSubIntents--;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) {
                int prev = sStartedCount;
                sStartedCount++;
                if (prev == 0 && sBackgrounded) {
                    EntryStore.clearActivePassword();
                    sBackgrounded = false;
                }
            }
            @Override public void onActivityStopped(Activity a) {
                if (sStartedCount > 0) sStartedCount--;
                if (sStartedCount == 0 && sSubIntents == 0) {
                    sBackgrounded = true;
                }
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
