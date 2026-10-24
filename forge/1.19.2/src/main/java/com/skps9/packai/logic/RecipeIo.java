package com.skps9.packai.logic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Reflection-based recipe I/O extraction (fail-soft). No client dependencies.
 */
public final class RecipeIo {
    private RecipeIo() {}

    public record ItemOut(ItemStack stack, String accessor) {}

    public record FluidOut(String fluidId, int amountMb, String accessor) {}

    /** One recipe input slot: distinct items (OR) with stack count for that slot. */
    public record Group(List<Item> items, int count) {}

    /** Empty {@code stackCounts} → 1; else max stack count in the slot (not summed). */
    public static int pickCount(int[] stackCounts) {
        if (stackCounts == null || stackCounts.length == 0) {
            return 1;
        }
        int max = stackCounts[0];
        for (int i = 1; i < stackCounts.length; i++) {
            max = Math.max(max, stackCounts[i]);
        }
        // A slot always consumes at least one item: counts <= 0 mean "unknown", never zero.
        return Math.max(1, max);
    }

    public static int countOrOne(int raw) {
        return raw <= 0 ? 1 : raw;
    }

    /** Per-class winning accessor names; empty arrays mean skip all reflection for that side. */
    private record ClassPlan(
            String[] outputs,
            String[] inputs,
            String[] fluidInputs,
            String[] fluidOutputs,
            String[] unrepresentable) {}

    private static final String[] EMPTY_PLAN = new String[0];

    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Method>> METHOD_CACHE =
            new ConcurrentHashMap<>();

    /** Per-class missing no-arg method names → single reused NoSuchMethodException instance. */
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, NoSuchMethodException>>
            MISSING_METHOD_CACHE = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Class<?>, ClassPlan> WINNING_ACCESSORS =
            new ConcurrentHashMap<>();

    /** Per-class census accessor names (existence probe once per class). */
    private static final ConcurrentHashMap<Class<?>, String[]> CENSUS_ACCESSORS =
            new ConcurrentHashMap<>();

    private static volatile String lastFluidAccessor = "";
    private static volatile boolean lastSkippedInputSlots;

    private static final String[] OUTPUT_ACCESSORS = {
            "getResultItem",
            "getRollableResults",
            "getOutputItems",
            "getOutputDefinition",
            "getResults",
            "getOutput",
    };

    private static final String[] INPUT_ACCESSORS = {
            "getIngredients",
            "getInputItems",
            "getInputs",
            "getRecipeInputs",
            "getInput",
            "getMainInput",
            "getExtraInput",
            "getItemInput",
    };

    private static final String[] INPUT_FIELD_NAMES = {
            "input",
            "inputs",
            "itemInput",
            "itemInputs",
            "ingredients",
            "inputList",
            "secondaryInput",
    };

    private static final String[] FLUID_INPUT_ACCESSORS = {
            "getFluidIngredients",
            "getInputFluids",
            "getFluidInput",
            "getFluids",
    };

    private static final String[] FLUID_INPUT_FIELD_NAMES = {
            "fluid",
            "fluids",
            "fluidInput",
    };

    private static final String[] FLUID_OUTPUT_ACCESSORS = {
            "getFluidResults",
            "getOutputFluids",
    };

    private static final String[] UNREPRESENTABLE_ACCESSORS = {
            "getChemicalInput",
            "getChemicalInputs",
            "getChemicalStackInput",
    };

    private record RequirementAccessor(String method, String kind) {}

    private static final RequirementAccessor[] REQUIREMENT_ACCESSORS = {
            new RequirementAccessor("getEnergy", "energy"),
            new RequirementAccessor("getEnergyRequired", "energy"),
            new RequirementAccessor("getEnergyPerTick", "energy"),
            new RequirementAccessor("getTotalEnergy", "energy"),
            new RequirementAccessor("getRfCost", "energy"),
            new RequirementAccessor("getChemicalInput", "chemical"),
            new RequirementAccessor("getChemicalInputs", "chemical"),
            new RequirementAccessor("getChemicalStackInput", "chemical"),
            new RequirementAccessor("getInputChemical", "chemical"),
            new RequirementAccessor("getGasInput", "gas"),
            new RequirementAccessor("getGasInputs", "gas"),
            new RequirementAccessor("getFluidChemicalInput", "chemical"),
            new RequirementAccessor("getSlurryInput", "slurry"),
            new RequirementAccessor("getPigmentInput", "pigment"),
            new RequirementAccessor("getTemperature", "temperature"),
            new RequirementAccessor("getHeat", "temperature"),
            new RequirementAccessor("getHeatRequired", "temperature"),
            new RequirementAccessor("getMana", "other"),
            new RequirementAccessor("getManaCost", "other"),
            new RequirementAccessor("getSource", "other"),
            new RequirementAccessor("getStarlight", "other"),
            new RequirementAccessor("getAura", "other"),
            new RequirementAccessor("getEmber", "other"),
            new RequirementAccessor("getStress", "other"),
            new RequirementAccessor("getStressImpact", "other"),
            new RequirementAccessor("getPressure", "other"),
    };

    /** Harness-only: increments on each {@link #censusRequirements} call. */
    static volatile int censusHarnessCalls;

    /** Harness-only: bench profiling (single-threaded {@code benchBuild}). */
    static long statGetItemsNanos;
    static long statGetItemsCalls;
    static long statReflectNanos;
    static long statRequirementsNanos;

    static void resetStats() {
        statGetItemsNanos = 0L;
        statGetItemsCalls = 0L;
        statReflectNanos = 0L;
        statRequirementsNanos = 0L;
    }

    static String statsLine() {
        return "getItems_ns="
                + statGetItemsNanos
                + " calls="
                + statGetItemsCalls
                + " reflect_ns="
                + statReflectNanos
                + " req_ns="
                + statRequirementsNanos;
    }

    private static final String[] CENSUS_ACCESSOR_CANDIDATES = {
            "getChemicalInput",
            "getChemicalInputs",
            "getChemicalStackInput",
            "getInputChemical",
            "getGasInput",
            "getGasInputs",
            "getFluidChemicalInput",
            "getEnergy",
            "getEnergyRequired",
            "getEnergyPerTick",
            "getTotalEnergy",
            "getMana",
            "getManaCost",
            "getSource",
            "getStarlight",
            "getAura",
            "getEmber",
            "getStress",
            "getStressImpact",
            "getHeat",
            "getHeatRequired",
            "getTemperature",
            "getPressure",
            "getRfCost",
    };

    public static String lastFluidAccessor() {
        return lastFluidAccessor;
    }

    /** Package-private: set after {@link #inputGroups} when an ingredient slot was empty or unresolved. */
    static boolean skippedInputSlots() {
        return lastSkippedInputSlots;
    }

    public static List<ItemOut> outputs(Recipe<?> recipe) {
        if (recipe == null) {
            return List.of();
        }
        ClassPlan plan = planFor(recipe);
        if (plan.outputs.length == 0) {
            return List.of();
        }
        List<ItemOut> out = new ArrayList<>();
        for (String accessor : plan.outputs) {
            appendOutputs(recipe, accessor, out);
            if (!out.isEmpty()) {
                return List.copyOf(out);
            }
        }
        return List.copyOf(out);
    }

    public static List<List<Item>> inputGroups(Recipe<?> recipe) {
        List<Group> withCounts = inputGroupsWithCounts(recipe);
        if (withCounts.isEmpty()) {
            return List.of();
        }
        List<List<Item>> out = new ArrayList<>(withCounts.size());
        for (Group g : withCounts) {
            out.add(g.items());
        }
        return List.copyOf(out);
    }

    public static List<Group> inputGroupsWithCounts(Recipe<?> recipe) {
        if (recipe == null) {
            return List.of();
        }
        lastSkippedInputSlots = false;
        ClassPlan plan = planFor(recipe);
        if (plan.inputs.length == 0) {
            return List.of();
        }
        for (String accessor : plan.inputs) {
            List<Group> groups = tryInputAccessor(recipe, accessor);
            if (!groups.isEmpty()) {
                return List.copyOf(groups);
            }
        }
        return List.of();
    }

    public static List<FluidOut> fluidInputs(Recipe<?> recipe) {
        if (recipe == null) {
            return List.of();
        }
        return fluidSideFromPlan(recipe, planFor(recipe).fluidInputs);
    }

    public static List<FluidOut> fluidOutputs(Recipe<?> recipe) {
        if (recipe == null) {
            return List.of();
        }
        return fluidSideFromPlan(recipe, planFor(recipe).fluidOutputs);
    }

    public static boolean hasUnrepresentableInputs(Recipe<?> recipe) {
        if (recipe == null) {
            return false;
        }
        String[] accessors = planFor(recipe).unrepresentable;
        if (accessors.length == 0) {
            return false;
        }
        for (String accessor : accessors) {
            if (unrepresentableAccessorHit(recipe, accessor)) {
                return true;
            }
        }
        return false;
    }

    public static List<ObtainGraph.Requirement> requirements(Recipe<?> recipe) {
        if (recipe == null) {
            return List.of();
        }
        long t0 = System.nanoTime();
        try {
            List<ObtainGraph.Requirement> out = new ArrayList<>();
            for (RequirementAccessor spec : REQUIREMENT_ACCESSORS) {
                try {
                    Object value = invokeNoArg(recipe, spec.method());
                    appendRequirementsFromValue(out, spec.kind(), value);
                } catch (Throwable ignored) {
                    // fail-soft per accessor
                }
            }
            return List.copyOf(out);
        } finally {
            statRequirementsNanos += System.nanoTime() - t0;
        }
    }

    public static void censusRequirements(
            Recipe<?> recipe,
            ConcurrentHashMap<String, Integer> byAccessor,
            ConcurrentHashMap<String, Integer> byType) {
        censusHarnessCalls++;
        if (recipe == null || byAccessor == null || byType == null) {
            return;
        }
        Class<?> clazz = recipe.getClass();
        String[] plan = CENSUS_ACCESSORS.get(clazz);
        if (plan == null) {
            plan = CENSUS_ACCESSORS.computeIfAbsent(clazz, k -> discoverCensusAccessors(recipe));
        }
        if (plan.length == 0) {
            return;
        }
        for (String accessor : plan) {
            try {
                Object value = invokeNoArg(recipe, accessor);
                if (!censusValuePresent(value) || censusSkipValue(value)) {
                    continue;
                }
                byAccessor.merge(accessor, 1, Integer::sum);
                byType.merge(value.getClass().getSimpleName(), 1, Integer::sum);
            } catch (Throwable ignored) {
                // fail-soft per accessor
            }
        }
    }

    private static String[] discoverCensusAccessors(Recipe<?> recipe) {
        List<String> found = new ArrayList<>();
        for (String accessor : CENSUS_ACCESSOR_CANDIDATES) {
            try {
                invokeNoArg(recipe, accessor);
                found.add(accessor);
            } catch (Throwable ignored) {
                // accessor absent or unusable on this class
            }
        }
        return found.isEmpty() ? EMPTY_PLAN : found.toArray(new String[0]);
    }

    private static boolean censusSkipValue(Object value) {
        return value instanceof ItemStack
                || value instanceof FluidStack
                || value instanceof Ingredient;
    }

    private static void appendRequirementsFromValue(
            List<ObtainGraph.Requirement> out, String kind, Object value) {
        if (value == null || censusSkipValue(value)) {
            return;
        }
        if (value instanceof Number n) {
            int amount = (int) Math.round(n.doubleValue());
            if (amount == 0) {
                return;
            }
            String unit = "energy".equals(kind) ? "FE" : "";
            out.add(new ObtainGraph.Requirement(kind, "", amount, unit));
            return;
        }
        if (value instanceof Collection<?> c) {
            for (Object el : c) {
                appendRequirementsFromValue(out, kind, el);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) {
                appendRequirementsFromValue(out, kind, java.lang.reflect.Array.get(value, i));
            }
            return;
        }
        ObtainGraph.Requirement one = requirementFromObject(kind, value);
        if (one != null) {
            out.add(one);
        }
    }

    private static ObtainGraph.Requirement requirementFromObject(String kind, Object value) {
        if (value == null) {
            return null;
        }
        int amount = requirementAmount(value);
        if (amount <= 0 && !"energy".equals(kind)) {
            // non-numeric chemical/gas stacks still count when present
            amount = Math.max(amount, 1);
        }
        if (amount == 0) {
            return null;
        }
        String id = requirementId(value);
        String unit = "energy".equals(kind) ? "FE" : "";
        return new ObtainGraph.Requirement(kind, id == null ? "" : id, amount, unit);
    }

    private static int requirementAmount(Object value) {
        for (String m : new String[] {
                "getAmount", "getRequiredAmount", "getNeeded", "getCount", "amount"
        }) {
            try {
                Object r = invokeNoArg(value, m);
                if (r instanceof Number n) {
                    int a = (int) Math.round(n.doubleValue());
                    if (a != 0) {
                        return a;
                    }
                }
            } catch (Throwable ignored) {
                // try next
            }
        }
        if (value instanceof Number n) {
            return (int) Math.round(n.doubleValue());
        }
        return 0;
    }

    private static String requirementId(Object value) {
        for (String m : new String[] {
                "getType", "getChemical", "getGas", "getSlurry", "getPigment", "getStack", "getIngredient"
        }) {
            try {
                Object r = invokeNoArg(value, m);
                String id = resourceIdFromObject(r);
                if (id != null && !id.isEmpty()) {
                    return id;
                }
            } catch (Throwable ignored) {
                // try next
            }
        }
        return resourceIdFromObject(value);
    }

    private static String resourceIdFromObject(Object obj) {
        if (obj == null) {
            return "";
        }
        if (obj instanceof String s) {
            return s;
        }
        if (obj instanceof ResourceLocation rl) {
            return rl.toString();
        }
        try {
            Object rl = invokeNoArg(obj, "getRegistryName");
            if (rl instanceof ResourceLocation r) {
                return r.toString();
            }
        } catch (Throwable ignored) {
            // no registry name
        }
        try {
            Object rl = invokeNoArg(obj, "getId");
            if (rl instanceof ResourceLocation r) {
                return r.toString();
            }
        } catch (Throwable ignored) {
            // no id
        }
        String simple = obj.getClass().getSimpleName();
        return simple == null || simple.isEmpty() ? "resource" : simple;
    }

    private static boolean censusValuePresent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0.0;
        }
        if (value instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) > 0;
        }
        return true;
    }

    private static ClassPlan planFor(Recipe<?> recipe) {
        Class<?> clazz = recipe.getClass();
        ClassPlan cached = WINNING_ACCESSORS.get(clazz);
        if (cached != null) {
            return cached;
        }
        return WINNING_ACCESSORS.computeIfAbsent(clazz, k -> discoverPlan(recipe));
    }

    private static ClassPlan discoverPlan(Recipe<?> recipe) {
        return new ClassPlan(
                discoverOutputAccessors(recipe),
                discoverInputAccessors(recipe),
                discoverFluidInputAccessors(recipe),
                discoverFluidAccessors(recipe, FLUID_OUTPUT_ACCESSORS),
                discoverUnrepresentableAccessors(recipe));
    }

    private static String[] discoverUnrepresentableAccessors(Recipe<?> recipe) {
        List<String> found = new ArrayList<>();
        for (String accessor : UNREPRESENTABLE_ACCESSORS) {
            if (unrepresentableAccessorHit(recipe, accessor)) {
                found.add(accessor);
            }
        }
        return found.isEmpty() ? EMPTY_PLAN : found.toArray(new String[0]);
    }

    private static boolean unrepresentableAccessorHit(Recipe<?> recipe, String accessor) {
        if ("getEnergy".equals(accessor)) {
            try {
                Object energy = invokeNoArg(recipe, accessor);
                return energy instanceof Number n && n.doubleValue() != 0.0;
            } catch (Throwable ignored) {
                return false;
            }
        }
        try {
            Object obj = invokeNoArg(recipe, accessor);
            if (obj == null) {
                return false;
            }
            if (obj instanceof List<?> list) {
                return !list.isEmpty();
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String[] discoverOutputAccessors(Recipe<?> recipe) {
        for (String accessor : OUTPUT_ACCESSORS) {
            List<ItemOut> trial = new ArrayList<>();
            appendOutputs(recipe, accessor, trial);
            if (!trial.isEmpty()) {
                return new String[] {accessor};
            }
        }
        return EMPTY_PLAN;
    }

    private static String[] discoverInputAccessors(Recipe<?> recipe) {
        for (String accessor : INPUT_ACCESSORS) {
            List<Group> groups = tryInputAccessor(recipe, accessor);
            if (!groups.isEmpty()) {
                return new String[] {accessor};
            }
        }
        for (String fieldName : INPUT_FIELD_NAMES) {
            List<Group> groups = tryInputFromField(recipe, fieldName);
            if (!groups.isEmpty()) {
                return new String[] {"field:" + fieldName};
            }
        }
        return EMPTY_PLAN;
    }

    private static String[] discoverFluidInputAccessors(Recipe<?> recipe) {
        for (String accessor : FLUID_INPUT_ACCESSORS) {
            List<FluidOut> trial = new ArrayList<>();
            if (appendFluid(recipe, accessor, trial) && !trial.isEmpty()) {
                return new String[] {accessor};
            }
        }
        for (String fieldName : FLUID_INPUT_FIELD_NAMES) {
            List<FluidOut> trial = new ArrayList<>();
            String accessor = "field:" + fieldName;
            if (appendFluid(recipe, accessor, trial) && !trial.isEmpty()) {
                return new String[] {accessor};
            }
        }
        return EMPTY_PLAN;
    }

    private static String[] discoverFluidAccessors(Recipe<?> recipe, String[] candidates) {
        for (String accessor : candidates) {
            List<FluidOut> trial = new ArrayList<>();
            if (appendFluid(recipe, accessor, trial) && !trial.isEmpty()) {
                return new String[] {accessor};
            }
        }
        return EMPTY_PLAN;
    }

    private static void appendOutputs(Recipe<?> recipe, String accessor, List<ItemOut> out) {
        try {
            if ("getResultItem".equals(accessor)) {
                ItemStack stack = recipe.getResultItem();
                collectItemStack(out, stack, accessor);
                return;
            }
            if ("getRollableResults".equals(accessor)) {
                Object listObj = invokeNoArg(recipe, accessor);
                if (listObj instanceof List<?> list) {
                    for (Object el : list) {
                        if (el == null) {
                            continue;
                        }
                        ItemStack stack = unwrapItemStack(el);
                        collectItemStack(out, stack, accessor);
                    }
                }
                return;
            }
            Object obj = invokeNoArg(recipe, accessor);
            collectItemOutputObject(out, obj, accessor);
        } catch (Throwable ignored) {
            // fail-soft per accessor
        }
    }

    private static List<Group> tryInputAccessor(Recipe<?> recipe, String accessor) {
        if (accessor.startsWith("field:")) {
            return tryInputFromField(recipe, accessor.substring("field:".length()));
        }
        List<Group> groups = new ArrayList<>();
        try {
            if ("getIngredients".equals(accessor)) {
                var ings = recipe.getIngredients();
                if (ings != null) {
                    for (Ingredient ing : ings) {
                        addInputGroupFromIngredient(groups, ing, accessor);
                    }
                }
            } else {
                Object obj = invokeNoArg(recipe, accessor);
                addInputGroupsFromObject(groups, obj, accessor);
            }
        } catch (Throwable ignored) {
            // fail-soft per accessor
        }
        return groups;
    }

    private static List<Group> tryInputFromField(Recipe<?> recipe, String fieldName) {
        long t0 = System.nanoTime();
        try {
            List<Group> groups = new ArrayList<>();
            String accessor = "field:" + fieldName;
            try {
                Field f = recipe.getClass().getField(fieldName);
                if (Modifier.isStatic(f.getModifiers())) {
                    return groups;
                }
                Object obj = f.get(recipe);
                addInputGroupsFromObject(groups, obj, accessor);
            } catch (Throwable ignored) {
                // fail-soft per field
            }
            return groups;
        } finally {
            statReflectNanos += System.nanoTime() - t0;
        }
    }

    private static List<FluidOut> fluidSideFromPlan(Recipe<?> recipe, String[] accessors) {
        if (accessors.length == 0) {
            return List.of();
        }
        List<FluidOut> out = new ArrayList<>();
        for (String accessor : accessors) {
            appendFluid(recipe, accessor, out);
            if (!out.isEmpty()) {
                return List.copyOf(out);
            }
        }
        return List.copyOf(out);
    }

    /** @return true when reflection ran without throwable (out may still be empty) */
    private static boolean appendFluid(Recipe<?> recipe, String accessor, List<FluidOut> out) {
        try {
            Object obj;
            if (accessor.startsWith("field:")) {
                Field f = recipe.getClass().getField(accessor.substring("field:".length()));
                if (Modifier.isStatic(f.getModifiers())) {
                    return false;
                }
                obj = f.get(recipe);
            } else {
                obj = invokeNoArg(recipe, accessor);
            }
            if (obj == null) {
                return false;
            }
            lastFluidAccessor = accessor;
            boolean unresolved = collectFluidsFromObject(out, obj, accessor);
            if (unresolved && out.isEmpty()) {
                lastFluidAccessor = accessor + ":unresolved";
            }
            return true;
        } catch (Throwable ignored) {
            // fail-soft per accessor
            return false;
        }
    }

    /** @return true when a fluid-ingredient-like value could not be expanded */
    private static boolean collectFluidsFromObject(List<FluidOut> out, Object obj, String accessor) {
        if (obj instanceof FluidStack fs) {
            addFluidStack(out, fs, accessor);
            return false;
        }
        if (obj instanceof FluidStack[] arr) {
            for (FluidStack fs : arr) {
                addFluidStack(out, fs, accessor);
            }
            return false;
        }
        if (obj instanceof List<?> list) {
            boolean unresolved = false;
            for (Object el : list) {
                if (el == null) {
                    continue;
                }
                if (el instanceof FluidStack fs) {
                    addFluidStack(out, fs, accessor);
                } else if (collectFluidFromElement(out, el, accessor)) {
                    unresolved = true;
                }
            }
            return unresolved;
        }
        if (obj instanceof Object[] arr) {
            boolean unresolved = false;
            for (Object el : arr) {
                if (el == null) {
                    continue;
                }
                if (el instanceof FluidStack fs) {
                    addFluidStack(out, fs, accessor);
                } else if (collectFluidFromElement(out, el, accessor)) {
                    unresolved = true;
                }
            }
            return unresolved;
        }
        return collectFluidFromElement(out, obj, accessor);
    }

    private static void collectItemOutputObject(List<ItemOut> out, Object obj, String accessor) {
        if (obj == null) {
            return;
        }
        if (obj instanceof ItemStack stack) {
            collectItemStack(out, stack, accessor);
            return;
        }
        if (obj instanceof ItemStack[] arr) {
            for (ItemStack stack : arr) {
                collectItemStack(out, stack, accessor);
            }
            return;
        }
        if (obj instanceof Iterable<?> it) {
            for (Object el : it) {
                collectItemOutputObject(out, el, accessor);
            }
            return;
        }
        if (obj instanceof List<?> list) {
            for (Object el : list) {
                collectItemOutputObject(out, el, accessor);
            }
            return;
        }
        ItemStack wrapped = unwrapItemStack(obj);
        collectItemStack(out, wrapped, accessor);
    }

    private static void collectItemStack(List<ItemOut> out, ItemStack stack, String accessor) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        if (item == null || item == Items.AIR || item == Items.BARRIER) {
            return;
        }
        out.add(new ItemOut(stack, accessor));
    }

    private static ItemStack unwrapItemStack(Object obj) {
        if (obj == null) {
            return ItemStack.EMPTY;
        }
        if (obj instanceof ItemStack stack) {
            return stack;
        }
        for (String m : new String[] {"getStack", "getOutput", "getResult"}) {
            try {
                Object r = invokeNoArg(obj, m);
                if (r instanceof ItemStack stack) {
                    return stack;
                }
            } catch (Throwable ignored) {
                // try next
            }
        }
        return ItemStack.EMPTY;
    }

    private static void addInputGroupFromIngredient(List<Group> groups, Ingredient ing, String accessor) {
        if (ing == null || ing.isEmpty()) {
            lastSkippedInputSlots = true;
            return;
        }
        long t0 = System.nanoTime();
        ItemStack[] stacks = ing.getItems();
        statGetItemsNanos += System.nanoTime() - t0;
        statGetItemsCalls++;
        LinkedHashSet<Item> distinct = new LinkedHashSet<>();
        int[] counts = new int[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            ItemStack stack = stacks[i];
            counts[i] = stack == null ? 0 : stack.getCount();
            distinct.addAll(itemsFromStack(stack));
        }
        addDistinctGroup(groups, distinct, pickCount(counts), accessor);
    }

    private static void addInputGroupsFromObject(List<Group> groups, Object obj, String accessor) {
        if (obj == null) {
            return;
        }
        if (obj instanceof Ingredient ing) {
            addInputGroupFromIngredient(groups, ing, accessor);
            return;
        }
        if (obj instanceof ItemStack stack) {
            LinkedHashSet<Item> one = itemsFromStack(stack);
            addDistinctGroup(groups, one, pickCount(new int[] {stack.getCount()}), accessor);
            return;
        }
        if (obj instanceof ItemStack[] arr) {
            for (ItemStack stack : arr) {
                if (stack == null) {
                    continue;
                }
                addDistinctGroup(
                        groups, itemsFromStack(stack), pickCount(new int[] {stack.getCount()}), accessor);
            }
            return;
        }
        if (obj instanceof Iterable<?> it) {
            for (Object el : it) {
                addInputGroupsFromObject(groups, el, accessor);
            }
            return;
        }
        if (obj instanceof List<?> list) {
            if ("getIngredients".equals(accessor) || list.stream().anyMatch(e -> e instanceof Ingredient)) {
                for (Object el : list) {
                    addInputGroupsFromObject(groups, el, accessor);
                }
                return;
            }
            for (Object el : list) {
                addInputGroupsFromObject(groups, el, accessor);
            }
            return;
        }
        List<ItemStack> reps = representations(obj);
        if (!reps.isEmpty()) {
            LinkedHashSet<Item> distinct = new LinkedHashSet<>();
            for (ItemStack stack : reps) {
                distinct.addAll(itemsFromStack(stack));
            }
            addDistinctGroup(groups, distinct, pickCount(stackCountsFromStacks(reps)), accessor);
            return;
        }
        List<ItemStack> matching = matchingStacks(obj);
        if (!matching.isEmpty()) {
            LinkedHashSet<Item> distinct = new LinkedHashSet<>();
            for (ItemStack stack : matching) {
                distinct.addAll(itemsFromStack(stack));
            }
            addDistinctGroup(groups, distinct, pickCount(stackCountsFromStacks(matching)), accessor);
            return;
        }
        ItemStack unwrapped = unwrapItemStack(obj);
        LinkedHashSet<Item> one = itemsFromStack(unwrapped);
        addDistinctGroup(groups, one, pickCount(new int[] {unwrapped.getCount()}), accessor);
    }

    private static int[] stackCountsFromStacks(List<ItemStack> stacks) {
        int[] counts = new int[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            counts[i] = stack == null ? 0 : stack.getCount();
        }
        return counts;
    }

    private static List<ItemStack> representations(Object obj) {
        try {
            Object r = invokeNoArg(obj, "getRepresentations");
            if (r instanceof List<?> list) {
                List<ItemStack> stacks = new ArrayList<>();
                for (Object el : list) {
                    if (el instanceof ItemStack stack) {
                        stacks.add(stack);
                    }
                }
                return stacks;
            }
        } catch (Throwable ignored) {
            // no representations
        }
        return List.of();
    }

    private static List<ItemStack> matchingStacks(Object obj) {
        try {
            Object r = invokeNoArg(obj, "getMatchingStacks");
            if (r instanceof ItemStack[] arr) {
                List<ItemStack> stacks = new ArrayList<>();
                for (ItemStack stack : arr) {
                    if (stack != null) {
                        stacks.add(stack);
                    }
                }
                return stacks;
            }
            if (r instanceof List<?> list) {
                List<ItemStack> stacks = new ArrayList<>();
                for (Object el : list) {
                    if (el instanceof ItemStack stack) {
                        stacks.add(stack);
                    }
                }
                return stacks;
            }
        } catch (Throwable ignored) {
            // no matching stacks
        }
        return List.of();
    }

    private static LinkedHashSet<Item> itemsFromStack(ItemStack stack) {
        LinkedHashSet<Item> distinct = new LinkedHashSet<>();
        if (stack == null || stack.isEmpty()) {
            return distinct;
        }
        Item it = stack.getItem();
        if (it == null || it == Items.AIR || it == Items.BARRIER) {
            return distinct;
        }
        distinct.add(it);
        return distinct;
    }

    private static void addDistinctGroup(
            List<Group> groups, LinkedHashSet<Item> distinct, int count, String accessor) {
        if (distinct.isEmpty()) {
            lastSkippedInputSlots = true;
            return;
        }
        List<Item> sorted = new ArrayList<>(distinct);
        sorted.sort(Comparator.comparing(ObtainGraph::itemKey));
        int slotCount = count <= 0 ? 1 : count;
        groups.add(new Group(List.copyOf(sorted), slotCount));
    }

    /** @return true when {@code fi} looks like a fluid ingredient but yielded no stacks */
    private static boolean collectFluidFromElement(List<FluidOut> out, Object fi, String accessor) {
        int before = out.size();
        if (fi instanceof FluidStack fs) {
            addFluidStack(out, fs, accessor);
            return false;
        }
        if (fi instanceof FluidStack[] arr) {
            for (FluidStack fs : arr) {
                addFluidStack(out, fs, accessor);
            }
            return false;
        }
        if (fi instanceof List<?> list) {
            boolean unresolved = false;
            for (Object el : list) {
                if (el == null) {
                    continue;
                }
                if (el instanceof FluidStack fs) {
                    addFluidStack(out, fs, accessor);
                } else if (collectFluidFromElement(out, el, accessor)) {
                    unresolved = true;
                }
            }
            return unresolved && out.size() == before;
        }
        if (fi instanceof Object[] arr) {
            boolean unresolved = false;
            for (Object el : arr) {
                if (el == null) {
                    continue;
                }
                if (el instanceof FluidStack fs) {
                    addFluidStack(out, fs, accessor);
                } else if (collectFluidFromElement(out, el, accessor)) {
                    unresolved = true;
                }
            }
            return unresolved && out.size() == before;
        }
        for (String m : new String[] {
                "getMatchingFluidStacks", "getFluids", "getMatchingFluids", "getFluidStack"
        }) {
            try {
                Object r = invokeNoArg(fi, m);
                if (r instanceof List<?> list) {
                    for (Object el : list) {
                        if (el instanceof FluidStack fs) {
                            addFluidStack(out, fs, accessor);
                        } else {
                            collectFluidFromElement(out, el, accessor);
                        }
                    }
                    if (out.size() > before) {
                        return false;
                    }
                    if (!list.isEmpty()) {
                        continue;
                    }
                }
                if (r instanceof FluidStack[] arr) {
                    for (FluidStack fs : arr) {
                        addFluidStack(out, fs, accessor);
                    }
                    if (out.size() > before) {
                        return false;
                    }
                    if (arr.length > 0) {
                        continue;
                    }
                }
                if (r instanceof FluidStack fs) {
                    addFluidStack(out, fs, accessor);
                    return false;
                }
            } catch (Throwable ignored) {
                // try next
            }
        }
        return out.size() == before && isFluidIngredientLike(fi);
    }

    private static boolean isFluidIngredientLike(Object fi) {
        if (fi == null) {
            return false;
        }
        String simple = fi.getClass().getSimpleName();
        return simple.contains("FluidIngredient");
    }

    private static void addFluidStack(List<FluidOut> out, FluidStack st, String accessor) {
        if (st == null || st.isEmpty()) {
            return;
        }
        var key = ForgeRegistries.FLUIDS.getKey(st.getFluid());
        if (key == null) {
            return;
        }
        out.add(new FluidOut(key.toString(), st.getAmount(), accessor));
    }

    private static Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        long t0 = System.nanoTime();
        try {
            Class<?> clazz = target.getClass();
            ConcurrentHashMap<String, NoSuchMethodException> missing = MISSING_METHOD_CACHE.get(clazz);
            if (missing != null) {
                NoSuchMethodException cachedMiss = missing.get(methodName);
                if (cachedMiss != null) {
                    throw cachedMiss;
                }
            }
            ConcurrentHashMap<String, Method> perClass =
                    METHOD_CACHE.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
            Method m = perClass.get(methodName);
            if (m == null) {
                try {
                    m = resolveNoArgMethod(clazz, methodName);
                } catch (NoSuchMethodException e) {
                    NoSuchMethodException cached =
                            MISSING_METHOD_CACHE
                                    .computeIfAbsent(clazz, k -> new ConcurrentHashMap<>())
                                    .computeIfAbsent(methodName, k -> e);
                    throw cached;
                }
                perClass.put(methodName, m);
            }
            return m.invoke(target);
        } finally {
            statReflectNanos += System.nanoTime() - t0;
        }
    }

    private static Method resolveNoArgMethod(Class<?> clazz, String methodName) throws NoSuchMethodException {
        try {
            return clazz.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            Method declared = findDeclaredNoArgMethod(clazz, methodName);
            if (declared == null) {
                throw e;
            }
            declared.setAccessible(true);
            return declared;
        }
    }

    private static Method findDeclaredNoArgMethod(Class<?> start, String methodName) {
        LinkedHashSet<Class<?>> visitedIfaces = new LinkedHashSet<>();
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            Method m = tryGetDeclaredNoArg(c, methodName);
            if (m != null) {
                return m;
            }
            m = findDeclaredOnInterfaces(c.getInterfaces(), methodName, visitedIfaces);
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    private static Method findDeclaredOnInterfaces(
            Class<?>[] interfaces, String methodName, LinkedHashSet<Class<?>> visited) {
        for (Class<?> iface : interfaces) {
            if (!visited.add(iface)) {
                continue;
            }
            Method m = tryGetDeclaredNoArg(iface, methodName);
            if (m != null) {
                return m;
            }
            m = findDeclaredOnInterfaces(iface.getInterfaces(), methodName, visited);
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    private static Method tryGetDeclaredNoArg(Class<?> c, String methodName) {
        try {
            return c.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
