package com.flipphoneguy.totp;

import android.net.Uri;

/** Parses otpauth://totp/Issuer:account?secret=BASE32&issuer=Issuer URIs. */
public final class OtpAuthUri {
    private OtpAuthUri() {}

    public static TotpEntry parse(String s) {
        if (s == null) return null;
        try {
            Uri u = Uri.parse(s.trim());
            if (!"otpauth".equalsIgnoreCase(u.getScheme())) return null;
            String secret = u.getQueryParameter("secret");
            if (secret == null || secret.isEmpty()) return null;
            if (!Base32.isValid(secret)) return null;

            String label = u.getPath();
            if (label != null && label.startsWith("/")) label = label.substring(1);
            try { label = Uri.decode(label); } catch (Exception ignored) {}

            String issuer = u.getQueryParameter("issuer");
            String name;
            if (issuer != null && !issuer.isEmpty()) {
                String acct = label;
                if (acct != null && acct.contains(":"))
                    acct = acct.substring(acct.indexOf(':') + 1).trim();
                name = (acct == null || acct.isEmpty()) ? issuer : (issuer + " (" + acct + ")");
            } else if (label != null && !label.isEmpty()) {
                name = label;
            } else {
                name = "Account";
            }
            return new TotpEntry(name, secret);
        } catch (Exception e) {
            return null;
        }
    }
}
