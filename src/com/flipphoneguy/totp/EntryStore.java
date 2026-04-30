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
 * Always-encrypted entry store.
 *
 *   No password set: entries are encrypted with an Android Keystore key
 *   (AES-GCM-256, key never leaves the secure enclave on devices that have
 *   one). At rest is "TOTPK\n…".
 *
 *   Password set: encrypted with PBKDF2-derived key from the user's
 *   password. At rest is "TOTP1\n…".
 *
 * Either way the on-disk file is the same path: entries.enc.
 */
public class EntryStore {
    private static final String FILE = "entries.enc";
    private static final String LEGACY_PLAIN = "entries.json";

    private static final String PREFS = "totp";
    private static final String KEY_HAS_PASSWORD = "has_password";

    /** Active password held in memory while app is unlocked. */
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

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<TotpEntry> load(Context ctx) {
        try {
            migrateLegacyIfNeeded(ctx);
            File f = new File(ctx.getFilesDir(), FILE);
            if (!f.exists()) return new ArrayList<>();

            String wire = readAll(f);
            String json;
            if (isPasswordEnabled(ctx)) {
                if (sActivePassword == null) return new ArrayList<>();
                json = CryptoUtil.decryptWithPassword(wire, sActivePassword);
            } else {
                json = CryptoUtil.decryptWithKeystore(wire);
            }
            return JsonCodec.parse(json);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void save(Context ctx, List<TotpEntry> entries) throws Exception {
        String json = JsonCodec.serialize(entries);
        String wire;
        if (isPasswordEnabled(ctx)) {
            if (sActivePassword == null)
                throw new IllegalStateException("No active password");
            wire = CryptoUtil.encryptWithPassword(json, sActivePassword);
        } else {
            wire = CryptoUtil.encryptWithKeystore(json);
        }
        writeAll(new File(ctx.getFilesDir(), FILE), wire);
        // Make sure no legacy plaintext lingers
        new File(ctx.getFilesDir(), LEGACY_PLAIN).delete();
    }

    /** Migrate a pre-1.0.1 plaintext entries.json into encrypted entries.enc. */
    private static void migrateLegacyIfNeeded(Context ctx) {
        File legacy = new File(ctx.getFilesDir(), LEGACY_PLAIN);
        File current = new File(ctx.getFilesDir(), FILE);
        if (!legacy.exists() || current.exists()) return;
        try {
            String json = readAll(legacy);
            String wire = isPasswordEnabled(ctx) && sActivePassword != null
                ? CryptoUtil.encryptWithPassword(json, sActivePassword)
                : CryptoUtil.encryptWithKeystore(json);
            writeAll(current, wire);
            legacy.delete();
        } catch (Exception ignored) {}
    }

    public static void enablePassword(Context ctx, char[] newPw) throws Exception {
        // Read existing entries with the keystore key directly, then re-encrypt
        // under the password. We don't go through load() because load() picks
        // its decryption mode from the (about-to-change) preference, and a
        // silent failure would wipe the user's accounts.
        File f = new File(ctx.getFilesDir(), FILE);
        List<TotpEntry> existing;
        if (f.exists()) {
            String json = CryptoUtil.decryptWithKeystore(readAll(f));
            existing = JsonCodec.parse(json);
        } else {
            existing = new ArrayList<>();
        }
        prefs(ctx).edit().putBoolean(KEY_HAS_PASSWORD, true).apply();
        setActivePassword(newPw);
        save(ctx, existing);
    }

    public static void disablePassword(Context ctx) throws Exception {
        // Read with the in-memory password directly, then re-encrypt under the
        // keystore key. Going through load() would silently return an empty
        // list if the active password is missing, wiping accounts.
        File f = new File(ctx.getFilesDir(), FILE);
        List<TotpEntry> existing;
        if (f.exists()) {
            if (sActivePassword == null)
                throw new IllegalStateException("App is locked — unlock before disabling password.");
            String json = CryptoUtil.decryptWithPassword(readAll(f), sActivePassword);
            existing = JsonCodec.parse(json);
        } else {
            existing = new ArrayList<>();
        }
        prefs(ctx).edit().putBoolean(KEY_HAS_PASSWORD, false).apply();
        clearActivePassword();
        save(ctx, existing);
    }

    public static String readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try { return readAll(in); } finally { in.close(); }
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
        try { out.write(s.getBytes("UTF-8")); } finally { out.close(); }
    }
}
