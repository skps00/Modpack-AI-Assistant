package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import com.skps9.packai.config.PackAiConfig;

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

    private static volatile BiConsumer<String, String> unknownItemLog = (id, reason) -> {};

    private ObtainPathText() {}

    /** Client Ask: route label misses into {@link UnknownItemLog}. */
    public static void bindUnknownItemLog(Path gameDir) {
        if (gameDir == null) {
            unknownItemLog = (id, reason) -> {};
            return;
        }
        unknownItemLog = (id, reason) -> {
            if (PackAiConfig.knowledgeEnabled()) {
                UnknownItemLog.record(gameDir, id, reason);
            }
        };
    }

    /** Test / harness entry for item labels in obtain lines. */
    public static String itemLabelForItem(Item item, String lang) {
        return itemLabel(item, lang);
    }

    /** Test / harness: label with NBT type qualifier from tooltip when present. */
    public static String itemLabelForStack(ItemStack stack, String lang) {
        if (stack == null || stack.isEmpty()) {
            return ReplyLang.tr(lang, "packai.reply.unknown_item");
        }
        return itemLabelWithOptionalStack(stack.getItem(), stack, lang);
    }

    /** Harness: format inputs with parallel per-member stacks (same shape as item groups). */
    static String formatInputsForHarness(
            List<List<Item>> groups,
            List<List<ItemStack>> stackGroups,
            List<ObtainGraph.FluidReq> fluids,
            String lang) {
        return formatInputs(groups, stackGroups, fluids, lang);
    }

    /** Harness negative control: always {@code packai.reply.unknown_item}. */
    static String harnessItemLabelOrUnknown(Item item, String lang) {
        return ReplyLang.tr(lang, "packai.reply.unknown_item");
    }

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
        Set<String> seenFirstStepTexts = new HashSet<>();
        boolean firstPath = true;
        for (ObtainPaths.Path path : paths) {
            if (path == null || path.steps() == null || path.steps().isEmpty()) {
                continue;
            }
            Line firstRenderable = null;
            for (ObtainPaths.Step step : path.steps()) {
                firstRenderable = renderStep(step, lang);
                if (firstRenderable != null) {
                    break;
                }
            }
            if (firstRenderable == null) {
                continue;
            }
            String firstText = firstRenderable.text() == null ? "" : firstRenderable.text().trim();
            // Skip alt whose step text equals primary (or a prior alt) after trim.
            if (!firstPath && !firstText.isEmpty() && seenFirstStepTexts.contains(firstText)) {
                continue;
            }
            if (!firstText.isEmpty()) {
                seenFirstStepTexts.add(firstText);
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
        String inputs = formatInputs(
                step.groups() != null ? step.groups() : edge.inputGroups(),
                null,
                edge.fluids(),
                lang);
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
        text = appendRequirementLines(text, edge.requirements(), lang);
        text = appendFluidSourceLines(text, step, edge, lang);
        return new Line("R|" + edge.recipeId(), text);
    }

    private static String appendRequirementLines(String mainLine, List<ObtainGraph.Requirement> reqs, String lang) {
        if (reqs == null || reqs.isEmpty()) {
            return mainLine;
        }
        StringBuilder sb = new StringBuilder(mainLine);
        for (ObtainGraph.Requirement req : reqs) {
            if (req == null || req.amount() <= 0) {
                continue;
            }
            String line;
            if ("energy".equals(req.kind())) {
                line = ReplyLang.tr(lang, "packai.obtain.req.energy", Integer.valueOf(req.amount()));
            } else {
                String id = req.id() == null || req.id().isBlank() ? req.kind() : req.id();
                line = ReplyLang.tr(
                        lang,
                        "packai.obtain.req.extra",
                        Integer.valueOf(req.amount()),
                        id,
                        req.kind() == null ? "" : req.kind());
            }
            if (line != null && !line.isEmpty()) {
                sb.append('\n').append(line);
            }
        }
        return sb.toString();
    }

    private static String appendFluidSourceLines(
            String mainLine, ObtainPaths.Step step, ObtainGraph.Edge edge, String lang) {
        if (edge.fluids() == null || edge.fluids().isEmpty()) {
            return mainLine;
        }
        List<ObtainGraph.FluidEdge> sources =
                step.fluidSources() != null ? step.fluidSources() : List.of();
        StringBuilder sb = new StringBuilder(mainLine);
        for (ObtainGraph.FluidReq req : edge.fluids()) {
            if (req == null || req.fluidId() == null || req.fluidId().isBlank()) {
                continue;
            }
            if (ObtainGraph.FluidSources.isNaturalFluid(req.fluidId())) {
                continue;
            }
            ObtainGraph.FluidEdge src = findFluidSource(sources, req.fluidId());
            if (src != null) {
                String machine = fluidEdgeMachineName(src, lang);
                String inputs = formatInputs(src.itemGroups(), null, src.fluidInputs(), lang);
                sb.append('\n').append(ReplyLang.tr(lang, "packai.obtain.fluid_source", machine, inputs));
            } else {
                sb.append('\n').append(ReplyLang.tr(lang, "packai.obtain.fluid_source_missing"));
            }
        }
        return sb.toString();
    }

    private static ObtainGraph.FluidEdge findFluidSource(List<ObtainGraph.FluidEdge> sources, String fluidId) {
        if (sources == null || fluidId == null) {
            return null;
        }
        for (ObtainGraph.FluidEdge fe : sources) {
            if (fe != null && fluidId.equals(fe.fluidId())) {
                return fe;
            }
        }
        return null;
    }

    private static String fluidEdgeMachineName(ObtainGraph.FluidEdge fe, String lang) {
        String label = MachineLabels.resolve(fe.actionLabel(), fe.typeId(), lang);
        return label != null ? label : "";
    }

    private static String machineActionName(ObtainGraph.Edge edge, String lang) {
        String label = MachineLabels.resolve(edge.actionLabel(), edge.typeId(), lang);
        return label != null ? label : "";
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

    private static String formatInputs(
            List<List<Item>> groups,
            List<List<ItemStack>> stackGroups,
            List<ObtainGraph.FluidReq> fluids,
            String lang) {
        String items = formatGroups(groups, stackGroups, lang);
        String fluidText = formatFluids(fluids, lang);
        boolean hasItems = items != null && !items.isEmpty();
        boolean hasFluids = fluidText != null && !fluidText.isEmpty();
        if (hasItems && hasFluids) {
            return items + " + " + fluidText;
        }
        if (hasItems) {
            return items;
        }
        return hasFluids ? fluidText : "";
    }

    private static String formatFluids(List<ObtainGraph.FluidReq> fluids, String lang) {
        if (fluids == null || fluids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ObtainGraph.FluidReq req : fluids) {
            if (req == null) {
                continue;
            }
            String part = FluidNames.amountText(req.fluidId(), req.amountMb(), lang);
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private static String formatGroups(
            List<List<Item>> groups, List<List<ItemStack>> stackGroups, String lang) {
        if (groups == null || groups.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            List<ItemStack> stacks = stackGroups != null && i < stackGroups.size()
                    ? stackGroups.get(i)
                    : null;
            sb.append(formatGroup(groups.get(i), stacks, lang));
        }
        return sb.toString();
    }

    private static String formatGroup(List<Item> group, List<ItemStack> stacks, String lang) {
        if (group == null || group.isEmpty()) {
            return "";
        }
        if (group.size() == 1) {
            return itemLabelWithOptionalStack(group.get(0), memberStack(stacks, 0), lang);
        }
        StringBuilder examples = new StringBuilder();
        int n = Math.min(EXAMPLE_CAP, group.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                examples.append(", ");
            }
            examples.append(itemLabelWithOptionalStack(group.get(i), memberStack(stacks, i), lang));
        }
        return ReplyLang.tr(
                lang,
                "packai.obtain.any_of",
                examples.toString(),
                Integer.valueOf(group.size()));
    }

    private static ItemStack memberStack(List<ItemStack> stacks, int index) {
        if (stacks == null || index < 0 || index >= stacks.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack st = stacks.get(index);
        return st == null ? ItemStack.EMPTY : st;
    }

    private static String itemLabelWithOptionalStack(Item item, ItemStack stack, String lang) {
        String base = itemLabel(item, lang);
        if (stack == null || stack.isEmpty()) {
            return base;
        }
        String qualifier = NbtQualifier.qualifier(stack);
        if (qualifier.isEmpty()) {
            return base;
        }
        return ReplyLang.tr(lang, "packai.reply.nbt_qualifier", base, qualifier);
    }

    private static String itemLabel(Item item, String lang) {
        if (item == null) {
            return ReplyLang.tr(lang, "packai.reply.unknown_item");
        }
        try {
            String id = ObtainGraph.itemKey(item);
            String name = OfficialDisplay.officialName(id);
            if (name != null && !name.isBlank() && !OfficialDisplay.isTranslationKeyShaped(name)) {
                return Plainify.stripMcFormat(name);
            }
            String readable = readablePathLabel(id);
            if (readable != null && !readable.isBlank()) {
                unknownItemLog.accept(id, "label_miss");
                return readable;
            }
        } catch (Throwable ignored) {
            // headless / missing lang
        }
        return ReplyLang.tr(lang, "packai.reply.unknown_item");
    }

    static String readablePathLabel(String registryId) {
        if (registryId == null || registryId.isBlank()) {
            return "";
        }
        String leaf = ReplyLang.idToLabel(registryId);
        if (leaf == null || leaf.isBlank() || leaf.contains(":")) {
            return "";
        }
        if (leaf.equals(registryId.trim())) {
            return "";
        }
        return Character.toUpperCase(leaf.charAt(0)) + leaf.substring(1);
    }
}
