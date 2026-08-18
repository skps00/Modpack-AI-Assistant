package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** JEI information pages → acquire vs PURPOSE FACT. Run with -ea. */
public final class JeiInfoFactsCheck {
    private JeiInfoFactsCheck() {}

    public static void main(String[] args) throws Exception {
        String carry = "携带T-02-99击杀骷髅1%概率获得";
        String chest = "可以在下界中的箱子获得";

        assert JeiInfoFacts.classify("mod:focus", List.of("mod:bone", "mod:flower"), carry)
                == JeiInfoFacts.Kind.PURPOSE : "other outputs ≠ focus → use";
        assert JeiInfoFacts.classify("mod:bone", List.of("mod:bone"), carry)
                == JeiInfoFacts.Kind.ACQUIRE : "focus is output → obtain";
        assert JeiInfoFacts.classify("ns:t-02-99", List.of(), carry)
                == JeiInfoFacts.Kind.PURPOSE : "carry-X-to-get-Y on the carried item → use";
        assert JeiInfoFacts.classify("mod:bone", List.of(), carry)
                == JeiInfoFacts.Kind.ACQUIRE : "carry text but focus is the drop → obtain";
        assert JeiInfoFacts.classify("mod:boot", List.of(), chest)
                == JeiInfoFacts.Kind.ACQUIRE : "chest line → obtain";

        List<String> kube = JeiInfoFacts.parseKubeJs("""
                JEIEvents.information(event => {
                    event.addItem(['mod:glow_bone', 'mod:blue_flower'], ['携带T-02-99击杀骷髅1%概率获得'])
                    event.addItem('mod:boot', Text.black('可以在下界中的箱子获得'))
                })
                """);
        assert kube.stream().anyMatch(f ->
                f.contains("item:mod:glow_bone")
                        && f.contains("via:jei_info")
                        && f.contains(carry)
                        && f.contains("-[loot]->")) : kube;
        assert kube.stream().anyMatch(f ->
                f.contains("item:mod:boot")
                        && f.contains(chest)
                        && f.contains("-[loot]->")) : kube;
        assert kube.stream().noneMatch(f -> f.contains("item:mod:glow_bone") && f.contains("script_use"))
                : kube;

        List<String> forTool = JeiInfoFacts.factsForFocus(kube, "pack:t-02-99");
        assert forTool.stream().anyMatch(f ->
                f.contains("item:pack:t-02-99")
                        && f.contains("script_use")
                        && f.contains(carry)) : forTool;
        assert forTool.stream().anyMatch(AskPurposeContext::isPurposeGraphFact) : forTool;

        String dump = "JEI information：\n  "
                + JeiInfoFacts.dumpLine(JeiInfoFacts.Kind.PURPOSE, carry, List.of("mod:bone", "mod:flower")) + "\n  "
                + JeiInfoFacts.dumpLine(JeiInfoFacts.Kind.ACQUIRE, chest);
        assert dump.contains("related:mod:bone,mod:flower") : dump;
        assert JeiInfoFacts.relatedFromDumpLine(dump.substring(dump.indexOf("jei_info_use:")))
                .equals(List.of("mod:bone", "mod:flower"));
        JeiInfoFacts.Split split = JeiInfoFacts.splitFromDump(dump);
        assert split.use().contains(carry) : split;
        assert split.use().stream().noneMatch(s -> s.contains("related:")) : split;
        assert split.acquire().contains(chest) : split;
        assert JeiInfoFacts.hasAny(dump);

        String nlDump = JeiInfoFacts.dumpLine(JeiInfoFacts.Kind.ACQUIRE, "携带T-02-99击杀骷髅1%概率获得 \\n");
        assert nlDump.contains("获得") : nlDump;
        assert !nlDump.contains("\\n") : nlDump;
        JeiInfoFacts.Split nlSplit = JeiInfoFacts.splitFromDump("JEI information：\n  " + nlDump);
        assert nlSplit.acquire().stream().anyMatch(s -> s.contains("获得")) : nlSplit;

        String lied = "3. 取得方式：LootJS\n本地索引未标明具体由哪种生物或宝箱掉落\n";
        String scrubbed = JeiInfoFacts.stripUnspecifiedMiss(lied);
        assert !scrubbed.contains("未标明") : scrubbed;
        assert scrubbed.contains("LootJS") : scrubbed;
        assert !JeiInfoFacts.looksUnspecifiedMiss(scrubbed);

        Path root = Files.createTempDirectory("packai-jei-info");
        Path js = root.resolve("kubejs/client_scripts/jei_info.js");
        Files.createDirectories(js.getParent());
        Files.writeString(js, """
                JEIEvents.information(event => {
                    event.addItem(['mod:glow_bone', 'mod:blue_flower'], ['携带T-02-99击杀骷髅1%概率获得'])
                    event.addItem('pack:t-02-99', Text.black('可以在下界中的箱子获得'))
                })
                """);
        PackIndex idx = new PackIndex();
        idx.build(root, List.of("kubejs"));
        List<String> obtain = idx.acquireFactsFor("pack:t-02-99", "zh_cn");
        assert obtain.stream().anyMatch(s -> s.contains("可以在下界中的箱子获得") || s.contains("the_nether")
                || s.contains("下界")) : obtain;
        var ask = idx.retrieve("这个有什么用", "pack:t-02-99", List.of());
        assert ask.graphFacts().stream().anyMatch(f ->
                f.contains("via:jei_info") && f.contains(carry) && AskPurposeContext.isPurposeGraphFact(f))
                : ask.graphFacts();
        assert ask.graphFacts().stream().noneMatch(f ->
                f.contains("item:pack:t-02-99") && f.contains(carry) && f.contains("-[loot]->"))
                : "carry page must not be 怎么来 of the carried item: " + ask.graphFacts();

        System.out.println("JeiInfoFactsCheck OK");
    }
}
