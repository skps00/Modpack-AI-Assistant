package com.skps9.packai.client.routes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skps9.packai.logic.ObtainGraph;
import com.skps9.packai.logic.ObtainPaths;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** JSON codec + in-memory caps for saved obtain routes (no file I/O). */
public final class RouteStoreCodec {
    public static final int MAX_ROUTES = 20;
    public static final int MAX_FILE_BYTES = 262_144;

    public record FluidDto(String fluidId, int amountMb) {}

    public record FluidSourceDto(
            String fluidId,
            String recipeId,
            String typeId,
            String actionLabel,
            int amountMb,
            List<String> inputs) {}

    public record StepDto(
            String recipeId,
            String typeId,
            String output,
            String actionLabel,
            List<String> inputs,
            List<FluidDto> fluids,
            List<FluidSourceDto> fluidSources) {

        public StepDto(
                String recipeId,
                String typeId,
                String output,
                String actionLabel,
                List<String> inputs,
                List<FluidDto> fluids) {
            this(recipeId, typeId, output, actionLabel, inputs, fluids, List.of());
        }
    }

    public record SavedRoute(
            String itemId,
            String createdAt,
            boolean complete,
            String modsStamp,
            List<StepDto> steps) {}

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Comparator<SavedRoute> NEWEST_FIRST =
            Comparator.comparing((SavedRoute r) -> nullSafeCreatedAt(r.createdAt()),
                    Comparator.nullsFirst(Comparator.<String>naturalOrder())).reversed();

    private RouteStoreCodec() {}

    public static String encode(List<SavedRoute> routes) {
        if (routes == null || routes.isEmpty()) {
            return "[]";
        }
        JsonArray array = new JsonArray();
        for (SavedRoute r : routes) {
            if (r != null) {
                array.add(encodeRoute(r));
            }
        }
        return GSON.toJson(array);
    }

    public static List<SavedRoute> decode(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) {
                return List.of();
            }
            List<SavedRoute> out = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray()) {
                try {
                    SavedRoute row = decodeRoute(el);
                    if (row == null || row.itemId() == null || row.itemId().isBlank()) {
                        continue;
                    }
                    out.add(row);
                } catch (RuntimeException ignored) {
                    // skip bad entry
                }
            }
            if (out.size() > MAX_ROUTES) {
                out = new ArrayList<>(out.subList(0, MAX_ROUTES));
            }
            return List.copyOf(out);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static JsonObject encodeRoute(SavedRoute r) {
        JsonObject o = new JsonObject();
        addNullableString(o, "itemId", r.itemId());
        addNullableString(o, "createdAt", r.createdAt());
        o.addProperty("complete", r.complete());
        addNullableString(o, "modsStamp", r.modsStamp());
        JsonArray steps = new JsonArray();
        if (r.steps() != null) {
            for (StepDto s : r.steps()) {
                if (s != null) {
                    steps.add(encodeStep(s));
                }
            }
        }
        o.add("steps", steps);
        return o;
    }

    private static JsonObject encodeStep(StepDto s) {
        JsonObject o = new JsonObject();
        addNullableString(o, "recipeId", s.recipeId());
        addNullableString(o, "typeId", s.typeId());
        addNullableString(o, "output", s.output());
        addNullableString(o, "actionLabel", s.actionLabel());
        JsonArray inputs = new JsonArray();
        if (s.inputs() != null) {
            for (String in : s.inputs()) {
                if (in != null) {
                    inputs.add(in);
                } else {
                    inputs.add(JsonNull.INSTANCE);
                }
            }
        }
        o.add("inputs", inputs);
        JsonArray fluids = new JsonArray();
        if (s.fluids() != null) {
            for (FluidDto f : s.fluids()) {
                if (f == null) {
                    continue;
                }
                JsonObject fo = new JsonObject();
                addNullableString(fo, "id", f.fluidId());
                fo.addProperty("amount", f.amountMb());
                fluids.add(fo);
            }
        }
        o.add("fluids", fluids);
        JsonArray fluidSources = new JsonArray();
        if (s.fluidSources() != null) {
            for (FluidSourceDto fs : s.fluidSources()) {
                if (fs == null) {
                    continue;
                }
                JsonObject fo = new JsonObject();
                addNullableString(fo, "fluidId", fs.fluidId());
                addNullableString(fo, "recipeId", fs.recipeId());
                addNullableString(fo, "typeId", fs.typeId());
                addNullableString(fo, "actionLabel", fs.actionLabel());
                fo.addProperty("amountMb", fs.amountMb());
                JsonArray srcInputs = new JsonArray();
                if (fs.inputs() != null) {
                    for (String in : fs.inputs()) {
                        if (in != null) {
                            srcInputs.add(in);
                        } else {
                            srcInputs.add(JsonNull.INSTANCE);
                        }
                    }
                }
                fo.add("inputs", srcInputs);
                fluidSources.add(fo);
            }
        }
        o.add("fluidSources", fluidSources);
        return o;
    }

    private static void addNullableString(JsonObject o, String key, String value) {
        if (value == null) {
            o.add(key, JsonNull.INSTANCE);
        } else {
            o.addProperty(key, value);
        }
    }

    private static SavedRoute decodeRoute(JsonElement el) {
        if (el == null || el.isJsonNull() || !el.isJsonObject()) {
            return null;
        }
        JsonObject o = el.getAsJsonObject();
        String itemId = readStringField(o, "itemId", true);
        String createdAt = readStringField(o, "createdAt", false);
        boolean complete = readBooleanField(o, "complete");
        String modsStamp = readStringField(o, "modsStamp", false);
        List<StepDto> steps = readStepsField(o);
        return new SavedRoute(itemId, createdAt, complete, modsStamp, steps);
    }

    private static StepDto decodeStep(JsonElement el) {
        if (el == null || el.isJsonNull() || !el.isJsonObject()) {
            return null;
        }
        JsonObject o = el.getAsJsonObject();
        String recipeId = readStringField(o, "recipeId", false);
        String typeId = readStringField(o, "typeId", false);
        String output = readStringField(o, "output", false);
        String actionLabel = readStringField(o, "actionLabel", false);
        List<String> inputs = readStringListField(o, "inputs");
        List<FluidDto> fluids = readFluidsField(o);
        List<FluidSourceDto> fluidSources = readFluidSourcesField(o);
        return new StepDto(recipeId, typeId, output, actionLabel, inputs, fluids, fluidSources);
    }

    /** @param strict if present but not a JSON string, fail (skip route) */
    private static String readStringField(JsonObject o, String key, boolean strict) {
        if (!o.has(key)) {
            return null;
        }
        JsonElement el = o.get(key);
        if (el.isJsonNull()) {
            return null;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            if (strict) {
                throw new IllegalArgumentException("bad type: " + key);
            }
            return null;
        }
        return el.getAsString();
    }

    private static boolean readBooleanField(JsonObject o, String key) {
        if (!o.has(key)) {
            return false;
        }
        JsonElement el = o.get(key);
        if (el.isJsonNull()) {
            return false;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("bad type: " + key);
        }
        return el.getAsBoolean();
    }

    private static List<StepDto> readStepsField(JsonObject o) {
        if (!o.has("steps")) {
            return List.of();
        }
        JsonElement el = o.get("steps");
        if (el.isJsonNull()) {
            return List.of();
        }
        if (!el.isJsonArray()) {
            throw new IllegalArgumentException("bad type: steps");
        }
        List<StepDto> steps = new ArrayList<>();
        for (JsonElement stepEl : el.getAsJsonArray()) {
            try {
                StepDto s = decodeStep(stepEl);
                if (s != null) {
                    steps.add(s);
                }
            } catch (RuntimeException ignored) {
                // skip bad step
            }
        }
        return List.copyOf(steps);
    }

    private static List<String> readStringListField(JsonObject o, String key) {
        if (!o.has(key)) {
            return List.of();
        }
        JsonElement el = o.get(key);
        if (el.isJsonNull()) {
            return List.of();
        }
        if (!el.isJsonArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement item : el.getAsJsonArray()) {
            if (item.isJsonNull()) {
                out.add(null);
            } else if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                out.add(item.getAsString());
            }
            // skip non-string elements
        }
        return List.copyOf(out);
    }

    private static List<FluidDto> readFluidsField(JsonObject o) {
        if (!o.has("fluids")) {
            return List.of();
        }
        JsonElement el = o.get("fluids");
        if (el.isJsonNull()) {
            return List.of();
        }
        if (!el.isJsonArray()) {
            return List.of();
        }
        List<FluidDto> out = new ArrayList<>();
        for (JsonElement item : el.getAsJsonArray()) {
            if (item == null || item.isJsonNull() || !item.isJsonObject()) {
                continue;
            }
            JsonObject fo = item.getAsJsonObject();
            String id = null;
            if (fo.has("id") && !fo.get("id").isJsonNull()) {
                JsonElement idEl = fo.get("id");
                if (idEl.isJsonPrimitive() && idEl.getAsJsonPrimitive().isString()) {
                    id = idEl.getAsString();
                }
            }
            int amount = 0;
            if (fo.has("amount") && !fo.get("amount").isJsonNull()) {
                JsonElement amtEl = fo.get("amount");
                if (amtEl.isJsonPrimitive() && amtEl.getAsJsonPrimitive().isNumber()) {
                    amount = amtEl.getAsJsonPrimitive().getAsInt();
                }
            }
            if (id != null && !id.isBlank()) {
                out.add(new FluidDto(id, amount));
            }
        }
        return List.copyOf(out);
    }

    private static List<FluidSourceDto> readFluidSourcesField(JsonObject o) {
        if (!o.has("fluidSources")) {
            return List.of();
        }
        JsonElement el = o.get("fluidSources");
        if (el.isJsonNull()) {
            return List.of();
        }
        if (!el.isJsonArray()) {
            return List.of();
        }
        List<FluidSourceDto> out = new ArrayList<>();
        for (JsonElement item : el.getAsJsonArray()) {
            if (item == null || item.isJsonNull() || !item.isJsonObject()) {
                continue;
            }
            JsonObject fo = item.getAsJsonObject();
            String fluidId = readStringField(fo, "fluidId", false);
            String recipeId = readStringField(fo, "recipeId", false);
            String typeId = readStringField(fo, "typeId", false);
            String actionLabel = readStringField(fo, "actionLabel", false);
            int amountMb = 0;
            if (fo.has("amountMb") && !fo.get("amountMb").isJsonNull()) {
                JsonElement amtEl = fo.get("amountMb");
                if (amtEl.isJsonPrimitive() && amtEl.getAsJsonPrimitive().isNumber()) {
                    amountMb = amtEl.getAsJsonPrimitive().getAsInt();
                }
            }
            List<String> inputs = readStringListField(fo, "inputs");
            if (fluidId != null && !fluidId.isBlank()) {
                out.add(new FluidSourceDto(fluidId, recipeId, typeId, actionLabel, amountMb, inputs));
            }
        }
        return List.copyOf(out);
    }

    public static List<SavedRoute> put(List<SavedRoute> existing, SavedRoute add) {
        if (add == null || add.itemId() == null || add.itemId().isBlank()) {
            return existing == null ? List.of() : sortNewestFirst(existing);
        }
        List<SavedRoute> list = new ArrayList<>();
        if (existing != null) {
            for (SavedRoute r : existing) {
                if (r != null && !add.itemId().equals(r.itemId())) {
                    list.add(r);
                }
            }
        }
        list.add(add);
        while (list.size() > MAX_ROUTES) {
            dropOldest(list);
        }
        return sortNewestFirst(list);
    }

    public static List<SavedRoute> capByBytes(List<SavedRoute> routes) {
        if (routes == null || routes.isEmpty()) {
            return List.of();
        }
        List<SavedRoute> list = new ArrayList<>(sortNewestFirst(routes));
        while (encodedBytes(list) > MAX_FILE_BYTES && list.size() > 1) {
            list.remove(list.size() - 1);
        }
        if (encodedBytes(list) > MAX_FILE_BYTES) {
            return List.of();
        }
        return List.copyOf(list);
    }

    public static SavedRoute fromPath(
            String itemId, String createdAtIso, String modsStamp, ObtainPaths.Path path) {
        List<StepDto> steps = new ArrayList<>();
        boolean complete = path != null && path.complete();
        if (path != null && path.steps() != null) {
            for (ObtainPaths.Step step : path.steps()) {
                if (step == null || step.edge() == null) {
                    continue;
                }
                ObtainGraph.Edge edge = step.edge();
                List<String> inputs = new ArrayList<>();
                List<List<Item>> groups = step.groups() != null ? step.groups() : edge.inputGroups();
                if (groups != null) {
                    for (List<Item> group : groups) {
                        if (group != null && !group.isEmpty()) {
                            inputs.add(registryId(group.get(0)));
                        }
                    }
                }
                ResourceLocation recipeId = edge.recipeId();
                ResourceLocation typeId = edge.typeId();
                List<FluidDto> fluids = new ArrayList<>();
                if (edge.fluids() != null) {
                    for (ObtainGraph.FluidReq fr : edge.fluids()) {
                        if (fr != null && fr.fluidId() != null && !fr.fluidId().isBlank()) {
                            fluids.add(new FluidDto(fr.fluidId(), fr.amountMb()));
                        }
                    }
                }
                List<FluidSourceDto> fluidSources = new ArrayList<>();
                if (step.fluidSources() != null) {
                    for (ObtainGraph.FluidEdge fe : step.fluidSources()) {
                        if (fe == null || fe.fluidId() == null || fe.fluidId().isBlank()) {
                            continue;
                        }
                        List<String> srcInputs = new ArrayList<>();
                        if (fe.itemGroups() != null) {
                            for (List<Item> group : fe.itemGroups()) {
                                if (group != null && !group.isEmpty()) {
                                    srcInputs.add(registryId(group.get(0)));
                                }
                            }
                        }
                        fluidSources.add(new FluidSourceDto(
                                fe.fluidId(),
                                fe.recipeId() == null ? "" : fe.recipeId().toString(),
                                fe.typeId() == null ? "" : fe.typeId().toString(),
                                fe.actionLabel(),
                                fe.amountMb(),
                                List.copyOf(srcInputs)));
                    }
                }
                steps.add(
                        new StepDto(
                                recipeId == null ? "" : recipeId.toString(),
                                typeId == null ? "" : typeId.toString(),
                                registryId(edge.output()),
                                edge.actionLabel(),
                                List.copyOf(inputs),
                                List.copyOf(fluids),
                                List.copyOf(fluidSources)));
            }
        }
        return new SavedRoute(itemId, createdAtIso, complete, modsStamp, List.copyOf(steps));
    }

    private static String registryId(Item item) {
        if (item == null) {
            return "";
        }
        ResourceLocation key = Registry.ITEM.getKey(item);
        return key == null ? "" : key.toString();
    }

    private static List<SavedRoute> sortNewestFirst(List<SavedRoute> routes) {
        List<SavedRoute> list = new ArrayList<>();
        for (SavedRoute r : routes) {
            if (r != null) {
                list.add(r);
            }
        }
        list.sort(NEWEST_FIRST);
        return List.copyOf(list);
    }

    private static void dropOldest(List<SavedRoute> list) {
        if (list.isEmpty()) {
            return;
        }
        int oldest = 0;
        String oldestAt = nullSafeCreatedAt(list.get(0).createdAt());
        for (int i = 1; i < list.size(); i++) {
            String at = nullSafeCreatedAt(list.get(i).createdAt());
            if (at.compareTo(oldestAt) < 0) {
                oldest = i;
                oldestAt = at;
            }
        }
        list.remove(oldest);
    }

    private static String nullSafeCreatedAt(String createdAt) {
        return createdAt == null ? "" : createdAt;
    }

    private static int encodedBytes(List<SavedRoute> routes) {
        return encode(routes).getBytes(StandardCharsets.UTF_8).length;
    }
}
