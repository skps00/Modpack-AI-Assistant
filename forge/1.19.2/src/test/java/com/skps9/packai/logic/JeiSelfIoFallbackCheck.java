package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import com.skps9.packai.client.jei.JeiLookup;

/** Runnable check: JEI same-item upgrade fallback only when useful==0. Run with -ea. */
public final class JeiSelfIoFallbackCheck {
    private JeiSelfIoFallbackCheck() {}

    public static void main(String[] args) {
        // A: useful=0 + 3 candidates → all 3
        List<String> a = JeiLookup.selfIoFallback(List.of("a", "b", "c"), 0, 3);
        assert a.size() == 3 : a;
        assert a.equals(List.of("a", "b", "c")) : a;

        // B: useful=0 + 8 candidates → cap 3
        List<String> eight = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            eight.add("r" + i);
        }
        List<String> b = JeiLookup.selfIoFallback(eight, 0, 3);
        assert b.size() == 3 : b;
        assert b.equals(List.of("r0", "r1", "r2")) : b;

        // C: negative control — useful>0 → empty (no noise when normal recipes exist)
        List<String> c = JeiLookup.selfIoFallback(List.of("a"), 2, 3);
        assert c.isEmpty() : c;

        // D: empty / null pending → empty, no NPE
        assert JeiLookup.selfIoFallback(List.of(), 0, 3).isEmpty();
        assert JeiLookup.selfIoFallback(null, 0, 3).isEmpty();

        // E: max=0 → empty
        assert JeiLookup.selfIoFallback(List.of("a", "b"), 0, 0).isEmpty();

        // Lang helper present (bundle key)
        String zh = ReplyLang.jeiSelfIoUpgrade("zh_cn");
        assert zh != null && !zh.isBlank() && !zh.equals("packai.reply.jei_self_io_upgrade") : zh;
        String en = ReplyLang.jeiSelfIoUpgrade("en_us");
        assert en != null && en.contains("upgrade") : en;

        System.out.println("JeiSelfIoFallbackCheck OK");
    }
}
