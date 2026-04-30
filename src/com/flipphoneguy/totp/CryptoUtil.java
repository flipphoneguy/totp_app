package com.flipphoneguy.totp;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM with PBKDF2-HMAC-SHA256 derived key.
 *
 * Wire format ("TOTP1\n" + base64 of: salt[16] || iv[12] || ciphertext+tag).
 */
public final class CryptoUtil {

    public static final String MAGIC = "TOTP1\n";
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int KEY_LEN = 32; // 256-bit
    private static final int TAG_LEN = 128;
    private static final int ITERATIONS = 100_000;

    private CryptoUtil() {}

    private static SecretKey deriveKey(char[] password, byte[] salt) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LEN * 8);
        byte[] keyBytes = f.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static String encrypt(String plaintext, char[] password) throws Exception {
        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[IV_LEN];
        SecureRandom rnd = new SecureRandom();
        rnd.nextBytes(salt);
        rnd.nextBytes(iv);

        SecretKey key = deriveKey(password, salt);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN, iv));
        byte[] ct = c.doFinal(plaintext.getBytes("UTF-8"));

        byte[] all = new byte[salt.length + iv.length + ct.length];
        System.arraycopy(salt, 0, all, 0, salt.length);
        System.arraycopy(iv, 0, all, salt.length, iv.length);
        System.arraycopy(ct, 0, all, salt.length + iv.length, ct.length);

        return MAGIC + android.util.Base64.encodeToString(all, android.util.Base64.NO_WRAP);
    }

    public static String decrypt(String wire, char[] password) throws Exception {
        if (wire == null || !wire.startsWith(MAGIC))
            throw new IllegalArgumentException("Not an encrypted backup");
        String b64 = wire.substring(MAGIC.length()).trim();
        byte[] all = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
        if (all.length < SALT_LEN + IV_LEN + 16)
            throw new IllegalArgumentException("Backup truncated");

        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[IV_LEN];
        System.arraycopy(all, 0, salt, 0, SALT_LEN);
        System.arraycopy(all, SALT_LEN, iv, 0, IV_LEN);
        byte[] ct = new byte[all.length - SALT_LEN - IV_LEN];
        System.arraycopy(all, SALT_LEN + IV_LEN, ct, 0, ct.length);

        SecretKey key = deriveKey(password, salt);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN, iv));
        byte[] pt = c.doFinal(ct);
        return new String(pt, "UTF-8");
    }

    public static boolean isEncrypted(String text) {
        return text != null && text.startsWith(MAGIC);
    }
}
