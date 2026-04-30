package com.flipphoneguy.totp;

import java.nio.ByteBuffer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TotpGenerator {
    private static final int PERIOD = 30;
    private static final int DIGITS = 6;

    private TotpGenerator() {}

    public static String code(String base32Seed) {
        try {
            byte[] key = Base32.decode(base32Seed);
            long counter = System.currentTimeMillis() / 1000L / PERIOD;
            byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(msg);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset]     & 0x7F) << 24)
                       | ((hash[offset + 1] & 0xFF) << 16)
                       | ((hash[offset + 2] & 0xFF) <<  8)
                       |  (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, DIGITS);
            String s = Integer.toString(otp);
            while (s.length() < DIGITS) s = "0" + s;
            return s;
        } catch (Exception e) {
            return "------";
        }
    }

    /** Seconds remaining in the current 30s window. */
    public static int secondsRemaining() {
        long now = System.currentTimeMillis() / 1000L;
        return PERIOD - (int) (now % PERIOD);
    }

    public static int period() { return PERIOD; }
}
