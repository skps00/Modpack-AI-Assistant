package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Pure path renderer: paths + lang + budget → lines. No world access.
 * Player text from lang keys via {@link ReplyLang#tr} only.
 */
public final class ObtainPathText {
    private static final int EXAMPLE_CAP = 2;
    /** Hard insert cap: never more than this many lines regardless of budget. */
    public static final int MAX_INSERT_LINES = 4;

    public record Line(String key, String text) {}

    private ObtainPathText() {}

    /**
     * Budget: {@code 0} → no lines; {@code 1} → first step only (no too_many);
     * else fill up to {@code min(budget, MAX_INSERT_LINES)}; when truncated the
     * last line is {@code packai.obtain.too_many}.
     */
    public static List<Line> render(List<ObtainPaths.Path> paths, String lang, int budget) {
        if (budget <= 0 || paths == null || paths.isEmpty()) {
            return List.of();
        }
        List<Line> full = renderAll(paths, lang);
        if (full.isEmpty()) {
            return List.of();
        }
        if (budget == 1) {
            return List.of(full.get(0));
        }
        int cap = Math.min(budget, MAX_INSERT_LINES);
        if (full.size() <= cap) {
            return List.copyOf(full);
        }
        // Truncate: (cap-1) content lines + too_many.
        List<Line> out = new ArrayList<>(cap);
        out.addAll(full.subList(0, cap - 1));
        int more = Math.max(1, remainingWays(paths, full));
        String text = ReplyLang.tr(lang, "packai.obtain.too_many", Integer.valueOf(more));
        out.add(new Line("M|too_many", text));
        return List.copyOf(out);
    }

    private static int remainingWays(List<ObtainPaths.Path> paths, List<Line> full) {
        if (paths.size() > 1) {
            return paths.size() - 1;
        }
        return Math.max(1, full.size() - 1);
    }

    private static List<Line> renderAll(List<ObtainPaths.Path> paths, String lang) {
        List<Line> lines = new ArrayList<>();
        boolean firstPath = true;
        for (ObtainPaths.Path path : paths) {
            if (path == null || path.steps() == null || path.steps().isEmpty()) {
                continue;
            }
            boolean firstStep = true;
            for (ObtainPaths.Step step : path.steps()) {
                Line line = renderStep(step, lang);
                if (line == null) {
                    continue;
                }
                if (!firstPath && firstStep) {
                    String alt = ReplyLang.tr(lang, "packai.obtain.alt", line.text());
                    lines.add(new Line(line.key(), alt));
                } else {
                    lines.add(line);
                }
                firstStep = false;
            }
            if (!path.complete()) {
                lines.add(new Line("M|partial", ReplyLang.tr(lang, "packai.obtain.partial")));
            }
            firstPath = false;
        }
        return lines;
    }

    private static Line renderStep(ObtainPaths.Step step, String lang) {
        ObtainGraph.Edge edge = step.edge();
        if (edge == null || edge.recipeId() == null) {
            return null;
        }
        String product = itemLabel(edge.output(), lang);
        String inputs = formatGroups(step.groups() != null ? step.groups() : edge.inputGroups(), lang);
        String typePath = edge.typeId() != null ? edge.typeId().getPath() : "";
        String text;
        if (isCraft(typePath)) {
            text = ReplyLang.tr(lang, "packai.obtain.step.craft", product, inputs);
        } else if (isSmelt(typePath)) {
            text = ReplyLang.tr(lang, "packai.obtain.step.smelt", product, inputs);
        } else {
            String machine = machineActionName(edge, lang);
            text = ReplyLang.tr(lang, "packai.obtain.step.machine", product, inputs, machine);
        }
        return new Line("R|" + edge.recipeId(), text);
    }

    private static String machineActionName(ObtainGraph.Edge edge, String lang) {
        String label = edge.actionLabel();
        if (label != null && !label.isBlank()) {
            return label;
        }
        return ReplyLang.tr(lang, "packai.obtain.machine_generic");
    }

    private static boolean isCraft(String typePath) {
        return "crafting".equals(typePath)
                || "crafting_shaped".equals(typePath)
                || "crafting_shapeless".equals(typePath);
    }

    private static boolean isSmelt(String typePath) {
        return "smelting".equals(typePath)
                || "blasting".equals(typePath)
                || "smoking".equals(typePath)
                || "campfire_cooking".equals(typePath);
    }

    private static String formatGroups(List<List<Item>> groups, String lang) {
        if (groups == null || groups.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(formatGroup(groups.get(i), lang));
        }
        return sb.toString();
    }

    private static String formatGroup(List<Item> group, String lang) {
        if (group == null || group.isEmpty()) {
            return "";
        }
        if (group.size() == 1) {
            return itemLabel(group.get(0), lang);
        }
        StringBuilder examples = new StringBuilder();
        int n = Math.min(EXAMPLE_CAP, group.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                examples.append(", ");
            }
            examples.append(itemLabel(group.get(i), lang));
        }
        return ReplyLang.tr(
                lang,
                "packai.obtain.any_of",
                examples.toString(),
                Integer.valueOf(group.size()));
    }

    private static String itemLabel(Item item, String lang) {
        if (item == null) {
            return ReplyLang.tr(lang, "packai.reply.unknown_item");
        }
        try {
            String hover = Plainify.stripMcFormat(new ItemStack(item).getHoverName().getString());
            if (hover != null && !hover.isBlank()) {
                return hover;
            }
        } catch (Throwable ignored) {
            // headless / missing lang
        }
        return ReplyLang.tr(lang, "packai.reply.unknown_item");
    }
}
