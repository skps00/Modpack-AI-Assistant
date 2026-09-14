package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M1e-v2 KubeJS reflection bridge harness. Fixture / fake containers only. Run with -ea.
 */
public final class AskKubeJsBridgeCheck {
    private AskKubeJsBridgeCheck() {}

    /** Public fields mirror {@code EventHandlerContainer} shape for reflection tests. */
    public static final class FakeBox {
        public Object extraId;
        public String source;
        public int line;
        public Object child;

        FakeBox(Object extraId, String source, int line) {
            this.extraId = extraId;
            this.source = source;
            this.line = line;
        }
    }

    public static void main(String[] args) throws Exception {
        unavailableOk();
        askPathFastWhenUnavailable();
        fakeContainerExtractAndDedupe();
        System.out.println("AskKubeJsBridgeCheck OK");
    }

    static void unavailableOk() {
        KubeJsApiBridge.resetForTest();
        assert !KubeJsApiBridge.available() : "no kubejs on classpath → available false";
        assert KubeJsApiBridge.snapshot().isEmpty() : "snapshot empty when unavailable";
        assert KubeJsApiBridge.hitsFor("mod:x").isEmpty();
        // second call must not throw either
        assert !KubeJsApiBridge.available();
        System.out.println("unavailableOk OK");
    }

    static void askPathFastWhenUnavailable() throws Exception {
        KubeJsApiBridge.resetForTest();
        KubeJsMechanicScan.resetIndex();
        KubeJsMechanicScan.warmup();
        Path dir = Files.createTempDirectory("packai-bridge-ask");
        // discard class-init / first touch; measure warm ask path
        KubeJsMechanicScan.factsForItem(dir, "mod:missing_item");
        long t0 = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            List<String> facts = KubeJsMechanicScan.factsForItem(dir, "mod:missing_item");
            assert facts.isEmpty() : facts;
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assert ms < 50 : "ask path when bridge unavailable 100x took " + ms + "ms";
        assert "scan".equals(KubeJsApiBridge.lastMode()) : KubeJsApiBridge.lastMode();
        assert KubeJsApiBridge.lastHits() == 0;
        System.out.println("askPathFastWhenUnavailable OK ms=" + ms);
    }

    static void fakeContainerExtractAndDedupe() {
        KubeJsApiBridge.resetForTest();
        FakeBox a = new FakeBox("mod:demo_item", "server_scripts/demo.js", 57);
        FakeBox dup = new FakeBox("mod:demo_item", "server_scripts/demo.js", 57);
        FakeBox b = new FakeBox("mod:demo_item", "server_scripts/other.js", 10);
        Map<Object, Object> extra = new LinkedHashMap<>();
        extra.put("mod:demo_item", new Object[] {a, dup});
        extra.put("mod:other", b);

        Map<String, List<KubeJsApiBridge.Hit>> byItem =
                KubeJsApiBridge.hitsFromExtraMap("ItemEvents.rightClicked", extra);
        List<KubeJsApiBridge.Hit> hits = byItem.get("mod:demo_item");
        assert hits != null && hits.size() == 1 : hits;
        KubeJsApiBridge.Hit h = hits.get(0);
        assert "mod:demo_item".equals(
                KubeJsApiBridge.normalizeId(a.extraId));
        assert h.source.contains("server_scripts/demo.js") : h.source;
        assert h.line == 57 : h.line;
        assert byItem.containsKey("mod:other");
        System.out.println("fakeContainerExtractAndDedupe OK");
    }
}
