package com.skps9.packai.logic;

/** WP4 — PlayerUnlockStatus literal checklist (mock progress). Run with -ea. */
public final class PlayerUnlockStatusCheck {
    private PlayerUnlockStatusCheck() {}

    public static void main(String[] args) {
        try {
            assert !PlayerUnlockStatus.isLiteralAdvancementId(null);
            assert !PlayerUnlockStatus.isLiteralAdvancementId("");
            assert !PlayerUnlockStatus.isLiteralAdvancementId("Getting Wood");
            assert !PlayerUnlockStatus.isLiteralAdvancementId(
                    RecipeUnlockGates.UNKNOWN_ADV_SENTINEL);
            assert PlayerUnlockStatus.isLiteralAdvancementId("mod:story/done");
            assert PlayerUnlockStatus.isLiteralAdvancementId("minecraft:story/mine_stone");

            PlayerUnlockStatus.progressOverride = id -> {
                if ("mod:story/done".equals(id)) {
                    return PlayerUnlockStatus.Progress.DONE;
                }
                if ("mod:story/locked".equals(id)) {
                    return PlayerUnlockStatus.Progress.NOT_DONE;
                }
                return PlayerUnlockStatus.Progress.UNREADABLE;
            };

            assert PlayerUnlockStatus.progressFor("mod:story/done")
                    == PlayerUnlockStatus.Progress.DONE;
            assert PlayerUnlockStatus.progressFor("mod:story/locked")
                    == PlayerUnlockStatus.Progress.NOT_DONE;
            assert PlayerUnlockStatus.progressFor("mod:story/missing")
                    == PlayerUnlockStatus.Progress.UNREADABLE;
            assert PlayerUnlockStatus.progressFor("Getting Wood")
                    == PlayerUnlockStatus.Progress.UNREADABLE;

            String done = PlayerUnlockStatus.withProgress(
                    RecipeUnlockGates.Kind.ADVANCEMENT,
                    "mod:story/done",
                    "mod:story/done",
                    "en_us");
            assert done.contains("done") || done.contains("[") : done;
            assert !done.equals("mod:story/done") : done;

            String unknown = PlayerUnlockStatus.withProgress(
                    RecipeUnlockGates.Kind.UNKNOWN,
                    RecipeUnlockGates.UNKNOWN_ADV_SENTINEL,
                    "unknown advancement gate",
                    "en_us");
            assert unknown.equals("unknown advancement gate") : unknown;
            assert !unknown.toLowerCase().contains("[done]") : unknown;
            assert !unknown.toLowerCase().contains("not done") : unknown;

            String stage = PlayerUnlockStatus.withProgress(
                    RecipeUnlockGates.Kind.STAGE, "bronze", "bronze", "en_us");
            assert stage.equals("bronze") : stage;

            String titleOnly = PlayerUnlockStatus.withProgress(
                    RecipeUnlockGates.Kind.ADVANCEMENT,
                    "Getting Wood",
                    "Getting Wood",
                    "en_us");
            assert titleOnly.equals("Getting Wood") : titleOnly;

            // formatGateLabel: UNKNOWN never gets checklist; ADVANCEMENT literal does
            PlayerUnlockStatus.progressOverride = id -> PlayerUnlockStatus.Progress.NOT_DONE;
            String lit = RecipeUnlockGates.formatGateLabel(
                    new RecipeUnlockGates.Gate(
                            RecipeUnlockGates.Kind.ADVANCEMENT, "mod:story/done"));
            assert lit.contains("mod:story/done") : lit;
            assert lit.toLowerCase().contains("not done")
                    || lit.contains("[") : lit;

            String unk = RecipeUnlockGates.formatGateLabel(
                    new RecipeUnlockGates.Gate(
                            RecipeUnlockGates.Kind.UNKNOWN,
                            RecipeUnlockGates.UNKNOWN_ADV_SENTINEL));
            assert !unk.toLowerCase().contains("[done]") : unk;
            assert !unk.toLowerCase().contains("not done") : unk;
            assert !unk.toLowerCase().contains("unable to read") : unk;

            System.out.println("PlayerUnlockStatusCheck OK");
        } finally {
            PlayerUnlockStatus.progressOverride = null;
        }
    }
}
