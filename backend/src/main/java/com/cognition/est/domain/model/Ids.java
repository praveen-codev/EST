package com.cognition.est.domain.model;

import java.util.UUID;

/** Id helpers. {@link #stableId} is a bit-exact port of web/lib/domain/ids.ts (FNV-1a 32-bit). */
public final class Ids {
    private Ids() {}

    public static String hash(String input) {
        int h = 0x811c9dc5;
        for (int i = 0; i < input.length(); i++) {
            h ^= input.charAt(i);
            h = h * 0x01000193;
        }
        String hex = Integer.toUnsignedString(h, 16);
        return "0".repeat(Math.max(0, 8 - hex.length())) + hex;
    }

    public static String stableId(String namespace, String seed) {
        return namespace
                + "_"
                + hash(namespace + ":" + seed)
                + hash(new StringBuilder(seed).reverse().toString());
    }

    public static String fresh(String namespace) {
        return namespace + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
