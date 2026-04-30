package com.flipphoneguy.totp;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Two encryption modes, both AES-GCM-256:
 *
 *   "TOTP1\n…"  password-derived key (PBKDF2-HMAC-SHA256, 100k iters).
 *               Format: salt[16] || iv[12] || ct+tag.
 *
 *   "TOTPK\n…"  Android Keystore-backed key. Used as the at-rest default
 *               so accounts are always encrypted, even without a user password.
 *               Format: ivLen[1] || iv || ct+tag.
 */
public final class CryptoUtil {

    public static final String MAGIC_PW = "TOTP1\n";
    public static final String MAGIC_KS = "TOTPK\n";
    public static final String KS_ALIAS = "totp_default_v1";

    private static final int SALT_LEN = 16;
    private static final int PW_IV_LEN = 12;
    private static final int KEY_LEN = 32;
    private static final int TAG_LEN = 128;
    private static final int ITERATIONS = 100_000;

    private CryptoUtil() {}

    // ── Password-derived ───────────────────────────────────────────────────
    private static SecretKey derivePassword(char[] password, byte[] salt) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LEN * 8);
        return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
    }

    public static String encryptWithPassword(String plaintext, char[] password) throws Exception {
        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[PW_IV_LEN];
        SecureRandom rnd = new SecureRandom();
        rnd.nextBytes(salt);
        rnd.nextBytes(iv);

        SecretKey key = derivePassword(password, salt);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN, iv));
        byte[] ct = c.doFinal(plaintext.getBytes("UTF-8"));

        byte[] all = new byte[salt.length + iv.length + ct.length];
        System.arraycopy(salt, 0, all, 0, salt.length);
        System.arraycopy(iv, 0, all, salt.length, iv.length);
        System.arraycopy(ct, 0, all, salt.length + iv.length, ct.length);
        return MAGIC_PW + android.util.Base64.encodeToString(all, android.util.Base64.NO_WRAP);
    }

    public static String decryptWithPassword(String wire, char[] password) throws Exception {
        if (wire == null || !wire.startsWith(MAGIC_PW))
            throw new IllegalArgumentException("Not a password-encrypted blob");
        byte[] all = android.util.Base64.decode(
            wire.substring(MAGIC_PW.length()).trim(), android.util.Base64.DEFAULT);
        if (all.length < SALT_LEN + PW_IV_LEN + 16)
            throw new IllegalArgumentException("Backup truncated");

        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[PW_IV_LEN];
        System.arraycopy(all, 0, salt, 0, SALT_LEN);
        System.arraycopy(all, SALT_LEN, iv, 0, PW_IV_LEN);
        byte[] ct = new byte[all.length - SALT_LEN - PW_IV_LEN];
        System.arraycopy(all, SALT_LEN + PW_IV_LEN, ct, 0, ct.length);

        SecretKey key = derivePassword(password, salt);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN, iv));
        return new String(c.doFinal(ct), "UTF-8");
    }

    // ── Keystore-backed (default at-rest) ──────────────────────────────────
    private static SecretKey ensureKeystoreKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KS_ALIAS)) {
            return (SecretKey) ks.getKey(KS_ALIAS, null);
        }
        KeyGenerator kg = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KS_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build());
        return kg.generateKey();
    }

    public static String encryptWithKeystore(String plaintext) throws Exception {
        SecretKey key = ensureKeystoreKey();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(plaintext.getBytes("UTF-8"));

        byte[] all = new byte[1 + iv.length + ct.length];
        all[0] = (byte) iv.length;
        System.arraycopy(iv, 0, all, 1, iv.length);
        System.arraycopy(ct, 0, all, 1 + iv.length, ct.length);
        return MAGIC_KS + android.util.Base64.encodeToString(all, android.util.Base64.NO_WRAP);
    }

    public static String decryptWithKeystore(String wire) throws Exception {
        if (wire == null || !wire.startsWith(MAGIC_KS))
            throw new IllegalArgumentException("Not a keystore-encrypted blob");
        byte[] all = android.util.Base64.decode(
            wire.substring(MAGIC_KS.length()).trim(), android.util.Base64.DEFAULT);
        int ivLen = all[0] & 0xFF;
        if (ivLen <= 0 || ivLen > 16 || all.length < 1 + ivLen + 16)
            throw new IllegalArgumentException("Blob truncated");

        byte[] iv = new byte[ivLen];
        System.arraycopy(all, 1, iv, 0, ivLen);
        byte[] ct = new byte[all.length - 1 - ivLen];
        System.arraycopy(all, 1 + ivLen, ct, 0, ct.length);

        SecretKey key = ensureKeystoreKey();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN, iv));
        return new String(c.doFinal(ct), "UTF-8");
    }

    // ── Convenience for backups ────────────────────────────────────────────
    public static boolean isPasswordEncrypted(String text) {
        return text != null && text.startsWith(MAGIC_PW);
    }
}
