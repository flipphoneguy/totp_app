package com.flipphoneguy.totp;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads / writes the entry list as JSON. When a password is set, the file is
 * AES-GCM encrypted on disk; the active password is held in memory after
 * unlock and supplied by callers.
 */
public class EntryStore {
    private static final String FILE_PLAIN = "entries.json";
    private static final String FILE_ENC = "entries.enc";
    private static final String PREFS = "totp";
    private static final String KEY_HAS_PASSWORD = "has_password";
    private static final String KEY_DARK = "dark_mode";

    /** Holds the password while the app is unlocked. Cleared on lock/exit. */
    private static char[] sActivePassword;

    public static void setActivePassword(char[] pw) { sActivePassword = pw; }
    public static void clearActivePassword() {
        if (sActivePassword != null) {
            for (int i = 0; i < sActivePassword.length; i++) sActivePassword[i] = 0;
        }
        sActivePassword = null;
    }
    public static boolean hasActivePassword() { return sActivePassword != null; }

    public static boolean isPasswordEnabled(Context c) {
        return prefs(c).getBoolean(KEY_HAS_PASSWORD, false);
    }
    public static boolean isDarkMode(Context c) {
        return prefs(c).getBoolean(KEY_DARK, false);
    }
    public static void setDarkMode(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_DARK, v).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<TotpEntry> load(Context ctx) {
        try {
            String raw;
            if (isPasswordEnabled(ctx)) {
                File f = new File(ctx.getFilesDir(), FILE_ENC);
                if (!f.exists()) return new ArrayList<>();
                String wire = readAll(f);
                if (sActivePassword == null) return new ArrayList<>();
                raw = CryptoUtil.decrypt(wire, sActivePassword);
            } else {
                File f = new File(ctx.getFilesDir(), FILE_PLAIN);
                if (!f.exists()) return new ArrayList<>();
                raw = readAll(f);
            }
            return JsonCodec.parse(raw);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void save(Context ctx, List<TotpEntry> entries) throws Exception {
        String json = JsonCodec.serialize(entries);
        if (isPasswordEnabled(ctx)) {
            if (sActivePassword == null)
                throw new IllegalStateException("No active password");
            String wire = CryptoUtil.encrypt(json, sActivePassword);
            writeAll(new File(ctx.getFilesDir(), FILE_ENC), wire);
            // Make sure plaintext file is gone
            new File(ctx.getFilesDir(), FILE_PLAIN).delete();
        } else {
            writeAll(new File(ctx.getFilesDir(), FILE_PLAIN), json);
            new File(ctx.getFilesDir(), FILE_ENC).delete();
        }
    }

    /** Migrate storage between encrypted/plain when the password setting changes. */
    public static void enablePassword(Context ctx, char[] newPw) throws Exception {
        // Capture current entries with old setting
        List<TotpEntry> existing = load(ctx);
        prefs(ctx).edit().putBoolean(KEY_HAS_PASSWORD, true).apply();
        setActivePassword(newPw);
        save(ctx, existing);
    }

    public static void disablePassword(Context ctx) throws Exception {
        List<TotpEntry> existing = load(ctx);
        prefs(ctx).edit().putBoolean(KEY_HAS_PASSWORD, false).apply();
        clearActivePassword();
        save(ctx, existing);
    }

    public static String readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            return readAll(in);
        } finally {
            in.close();
        }
    }

    public static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return new String(out.toByteArray(), "UTF-8");
    }

    public static void writeAll(File f, String s) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(s.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }
}
