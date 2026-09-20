package com.skps9.packai.client.autotest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.client.gui.AiAssistantScreen;
import com.skps9.packai.client.jei.JeiRecipeCards;
import com.skps9.packai.logic.RecipeCard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Build-flag sandbox driver. No flag resource ⇒ {@link #active()} is false and {@link #tick()}
 * returns before any file read. Writes only {@code <gameDir>/packai/autotest/}.
 */
public final class AutoTestHarness {
    private static final int WORLD_TIMEOUT_TICKS = 6000;
    private static final int MAX_CASES = 20;
    /** ponytail: first 12 OUTPUT cards only; raise if a pack hides the NBT sample deeper. */
    private static final int MAX_OUTPUT_CARDS = 12;
    private static final int MAX_DIALOG_PRESSES = 3;
    private static final int REST_TICKS = 10;
    private static final long CASE_TIMEOUT_MS = 180_000L;
    private static final long BUDGET_MS = 20L * 60L * 1000L;
    private static final String MAGIC = "{\"packaiAutotest\":1,";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private static boolean finished;
    private static Phase phase = Phase.IDLE;
    private static Sub sub = Sub.START;
    private static String world = "";
    private static boolean quitWhenDone;
    private static boolean capped;
    private static String failReason;
    private static List<CaseSpec> cases = List.of();
    private static final List<CaseResult> results = new ArrayList<>();
    private static long budgetStartMs;
    private static int openTicks;
    private static boolean loadSent;
    private static int confirmPresses;
    private static int backupPresses;
    private static int caseIndex;
    private static int restTicks;
    private static long caseStartMs;
    private static LocalDateTime caseStartTs = LocalDateTime.MIN;
    /** 設計意圖：每次開 game、一個 JVM session 只讀一次 flag。快取唔係 bug。 */
    private static Boolean activeCache;
    /** 設計意圖：cases.json 每次開 game、一個 JVM session 只讀一次。缺檔唔算讀過。快取唔係 bug。 */
    private static boolean casesRead;
    /** Rename 同 copy-delete 都失敗後，同一 JVM session 唔再讀 cases.json。 */
    private static boolean casesConsumed;
    private static boolean casesRenameFailed;
    private static boolean backupsSnapshotted;
    private static boolean backupsScanned;
    private static List<String> backupNamesAtStart = List.of();
    private static final List<String> backupsWritten = new ArrayList<>();
    /** Set when openAndAskAbout returns; the next tick checks the screen. */
    private static boolean armScreenCheck;
    private static long traceSig = Long.MIN_VALUE;
    private static Judge cachedJudge;

    private AutoTestHarness() {}

    public static boolean active() {
        // 每次開 game 一個 JVM session 只讀一次。快取係設計意圖，唔係 bug。
        if (activeCache != null) {
            return activeCache;
        }
        InputStream in = AutoTestHarness.class.getResourceAsStream("/packai-autotest.flag");
        if (in == null) {
            activeCache = Boolean.FALSE;
            return false;
        }
        try {
            in.close();
        } catch (IOException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] close flag stream 失敗", t);
        }
        activeCache = Boolean.TRUE;
        return true;
    }

    public static void tick() {
        if (!active() || finished) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return;
            }
            switch (phase) {
                case IDLE -> idle(mc);
                case OPEN_WORLD -> openWorld(mc);
                case RUN_CASES -> runCases(mc);
                case DONE -> {
                }
            }
        } catch (Throwable t) {
            failReason = errorText(t);
            PackAiMod.LOGGER.warn("[packai-autotest] tick 失敗", t);
            finish(Minecraft.getInstance(), "ABORT");
        }
    }

    private static void idle(Minecraft mc) {
        // cases 讀取快取：每次開 game 一個 JVM session 只讀一次。唔係 bug。
        if (casesRead || casesConsumed) {
            return;
        }
        Path dir = gameDir(mc);
        if (dir == null) {
            return;
        }
        Path file = dir.resolve("packai").resolve("autotest").resolve("cases.json");
        if (!Files.isRegularFile(file)) {
            return;
        }
        String raw;
        try {
            raw = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException t) {
            casesRead = true;
            PackAiMod.LOGGER.warn("[packai-autotest] read cases.json 失敗", t);
            return;
        }
        casesRead = true;
        int nl = raw.indexOf('\n');
        String first = nl < 0 ? raw : raw.substring(0, nl);
        if (!first.startsWith(MAGIC)) {
            return;
        }
        if (!parse(raw)) {
            return;
        }
        String stamp = STAMP.format(LocalDateTime.now());
        try {
            Files.move(file, file.resolveSibling("cases.json.consumed-" + stamp));
        } catch (IOException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] rename cases.json 失敗", t);
            try {
                Files.copy(file, dir.resolve("packai").resolve("autotest").resolve("cases.json.consumed-" + stamp));
                Files.delete(dir.resolve("packai").resolve("autotest").resolve("cases.json"));
            } catch (IOException copyFail) {
                PackAiMod.LOGGER.warn("[packai-autotest] copy-delete cases.json 失敗", copyFail);
                casesConsumed = true;
                casesRenameFailed = true;
            }
        }
        snapshotBackups(dir);
        budgetStartMs = System.currentTimeMillis();
        openTicks = 0;
        loadSent = false;
        confirmPresses = 0;
        backupPresses = 0;
        caseIndex = 0;
        restTicks = 0;
        sub = Sub.START;
        phase = Phase.OPEN_WORLD;
    }

    private static boolean parse(String raw) {
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (!root.has("world") || !root.has("cases")) {
                return false;
            }
            String w = root.get("world").getAsString();
            if (w == null || w.isBlank()) {
                return false;
            }
            JsonArray arr = root.getAsJsonArray("cases");
            if (arr == null || arr.isEmpty()) {
                return false;
            }
            List<CaseSpec> list = new ArrayList<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                String id = text(o, "id");
                String item = text(o, "item");
                list.add(new CaseSpec(id, item));
            }
            if (list.isEmpty()) {
                return false;
            }
            capped = list.size() > MAX_CASES;
            if (capped) {
                list = new ArrayList<>(list.subList(0, MAX_CASES));
            }
            world = w;
            quitWhenDone = root.has("quitWhenDone") && root.get("quitWhenDone").getAsBoolean();
            cases = list;
            results.clear();
            failReason = null;
            return true;
        } catch (RuntimeException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] parse cases.json 失敗", t);
            return false;
        }
    }

    private static String text(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
            return "";
        }
        return o.get(key).getAsString();
    }

    private static void openWorld(Minecraft mc) {
        openTicks++;
        if (overBudget()) {
            failReason = "BUDGET_EXCEEDED";
            finish(mc, "BUDGET_EXCEEDED");
            return;
        }
        if (mc.level != null) {
            noteNewBackups(mc);
            sub = Sub.START;
            phase = Phase.RUN_CASES;
            return;
        }
        if (openTicks >= WORLD_TIMEOUT_TICKS) {
            failReason = "world_timeout";
            finish(mc, "WORLD_TIMEOUT");
            return;
        }
        if (mc.screen instanceof BackupConfirmScreen backup) {
            pressSkipBackup(backup);
            return;
        }
        if (mc.screen instanceof ConfirmScreen confirm) {
            pressFirst(confirm);
            return;
        }
        if (!loadSent && mc.screen instanceof TitleScreen title) {
            try {
                mc.createWorldOpenFlows().loadLevel(title, world);
                loadSent = true;
            } catch (Throwable t) {
                failReason = errorText(t);
                PackAiMod.LOGGER.warn("[packai-autotest] loadLevel 失敗", t);
                finish(mc, "LOAD_FAILED");
            }
        }
    }

    private static void pressFirst(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            return;
        }
        if (confirmPresses >= MAX_DIALOG_PRESSES) {
            return;
        }
        int index = 0;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button) {
                logDialogPress(screen, index, button.getMessage().getString());
                button.onPress();
                confirmPresses++;
                return;
            }
            index++;
        }
    }

    private static void logDialogPress(Screen screen, int index, String label) {
        PackAiMod.LOGGER.warn(
                "[packai-autotest] press dialog index={} label={} screen={}",
                index,
                label,
                screen.getClass().getName());
    }

    // 避免寫 <gameDir>/backups/*.zip — 1.19.2 BackupConfirmScreen 第一粒係「建立備份」。
    private static void pressSkipBackup(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            return;
        }
        if (backupPresses >= MAX_DIALOG_PRESSES) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button) {
                buttons.add(button);
            }
        }
        if (buttons.isEmpty()) {
            return;
        }
        Button pick = null;
        for (Button button : buttons) {
            if (isSkipOrCancel(button.getMessage().getString())) {
                pick = button;
                break;
            }
        }
        if (pick == null) {
            pick = buttons.get(buttons.size() - 1);
        }
        if (createsBackup(pick.getMessage().getString())) {
            Button safer = null;
            for (int i = buttons.size() - 1; i >= 0; i--) {
                if (!createsBackup(buttons.get(i).getMessage().getString())) {
                    safer = buttons.get(i);
                    break;
                }
            }
            pick = safer;
        }
        if (pick == null) {
            PackAiMod.LOGGER.warn("[packai-autotest] BackupConfirmScreen 無非備份掣");
            return;
        }
        logDialogPress(screen, buttons.indexOf(pick), pick.getMessage().getString());
        pick.onPress();
        backupPresses++;
    }

    private static boolean createsBackup(String label) {
        if (label == null) {
            return false;
        }
        String trimmed = label.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("skip") || lower.contains("know what") || trimmed.contains("我知道")) {
            return false;
        }
        return lower.contains("backup") || trimmed.contains("备份") || trimmed.contains("備份");
    }

    /** 1.19.2 labels: skip = "I know what I'm doing!" / 「我知道我在做什麼！」; cancel = Cancel / 取消. */
    private static boolean isSkipOrCancel(String label) {
        if (label == null || createsBackup(label)) {
            return false;
        }
        String trimmed = label.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if ("cancel".equals(lower) || "取消".equals(trimmed)) {
            return true;
        }
        if (lower.contains("know what i'm doing")) {
            return true;
        }
        return trimmed.contains("我知道我在做什么") || trimmed.contains("我知道我在做什麼");
    }

    private static void runCases(Minecraft mc) {
        if (overBudget()) {
            if (sub == Sub.POLL && caseIndex < cases.size()) {
                CaseSpec spec = cases.get(caseIndex);
                results.add(new CaseResult(
                        spec.id(), "BUDGET_EXCEEDED", System.currentTimeMillis() - caseStartMs, 0, ""));
                mc.setScreen(null);
            }
            failReason = "BUDGET_EXCEEDED";
            finish(mc, "BUDGET_EXCEEDED");
            return;
        }
        switch (sub) {
            case START -> startCase(mc);
            case POLL -> pollCase(mc);
            case REST -> rest();
            default -> {
            }
        }
    }

    private static void startCase(Minecraft mc) {
        if (caseIndex >= cases.size()) {
            finish(mc, "DONE");
            return;
        }
        if (ChatSession.isBusy()) {
            return;
        }
        CaseSpec spec = cases.get(caseIndex);
        caseStartMs = System.currentTimeMillis();
        caseStartTs = LocalDateTime.now();
        traceSig = Long.MIN_VALUE;
        cachedJudge = null;
        armScreenCheck = false;
        ItemStack stack = sample(spec.item());
        if (stack.isEmpty()) {
            results.add(new CaseResult(spec.id(), "NO_SAMPLE", System.currentTimeMillis() - caseStartMs, 0, ""));
            caseIndex++;
            return;
        }
        AiAssistantScreen.openAndAskAbout(stack);
        armScreenCheck = true;
        sub = Sub.POLL;
    }

    private static void pollCase(Minecraft mc) {
        CaseSpec spec = cases.get(caseIndex);
        if (armScreenCheck) {
            armScreenCheck = false;
            if (!(mc.screen instanceof AiAssistantScreen)) {
                results.add(new CaseResult(spec.id(), "NO_SCREEN", System.currentTimeMillis() - caseStartMs, 0, ""));
                caseIndex++;
                sub = Sub.START;
                return;
            }
        }
        Path dir = gameDir(mc);
        if (dir != null) {
            Judge judged = judge(dir, caseStartTs, spec.item());
            if (judged.status != null) {
                results.add(new CaseResult(
                        spec.id(),
                        judged.status,
                        System.currentTimeMillis() - caseStartMs,
                        judged.cardsOut,
                        judged.traceFile));
                mc.setScreen(null);
                restTicks = 0;
                sub = Sub.REST;
                return;
            }
        }
        if (System.currentTimeMillis() - caseStartMs >= CASE_TIMEOUT_MS) {
            results.add(new CaseResult(spec.id(), "TIMEOUT", System.currentTimeMillis() - caseStartMs, 0, ""));
            mc.setScreen(null);
            restTicks = 0;
            sub = Sub.REST;
        }
    }

    private static void rest() {
        restTicks++;
        if (ChatSession.isBusy()) {
            return;
        }
        if (restTicks < REST_TICKS) {
            return;
        }
        caseIndex++;
        sub = Sub.START;
    }

    /**
     * Public API actually on this tree: {@code JeiRecipeCards.forItem(ItemStack, int, int)}
     * then {@code RecipeCard.outputs()}. No {@code forItem(String)}.
     */
    private static ItemStack sample(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id;
        try {
            id = new ResourceLocation(itemId.trim());
        } catch (RuntimeException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] parse item id 失敗", t);
            return ItemStack.EMPTY;
        }
        if (!Registry.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        ItemStack bare = new ItemStack(Registry.ITEM.get(id));
        if (bare.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<RecipeCard> cards;
        try {
            cards = JeiRecipeCards.forItem(bare, MAX_OUTPUT_CARDS, 0);
        } catch (Throwable t) {
            PackAiMod.LOGGER.warn("[packai-autotest] JEI sample 失敗", t);
            return ItemStack.EMPTY;
        }
        if (cards == null || cards.isEmpty()) {
            return ItemStack.EMPTY;
        }
        String want = id.toString();
        ItemStack plain = ItemStack.EMPTY;
        for (RecipeCard card : cards) {
            if (card == null || card.outputs() == null) {
                continue;
            }
            if (card.focusRole() != RecipeCard.FocusRole.OUTPUT) {
                continue;
            }
            for (ItemStack out : card.outputs()) {
                if (!sameId(out, want)) {
                    continue;
                }
                ItemStack copy = out.copy();
                if (copy.hasTag()) {
                    return copy;
                }
                if (plain.isEmpty()) {
                    plain = copy;
                }
            }
        }
        return plain;
    }

    private static boolean sameId(ItemStack stack, String want) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
        return key != null && key.toString().equals(want);
    }

    private static Judge judge(Path gameDir, LocalDateTime since, String itemId) {
        Path traceDir = gameDir.resolve("packai").resolve("trace");
        if (!Files.isDirectory(traceDir)) {
            return new Judge(null, 0, "");
        }
        List<Path> files = new ArrayList<>();
        long sig = 17;
        try (var stream = Files.list(traceDir)) {
            for (Path p : stream.toList()) {
                String n = p.getFileName().toString();
                if (!n.startsWith("ask-") || !n.endsWith(".jsonl")) {
                    continue;
                }
                files.add(p);
                try {
                    sig = sig * 31 + Files.size(p);
                } catch (IOException t) {
                    PackAiMod.LOGGER.warn("[packai-autotest] stat trace 失敗", t);
                }
            }
        } catch (IOException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] list trace 失敗", t);
            return new Judge(null, 0, "");
        }
        if (sig == traceSig && cachedJudge != null) {
            return cachedJudge;
        }
        List<Path> picked = traceFilesForItem(files, itemId);
        Judge found = scanTraces(picked, since, itemId);
        traceSig = sig;
        cachedJudge = found;
        if (found.traceFile() != null && !found.traceFile().isEmpty()) {
            PackAiMod.LOGGER.info("[packai-autotest] trace file {}", found.traceFile());
        }
        return found;
    }

    /** {@code tetra:modular_double} → filename segment {@code tetra_modular_double}. No match → all files. */
    private static List<Path> traceFilesForItem(List<Path> files, String itemId) {
        String key = itemFileKey(itemId);
        if (key.isEmpty()) {
            return files;
        }
        List<Path> named = new ArrayList<>();
        for (Path file : files) {
            if (file.getFileName().toString().contains(key)) {
                named.add(file);
            }
        }
        return named.isEmpty() ? files : named;
    }

    private static String itemFileKey(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        return itemId.trim().replace(':', '_');
    }

    private static Judge scanTraces(List<Path> files, LocalDateTime since, String itemId) {
        Judge ok = null;
        long okMtime = Long.MIN_VALUE;
        boolean displayOnly = false;
        String displayFile = "";
        for (Path file : files) {
            LineHit hit = scanFile(file, since, itemId);
            if (hit.display && hit.cards) {
                long mtime = 0;
                try {
                    mtime = Files.getLastModifiedTime(file).toMillis();
                } catch (IOException t) {
                    PackAiMod.LOGGER.warn("[packai-autotest] stat trace 失敗", t);
                }
                if (ok == null || mtime >= okMtime) {
                    okMtime = mtime;
                    ok = new Judge("OK", hit.cardsOut, file.getFileName().toString());
                }
            } else if (hit.display) {
                displayOnly = true;
                displayFile = file.getFileName().toString();
            }
        }
        if (ok != null) {
            return ok;
        }
        if (displayOnly) {
            return new Judge("NO_CARDS", 0, displayFile);
        }
        return new Judge(null, 0, "");
    }

    private static LineHit scanFile(Path file, LocalDateTime since, String itemId) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] read trace 失敗", t);
            return new LineHit(false, false, 0);
        }
        boolean display = false;
        boolean cards = false;
        int cardsOut = 0;
        for (String rawLine : text.split("\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            JsonObject o;
            try {
                JsonElement el = JsonParser.parseString(line);
                if (!el.isJsonObject()) {
                    continue;
                }
                o = el.getAsJsonObject();
            } catch (RuntimeException t) {
                PackAiMod.LOGGER.warn("[packai-autotest] parse trace line 失敗", t);
                continue;
            }
            if (!newer(o, since)) {
                continue;
            }
            String event = text(o, "event");
            if ("display.body.final".equals(event)) {
                display = true;
            } else if ("render.cards.final".equals(event) && itemId != null && itemId.equals(text(o, "item"))) {
                cards = true;
                cardsOut = intProp(o, "cardsOut");
            }
        }
        return new LineHit(display, cards, cardsOut);
    }

    private static boolean newer(JsonObject o, LocalDateTime since) {
        if (!o.has("ts") || !o.get("ts").isJsonPrimitive()) {
            return false;
        }
        try {
            LocalDateTime ts = LocalDateTime.parse(o.get("ts").getAsString(), TS);
            return ts.isAfter(since);
        } catch (RuntimeException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] parse trace ts 失敗", t);
            return false;
        }
    }

    private static int intProp(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
            return 0;
        }
        try {
            return o.get(key).getAsInt();
        } catch (RuntimeException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] read cardsOut 失敗", t);
            return 0;
        }
    }

    private static String errorText(Throwable t) {
        String name = t.getClass().getSimpleName();
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return name;
        }
        return name + ": " + msg;
    }

    private static void finish(Minecraft mc, String status) {
        if (finished) {
            return;
        }
        phase = Phase.DONE;
        finished = true;
        noteNewBackups(mc);
        try {
            writeStatus(mc, status);
        } catch (Throwable t) {
            PackAiMod.LOGGER.warn("[packai-autotest] write status 失敗", t);
        }
        PackAiMod.LOGGER.info("packai autotest status={} cases={}", status, results.size());
        if (quitWhenDone && mc != null) {
            try {
                mc.stop();
            } catch (Throwable t) {
                PackAiMod.LOGGER.warn("[packai-autotest] stop client 失敗", t);
            }
        }
    }

    private static void writeStatus(Minecraft mc, String status) throws IOException {
        Path dir = gameDir(mc);
        if (dir == null) {
            return;
        }
        long elapsed = budgetStartMs == 0 ? 0 : System.currentTimeMillis() - budgetStartMs;
        JsonObject root = new JsonObject();
        root.addProperty("packaiAutotest", 1);
        root.addProperty("status", status);
        root.addProperty("elapsedMs", elapsed);
        root.addProperty("caseCount", results.size());
        root.addProperty("quitWhenDone", quitWhenDone);
        if (world != null && !world.isEmpty()) {
            root.addProperty("world", world);
        }
        if (capped) {
            root.addProperty("capped", true);
        }
        if (failReason != null && !failReason.isEmpty()) {
            root.addProperty("reason", failReason);
        }
        if (casesRenameFailed) {
            root.addProperty("casesRenameFailed", true);
        }
        if (!backupsWritten.isEmpty()) {
            JsonArray zips = new JsonArray();
            for (String name : backupsWritten) {
                zips.add(name);
            }
            root.add("backupsWritten", zips);
        }
        int ok = 0;
        JsonArray arr = new JsonArray();
        for (CaseResult r : results) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id());
            o.addProperty("status", r.status());
            o.addProperty("elapsedMs", r.elapsedMs());
            o.addProperty("cardsOut", r.cardsOut());
            o.addProperty("traceFile", r.traceFile() == null ? "" : r.traceFile());
            arr.add(o);
            if ("OK".equals(r.status())) {
                ok++;
            }
        }
        root.add("cases", arr);
        root.addProperty("ok", ok);
        String name = "status-" + STAMP.format(LocalDateTime.now()) + ".json";
        String body = GSON.toJson(root);
        Files.createDirectories(dir.resolve("packai").resolve("autotest"));
        Files.writeString(
                dir.resolve("packai").resolve("autotest").resolve(name),
                body,
                StandardCharsets.UTF_8);
    }

    private static boolean overBudget() {
        return budgetStartMs != 0 && System.currentTimeMillis() - budgetStartMs >= BUDGET_MS;
    }

    private static void snapshotBackups(Path gameDir) {
        if (backupsSnapshotted || gameDir == null) {
            return;
        }
        backupsSnapshotted = true;
        backupNamesAtStart = listBackupNames(gameDir.resolve("backups"));
    }

    private static void noteNewBackups(Minecraft mc) {
        if (backupsScanned) {
            return;
        }
        Path dir = gameDir(mc);
        if (!backupsSnapshotted) {
            snapshotBackups(dir);
        }
        backupsScanned = true;
        if (dir == null) {
            return;
        }
        for (String name : listBackupNames(dir.resolve("backups"))) {
            if (!name.endsWith(".zip") || backupNamesAtStart.contains(name)) {
                continue;
            }
            backupsWritten.add(name);
            PackAiMod.LOGGER.error("[packai-autotest] backups written: " + name);
        }
    }

    private static List<String> listBackupNames(Path dir) {
        List<String> names = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return names;
        }
        try (var stream = Files.list(dir)) {
            for (Path p : stream.toList()) {
                names.add(p.getFileName().toString());
            }
        } catch (IOException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] list backups 失敗", t);
        }
        return names;
    }

    private static Path gameDir(Minecraft mc) {
        if (mc == null || mc.gameDirectory == null) {
            return null;
        }
        return mc.gameDirectory.toPath();
    }

    private enum Phase {
        IDLE, OPEN_WORLD, RUN_CASES, DONE
    }

    private enum Sub {
        START, POLL, REST
    }

    private record CaseSpec(String id, String item) {}

    private record CaseResult(String id, String status, long elapsedMs, int cardsOut, String traceFile) {}

    /** {@code status == null} means keep polling. */
    private record Judge(String status, int cardsOut, String traceFile) {}

    private record LineHit(boolean display, boolean cards, int cardsOut) {}
}
