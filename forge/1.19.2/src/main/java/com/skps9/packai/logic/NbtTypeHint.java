package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import net.minecraft.world.item.ItemStack;

/**
 * Post-LLM guarantee: typed ingredients must match the output variant (e.g. Productive Bees).
 */
public final class NbtTypeHint {
    private static final Pattern NUMBERED_STEP_LINE = Pattern.compile("^[ \\t]*\\d+[.)][ \\t]+\\S");

    /** Harness negative control — when false, skip the \"already mentioned\" guard. */
    static boolean MENTION_GUARD_ENABLED = true;

    private NbtTypeHint() {}

    public record TypeMatchContext(List<String> ingredientNames, String outputQualifier) {
        public static TypeMatchContext empty() {
            return new TypeMatchContext(List.of(), "");
        }
    }

    /**
     * From rendered recipe cards (same rules as {@link RecipeIoSummary#appendTypeMatchFacts}).
     */
    public static TypeMatchContext contextFromCards(List<RecipeCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return TypeMatchContext.empty();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        String outQ = "";
        for (RecipeCard card : cards) {
            TypeMatchContext slice = sliceFromCard(card);
            if (slice.outputQualifier().isEmpty()) {
                continue;
            }
            if (outQ.isEmpty()) {
                outQ = slice.outputQualifier();
            }
            names.addAll(slice.ingredientNames());
        }
        if (outQ.isEmpty() || names.isEmpty()) {
            return TypeMatchContext.empty();
        }
        return new TypeMatchContext(List.copyOf(names), outQ);
    }

    static TypeMatchContext sliceFromCard(RecipeCard card) {
        if (card == null) {
            return TypeMatchContext.empty();
        }
        ItemStack output = primaryOutputStack(card);
        if (!ItemResolver.hasVariantData(output)) {
            return TypeMatchContext.empty();
        }
        String outType = NbtQualifier.qualifier(output);
        if (outType.isEmpty()) {
            return TypeMatchContext.empty();
        }
        List<String> names = new ArrayList<>();
        List<ItemStack> inputs = card.layoutInputStacks();
        if (inputs == null) {
            return TypeMatchContext.empty();
        }
        for (ItemStack st : inputs) {
            if (st == null || st.isEmpty()) {
                continue;
            }
            if (NbtQualifier.qualifier(st).isEmpty()) {
                continue;
            }
            String matName = Plainify.stripMcFormat(st.getHoverName().getString());
            if (matName == null || matName.isBlank()) {
                continue;
            }
            names.add(matName.trim());
        }
        if (names.isEmpty()) {
            return TypeMatchContext.empty();
        }
        return new TypeMatchContext(names, outType);
    }

    /**
     * Insert {@code packai.reply.nbt_type_required} when the reply omits the output type and typed inputs.
     */
    public static String ensure(
            String body,
            List<String> ingredientQualifiers,
            String outputQualifier,
            String lang
    ) {
        if (outputQualifier == null || outputQualifier.isBlank()) {
            return body == null ? "" : body;
        }
        if (ingredientQualifiers == null || ingredientQualifiers.isEmpty()) {
            return body == null ? "" : body;
        }
        List<String> ingredients = new ArrayList<>();
        for (String name : ingredientQualifiers) {
            if (name != null && !name.isBlank()) {
                ingredients.add(name.trim());
            }
        }
        if (ingredients.isEmpty()) {
            return body == null ? "" : body;
        }
        if (MENTION_GUARD_ENABLED && replyAlreadyMentions(body, ingredients, outputQualifier)) {
            return body == null ? "" : body;
        }
        String code = lang == null || lang.isBlank() ? ReplyLang.current() : lang.trim();
        String ingredient = ingredients.get(0);
        String line = ReplyLang.tr(code, "packai.reply.nbt_type_required", ingredient, outputQualifier);
        if (line == null || line.isBlank() || line.equals("packai.reply.nbt_type_required")) {
            return body == null ? "" : body;
        }
        if (body != null && body.contains(line)) {
            return body;
        }
        return insertHintLine(body, line);
    }

    static boolean replyAlreadyMentions(String body, List<String> ingredientNames, String outputQualifier) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        if (!lower.contains(outputQualifier.toLowerCase(Locale.ROOT))) {
            return false;
        }
        for (String name : ingredientNames) {
            if (name != null && !name.isBlank() && lower.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String insertHintLine(String body, String line) {
        if (body == null || body.isBlank()) {
            return line;
        }
        var m = ReplySources.HEADER.matcher(body);
        if (m.find()) {
            int at = m.start();
            String before = insertAfterFirstNumberedOrAppend(body.substring(0, at).stripTrailing(), line);
            String after = body.substring(at);
            return collapseBlankRuns(before + "\n\n" + after);
        }
        return collapseBlankRuns(insertAfterFirstNumberedOrAppend(body.stripTrailing(), line));
    }

    private static String insertAfterFirstNumberedOrAppend(String main, String line) {
        if (main == null || main.isEmpty()) {
            return line;
        }
        String[] lines = main.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (isNumberedStepLine(lines[i])) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j <= i; j++) {
                    if (j > 0) {
                        sb.append('\n');
                    }
                    sb.append(lines[j]);
                }
                sb.append('\n').append(line);
                for (int j = i + 1; j < lines.length; j++) {
                    sb.append('\n').append(lines[j]);
                }
                return collapseBlankRuns(sb.toString());
            }
        }
        return collapseBlankRuns(main + "\n" + line);
    }

    private static boolean isNumberedStepLine(String line) {
        return line != null && NUMBERED_STEP_LINE.matcher(line).find();
    }

    private static String collapseBlankRuns(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return text.replaceAll("\n{3,}", "\n\n").stripTrailing();
    }

    private static ItemStack primaryOutputStack(RecipeCard card) {
        if (card.outputs() == null) {
            return ItemStack.EMPTY;
        }
        for (ItemStack stack : card.outputs()) {
            if (stack != null && !stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
