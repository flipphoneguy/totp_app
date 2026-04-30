package com.flipphoneguy.totp;

import android.app.Activity;

public final class ThemeUtil {
    private ThemeUtil() {}

    public static void applyTheme(Activity a) {
        if (EntryStore.isDarkMode(a)) {
            a.setTheme(R.style.AppTheme_Dark);
        } else {
            a.setTheme(R.style.AppTheme_Light);
        }
    }
}
