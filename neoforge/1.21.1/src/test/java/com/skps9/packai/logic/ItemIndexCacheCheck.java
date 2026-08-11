package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** WP3 — ItemIndexCache fingerprint / hit-miss. Run with -ea. */
public final class ItemIndexCacheCheck {
    private ItemIndexCacheCheck() {}

    public static void main(String[] args) throws Exception {
        List<String> modsA = List.of("jei@10", "minecraft@1.21.1", "packai@0.1.5");
        List<String> modsB = List.of("jei@10", "minecraft@1.21.1", "packai@0.1.6");
        String fpA = ItemIndexCache.fingerprintMods(modsA);
        String fpB = ItemIndexCache.fingerprintMods(modsB);
        assert !fpA.isEmpty();
        assert !fpA.equals(fpB) : "mod version change must change fingerprint";
        assert fpA.equals(ItemIndexCache.fingerprintMods(List.of("packai@0.1.5", "jei@10", "minecraft@1.21.1")));

        ItemIndexCache.Meta want =
                new ItemIndexCache.Meta("1.21.1", "neoforge", "zh_tw", fpA);
        ItemIndexCache.Meta same =
                new ItemIndexCache.Meta("1.21.1", "neoforge", "zh_tw", fpA);
        ItemIndexCache.Meta lang =
                new ItemIndexCache.Meta("1.21.1", "neoforge", "en_us", fpA);
        ItemIndexCache.Meta mods =
                new ItemIndexCache.Meta("1.21.1", "neoforge", "zh_tw", fpB);
        assert ItemIndexCache.metaMatches(want, same);
        assert !ItemIndexCache.shouldRebuild(want, same);
        assert ItemIndexCache.shouldRebuild(want, lang) : "lang change → rebuild";
        assert ItemIndexCache.shouldRebuild(want, mods) : "mod fp change → rebuild";
        assert ItemIndexCache.shouldRebuild(null, want);
        assert ItemIndexCache.shouldRebuild(new ItemIndexCache.Meta("", "", "", ""), want);
        // jei flag advisory: identity match ignores jei; upgrade helper catches false→true
        ItemIndexCache.Meta noJei =
                new ItemIndexCache.Meta("1.21.1", "neoforge", "zh_tw", fpA, false);
        ItemIndexCache.Meta withJei =
                new ItemIndexCache.Meta("1.21.1", "neoforge", "zh_tw", fpA, true);
        assert ItemIndexCache.metaMatches(noJei, withJei);
        assert !ItemIndexCache.shouldRebuild(noJei, withJei);
        assert ItemIndexCache.shouldUpgradeForJei(noJei, true);
        assert !ItemIndexCache.shouldUpgradeForJei(withJei, true);
        assert !ItemIndexCache.shouldUpgradeForJei(noJei, false);

        String key = ItemIndexCache.cacheKey(want);
        assert key.startsWith("v1_");
        assert key.contains("1.21.1");
        assert key.contains("neoforge");
        assert key.contains("zh_tw");
        assert !key.contains("/") && !key.contains("\\");

        ItemIndexCache.Document doc = new ItemIndexCache.Document(
                want,
                List.of(
                        new ItemIndexCache.Entry(
                                "minecraft:dirt", "Dirt", "", List.of(), "minecraft:dirt"),
                        new ItemIndexCache.Entry(
                                "tetra:scroll_rolled",
                                "卷軸",
                                "{key:\"mirror\"}",
                                List.of("tetra:mirror", "mirror"),
                                "tetra:scroll_rolled#tetra:mirror")));
        String json = ItemIndexCache.toJson(doc);
        ItemIndexCache.Document round = ItemIndexCache.parseJson(json);
        assert ItemIndexCache.metaMatches(round.meta(), want);
        assert round.entries().size() == 2;
        assert "minecraft:dirt".equals(round.entries().get(0).id());
        assert round.entries().get(1).schem().contains("mirror");

        // Wrong format version → empty (force rebuild path)
        assert ItemIndexCache.parseJson("{\"v\":0,\"mc\":\"1.21.1\",\"entries\":[]}").entries().isEmpty();

        Path tmp = Files.createTempDirectory("packai-item-index-check");
        Path file = ItemIndexCache.cacheFile(tmp, want);
        assert ItemIndexCache.save(file, doc);
        ItemIndexCache.Document loaded = ItemIndexCache.load(file);
        assert ItemIndexCache.metaMatches(loaded.meta(), want);
        assert loaded.entries().size() == 2;
        assert ItemIndexCache.cacheDir(tmp).endsWith(Path.of("config", "packai", "item-index"));

        System.out.println("ItemIndexCacheCheck OK");
    }
}
