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
     * LLM style bans Markdown {@code #}, so section facts get paraphrased away;
     * offline path already embeds the section — this mirrors that for online answers.
     * If how-to-use already mentioned hoppers/automation, omit the soft auto tip to avoid a duplicate wall.
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
        if (replyMentionsAutomation(body)) {
            section = stripTrailingAutoSuggest(section, replyLang);
            if (section.isBlank()) {
                return body;
            }
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
        // Do not treat soft auto-suggest alone as Machine — LLM may paste tip into how-to-use
        // without a Machine header; ensureVisible must still inject the header+I/O block.
        return body.contains("【機器】")
                || body.contains("【机器】")
                || body.contains("[Machine]")
                || body.contains("## Machine")
                || body.contains("## 機器")
                || body.contains("## 机器");
    }

    /** True when how-to-use already talks hoppers / pipes / automation (tip would duplicate). */
    static boolean replyMentionsAutomation(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String b = body.toLowerCase();
        return b.contains("hopper")
                || b.contains("漏斗")
                || b.contains("管道")
                || b.contains("傳送帶")
                || b.contains("传送带")
                || b.contains("automation")
                || b.contains("自動化")
                || b.contains("自动化")
                || b.contains("belt")
                || b.contains("pipe");
    }

    static String stripTrailingAutoSuggest(String section, String replyLang) {
        if (section == null || section.isBlank()) {
            return "";
        }
        String lang = replyLang == null || replyLang.isBlank() ? ReplyLang.current() : replyLang;
        String tip = ReplyLang.machineAutoSuggest(lang);
        String s = section;
        if (tip != null && !tip.isBlank() && s.contains(tip)) {
            s = s.replace(tip, "");
        }
        // Locale-agnostic: drop leftover tip-looking lines
        StringBuilder kept = new StringBuilder();
        for (String line : s.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("自動化") || t.startsWith("自动化") || t.startsWith("Automation")) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('\n');
            }
            kept.append(line);
        }
        return kept.toString().replaceAll("\n{3,}", "\n\n").trim();
    }
}
