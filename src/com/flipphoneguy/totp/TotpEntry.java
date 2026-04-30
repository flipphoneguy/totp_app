package com.flipphoneguy.totp;

public class TotpEntry {
    public String name;
    public String seed;

    public TotpEntry() {}
    public TotpEntry(String name, String seed) {
        this.name = name;
        this.seed = seed;
    }
}
