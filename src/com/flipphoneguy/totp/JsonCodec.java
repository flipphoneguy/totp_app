package com.flipphoneguy.totp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Tiny JSON serializer/deserializer for TotpEntry lists. */
public final class JsonCodec {
    private JsonCodec() {}

    public static String serialize(List<TotpEntry> entries) throws Exception {
        JSONArray arr = new JSONArray();
        for (TotpEntry e : entries) {
            JSONObject o = new JSONObject();
            o.put("name", e.name == null ? "" : e.name);
            o.put("seed", e.seed == null ? "" : e.seed);
            arr.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("v", 1);
        root.put("entries", arr);
        return root.toString();
    }

    public static List<TotpEntry> parse(String json) throws Exception {
        List<TotpEntry> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.optJSONArray("entries");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            TotpEntry e = new TotpEntry();
            e.name = o.optString("name", "");
            e.seed = o.optString("seed", "");
            out.add(e);
        }
        return out;
    }
}
