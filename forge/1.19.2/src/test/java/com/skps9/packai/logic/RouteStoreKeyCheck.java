package com.skps9.packai.logic;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import com.skps9.packai.client.routes.RouteStore;

/**
 * Headless checks for {@link RouteStore#worldKeyFor(String)} (no Minecraft).
 */
public final class RouteStoreKeyCheck {
    private static final Pattern SAFE_KEY = Pattern.compile("^[a-z0-9._-]+$");

    private RouteStoreKeyCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    private static void neq(String a, String b, String what) {
        if (a.equals(b)) {
            fail(what + ": keys must differ but both were " + a);
        }
    }

    private static void eq(String a, String b, String what) {
        if (!a.equals(b)) {
            fail(what + ": want=" + a + " got=" + b);
        }
    }

    public static void main(String[] args) {
        int cases = 0;

        neq(
                RouteStore.worldKeyFor("New World"),
                RouteStore.worldKeyFor("New_World"),
                "space vs underscore");
        cases += 2;

        neq(RouteStore.worldKeyFor("World"), RouteStore.worldKeyFor("world"), "case sensitivity");
        cases += 2;

        eq(
                RouteStore.worldKeyFor("test-realm"),
                RouteStore.worldKeyFor("test-realm"),
                "deterministic");
        cases += 1;

        String longA = "a".repeat(99) + "x";
        String longB = "a".repeat(99) + "y";
        neq(RouteStore.worldKeyFor(longA), RouteStore.worldKeyFor(longB), "long suffix differs");
        cases += 2;

        String ipKey = RouteStore.worldKeyFor("192.168.50.89");
        if (!SAFE_KEY.matcher(ipKey).matches() || ipKey.length() > 33) {
            fail("ip key unsafe or too long: " + ipKey);
        }
        cases += 1;

        String emptyKey = RouteStore.worldKeyFor("");
        String nullKey = RouteStore.worldKeyFor(null);
        eq(emptyKey, nullKey, "null vs empty");
        if ("unknown".equals(emptyKey)) {
            fail("empty key must not be literal unknown");
        }
        cases += 2;

        Random rnd = new Random(42);
        Set<String> seen = new HashSet<>();
        String[] seeds = {"世界", " ", "/", "\\", "..", "😀", "a/b", "x\\y", "..\n"};
        for (int i = 0; i < 200; i++) {
            StringBuilder sb = new StringBuilder();
            sb.append(seeds[i % seeds.length]);
            sb.append(i);
            sb.append((char) ('\u4e00' + rnd.nextInt(100)));
            if (rnd.nextBoolean()) {
                sb.append('/');
            }
            String key = RouteStore.worldKeyFor(sb.toString());
            cases += 1;
            if (key.length() > 33) {
                fail("key too long: " + key);
            }
            if (key.contains("/") || key.contains("\\") || key.contains("..")) {
                fail("key has path chars: " + key);
            }
            if (!SAFE_KEY.matcher(key).matches()) {
                fail("key has illegal chars: " + key);
            }
            if (!seen.add(key)) {
                fail("duplicate key for input " + sb + ": " + key);
            }
        }

        // Sanity: hash8 matches SHA-256 prefix (MessageDigest in check, independent of RouteStore).
        String sample = RouteStore.worldKeyFor("New World");
        String expectedHash = sha256Hex8("New World");
        if (!sample.endsWith("-" + expectedHash)) {
            fail("New World key suffix mismatch: " + sample);
        }
        cases += 1;

        System.out.println("check RouteStoreKeyCheck OK (" + cases + " cases)");
    }

    private static String sha256Hex8(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(dig).substring(0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
