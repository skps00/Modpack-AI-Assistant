package com.skps9.packai.logic;

import java.util.List;

/**
 * Headless: B1 permanent card debug log helpers + B9 emissionRefs filter. Run with -ea.
 * Does not construct RecipeCard (needs Minecraft registry) — identity cases use null card skip.
 */
public final class AskCardsDebugCheck {
    private AskCardsDebugCheck() {}

    public static void main(String[] args) {
        sectionLabelsWithoutCard();
        bodyPartsHeadingDetect();
        toolPartsPathLabels();
        stackIdsBriefEmpty();
        missingByIdentity();
        visibleEmissionRefIdsDropOnly();
        visibleEmissionRefIdsKeepIdentity();
        formatSuppressedLineNullSafe();
        // Negative control: empty body must not look like parts heading
        assert !AskCardsDebug.bodyHasPartsHeading("") : "empty body";
        assert !AskCardsDebug.bodyHasPartsHeading("没有零件在句子中间") : "mid-sentence 零件";
        System.out.println("AskCardsDebugCheck OK");
    }

    /** sectionLabel needs a card — exercise path helpers + format strings without stacks. */
    static void sectionLabelsWithoutCard() {
        assert "?".equals(AskCardsDebug.sectionLabel(null)) : "null card";
        String line = AskCardsDebug.formatEmittedLine(0, null, "auto");
        assert line.startsWith("#0 ") : line;
        assert line.contains("section=?") : line;
        assert line.contains("ref=auto") : line;
        assert line.contains("outputs=[]") : line;
        assert line.contains("grid=[]") : line;
        assert "auto".equals(AskCardsDebug.refToken(null, List.of())) : "ref null";
        System.out.println("sectionLabelsWithoutCard OK");
    }

    static void bodyPartsHeadingDetect() {
        assert AskCardsDebug.bodyHasPartsHeading("零件：\n下面应该有卡") : "zh colon";
        assert AskCardsDebug.bodyHasPartsHeading("零件:\n") : "zh halfwidth colon";
        assert AskCardsDebug.bodyHasPartsHeading("## 零件\n") : "markdown";
        assert AskCardsDebug.bodyHasPartsHeading("Parts:\nblade / handle") : "en";
        assert AskCardsDebug.bodyHasPartsHeading("1. 零件：\n") : "numbered";
        assert !AskCardsDebug.bodyHasPartsHeading("用作材料：Crafting") : "uses heading";
        assert !AskCardsDebug.bodyHasPartsHeading("怎么来：\n1. 工作台") : "obtain heading";
        System.out.println("bodyPartsHeadingDetect OK");
    }

    static void toolPartsPathLabels() {
        assert "strip".equals(AskCardsDebug.toolPartsPath(true, true)) : "strip wins";
        assert "strip".equals(AskCardsDebug.toolPartsPath(true, false)) : "strip alone";
        assert "body_text".equals(AskCardsDebug.toolPartsPath(false, true)) : "body";
        assert "none".equals(AskCardsDebug.toolPartsPath(false, false)) : "none";
        System.out.println("toolPartsPathLabels OK");
    }

    static void stackIdsBriefEmpty() {
        assert "[]".equals(AskCardsDebug.stackIdsBrief(null, 6)) : "null";
        assert "[]".equals(AskCardsDebug.stackIdsBrief(List.of(), 6)) : "empty";
        System.out.println("stackIdsBriefEmpty OK");
    }

    static void missingByIdentity() {
        assert AskCardsDebug.missingByIdentity(null, List.of()).isEmpty();
        assert AskCardsDebug.missingByIdentity(List.of(), List.of()).isEmpty();
        System.out.println("missingByIdentity OK");
    }

    /**
     * B9: drop-only refs. Without RecipeCard instances we still prove:
     * null/empty → empty; null-card / refId&lt;=0 skipped; empty shown drops all
     * (negative: dangling emission with null card never invents a renumbered id).
     */
    static void visibleEmissionRefIdsDropOnly() {
        assert AskCardsDebug.visibleEmissionRefIds(null, List.of()).isEmpty() : "null emissions";
        assert AskCardsDebug.visibleEmissionRefIds(List.of(), List.of()).isEmpty() : "empty emissions";
        CardEmission unset = new CardEmission("a:b", "output", null, 0);
        CardEmission dangling = new CardEmission("a:b", "output", null, 4);
        List<Integer> dropped = AskCardsDebug.visibleEmissionRefIds(
                List.of(unset, dangling), List.of());
        assert dropped.isEmpty() : "empty shown must drop; got " + dropped;
        // Negative control: never invent / renumber — null card + high refId still absent
        assert !dropped.contains(1) : "must not invent ref 1";
        assert !dropped.contains(4) : "null-card emission must not keep ref 4";
        System.out.println("visibleEmissionRefIdsDropOnly OK");
    }

    /** B9 positive: keep original refId for shown identity; drop suppressed; never renumber to 1..n. */
    static void visibleEmissionRefIdsKeepIdentity() {
        Object keep = new Object();
        Object gone = new Object();
        List<Integer> refs = AskCardsDebug.filterRefsByIdentity(
                List.of(2, 4), List.of(gone, keep), List.of(keep));
        assert refs.equals(List.of(4)) : "expect [4] only, got " + refs;
        // Negative: surviving card must not be renumbered to 1
        assert !refs.contains(1) : "must not renumber ref 4 → 1";
        // Equal value, different identity → drop (String intern trap avoided via new String)
        String a = new String("same");
        String b = new String("same");
        assert a != b : "need distinct identity";
        List<Integer> byId = AskCardsDebug.filterRefsByIdentity(
                List.of(7), List.of(a), List.of(b));
        assert byId.isEmpty() : "equals≠identity; got " + byId;
        System.out.println("visibleEmissionRefIdsKeepIdentity OK");
    }

    static void formatSuppressedLineNullSafe() {
        String s = AskCardsDebug.formatSuppressedLine(null, "frame");
        assert s.contains("reason=frame") : s;
        assert s.contains("section=?") : s;
        System.out.println("formatSuppressedLineNullSafe OK");
    }
}
