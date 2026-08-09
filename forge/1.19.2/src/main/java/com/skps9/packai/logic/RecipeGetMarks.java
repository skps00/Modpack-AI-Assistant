package com.skps9.packai.logic;

/** Markers AskService / PackKnowledge prefix onto recipe-get text for AskEngine. */
public final class RecipeGetMarks {
    public static final String EMI_PREVIEW = "[[packai.emi_preview]]\n";
    public static final String NO_RECIPE_UI = "[[packai.no_recipe_ui]]\n";
    /** Separates JEI get-summary from PackKnowledge Machine section in the same payload. */
    public static final String MACHINE_MARK = "[[packai.machine]]\n";

    private RecipeGetMarks() {}

    public static String strip(String recipeGetText) {
        if (recipeGetText == null || recipeGetText.isBlank()) {
            return recipeGetText;
        }
        if (recipeGetText.startsWith(EMI_PREVIEW)) {
            return recipeGetText.substring(EMI_PREVIEW.length());
        }
        if (recipeGetText.startsWith(NO_RECIPE_UI)) {
            return recipeGetText.substring(NO_RECIPE_UI.length());
        }
        return recipeGetText;
    }

    public static boolean isEmiPreview(String recipeGetText) {
        return recipeGetText != null && recipeGetText.startsWith(EMI_PREVIEW);
    }

    public static boolean isNoRecipeUi(String recipeGetText) {
        return recipeGetText != null && recipeGetText.startsWith(NO_RECIPE_UI);
    }

    /** Body after {@link #MACHINE_MARK}, or empty. */
    public static String extractMachine(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        int i = payload.indexOf(MACHINE_MARK);
        if (i < 0) {
            return "";
        }
        return payload.substring(i + MACHINE_MARK.length()).trim();
    }

    /** JEI / gap text before {@link #MACHINE_MARK} (mark + machine stripped). */
    public static String stripMachine(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        int i = payload.indexOf(MACHINE_MARK);
        if (i < 0) {
            return payload;
        }
        return payload.substring(0, i).trim();
    }

    /**
     * Force Machine section into the player-visible reply (post-LLM).
     * LLM style bans Markdown {@code #}, so {@code ## Machine} / {@code ## 機器} facts get paraphrased away;
     * offline path already embeds the section — this mirrors that for online answers.
     */
    public static String ensureVisibleInReply(String body, String machineSection, String replyLang) {
        if (machineSection == null || machineSection.isBlank()) {
            return body == null ? "" : body;
        }
        String section = machineSection.trim();
        if (body == null || body.isBlank()) {
            return section;
        }
        if (replyAlreadyHasMachine(body, replyLang, section)) {
            return body;
        }
        var m = ReplySources.HEADER.matcher(body);
        if (m.find()) {
            int at = m.start();
            String before = body.substring(0, at).stripTrailing();
            String after = body.substring(at);
            return before + "\n\n" + section + "\n\n" + after;
        }
        return body.stripTrailing() + "\n\n" + section;
    }

    private static boolean replyAlreadyHasMachine(String body, String replyLang, String section) {
        if (body.contains(section)) {
            return true;
        }
        String lang = replyLang == null || replyLang.isBlank() ? ReplyLang.current() : replyLang;
        String title = ReplyLang.sectionMachine(lang);
        if (title != null && !title.isBlank() && body.contains(title)) {
            return true;
        }
        String suggest = ReplyLang.machineAutoSuggest(lang);
        if (suggest != null && !suggest.isBlank() && body.contains(suggest)) {
            return true;
        }
        // Any locale header (LLM may answer in mixed lang)
        return body.contains("## Machine")
                || body.contains("## 機器")
                || body.contains("## 机器");
    }
}
