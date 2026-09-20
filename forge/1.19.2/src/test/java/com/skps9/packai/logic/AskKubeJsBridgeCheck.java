package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /** KubeJS Extra.ID-shaped: getId() + toString wrapper. */
    public static final class FakeExtraId {
        private final String id;

        FakeExtraId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return "Extra.ID[" + id + "]";
        }
    }

    /** Item-shaped: getRegistryName() returns ns:path. */
    public static final class FakeItem {
        public Object getRegistryName() {
            return "minecraft:dirt";
        }

        @Override
        public String toString() {
            return "Item{minecraft:dirt}";
        }
    }

    /**
     * ItemStack-shaped prime suspect: toString is {@code 1 minecraft:dirt} (count prefix),
     * no clean id getter.
     */
    public static final class FakeItemStackCountPrefix {
        @Override
        public String toString() {
            return "1 minecraft:dirt";
        }
    }

    public static void main(String[] args) throws Exception {
        unavailableOk();
        askPathFastWhenUnavailable();
        fakeContainerExtractAndDedupe();
        normalizeIdExtractsNsPath();
        installHitsReportsApiMode();
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

    static void normalizeIdExtractsNsPath() {
        // String
        assert "minecraft:dirt".equals(KubeJsApiBridge.normalizeId("minecraft:dirt"));
        assert "mod:demo".equals(KubeJsApiBridge.normalizeId("mod:demo"));
        // ResourceKey / Optional text shapes
        assert "minecraft:dirt".equals(
                KubeJsApiBridge.normalizeId("ResourceKey[minecraft:dirt]"));
        assert "pack:rk".equals(
                KubeJsApiBridge.normalizeId("ResourceKey[pack:rk]"));
        assert "minecraft:dirt".equals(
                KubeJsApiBridge.normalizeId("Optional[minecraft:dirt]"));
        assert "minecraft:dirt".equals(
                KubeJsApiBridge.normalizeId(Optional.of("minecraft:dirt")));
        // wrapped / Item-like / ItemStack count-prefix (prime suspect)
        assert "mod:wrapped".equals(
                KubeJsApiBridge.normalizeId("Item[mod:wrapped]"));
        assert "minecraft:dirt".equals(
                KubeJsApiBridge.normalizeId(new FakeItem()));
        assert "minecraft:dirt".equals(
                KubeJsApiBridge.normalizeId(new FakeItemStackCountPrefix()))
                : "ItemStack toString '1 minecraft:dirt' must yield ns:path";
        // KubeJS Extra.ID wrapper + nested Iterable / array
        assert "minecraft:dirt".equals(
                KubeJsApiBridge.normalizeId(new FakeExtraId("minecraft:dirt")));
        assert "minecraft:dirt".equals(
                KubeJsApiBridge.normalizeId(List.of(new FakeExtraId("minecraft:dirt"))));
        assert "minecraft:dirt".equals(
                KubeJsApiBridge.normalizeId(new Object[] {new FakeExtraId("minecraft:dirt")}));
        // Negative control: garbage / null / empty must not invent an id
        assert KubeJsApiBridge.normalizeId(null).isEmpty();
        assert KubeJsApiBridge.normalizeId("").isEmpty();
        assert KubeJsApiBridge.normalizeId("not-an-id").isEmpty();
        assert KubeJsApiBridge.normalizeId(List.of()).isEmpty();
        assert KubeJsApiBridge.normalizeId(new Object[0]).isEmpty();
        assert KubeJsApiBridge.normalizeId("garbage xyz").isEmpty();
        // diag line shape + bound
        String diag = KubeJsApiBridge.buildDiagLine();
        assert diag.startsWith("Pack AI kubejs bridge diag extra=") : diag;
        assert diag.contains("lookup=") && diag.contains("byKeys=") : diag;
        assert diag.length() <= 400 : diag.length();
        assert !KubeJsApiBridge.maskDiagText("C:\\Users\\skps9\\secret\\file").contains("Users");
        System.out.println("normalizeIdExtractsNsPath OK");
    }

    static void installHitsReportsApiMode() throws Exception {
        KubeJsApiBridge.resetForTest();
        KubeJsMechanicScan.resetIndex();
        Path dir = Files.createTempDirectory("packai-bridge-api");
        KubeJsApiBridge.installHitsForTest(
                "mod:api_item",
                List.of(new KubeJsApiBridge.Hit("ItemEvents.rightClicked", "server_scripts/x.js", 1, "extra")));
        // Without real source file, factsFromBridgeHits may return empty — mode still api.
        KubeJsMechanicScan.factsForItem(dir, "mod:api_item");
        assert "api".equals(KubeJsApiBridge.lastMode()) : KubeJsApiBridge.lastMode();
        assert KubeJsApiBridge.lastHits() > 0 : KubeJsApiBridge.lastHits();
        // Missing item → scan / 0 when no index.
        KubeJsMechanicScan.factsForItem(dir, "mod:missing_for_scan");
        assert "scan".equals(KubeJsApiBridge.lastMode()) : KubeJsApiBridge.lastMode();
        assert KubeJsApiBridge.lastHits() == 0;
        System.out.println("installHitsReportsApiMode OK");
    }
}
