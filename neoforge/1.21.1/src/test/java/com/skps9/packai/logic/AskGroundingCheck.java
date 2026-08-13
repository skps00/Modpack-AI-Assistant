package com.skps9.packai.logic;

import java.util.List;

/** Grounding: this focus + variant; max 1 lookup; other-variant ≠ support. Run with -ea. */
public final class AskGroundingCheck {
    private AskGroundingCheck() {}

    public static void main(String[] args) {
        purposeSkip();
        otherVariantNotSupport();
        maxOneLookup();
        variantHitGrounded();
        stationTemplateGrounded();
        System.out.println("AskGroundingCheck OK");
    }

    private static void purposeSkip() {
        AskLoopState s = AskLoopState.start("用途是什麼", "mod:x", List.of(), now());
        s.setIntent(AskLoopState.Intent.PURPOSE);
        AskGrounding.Result r = AskGrounding.check("anything", s);
        assert r.grounded();
        assert !r.needsLookup();
    }

    private static void otherVariantNotSupport() {
        AskLoopState s = AskLoopState.start("配方怎麼做", "irons_spellbooks:scroll",
                List.of("irons_spellbooks:fireball"), now());
        s.setIntent(AskLoopState.Intent.CRAFT);
        s.setDumpLevel("OUTPUT");
        s.setJeiText("OUTPUT recipes for ice_spell and blank scroll");
        AskGrounding.Result r = AskGrounding.check("craft with ink and paper", s);
        assert !r.grounded();
        assert r.needsLookup();
        assert "jei_lookup".equals(r.lookupTool());
    }

    private static void maxOneLookup() {
        AskLoopState s = AskLoopState.start("配方怎麼做", "irons_spellbooks:scroll",
                List.of("irons_spellbooks:fireball"), now());
        s.setIntent(AskLoopState.Intent.CRAFT);
        s.setDumpLevel("OUTPUT");
        s.setJeiText("wrong variant");
        s.incGroundingLookups();
        s.noteShot0("jei_lookup", "OUTPUT", List.of("irons_spellbooks:fireball"), "");
        AskGrounding.Result r = AskGrounding.check("invented", s);
        assert !r.needsLookup();
        assert !r.grounded();
    }

    private static void variantHitGrounded() {
        AskLoopState s = AskLoopState.start("配方怎麼做", "irons_spellbooks:scroll",
                List.of("irons_spellbooks:fireball"), now());
        s.setIntent(AskLoopState.Intent.CRAFT);
        s.setDumpLevel("OUTPUT");
        s.setJeiText("recipe irons_spellbooks:fireball ink paper");
        AskGrounding.Result r = AskGrounding.check(
                "用墨水做 {{item:irons_spellbooks:scroll}} fireball", s);
        assert r.grounded() : r;
        assert !r.needsLookup();
    }

    private static void stationTemplateGrounded() {
        AskLoopState s = AskLoopState.start("配方怎麼做", "irons_spellbooks:scroll",
                List.of("irons_spellbooks:wither_skull"), now());
        s.setIntent(AskLoopState.Intent.CRAFT);
        s.setDumpLevel("OUTPUT");
        s.setJeiText("機器撰寫台：墨水 → 卷軸");
        s.setJeiStationTemplate(true);
        AskGrounding.Result r = AskGrounding.check("去撰寫台選這張法術", s);
        assert r.grounded() : r;
        assert !r.needsLookup();
    }

    private static long now() {
        return System.currentTimeMillis() + 90_000L;
    }
}
