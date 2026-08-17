package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Hybrid loop branches. Run with -ea. No Minecraft. */
public final class AskToolLoopCheck {
    private AskToolLoopCheck() {}

    public static void main(String[] args) {
        fingerprintKeysSorted();
        purposeZeroExtra();
        h1CraftEmptyDrainsGuideQuestNotJei();
        h1FatAcquireDoesNotSkipDrain();
        h1EmptyDrainSkipsLlm();
        h1HitDropsMissPin();
        h2ObtainEmptyDrainsEvenIfJeiFat();
        h2SkipSameArgsAcquire();
        h3VariantArgsNotDup();
        jsonMarkerOnly();
        jsonDropsUnknown();
        dupAbort();
        wallClock();
        probe400NotARound();
        status401NoSwitch();
        jsonHopWhenNoNative();
        firstAskCapableSendsFiveTools();
        firstAsk400FallsBackNoTools();
        firstAskOffNeverSends();
        firstAskPurposeSendsTools();
        offerRouting();
        typedMissOnEmptyLlmCall();
        followupRoundStillSendsTools();
        roleToolMessageShape();
        cardAlignMismatchOmits();
        queryToolFingerprintUsesArgsItem();
        dsmlRecipeLookupMappedAndHop();
        System.out.println("AskToolLoopCheck OK");
    }

    private static void fingerprintKeysSorted() {
        String a = AskToolLoop.fingerprint("jei_lookup", "mod:scroll", "OUTPUT", List.of("b", "a"));
        String b = AskToolLoop.fingerprint("jei_lookup", "mod:scroll", "OUTPUT", List.of("a", "b"));
        assert a.equals(b) : a;
        String c = AskToolLoop.fingerprint("jei_lookup", "mod:scroll", "OUTPUT", List.of());
        assert !a.equals(c);
    }

    private static void purposeZeroExtra() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        AtomicInteger jei = new AtomicInteger();
        AtomicInteger guide = new AtomicInteger();
        loop.replaceAll(List.of(
                fake("jei_lookup", jei, "recipes"),
                fake("guide_fetch", guide, "book")));
        AskLoopState s = base("用途是什麼", AskLoopState.Intent.PURPOSE);
        s.noteShot0("jei_lookup", "SLIM", List.of(), "slim jei");
        int before = s.localTools();
        loop.drainBeforeFirstLlm(s);
        assert s.intent() == AskLoopState.Intent.PURPOSE;
        assert !s.skipLlm();
        assert s.localTools() == before : s.localTools();
        assert guide.get() == 0 : "purpose must not drain extra tools";
        assert jei.get() == 0;
        String out = loop.continueAfterAsk(s, "用途：燒東西", unusedLlm());
        assert "用途：燒東西".equals(out);
        assert s.llmRounds() == 0;
        assert !s.escalate();
    }

    private static void h1CraftEmptyDrainsGuideQuestNotJei() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        AtomicInteger jei = new AtomicInteger();
        List<String> order = new ArrayList<>();
        loop.replaceAll(List.of(
                fake("jei_lookup", jei, "SHOULD_NOT"),
                tracing("guide_fetch", order, ""),
                tracing("quest_fetch", order, "")));
        AskLoopState s = base("這個怎麼合成？", AskLoopState.Intent.CRAFT);
        s.setDumpLevel("OUTPUT");
        s.noteShot0("jei_lookup", "OUTPUT", List.of(), "");
        s.noteShot0("acquire", "FULL", List.of(), "Loot: chest of diamonds");
        loop.drainBeforeFirstLlm(s);
        assert jei.get() == 0 : "must not re-call same-args jei_lookup";
        assert order.equals(List.of("guide_fetch", "quest_fetch")) : order;
        assert s.skipLlm();
    }

    private static void h1FatAcquireDoesNotSkipDrain() {
        h1CraftEmptyDrainsGuideQuestNotJei();
    }

    private static void h1EmptyDrainSkipsLlm() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        loop.replaceAll(List.of(
                fake("guide_fetch", new AtomicInteger(), ""),
                fake("quest_fetch", new AtomicInteger(), "")));
        AskLoopState s = base("配方怎麼做", AskLoopState.Intent.CRAFT);
        s.setDumpLevel("OUTPUT");
        s.noteShot0("jei_lookup", "OUTPUT", List.of(), "");
        loop.drainBeforeFirstLlm(s);
        assert s.skipLlm();
        FakeLlm llm = new FakeLlm();
        loop.continueAfterAsk(s, "should not matter", llm);
        assert llm.asks == 0 && llm.completes == 0;
        assert s.llmRounds() == 0;
    }

    private static void h1HitDropsMissPin() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        loop.replaceAll(List.of(
                fake("guide_fetch", new AtomicInteger(), "Guide: craft with stick"),
                fake("quest_fetch", new AtomicInteger(), "")));
        AskLoopState s = base("配方怎麼做", AskLoopState.Intent.CRAFT);
        s.setDumpLevel("OUTPUT");
        s.noteShot0("jei_lookup", "OUTPUT", List.of(), "");
        s.setMissPin(true);
        loop.drainBeforeFirstLlm(s);
        assert !s.skipLlm();
        assert !s.missPin();
        assert s.jeiText().isBlank();
        assert s.guideText().contains("stick");
    }

    private static void h2ObtainEmptyDrainsEvenIfJeiFat() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        List<String> order = new ArrayList<>();
        loop.replaceAll(List.of(
                tracing("acquire", order, ""),
                tracing("guide_fetch", order, ""),
                tracing("quest_fetch", order, ""),
                tracing("consume_use", order, "")));
        AskLoopState s = base("如何取得？", AskLoopState.Intent.OBTAIN);
        s.setDumpLevel("OUTPUT");
        s.noteShot0("jei_lookup", "OUTPUT", List.of(), "OUTPUT: 9 recipes for this item");
        s.noteShot0("acquire", "FULL", List.of(), "");
        loop.drainBeforeFirstLlm(s);
        assert !order.contains("acquire") : "same-args acquire already ran";
        assert order.equals(List.of("guide_fetch", "quest_fetch", "consume_use")) : order;
        assert s.skipLlm() : "fat JEI is not obtain evidence";
    }

    private static void h2SkipSameArgsAcquire() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        AtomicInteger acq = new AtomicInteger();
        loop.replaceAll(List.of(
                fake("acquire", acq, "Loot: should not run"),
                fake("guide_fetch", new AtomicInteger(), "book obtain"),
                fake("quest_fetch", new AtomicInteger(), ""),
                fake("consume_use", new AtomicInteger(), "")));
        AskLoopState s = base("how to get", AskLoopState.Intent.OBTAIN);
        s.noteShot0("acquire", "FULL", List.of(), "");
        loop.drainBeforeFirstLlm(s);
        assert acq.get() == 0;
        assert !s.skipLlm();
        assert !s.missPin();
    }

    private static void h3VariantArgsNotDup() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        AtomicInteger jei = new AtomicInteger();
        loop.replaceAll(List.of(fake("jei_lookup", jei, "spell recipe ISB:fire")));
        AskLoopState s = base("這個卷軸配方怎麼做？", AskLoopState.Intent.CRAFT);
        s.setDumpLevel("OUTPUT");
        s.setVariantKeys(List.of("irons_spellbooks:fireball"));
        s.noteShot0("jei_lookup", "OUTPUT", List.of(), "bare scroll recipes");
        loop.drainBeforeFirstLlm(s);
        assert jei.get() == 1 : "variant keys are different args";
        assert !s.skipLlm();
        assert s.jeiText().contains("fire");
        String fpBare = AskToolLoop.fingerprint("jei_lookup", s.itemId(), "OUTPUT", List.of());
        String fpVar = AskToolLoop.fingerprint("jei_lookup", s.itemId(), "OUTPUT", s.variantKeys());
        assert !fpBare.equals(fpVar);
        assert s.alreadyRan(fpVar);
    }

    private static void jsonMarkerOnly() {
        List<AskToolCall> ok = AskToolLoop.parseJsonTools(
                "chat [[tools]] {\"calls\":[{\"name\":\"guide_fetch\",\"args\":{\"item\":\"mod:x\"}}]}");
        assert ok.size() == 1 : ok;
        assert "guide_fetch".equals(ok.get(0).name());
        assert "mod:x".equals(ok.get(0).itemId());
        List<AskToolCall> bare = AskToolLoop.parseJsonTools("{\"calls\":[{\"name\":\"guide_fetch\"}]}");
        assert bare.isEmpty() : "bare { must be ignored";
        assert AskToolLoop.parseJsonTools(null).isEmpty();
        assert !AskToolLoop.hasJsonMarker("nope");
        assert AskToolLoop.hasJsonMarker("x [[tools]] y");
    }

    private static void jsonDropsUnknown() {
        List<AskToolCall> calls = AskToolLoop.parseJsonTools(
                "[[tools]] {\"calls\":[{\"name\":\"decompile\"},{\"name\":\"quest_fetch\"}]}");
        assert calls.size() == 1 : calls;
        assert "quest_fetch".equals(calls.get(0).name());
    }

    private static void dupAbort() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        AtomicInteger n = new AtomicInteger();
        loop.replaceAll(List.of(fake("guide_fetch", n, "once")));
        AskLoopState s = base("配方怎麼做", AskLoopState.Intent.CRAFT);
        AskToolArgs args = AskToolArgs.from(s, "", List.of());
        loop.run(s, "guide_fetch", args);
        loop.run(s, "guide_fetch", args);
        assert n.get() == 1 : n.get();
        assert s.localTools() == 1;
    }

    private static void wallClock() {
        long[] now = {1_000_000L};
        AskLoopState s = AskLoopState.start("q", "mod:x", List.of(), 1_000_000L + 90_000L);
        s.setClock(() -> now[0]);
        assert s.httpTimeout().toMillis() == 90_000L;
        now[0] += 91_000L;
        assert s.wallExpired();
        assert s.httpTimeout().toMillis() == 1L;
        assert !s.canLlm();
    }

    private static void probe400NotARound() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        loop.replaceAll(List.of(fake("guide_fetch", new AtomicInteger(), "later")));
        AskLoopState s = base("配方怎麼做", AskLoopState.Intent.CRAFT);
        s.setDumpLevel("OUTPUT");
        s.noteShot0("jei_lookup", "OUTPUT", List.of(), "recipe lines here");
        s.countSuccessfulLlm();
        FakeLlm llm = new FakeLlm();
        llm.completeStatus = 400;
        llm.probe = true;
        llm.askAnswer = "fallback from updated FACT";
        String out = loop.continueAfterAsk(s, "craft {{item:other:foo}} from ink", llm);
        assert llm.completes == 1;
        assert llm.asks == 1;
        assert llm.remembered;
        assert s.llmRounds() == 2 : s.llmRounds(); // first ask counted by caller + fallback
        assert "fallback from updated FACT".equals(out);
    }

    private static void status401NoSwitch() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        AskLoopState s = base("配方怎麼做", AskLoopState.Intent.CRAFT);
        s.setDumpLevel("OUTPUT");
        s.noteShot0("jei_lookup", "OUTPUT", List.of(), "recipe");
        s.countSuccessfulLlm();
        FakeLlm llm = new FakeLlm();
        llm.completeStatus = 401;
        llm.completeContent = "HTTP 401";
        String out = loop.continueAfterAsk(s, "craft {{item:other:foo}}", llm);
        assert "HTTP 401".equals(out);
        assert !llm.remembered;
        assert llm.asks == 0;
        assert s.llmRounds() == 1;
    }

    private static void jsonHopWhenNoNative() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        AtomicInteger guide = new AtomicInteger();
        loop.replaceAll(List.of(fake("guide_fetch", guide, "from json hop")));
        AskLoopState s = base("配方怎麼做", AskLoopState.Intent.CRAFT);
        s.setDumpLevel("OUTPUT");
        s.noteShot0("jei_lookup", "OUTPUT", List.of(), "recipe");
        s.countSuccessfulLlm();
        FakeLlm llm = new FakeLlm();
        llm.noNative = true;
        llm.askAnswer = "[[tools]] {\"calls\":[{\"name\":\"guide_fetch\"}]}";
        llm.askAnswers = List.of(
                "[[tools]] {\"calls\":[{\"name\":\"guide_fetch\"}]}",
                "final after hop");
        String out = loop.continueAfterAsk(s, "craft {{item:other:foo}}", llm);
        assert guide.get() == 1;
        assert "final after hop".equals(out) : out;
    }

    private static void firstAskCapableSendsFiveTools() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        AskLoopState s = base("配方怎麼做", AskLoopState.Intent.CRAFT);
        s.noteShot0("jei_lookup", "OUTPUT", List.of(), "recipe lines");
        FakeLlm llm = new FakeLlm();
        llm.completeContent = "craft [[recipe_card:1]] with stick";
        String out = loop.firstAsk(s, llm);
        assert llm.completes == 1 : llm.completes;
        assert llm.asks == 0 : llm.asks;
        assert llm.lastToolNames.containsAll(AskToolLoop.FIRST_ROUND_TOOLS) : llm.lastToolNames;
        assert out.contains("[[recipe_card:1]]") : out;
    }

    private static void firstAsk400FallsBackNoTools() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        AskLoopState s = base("配方怎麼做", AskLoopState.Intent.CRAFT);
        s.noteShot0("jei_lookup", "OUTPUT", List.of(), "FACT catalog");
        FakeLlm llm = new FakeLlm();
        llm.completeStatus = 400;
        llm.probe = true;
        llm.askAnswer = "fallback FACT [[recipe_card:2]]";
        String out = loop.firstAsk(s, llm);
        assert llm.completes == 1;
        assert llm.asks == 1;
        assert llm.remembered;
        assert "fallback FACT [[recipe_card:2]]".equals(out) : out;
    }

    private static void firstAskOffNeverSends() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        AskLoopState s = base("配方怎麼做", AskLoopState.Intent.CRAFT);
        FakeLlm llm = new FakeLlm();
        llm.nativeToolsMode = "off";
        llm.askAnswer = "off path [[recipe_card:3]]";
        String out = loop.firstAsk(s, llm);
        assert llm.completes == 0 : llm.completes;
        assert llm.asks == 1;
        assert out.contains("[[recipe_card:3]]");
    }

    private static void firstAskPurposeSendsTools() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        AskLoopState s = base("用途是什麼", AskLoopState.Intent.PURPOSE);
        FakeLlm llm = new FakeLlm();
        llm.completeContent = "purpose via tools";
        String out = loop.firstAsk(s, llm);
        assert llm.completes == 1 : llm.completes;
        assert llm.asks == 0;
        assert llm.lastToolNames.contains("purpose_lookup") : llm.lastToolNames;
        assert "purpose via tools".equals(out);
        FakeLlm off = new FakeLlm();
        off.nativeToolsMode = "off";
        off.askAnswer = "purpose only";
        String offOut = loop.firstAsk(base("用途是什麼", AskLoopState.Intent.PURPOSE), off);
        assert off.completes == 0;
        assert "purpose only".equals(offOut);
    }

    private static void offerRouting() {
        assert AskToolLoop.shouldOfferFirstRoundTools(AskLoopState.Intent.PURPOSE, "auto", false);
        assert !AskToolLoop.shouldOfferFirstRoundTools(AskLoopState.Intent.CRAFT, "off", false);
        assert AskToolLoop.shouldOfferFirstRoundTools(AskLoopState.Intent.CRAFT, "force", true);
        assert !AskToolLoop.shouldOfferFirstRoundTools(AskLoopState.Intent.CRAFT, "auto", true);
        assert AskToolLoop.shouldOfferFirstRoundTools(AskLoopState.Intent.OBTAIN, "auto", false);
    }

    private static void typedMissOnEmptyLlmCall() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        loop.replaceAll(List.of(fake("guide_fetch", new AtomicInteger(), "")));
        AskLoopState s = base("配方怎麼做", AskLoopState.Intent.CRAFT);
        s.noteShot0("jei_lookup", "OUTPUT", List.of(), "recipe");
        FakeLlm llm = new FakeLlm();
        llm.completeQueue = List.of(
                new LlmRound(200, "", List.of(new AskToolCall("guide_fetch", "", "", List.of())), false),
                new LlmRound(200, "after miss", List.of(), false));
        String out = loop.firstAsk(s, llm);
        assert "after miss".equals(out) : out;
        assert s.toolTurns().stream().anyMatch(t -> "tool".equals(t.role()) && t.content().contains("[TOOL_MISS]"))
                : s.toolTurns();
    }

    private static void followupRoundStillSendsTools() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        loop.replaceAll(List.of(fake("guide_fetch", new AtomicInteger(), "from tool")));
        AskLoopState s = base("配方怎麼做", AskLoopState.Intent.CRAFT);
        s.noteShot0("jei_lookup", "OUTPUT", List.of(), "recipe");
        FakeLlm llm = new FakeLlm();
        llm.completeQueue = List.of(
                new LlmRound(200, "", List.of(new AskToolCall("guide_fetch", "", "", List.of())), false),
                new LlmRound(200, "final after tools", List.of(), false));
        String out = loop.firstAsk(s, llm);
        assert llm.completes == 2 : llm.completes;
        assert llm.lastToolNames.containsAll(AskToolLoop.FIRST_ROUND_TOOLS) : llm.lastToolNames;
        assert "final after tools".equals(out) : out;
        assert s.toolTurns().stream().anyMatch(t -> "tool".equals(t.role())) : s.toolTurns();
    }

    private static void roleToolMessageShape() {
        var json = ToolChatTurn.tool("call_abc", "jei result").toMessageJson();
        assert "tool".equals(json.get("role").getAsString());
        assert "call_abc".equals(json.get("tool_call_id").getAsString());
        assert "jei result".equals(json.get("content").getAsString());
    }

    private static void cardAlignMismatchOmits() {
        var crafting = new RecipeCardAlign.Fingerprint(0, "Crafting", List.of("花", "药水"), List.of(), List.of());
        var brew = new RecipeCardAlign.Fingerprint(1, "活体酿造台", List.of("育种亢奋剂"), List.of("活体酿造台"), List.of());
        String reply = "活体酿造台 + 营养素 → 育种亢奋剂";
        List<Integer> hit = RecipeCardAlign.pickIndices(reply, List.of(crafting, brew));
        assert hit.equals(List.of(1)) : hit;
        List<Integer> miss = RecipeCardAlign.pickIndices(reply, List.of(crafting));
        assert miss.isEmpty() : miss;
        assert RecipeCardAlign.replyLooksSpecific(reply);
        assert RecipeCardAlign.strongMatch(reply, brew);
        assert !RecipeCardAlign.strongMatch(reply, crafting);
        assert RecipeCardAlign.bestLineIndex("育种亢奋剂", List.of("0 | Crafting", "1 | 活体酿造台 → 育种亢奋剂")) == 1;
        String multi = "活体酿造台 → 育种亢奋剂\n消化器 → 营养物质\n动力搅拌器 → 生物质\n黑暗祭坛 → 寄花图腾";
        var digest = new RecipeCardAlign.Fingerprint(2, "消化器", List.of("营养物质"), List.of("消化器"), List.of());
        var mix = new RecipeCardAlign.Fingerprint(3, "动力搅拌器", List.of("生物质"), List.of("工作盆"), List.of());
        var altar = new RecipeCardAlign.Fingerprint(4, "黑暗祭坛", List.of("寄花图腾"), List.of("黑暗祭坛"), List.of());
        List<Integer> many = RecipeCardAlign.pickIndices(multi, List.of(crafting, brew, digest, mix, altar));
        assert many.equals(List.of(1, 2, 3, 4)) : many;

        String noArrow = "可在黑暗祭坛制成暴食之钥，与圆环之理。序列组装可作会心一击处理器。";
        var altarKey = new RecipeCardAlign.Fingerprint(
                1, "黑暗祭坛", List.of("暴食之钥", "圆环之理"), List.of("黑暗祭坛"), List.of());
        var assembly = new RecipeCardAlign.Fingerprint(
                2, "序列组装", List.of("会心一击处理器"), List.of("序列组装"), List.of());
        assert RecipeCardAlign.replyLooksSpecific(noArrow);
        assert !RecipeCardAlign.replyLooksSpecific("可在任务书里搜尋相關任務");
        assert RecipeCardAlign.strongMatch(noArrow, altarKey);
        assert RecipeCardAlign.strongMatch(noArrow, assembly);
        assert !RecipeCardAlign.strongMatch(noArrow, crafting);
        List<Integer> noArrowHit = RecipeCardAlign.pickIndices(
                noArrow, List.of(crafting, altarKey, assembly));
        assert noArrowHit.equals(List.of(1, 2)) : noArrowHit;
        List<Integer> noArrowMiss = RecipeCardAlign.pickIndices(noArrow, List.of(crafting));
        assert noArrowMiss.isEmpty() : noArrowMiss;
    }

    private static void queryToolFingerprintUsesArgsItem() {
        AskToolLoop loop = AskToolLoop.INSTANCE;
        AtomicInteger n = new AtomicInteger();
        loop.replaceAll(List.of(fake("worldgen_lookup", n, "[WORLDGEN] hit")));
        AskLoopState s = base("tell me more", AskLoopState.Intent.PURPOSE);
        AskToolArgs q1 = new AskToolArgs(
                "diamond_ore", "", List.of(), s.question(), s.lang(),
                s.gameDir(), s.scanners(), s.deadlineMs());
        assert "[WORLDGEN] hit".equals(loop.run(s, "worldgen_lookup", q1));
        assert n.get() == 1;
        assert s.localTools() == 1;
        assert "[WORLDGEN] hit".equals(loop.run(s, "worldgen_lookup", q1));
        assert n.get() == 1 : "repeat query must hit cache";
        AskToolArgs q2 = new AskToolArgs(
                "iron_ore", "", List.of(), s.question(), s.lang(),
                s.gameDir(), s.scanners(), s.deadlineMs());
        assert "[WORLDGEN] hit".equals(loop.run(s, "worldgen_lookup", q2));
        assert n.get() == 2 : "distinct query must run";
        assert s.localTools() == 2 : "distinct query must record, not collide on held item";
        String heldFp = AskToolLoop.fingerprint("worldgen_lookup", s.itemId(), "", List.of());
        assert !s.alreadyRan(heldFp) : "must not store under held item id";
    }

    private static final String GRAVEYARD_DSML = ""
            + "< | DSML | | tool_calls>\n"
            + "< | DSML | | invoke name=\"recipe_lookup\">\n"
            + "< | DSML | | parameter name=\"item\" string=\"true\">graveyard:corruption</ | DSML | | parameter>\n"
            + "< | DSML | | parameter name=\"query\" string=\"true\">full</ | DSML | | parameter>\n"
            + "</ | DSML | | invoke>\n"
            + "</ | DSML | | tool_calls>\n";

    private static void dsmlRecipeLookupMappedAndHop() {
        List<AskToolCall> parsed = AskToolLoop.parseLeakedToolXml(GRAVEYARD_DSML);
        assert parsed.size() == 1 : parsed;
        assert "jei_lookup".equals(parsed.get(0).name()) : parsed.get(0).name();
        assert "graveyard:corruption".equals(parsed.get(0).itemId()) : parsed.get(0).itemId();
        assert "FULL".equals(parsed.get(0).dumpLevel()) : parsed.get(0).dumpLevel();

        AskToolCall altar = AskToolLoop.canonicalizeCall(
                "recipe_lookup", "graveyard:corruption", "", "Living Altar", List.of(), "", "");
        assert altar != null && "show_recipe_card".equals(altar.name()) : altar;
        assert "Living Altar".equals(altar.dumpLevel()) : altar.dumpLevel();

        AskToolLoop loop = AskToolLoop.INSTANCE;
        AtomicInteger jei = new AtomicInteger();
        loop.replaceAll(List.of(fake("jei_lookup", jei, "FULL recipes for corruption")));
        AskLoopState s = AskLoopState.start(
                "堕落精华 用途配方取得", "graveyard:corruption", List.of(),
                System.currentTimeMillis() + 90_000L);
        s.setIntent(AskLoopState.Intent.PURPOSE);
        s.setDumpLevel("SLIM");
        s.setLang("zh_tw");
        s.noteShot0("jei_lookup", "SLIM", List.of(), "slim catalog");
        FakeLlm llm = new FakeLlm();
        llm.completeQueue = List.of(
                new LlmRound(200, GRAVEYARD_DSML, List.of(), false),
                new LlmRound(200, "用途：仪式腐化。怎么来：合成。", List.of(), false));
        String out = loop.firstAsk(s, llm);
        assert jei.get() == 1 : "FULL dump must run (shot-0 was SLIM)";
        assert out.contains("用途：仪式腐化") : out;
        assert !out.contains("DSML") : out;
        assert !out.contains("recipe_lookup") : out;
        assert llm.completes == 2 : llm.completes;
    }

    private static AskLoopState base(String q, AskLoopState.Intent intent) {
        AskLoopState s = AskLoopState.start(q, "mod:demo", List.of(), System.currentTimeMillis() + 90_000L);
        s.setIntent(intent);
        s.setDumpLevel(intent == AskLoopState.Intent.PURPOSE ? "SLIM" : "OUTPUT");
        s.setLang("zh_tw");
        return s;
    }

    private static AskTool fake(String name, AtomicInteger n, String out) {
        return new AskTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String run(AskToolArgs args) {
                n.incrementAndGet();
                return out;
            }
        };
    }

    private static AskTool tracing(String name, List<String> order, String out) {
        return new AskTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String run(AskToolArgs args) {
                order.add(name);
                return out;
            }
        };
    }

    private static AskToolLoop.LlmBridge unusedLlm() {
        return new FakeLlm();
    }

    private static final class FakeLlm implements AskToolLoop.LlmBridge {
        int asks;
        int completes;
        int completeStatus = 200;
        boolean probe;
        boolean remembered;
        boolean noNative;
        String nativeToolsMode = "auto";
        String askAnswer = "ok";
        String completeContent = "ok";
        List<String> lastToolNames = List.of();
        List<AskToolCall> completeCalls = List.of();
        List<LlmRound> completeQueue = List.of();
        int completeIdx;
        List<String> askAnswers = List.of();
        int askIdx;

        @Override
        public String askNoTools() {
            asks++;
            if (askIdx < askAnswers.size()) {
                return askAnswers.get(askIdx++);
            }
            return askAnswer;
        }

        @Override
        public LlmRound completeWithTools(List<String> toolNames) {
            completes++;
            lastToolNames = toolNames == null ? List.of() : List.copyOf(toolNames);
            if (completeIdx < completeQueue.size()) {
                return completeQueue.get(completeIdx++);
            }
            return new LlmRound(completeStatus, completeContent, completeCalls, probe);
        }

        @Override
        public String nativeToolsMode() {
            return nativeToolsMode;
        }

        @Override
        public void rememberNoNativeTools() {
            remembered = true;
        }

        @Override
        public boolean noNativeTools() {
            return noNative;
        }
    }
}
