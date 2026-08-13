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
        String askAnswer = "ok";
        String completeContent = "ok";
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
            return new LlmRound(completeStatus, completeContent, List.of(), probe);
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
