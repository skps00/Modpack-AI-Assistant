package com.skps9.packai.client.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.skps9.packai.logic.ReplyLang;

/**
 * note:kubejs.tooltips.* resolves from an injected lang map; a miss becomes the generic sentence.
 */
public final class KubeJsTooltipTextCheck {
    private KubeJsTooltipTextCheck() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> pack = Map.of(
                "kubejs.tooltips.active_pill.1", "用于在沙漠维度地牢中进行神意挑战");
        String hit = AskService.resolveKubeJsTooltipFact(
                "item:kubejs:god_bless_full_necklace -[use]-> x note:kubejs.tooltips.active_pill.1"
                        + " (source:kubejs/server_scripts/a.js:1 tier:A)",
                pack,
                key -> key);
        assert hit.contains("用于在沙漠维度地牢中进行神意挑战") : hit;
        assert !hit.contains("kubejs.tooltips.") : hit;
        assert hit.startsWith("item:kubejs:god_bless_full_necklace") : hit;
        assert hit.contains("note:") : hit;
        assert hit.contains("(source:kubejs/server_scripts/a.js:1 tier:A)") : hit;

        String generic = ReplyLang.tr(ReplyLang.current(), "packai.reply.kubejs_tooltip_hint");
        String miss = AskService.resolveKubeJsTooltipFact(
                "note:kubejs.tooltips.missing.1 | kubejs.tooltips.missing.2 (source:kubejs/b.js:2)",
                Map.of(),
                key -> key);
        assert miss.equals("note:" + generic + "／" + generic + " (source:kubejs/b.js:2)") : miss;
        assert !miss.contains("kubejs.tooltips.") : miss;

        String blankGame = AskService.resolveKubeJsTooltipFact(
                "note:kubejs.tooltips.missing.1",
                Map.of(),
                key -> "");
        assert blankGame.equals("note:" + generic) : blankGame;
        assert !blankGame.contains("kubejs.tooltips.") : blankGame;

        String gameHit = AskService.resolveKubeJsTooltipFact(
                "note:kubejs.tooltips.charge.1", Map.of(),
                key -> "击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能");
        assert gameHit.contains("击败虚空之花") : gameHit;
        assert !gameHit.contains("kubejs.tooltips.") : gameHit;
        assert !gameHit.contains(generic) : gameHit;

        Path tmp = Files.createTempDirectory("kubejs-pack-lang");
        Path langFile = tmp.resolve("kubejs/assets/kubejs/lang/zh_cn.json");
        try {
            Files.createDirectories(langFile.getParent());
            Files.writeString(langFile,
                    "{\"kubejs.tooltips.charge.1\":\"击败虚空之花\"}",
                    StandardCharsets.UTF_8);
            Map<String, String> loaded = AskService.loadKubeJsPackLang(tmp, "zh_cn");
            assert "击败虚空之花".equals(loaded.get("kubejs.tooltips.charge.1")) : loaded;
            Map<String, String> missingDir = AskService.loadKubeJsPackLang(
                    tmp.resolve("does-not-exist"), "zh_cn");
            assert missingDir.isEmpty() : missingDir;
            Map<String, String> missingFile = AskService.loadKubeJsPackLang(tmp, "en_us");
            assert missingFile.isEmpty() : missingFile;
        } finally {
            Path p = langFile;
            while (p != null) {
                Files.deleteIfExists(p);
                if (p.equals(tmp)) {
                    break;
                }
                p = p.getParent();
            }
        }

        System.out.println("KubeJsTooltipTextCheck OK");
    }
}
