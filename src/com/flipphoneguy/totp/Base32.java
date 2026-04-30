package com.flipphoneguy.totp;

public final class Base32 {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {}

    public static byte[] decode(String input) {
        if (input == null) throw new IllegalArgumentException("null input");
        String s = input.replaceAll("[\\s-]", "").toUpperCase();
        // Strip padding
        int eq = s.indexOf('=');
        if (eq >= 0) s = s.substring(0, eq);
        if (s.isEmpty()) return new byte[0];

        int outLen = (s.length() * 5) / 8;
        byte[] out = new byte[outLen];
        int buffer = 0;
        int bitsLeft = 0;
        int idx = 0;

        for (int i = 0; i < s.length(); i++) {
            int v = ALPHABET.indexOf(s.charAt(i));
            if (v < 0) throw new IllegalArgumentException("Invalid Base32 char: " + s.charAt(i));
            buffer = (buffer << 5) | v;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out[idx++] = (byte) ((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out;
    }

    public static boolean isValid(String input) {
        if (input == null) return false;
        String s = input.replaceAll("[\\s-]", "").toUpperCase();
        int eq = s.indexOf('=');
        if (eq >= 0) s = s.substring(0, eq);
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (ALPHABET.indexOf(s.charAt(i)) < 0) return false;
        }
        return true;
    }
}
