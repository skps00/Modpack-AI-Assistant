package com.skps9.packai.client.autotest;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.QuestBookOpener;
import com.skps9.packai.client.chat.ChatMessage;
import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.client.focus.FocusAsk;
import com.skps9.packai.client.focus.FocusProbe;
import com.skps9.packai.client.focus.FocusTarget;
import com.skps9.packai.client.focus.FocusTrace;
import com.skps9.packai.client.gui.AiAssistantScreen;
import com.skps9.packai.client.gui.InvPickScreen;
import com.skps9.packai.client.gui.ModelPickerScreen;
import com.skps9.packai.client.gui.RouteLibraryScreen;
import com.skps9.packai.client.gui.RouteTreeScreen;
import com.skps9.packai.client.gui.settings.SettingsScreenV2;
import com.skps9.packai.client.routes.RouteStore;
import com.skps9.packai.client.routes.RouteStoreCodec.SavedRoute;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.client.jei.JeiCategoryIndex;
import com.skps9.packai.client.jei.JeiRecipeCards;
import com.skps9.packai.client.jei.PackAiJeiPlugin;
import com.skps9.packai.logic.AskTrace;
import com.skps9.packai.logic.JeiCoverageProbe;
import com.skps9.packai.logic.SpamAuditProbe;
import com.skps9.packai.logic.ObtainGraphRuntime;
import com.skps9.packai.logic.ObtainGraphRuntime.BenchResult;
import com.skps9.packai.logic.RecipeCard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Build-flag sandbox driver. No flag resource ⇒ {@link #active()} is false and {@link #tick()}
 * returns before any file read. Writes only {@code <gameDir>/packai/autotest/}.
 */
public final class AutoTestHarness {
    private static final int WORLD_TIMEOUT_TICKS = 6000;
    private static final int MAX_CASES = 300;    // 2026-10-10 實測：36s 固定 + 12s/case ⇒ 一次跑齊一個矩陣；大包需長 warm-up（obtain graph 每 tick 約幾 ms）
    /** ponytail: first 12 OUTPUT cards only; raise if a pack hides the NBT sample deeper. */
    private static final int MAX_OUTPUT_CARDS = 12;
    private static final int MAX_DIALOG_PRESSES = 3;
    private static final int REST_TICKS = 10;
    /** Ticks after opening a screen before {@link Screenshot#grab} in openScreens / route-library flows. */
    private static final int SCREENSHOT_PRE_TICKS = 20;
    /** Ticks after opening {@link RouteLibraryScreen} before in-game grab. */
    private static final int ROUTE_LIBRARY_SCREENSHOT_PRE_TICKS = SCREENSHOT_PRE_TICKS;
    /** Ticks after grab before {@link #finish} so the png is on disk. */
    private static final int ROUTE_LIBRARY_SCREENSHOT_POST_TICKS = 20;
    private static final long CASE_TIMEOUT_MS = 180_000L;
    /** How long {@code spawn_mob} waits for the summoned entity to show up on the client. */
    private static final long SPAWN_WAIT_MS = 5_000L;
    /** NBT spliced with {@code %s} so the test mob stays put (no AI, no gravity). */
    private static final String TEST_MOB_NBT =
            "{NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Silent:1b}";
    /** Status when {@link #spawnTestMob} returns null ({@code SUMMON_FAIL} or {@code NO_SERVER}). */
    private static String spawnStatus = "SUMMON_FAIL";
    private static final long BUDGET_MS = 25L * 60L * 1000L;   // 60 條 × 最長 21.8s 實測 + 開世界 ≈ 23 分鐘
    private static final String MAGIC = "{\"packaiAutotest\":1,";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private static boolean finished;
    private static Phase phase = Phase.IDLE;
    private static Sub sub = Sub.START;
    private static String world = "";
    private static boolean quitWhenDone;
    private static boolean routeMarks;
    private static boolean openRouteLibrary;
    private static boolean openRouteLibraryHandled;
    private static int routeLibraryHoldTicks;
    private static List<String> openScreens = List.of();
    private static int openScreenIndex;
    private static int openScreenHoldTicks;
    private static boolean openScreenShotTaken;
    private static boolean openScreenScrollApplied;
    private static int openScreenScrollY;
    private static boolean assistantScrollTop;
    private static boolean openScreenAssistantScrollApplied;
    private static boolean forceTruncated;
    private static int openScreenScrollWaitTicks;
    @Nullable
    private static String autotestRouteId;
    private static boolean skipGraphWait;
    private static boolean jeiCoverage;
    private static boolean jeiCoverageRan;
    private static boolean spamAudit;
    private static boolean spamAuditRan;
    private static boolean indexBuild;
    private static boolean indexBuildRan;
    private static boolean capped;
    private static String failReason;
    private static List<CaseSpec> cases = List.of();
    private static final List<CaseResult> results = new ArrayList<>();
    private static final List<JsonObject> routeMarksRows = new ArrayList<>();
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
    /** One fast-NO_CARDS retry per case (stale display after TIMEOUT). */
    private static boolean fastNoCardsRetried;

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
        if (!active()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return;
            }
            if (routeMarks) {
                RouteMarksAutotest.tickPending();
            }
            if (finished) return;
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
        fastNoCardsRetried = false;
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
                String question = text(o, "question");
                String action = text(o, "action");
                int benchRuns = 3;
                if (o.has("runs") && o.get("runs").isJsonPrimitive()) {
                    try {
                        benchRuns = o.get("runs").getAsInt();
                    } catch (RuntimeException t) {
                        PackAiMod.LOGGER.warn("[packai-autotest] parse runs 失敗", t);
                    }
                }
                benchRuns = Math.max(1, Math.min(5, benchRuns));
                list.add(new CaseSpec(
                        id,
                        item,
                        question,
                        action,
                        text(o, "kind"),
                        benchRuns,
                        text(o, "expectKind"),
                        text(o, "expectWinner"),
                        text(o, "expectState"),
                        text(o, "pitch"),
                        text(o, "yaw"),
                        text(o, "text")));
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
            routeMarks = root.has("routeMarks") && root.get("routeMarks").getAsBoolean();
            openRouteLibrary = root.has("openRouteLibrary") && root.get("openRouteLibrary").getAsBoolean();
            openScreens = parseOpenScreens(root);
            openScreenIndex = 0;
            openScreenHoldTicks = 0;
            openScreenShotTaken = false;
            openScreenScrollApplied = false;
            openScreenAssistantScrollApplied = false;
            openScreenScrollWaitTicks = 0;
            autotestRouteId = text(root, "routeId");
            if (autotestRouteId != null && autotestRouteId.isBlank()) {
                autotestRouteId = null;
            }
            skipGraphWait = root.has("skipGraphWait") && root.get("skipGraphWait").getAsBoolean();
            jeiCoverage = root.has("jeiCoverage") && root.get("jeiCoverage").getAsBoolean();
            jeiCoverageRan = false;
            spamAudit = root.has("spamAudit") && root.get("spamAudit").getAsBoolean();
            spamAuditRan = false;
            indexBuild = root.has("indexBuild") && root.get("indexBuild").getAsBoolean();
            indexBuildRan = false;
            openScreenScrollY = root.has("scrollY") ? root.get("scrollY").getAsInt() : 0;
            assistantScrollTop =
                    root.has("assistantScrollTop") && root.get("assistantScrollTop").getAsBoolean();
            forceTruncated =
                    root.has("forceTreeTruncated") && root.get("forceTreeTruncated").getAsBoolean();
            cases = list;
            results.clear();
            routeMarksRows.clear();
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

    private static List<String> parseOpenScreens(JsonObject root) {
        if (!root.has("openScreens") || !root.get("openScreens").isJsonArray()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("openScreens")) {
            if (!el.isJsonPrimitive()) {
                continue;
            }
            String s = el.getAsString();
            if (s != null && !s.isBlank()) {
                names.add(s.trim());
            }
        }
        return names;
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
                noteCaseDone(mc, spec);
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
            case ROUTE_LIBRARY_SHOT -> routeLibraryScreenshotHold(mc);
            case OPEN_SCREEN_SHOT -> openScreenScreenshotHold(mc);
            default -> {
            }
        }
    }

    private static void startCase(Minecraft mc) {
        if (!runIndexBuildIfRequested()) {
            return;
        }
        if (caseIndex >= cases.size()) {
            if (openRouteLibrary && !openRouteLibraryHandled) {
                openRouteLibraryHandled = true;
                Minecraft.getInstance().setScreen(new RouteLibraryScreen());
                routeLibraryHoldTicks = 0;
                sub = Sub.ROUTE_LIBRARY_SHOT;
                return;
            }
            if (beginOpenScreensIfAny(mc)) {
                return;
            }
            finish(mc, "DONE");
            return;
        }
        if (ChatSession.isBusy()) {
            return;
        }
        if (routeMarks) {
            ObtainGraphRuntime.INSTANCE.requestClientRebuildIfNeeded();
        }
        CaseSpec spec = cases.get(caseIndex);
        caseStartMs = System.currentTimeMillis();
        caseStartTs = LocalDateTime.now();
        traceSig = Long.MIN_VALUE;
        cachedJudge = null;
        armScreenCheck = false;
        if ("seed_reply".equals(spec.action())) {
            String seed = spec.text();
            if (seed == null || seed.isBlank()) {
                results.add(new CaseResult(spec.id(), "NO_SAMPLE", 0, 0, ""));
            } else {
                ChatSession.append(ChatMessage.assistant(seed));
                results.add(new CaseResult(spec.id(), "OK", 0, 0, ""));
            }
            ChatSession.setBusy(false);
            noteCaseDone(mc, spec);
            restTicks = 0;
            sub = Sub.REST;
            return;
        }
        if ("catalyst_census".equals(spec.action())) {
            String status = runCatalystCensus(mc);
            results.add(new CaseResult(
                    spec.id(), status, System.currentTimeMillis() - caseStartMs, 0, ""));
            noteCaseDone(mc, spec);
            restTicks = 0;
            sub = Sub.REST;
            return;
        }
        if ("ask_focus".equals(spec.action())) {
            String status = runAskFocus(mc);
            results.add(new CaseResult(
                    spec.id(), status, System.currentTimeMillis() - caseStartMs, 0, ""));
            noteCaseDone(mc, spec);
            restTicks = 0;
            sub = Sub.REST;
            return;
        }
        if ("ask_block".equals(spec.action())) {
            finishImmediate(mc, spec, runAskOnly(mc, spec, "block"));
            return;
        }
        if ("ask_entity".equals(spec.action())) {
            finishImmediate(mc, spec, runAskOnly(mc, spec, "entity"));
            return;
        }
        if ("focus_off".equals(spec.action())) {
            finishImmediate(mc, spec, runFocusOff(mc));
            return;
        }
        if ("face_away".equals(spec.action())) {
            finishImmediate(mc, spec, runFaceAway(mc, spec));
            return;
        }
        if ("spawn_mob".equals(spec.action())) {
            finishImmediate(mc, spec, runSpawnMob(mc, spec));
            return;
        }
        if ("graph_bench".equals(spec.kind())) {
            finishGraphBench(mc, spec);
            return;
        }
        if (spec.question() != null && !spec.question().isBlank()) {
            AiAssistantScreen.openAndAskQuestion(spec.question());
            armScreenCheck = true;
            sub = Sub.POLL;
            return;                       // 唔行 sample()，唔行 openAndAskAbout
        }
        ItemStack stack = sample(spec.item());
        if (stack.isEmpty()) {
            results.add(new CaseResult(spec.id(), "NO_SAMPLE", System.currentTimeMillis() - caseStartMs, 0, ""));
            noteCaseDone(mc, spec);
            caseIndex++;
            return;
        }
        AiAssistantScreen.openAndAskAbout(stack);
        armScreenCheck = true;
        sub = Sub.POLL;
    }

    private static void runJeiCoverageIfRequested() {
        if (!jeiCoverage || jeiCoverageRan) {
            return;
        }
        jeiCoverageRan = true;
        try {
            PackAiJeiPlugin.runtime().ifPresent(runtime -> JeiCoverageProbe.runOnce(runtime.getRecipeManager()));
        } catch (Throwable t) {
            PackAiMod.LOGGER.warn("[packai-autotest] jei_coverage 失敗", t);
        }
    }

    private static void runSpamAuditIfRequested() {
        if (!spamAudit || spamAuditRan) {
            return;
        }
        spamAuditRan = true;
        try {
            PackAiJeiPlugin.runtime().ifPresent(runtime -> SpamAuditProbe.runOnce(runtime.getRecipeManager()));
        } catch (Throwable t) {
            PackAiMod.LOGGER.warn("[packai-autotest] spam_audit 失敗", t);
        }
    }

    /**
     * When {@code indexBuild} is set: block cases until JEI is up, then synchronously finish the id index.
     */
    private static boolean runIndexBuildIfRequested() {
        if (!indexBuild || indexBuildRan) {
            return true;
        }
        var runtime = PackAiJeiPlugin.runtime();
        if (runtime.isEmpty()) {
            return false;
        }
        indexBuildRan = true;
        try {
            var jei = runtime.get();
            JeiCategoryIndex.INSTANCE.forceBuildComplete(
                    jei.getRecipeManager(), jei.getIngredientManager());
        } catch (Throwable t) {
            PackAiMod.LOGGER.warn("[packai-autotest] index_build 失敗", t);
        }
        return true;
    }

    /** Write {@code jei-catalyst-census.json} under packai/autotest. Status OK / NO_JEI / WRITE_FAIL. */
    private static String runCatalystCensus(Minecraft mc) {
        Path dir = gameDir(mc);
        if (dir == null) {
            return "WRITE_FAIL";
        }
        JsonObject root = JeiCatalystCensus.build();
        if (root == null) {
            return "NO_JEI";
        }
        try {
            Files.createDirectories(dir.resolve("packai").resolve("autotest"));
            Files.writeString(
                    dir.resolve("packai").resolve("autotest").resolve("jei-catalyst-census.json"),
                    GSON.toJson(root),
                    StandardCharsets.UTF_8);
            PackAiMod.LOGGER.info("[packai-autotest] wrote jei-catalyst-census.json");
            return "OK";
        } catch (Throwable t) {
            PackAiMod.LOGGER.warn("[packai-autotest] catalyst census write 失敗", t);
            return "WRITE_FAIL";
        }
    }

    /**
     * Look down / forward / up, probe each, ask via {@link FocusAsk#askFromWorld} on the
     * downward pitch (or the first BLOCK pitch). Writes {@code focus-probe.json}.
     */
    private static String runAskFocus(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return "NO_WORLD";
        }
        float savedXRot = mc.player.getXRot();
        double range = PackAiConfig.entityFocusRange();
        float[] pitches = {88f, 0f, -88f};
        String[] kinds = new String[3];
        double downDistance = 0.0;
        try {
            for (int i = 0; i < pitches.length; i++) {
                mc.player.setXRot(pitches[i]);
                FocusTarget t = FocusProbe.probe(mc, range);
                kinds[i] = t.kind().name();
                if (i == 0) {
                    downDistance = t.distance();
                }
            }
            float askPitch = 88f;
            String asked = kinds[0];
            if (!"BLOCK".equals(kinds[0])) {
                for (int i = 0; i < pitches.length; i++) {
                    if ("BLOCK".equals(kinds[i])) {
                        askPitch = pitches[i];
                        asked = kinds[i];
                        break;
                    }
                }
            }
            mc.player.setXRot(askPitch);
            FocusAsk.askFromWorld(mc, "focus");
            JsonObject root = new JsonObject();
            root.addProperty("range", range);
            root.addProperty("down", kinds[0]);
            root.addProperty("front", kinds[1]);
            root.addProperty("up", kinds[2]);
            root.addProperty("downDistance", downDistance);
            root.addProperty("asked", asked);
            Path dir = gameDir(mc);
            if (dir == null) {
                return "WRITE_FAIL";
            }
            try {
                Files.createDirectories(dir.resolve("packai").resolve("autotest"));
                Files.writeString(
                        dir.resolve("packai").resolve("autotest").resolve("focus-probe.json"),
                        GSON.toJson(root),
                        StandardCharsets.UTF_8);
                PackAiMod.LOGGER.info("[packai-autotest] wrote focus-probe.json");
                return "OK";
            } catch (Throwable t) {
                PackAiMod.LOGGER.warn("[packai-autotest] focus probe write 失敗", t);
                return "WRITE_FAIL";
            }
        } finally {
            mc.player.setXRot(savedXRot);
        }
    }

    private static void finishImmediate(Minecraft mc, CaseSpec spec, String status) {
        results.add(new CaseResult(
                spec.id(), status, System.currentTimeMillis() - caseStartMs, 0, ""));
        noteCaseDone(mc, spec);
        restTicks = 0;
        sub = Sub.REST;
    }

    private static void finishGraphBench(Minecraft mc, CaseSpec spec) {
        long elapsed = System.currentTimeMillis() - caseStartMs;
        if (mc.level == null) {
            results.add(new CaseResult(spec.id(), "NO_WORLD", elapsed, 0, ""));
            noteCaseDone(mc, spec);
            restTicks = 0;
            sub = Sub.REST;
            return;
        }
        RecipeManager rm = mc.level.getRecipeManager();
        int runs = spec.runs() <= 0 ? 3 : spec.runs();
        BenchResult bench = ObtainGraphRuntime.benchBuild(rm, runs);
        String status = bench.allEqual() ? "OK" : "FAIL";
        if (!bench.allEqual()) {
            PackAiMod.LOGGER.warn(
                    "[packai-autotest] graph_bench id={} allEqual=false shas={} edges={}",
                    spec.id(),
                    bench.shas(),
                    bench.edges());
        }
        results.add(new CaseResult(
                spec.id(),
                status,
                elapsed,
                0,
                "",
                bench.runs(),
                bench.edges(),
                bench.shas(),
                bench.allEqual()));
        noteCaseDone(mc, spec);
        restTicks = 0;
        sub = Sub.REST;
    }

    private static void noteCaseDone(Minecraft mc, CaseSpec spec) {
        if (!routeMarks || spec == null) {
            return;
        }
        RouteMarksAutotest.recordCase(mc, routeMarksRows, spec.id(), spec.item());
    }

    /** Client-side look. Call before probe in the same tick. */
    static void aimAt(Minecraft mc, float pitch, float yaw) {
        if (mc == null || mc.player == null) {
            return;
        }
        mc.player.setXRot(pitch);
        mc.player.setYRot(yaw);
    }

    /** Aim the client player at {@code entity} (eye to eye). */
    static void faceEntity(Minecraft mc, Entity entity) {
        if (mc == null || mc.player == null || entity == null) {
            return;
        }
        double dx = entity.getX() - mc.player.getX();
        double dy = entity.getEyeY() - mc.player.getEyeY();
        double dz = entity.getZ() - mc.player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
        float pitch = Mth.wrapDegrees((float) (-(Mth.atan2(dy, horiz) * (180.0 / Math.PI))));
        aimAt(mc, pitch, yaw);
    }

    /**
     * Summon a test mob ~3 blocks ahead (horizontal look, feet Y) and face it.
     * Status: OK / NO_WORLD / NO_SERVER / SUMMON_FAIL.
     */
    private static String runSpawnMob(Minecraft mc, CaseSpec spec) {
        if (mc.player == null || mc.level == null) {
            return "NO_WORLD";
        }
        String id = spec.item() == null ? "" : spec.item().trim();
        if (id.isEmpty()) {
            id = "minecraft:cow";
        }
        Entity mob = spawnTestMob(mc, id, 3.0);
        if (mob == null) {
            return spawnStatus;
        }
        faceEntity(mc, mob);
        return "OK";
    }

    /**
     * Summon {@code id} {@code dist} blocks ahead and wait until the client level has it.
     * Pinned with {@link #TEST_MOB_NBT} so it does not walk or fall out of probe range.
     * {@code null} ⇒ see {@link #spawnStatus}.
     */
    private static Entity spawnTestMob(Minecraft mc, String id, double dist) {
        spawnStatus = "SUMMON_FAIL";
        if (mc.player == null || mc.level == null) {
            return null;
        }
        if (id == null || id.isEmpty() || EntityType.byString(id).isEmpty() || !id.matches("[a-z0-9_.:-]+")) {
            return null;
        }
        Vec3 look = mc.player.getLookAngle();
        double hx = look.x;
        double hz = look.z;
        double len = Math.sqrt(hx * hx + hz * hz);
        if (len < 1.0E-4) {
            double yawRad = mc.player.getYRot() * (Math.PI / 180.0);
            hx = -Math.sin(yawRad);
            hz = Math.cos(yawRad);
        } else {
            hx /= len;
            hz /= len;
        }
        double x = mc.player.getX() + hx * dist;
        double y = mc.player.getY();
        double z = mc.player.getZ() + hz * dist;
        String cmd = String.format(Locale.ROOT, "summon %s %.3f %.3f %.3f %s", id, x, y, z, TEST_MOB_NBT);
        Set<Integer> before = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            before.add(e.getId());
        }
        String sent = dispatchSummon(mc, cmd);
        if (!"SENT".equals(sent)) {
            spawnStatus = sent;
            return null;
        }
        Entity mob = awaitNewMob(mc, before, id, x, y, z);
        if (mob == null) {
            spawnStatus = "SUMMON_FAIL";
        }
        return mob;
    }

    /** {@code SENT}, or a terminal status. Dispatcher first; else quest-book {@code sendCommand}. */
    private static String dispatchSummon(Minecraft mc, String cmd) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) {
                return "NO_SERVER";
            }
            try {
                int result = runOnServer(server, () -> {
                    CommandSourceStack src = sp.createCommandSourceStack()
                            .withPermission(Commands.LEVEL_OWNERS)
                            .withSuppressedOutput();
                    return server.getCommands().performPrefixedCommand(src, cmd);
                });
                if (result > 0) {
                    return "SENT";
                }
                return "SUMMON_FAIL";
            } catch (Throwable t) {
                PackAiMod.LOGGER.warn("[packai-autotest] summon dispatcher 失敗", t);
            }
        }
        if (mc.getConnection() == null) {
            return "NO_SERVER";
        }
        if (!summonViaSendCommand(mc.player, cmd)) {
            return server == null ? "NO_SERVER" : "SUMMON_FAIL";
        }
        return "SENT";
    }

    private static int runOnServer(MinecraftServer server, Supplier<Integer> task) throws Exception {
        if (server.isSameThread()) {
            return task.get();
        }
        return server.submit(task).get(SPAWN_WAIT_MS, TimeUnit.MILLISECONDS);
    }

    /** Same reflection chain as {@link QuestBookOpener} quest-book open. */
    private static boolean summonViaSendCommand(LocalPlayer player, String cmd) {
        try {
            Method m = QuestBookOpener.class.getDeclaredMethod("trySendCommand", LocalPlayer.class, String.class);
            m.setAccessible(true);
            Object result = m.invoke(null, player, cmd);
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] summon sendCommand 失敗", t);
            return false;
        }
    }

    private static Entity awaitNewMob(Minecraft mc, Set<Integer> before, String id, double x, double y, double z) {
        EntityType<?> want = EntityType.byString(id).orElse(null);
        long deadline = System.currentTimeMillis() + SPAWN_WAIT_MS;
        Entity found = null;
        while (System.currentTimeMillis() <= deadline) {
            while (mc.pollTask()) {
                // apply queued add-entity packets
            }
            found = nearestNew(mc, before, want, x, y, z);
            if (found != null) {
                return found;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return nearestNew(mc, before, want, x, y, z);
    }

    private static Entity nearestNew(Minecraft mc, Set<Integer> before, EntityType<?> want, double x, double y, double z) {
        AABB box = new AABB(x - 6.0, y - 3.0, z - 6.0, x + 6.0, y + 4.0, z + 6.0);
        List<Entity> list = mc.level.getEntities(mc.player, box, e -> e != null
                && !before.contains(e.getId())
                && (want == null || e.getType() == want));
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : list) {
            double d = e.distanceToSqr(x, y, z);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private static void aimFromSpec(Minecraft mc, CaseSpec spec) {
        if (mc.player == null) {
            return;
        }
        if (spec.pitch().isEmpty() && spec.yaw().isEmpty()) {
            return;
        }
        float pitch = spec.pitch().isEmpty() ? mc.player.getXRot() : Float.parseFloat(spec.pitch().trim());
        float yaw = spec.yaw().isEmpty() ? mc.player.getYRot() : Float.parseFloat(spec.yaw().trim());
        aimAt(mc, pitch, yaw);
    }

    /** {@code only=block} or {@code only=entity}, then read kind / winner / state from trace. */
    private static String runAskOnly(Minecraft mc, CaseSpec spec, String only) {
        if (mc.player == null || mc.level == null) {
            return "NO_WORLD";
        }
        aimFromSpec(mc, spec);
        double range = PackAiConfig.entityFocusRange();
        FocusTarget t = "block".equals(only)
                ? FocusProbe.probeBlockOnly(mc, range)
                : FocusProbe.probeEntityOnly(mc, range);
        FocusAsk.askResolved(mc, t, only, "world", "focus");
        return focusStatus(mc, spec);
    }

    /** Config off: probe must not write {@code check.focus}. */
    private static String runFocusOff(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return "NO_WORLD";
        }
        Path dir = gameDir(mc);
        int before = countFocusEvents(dir);
        boolean was = PackAiConfig.focusProbeEnabled();
        try {
            PackAiConfig.setFocusProbeEnabled(false);
            double range = PackAiConfig.entityFocusRange();
            FocusProbe.probe(mc, range);
            FocusProbe.probeBlockOnly(mc, range);
            FocusProbe.probeEntityOnly(mc, range);
            int after = countFocusEvents(dir);
            if (after != before) {
                return "FOCUS_EMITTED";
            }
            return "OK";
        } finally {
            PackAiConfig.setFocusProbeEnabled(was);
        }
    }

    /** Entity-only probe, then the mixed probe when that misses. */
    private static FocusTarget probeEntity(Minecraft mc, double range) {
        FocusTarget seen = FocusProbe.probeEntityOnly(mc, range);
        if (seen.kind() != FocusTarget.Kind.ENTITY) {
            seen = FocusProbe.probe(mc, range);
        }
        return seen;
    }

    /** Walk just past entity range, re-resolve by id, expect TOO_FAR. Position restored after. */
    private static String runFaceAway(Minecraft mc, CaseSpec spec) {
        if (mc.player == null || mc.level == null) {
            return "NO_WORLD";
        }
        aimFromSpec(mc, spec);
        double range = PackAiConfig.entityFocusRange();
        FocusTarget seen = probeEntity(mc, range);
        if (seen.kind() != FocusTarget.Kind.ENTITY || seen.entityId() < 0) {
            String id = spec.item() == null ? "" : spec.item().trim();
            if (id.isEmpty()) {
                id = "minecraft:cow";
            }
            Entity mob = spawnTestMob(mc, id, 3.0);
            if (mob == null) {
                return "NO_ENTITY";
            }
            faceEntity(mc, mob);
            seen = probeEntity(mc, range);
        }
        if (seen.kind() != FocusTarget.Kind.ENTITY || seen.entityId() < 0) {
            return "NO_ENTITY";
        }
        Entity mob = mc.level.getEntity(seen.entityId());
        if (mob == null) {
            return "NO_ENTITY";
        }
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        double dx = mob.getX() - x;
        double dz = mob.getZ() - z;
        double L = Math.sqrt(dx * dx + dz * dz);
        if (L < 1e-4) {
            dx = 1.0;
            dz = 0.0;
            L = 1.0;
        }
        // Away along player→entity horizontal inverse; step past TOO_FAR band.
        double step = seen.distance() + range + 4.0;
        try {
            mc.player.setPos(x - dx / L * step, y, z - dz / L * step);
            FocusTarget tracked = FocusProbe.resolveTracked(mc, seen.entityId(), seen.label(), range);
            if (tracked.state() != FocusTarget.State.TOO_FAR) {
                return "NOT_TOO_FAR:" + tracked.state().name() + ":"
                        + String.format(Locale.ROOT, "%.2f", tracked.distance());
            }
            Path dir = gameDir(mc);
            if (dir != null) {
                AskTrace.begin(dir, "focus", tracked.id());
                try {
                    FocusTrace.emitStateChange(tracked, range, "entity", "world", "focus");
                } finally {
                    AskTrace.close("ok");
                }
            }
            String status = focusStatus(mc, spec);
            if (!"OK".equals(status)) {
                return status;
            }
            FocusRow row = latestFocus(gameDir(mc));
            if (row == null || !"TOO_FAR".equals(row.state)) {
                return "NOT_TOO_FAR:" + tracked.state().name() + ":"
                        + String.format(Locale.ROOT, "%.2f", tracked.distance());
            }
            return "OK";
        } finally {
            mc.player.setPos(x, y, z);
        }
    }

    private static String focusStatus(Minecraft mc, CaseSpec spec) {
        FocusRow row = latestFocus(gameDir(mc));
        if (row == null) {
            return "NO_FOCUS_TRACE";
        }
        if (row.kind.isEmpty() || row.winner.isEmpty() || row.state.isEmpty()) {
            return "FOCUS_FIELDS";
        }
        return mismatch(spec, row);
    }

    /** {@code null} mismatch means the row matches. Empty expect fields are skipped. */
    private static String mismatch(CaseSpec spec, FocusRow row) {
        if (!spec.expectKind().isEmpty() && !spec.expectKind().equals(row.kind)) {
            return "FOCUS_KIND";
        }
        if (!spec.expectWinner().isEmpty() && !spec.expectWinner().equals(row.winner)) {
            return "FOCUS_WINNER";
        }
        if (!spec.expectState().isEmpty() && !spec.expectState().equals(row.state)) {
            return "FOCUS_STATE";
        }
        if ("NONE".equals(row.kind) && "OK".equals(spec.expectKind())) {
            return "FOCUS_KIND";
        }
        return "OK";
    }

    private static int countFocusEvents(Path gameDir) {
        int n = 0;
        for (Path file : listAskTraces(gameDir)) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                int from = 0;
                while (true) {
                    int at = text.indexOf("check.focus", from);
                    if (at < 0) {
                        break;
                    }
                    n++;
                    from = at + 11;
                }
            } catch (IOException t) {
                PackAiMod.LOGGER.warn("[packai-autotest] read trace 失敗", t);
            }
        }
        return n;
    }

    private static List<Path> listAskTraces(Path gameDir) {
        List<Path> files = new ArrayList<>();
        if (gameDir == null) {
            return files;
        }
        Path traceDir = gameDir.resolve("packai").resolve("trace");
        if (!Files.isDirectory(traceDir)) {
            return files;
        }
        try (var stream = Files.list(traceDir)) {
            for (Path p : stream.toList()) {
                String name = p.getFileName().toString();
                if (name.startsWith("ask-") && name.endsWith(".jsonl")) {
                    files.add(p);
                }
            }
        } catch (IOException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] list trace 失敗", t);
        }
        return files;
    }

    /** Latest {@code check.focus} line that has kind, winner, and state (C7). */
    private static FocusRow latestFocus(Path gameDir) {
        return latestFocus(gameDir, null);
    }

    private static FocusRow latestFocus(Path gameDir, LocalDateTime since) {
        FocusRow found = null;
        long mtime = Long.MIN_VALUE;
        long cut = since == null
                ? Long.MIN_VALUE
                : since.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1000L;
        for (Path file : listAskTraces(gameDir)) {
            String text;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException t) {
                PackAiMod.LOGGER.warn("[packai-autotest] read trace 失敗", t);
                continue;
            }
            FocusRow row = null;
            for (String line : text.split("\n")) {
                if (!line.contains("check.focus")) {
                    continue;
                }
                try {
                    JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                    if (!o.has("kind") || !o.has("winner") || !o.has("state")) {
                        continue;
                    }
                    row = new FocusRow(
                            o.get("kind").getAsString(),
                            o.get("winner").getAsString(),
                            o.get("state").getAsString(),
                            file.getFileName().toString());
                } catch (RuntimeException t) {
                    PackAiMod.LOGGER.warn("[packai-autotest] parse check.focus 失敗", t);
                }
            }
            if (row == null) {
                continue;
            }
            long mod = 0;
            try {
                mod = Files.getLastModifiedTime(file).toMillis();
            } catch (IOException t) {
                PackAiMod.LOGGER.warn("[packai-autotest] stat trace 失敗", t);
            }
            if (mod < cut) {
                continue;
            }
            if (found == null || mod >= mtime) {
                mtime = mod;
                found = row;
            }
        }
        return found;
    }

    private static void pollCase(Minecraft mc) {
        CaseSpec spec = cases.get(caseIndex);
        if (armScreenCheck) {
            armScreenCheck = false;
            if (!(mc.screen instanceof AiAssistantScreen)) {
                results.add(new CaseResult(spec.id(), "NO_SCREEN", System.currentTimeMillis() - caseStartMs, 0, ""));
                noteCaseDone(mc, spec);
                caseIndex++;
                sub = Sub.START;
                return;
            }
        }
        Path dir = gameDir(mc);
        if (dir != null) {
            Judge judged = judge(dir, caseStartTs, spec);
            if (judged.status != null) {
                if (retryFastNoCardsIfNeeded(mc, spec, judged)) {
                    return;
                }
                results.add(new CaseResult(
                        spec.id(),
                        judged.status,
                        System.currentTimeMillis() - caseStartMs,
                        judged.cardsOut,
                        judged.traceFile));
                noteCaseDone(mc, spec);
                mc.setScreen(null);
                restTicks = 0;
                sub = Sub.REST;
                return;
            }
        }
        if (System.currentTimeMillis() - caseStartMs >= CASE_TIMEOUT_MS) {
            results.add(new CaseResult(spec.id(), "TIMEOUT", System.currentTimeMillis() - caseStartMs, 0, ""));
            noteCaseDone(mc, spec);
            ChatSession.clear();
            mc.setScreen(null);
            restTicks = 0;
            sub = Sub.REST;
        }
    }

    /**
     * Stale {@code display.body.final} from a prior case can yield fast {@code NO_CARDS} with no LLM
     * rounds; retry once before recording.
     *
     * @return {@code true} if the same case was re-scheduled
     */
    private static boolean retryFastNoCardsIfNeeded(Minecraft mc, CaseSpec spec, Judge judged) {
        long elapsed = System.currentTimeMillis() - caseStartMs;
        if (fastNoCardsRetried
                || judged == null
                || !"NO_CARDS".equals(judged.status)
                || judged.cardsOut != 0
                || judged.llmRoundsSeen != 0
                || elapsed >= 5000L) {
            return false;
        }
        fastNoCardsRetried = true;
        PackAiMod.LOGGER.info(
                "[packai-autotest] case_retry id={} reason=fast_no_cards attempt=2", spec.id());
        ChatSession.clear();
        mc.setScreen(null);
        traceSig = Long.MIN_VALUE;
        cachedJudge = null;
        caseStartMs = System.currentTimeMillis();
        caseStartTs = LocalDateTime.now();
        armScreenCheck = false;
        restTicks = 0;
        sub = Sub.START;
        return true;
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
        fastNoCardsRetried = false;
        sub = Sub.START;
    }

    /** After all cases: grab route library UI, then {@link #finish}. */
    private static void routeLibraryScreenshotHold(Minecraft mc) {
        routeLibraryHoldTicks++;
        if (routeLibraryHoldTicks == ROUTE_LIBRARY_SCREENSHOT_PRE_TICKS) {
            captureRouteLibraryScreenshot(mc);
        }
        if (routeLibraryHoldTicks < ROUTE_LIBRARY_SCREENSHOT_PRE_TICKS + ROUTE_LIBRARY_SCREENSHOT_POST_TICKS) {
            return;
        }
        if (beginOpenScreensIfAny(mc)) {
            return;
        }
        finish(mc, "DONE");
    }

    /** {@code true} if openScreens sequence started or still has entries. */
    private static boolean beginOpenScreensIfAny(Minecraft mc) {
        if (openScreens.isEmpty()) {
            return false;
        }
        advanceOpenScreens(mc);
        return sub == Sub.OPEN_SCREEN_SHOT;
    }

    private static void advanceOpenScreens(Minecraft mc) {
        while (openScreenIndex < openScreens.size()) {
            String name = openScreens.get(openScreenIndex);
            Screen screen = null;
            String skipReason = null;
            try {
                if (name != null && (name.equals("routeTree") || name.startsWith("routeTree:"))) {
                    List<SavedRoute> routes = RouteStore.load();
                    if (routes.isEmpty()) {
                        skipReason = "no routes";
                    } else {
                        SavedRoute picked = pickRouteForAutotest(routes);
                        if (picked == null) {
                            PackAiMod.LOGGER.info(
                                    "openScreen routeTree skip=route_not_found id={}", autotestRouteId);
                            openScreenIndex++;
                            continue;
                        }
                        RouteTreeScreen.autotestSetForceTruncated(forceTruncated);
                        RouteTreeScreen tree = new RouteTreeScreen(picked, null);
                        int colon = name.indexOf(':');
                        if (colon >= 0 && colon < name.length() - 1) {
                            tree.setViewByName(name.substring(colon + 1));
                        }
                        screen = tree;
                    }
                } else {
                    screen = screenForOpenName(name);
                    if (screen == null) {
                        skipReason = "unknown screen";
                    }
                }
            } catch (Throwable t) {
                skipReason = errorText(t);
            }
            if (skipReason != null) {
                PackAiMod.LOGGER.warn("[packai-autotest] skip {}: {}", name, skipReason);
                openScreenIndex++;
                continue;
            }
            mc.setScreen(screen);
            openScreenHoldTicks = 0;
            openScreenShotTaken = false;
            openScreenAssistantScrollApplied = false;
            sub = Sub.OPEN_SCREEN_SHOT;
            return;
        }
    }

    @Nullable
    private static SavedRoute pickRouteForAutotest(List<SavedRoute> routes) {
        if (autotestRouteId == null) {
            return routes.get(0);
        }
        List<SavedRoute> matches = new ArrayList<>();
        for (SavedRoute r : routes) {
            if (r != null && autotestRouteId.equals(r.itemId())) {
                matches.add(r);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        matches.sort(Comparator.comparing(
                (SavedRoute r) -> r.createdAt() == null ? "" : r.createdAt(),
                Comparator.nullsFirst(Comparator.<String>naturalOrder())).reversed());
        return matches.get(0);
    }

    private static boolean isRouteTreeOpenName(String name) {
        return name != null && (name.equals("routeTree") || name.startsWith("routeTree:"));
    }

    private static String openScreenShotFileName(String openName) {
        return openName.replace(':', '-') + ".png";
    }

    /**
     * Autotest openScreens: defer grab until assistant answer idle and scroll is at top.
     *
     * @return {@code true} if the harness should return without grabbing yet
     */
    private static boolean deferOpenScreenShotForAssistantScroll(Minecraft mc, String name) {
        if (!assistantScrollTop || !"assistant".equals(name)) {
            return false;
        }
        if (ChatSession.isBusy()) {
            return true;
        }
        if (openScreenAssistantScrollApplied) {
            return false;
        }
        if (!(mc.screen instanceof AiAssistantScreen screen)) {
            return false;
        }
        screen.autotestScrollToTop();
        PackAiMod.LOGGER.info("Pack AI autotest assistant_scroll top=true");
        openScreenAssistantScrollApplied = true;
        openScreenScrollWaitTicks = 3;
        return true;
    }

    private static Screen screenForOpenName(String name) {
        if (name == null) {
            return null;
        }
        switch (name) {
            case "assistant":
                return new AiAssistantScreen();
            case "settings":
                return new SettingsScreenV2(null);
            case "routeLibrary":
                return new RouteLibraryScreen();
            case "modelPicker":
                return new ModelPickerScreen(null);
            case "invPick":
                return new InvPickScreen(null);
            default:
                return null;
        }
    }

    private static void openScreenScreenshotHold(Minecraft mc) {
        if (openScreenScrollWaitTicks > 0) {
            openScreenScrollWaitTicks--;
            return;
        }
        if (openScreenIndex >= openScreens.size()) {
            finish(mc, "DONE");
            return;
        }
        openScreenHoldTicks++;
        String name = openScreens.get(openScreenIndex);
        boolean routeTree = isRouteTreeOpenName(name);
        if (routeTree && !skipGraphWait) {
            boolean ready = ObtainGraphRuntime.INSTANCE.ready();
            if (!ready && openScreenHoldTicks < 1200) {
                return;
            }
            if (!openScreenShotTaken && openScreenHoldTicks >= SCREENSHOT_PRE_TICKS) {
                try {
                    if (openScreenScrollY > 0 && mc.screen instanceof RouteTreeScreen && !openScreenScrollApplied) {
                        ((RouteTreeScreen) mc.screen).autotestScrollTo(openScreenScrollY);
                        openScreenScrollApplied = true;
                        openScreenScrollWaitTicks = 3;
                        return;
                    }
                    if (deferOpenScreenShotForAssistantScroll(mc, name)) {
                        return;
                    }
                    Screenshot.grab(
                            mc.gameDirectory, openScreenShotFileName(name), mc.getMainRenderTarget(), msg -> {});
                } catch (Throwable t) {
                    PackAiMod.LOGGER.warn("[packai-autotest] openScreens grab {} 失敗", name, t);
                }
                PackAiMod.LOGGER.info(
                        "shots {} graph_ready={} ticks={}", name, ready, openScreenHoldTicks);
                openScreenShotTaken = true;
            }
            if (!openScreenShotTaken || openScreenHoldTicks < SCREENSHOT_PRE_TICKS) {
                return;
            }
        } else {
            if (!openScreenShotTaken && openScreenHoldTicks >= SCREENSHOT_PRE_TICKS) {
                try {
                    if (openScreenScrollY > 0 && mc.screen instanceof RouteTreeScreen && !openScreenScrollApplied) {
                        ((RouteTreeScreen) mc.screen).autotestScrollTo(openScreenScrollY);
                        openScreenScrollApplied = true;
                        openScreenScrollWaitTicks = 3;
                        return;
                    }
                    if (deferOpenScreenShotForAssistantScroll(mc, name)) {
                        return;
                    }
                    Screenshot.grab(
                            mc.gameDirectory, openScreenShotFileName(name), mc.getMainRenderTarget(), msg -> {});
                } catch (Throwable t) {
                    PackAiMod.LOGGER.warn("[packai-autotest] openScreens grab {} 失敗", name, t);
                }
                openScreenShotTaken = true;
            }
            if (!openScreenShotTaken || openScreenHoldTicks < SCREENSHOT_PRE_TICKS) {
                return;
            }
        }
        openScreenIndex++;
        if (openScreenIndex < openScreens.size()) {
            advanceOpenScreens(mc);
            if (sub != Sub.OPEN_SCREEN_SHOT) {
                finish(mc, "DONE");
            }
            return;
        }
        finish(mc, "DONE");
    }

    private static void captureRouteLibraryScreenshot(Minecraft mc) {
        Path dir = gameDir(mc);
        if (dir == null) {
            return;
        }
        try {
            Screenshot.grab(mc.gameDirectory, (String) null, mc.getMainRenderTarget(), msg -> {});
            Path screenshots = dir.resolve("screenshots");
            if (!Files.isDirectory(screenshots)) {
                return;
            }
            Path latest = null;
            long latestTime = Long.MIN_VALUE;
            try (var stream = Files.list(screenshots)) {
                for (Path p : stream.toList()) {
                    String name = p.getFileName().toString();
                    if (!name.endsWith(".png")) {
                        continue;
                    }
                    long mod = Files.getLastModifiedTime(p).toMillis();
                    if (mod >= latestTime) {
                        latestTime = mod;
                        latest = p;
                    }
                }
            }
            if (latest == null) {
                return;
            }
            if (!Files.isDirectory(dir.resolve("packai").resolve("autotest"))) {
                PackAiMod.LOGGER.warn("[packai-autotest] packai/autotest 不存在，略過 route-library.png");
                return;
            }
            Files.copy(latest, mc.gameDirectory.toPath().resolve("packai").resolve("autotest").resolve("route-library.png"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            PackAiMod.LOGGER.warn("[packai-autotest] route library screenshot 失敗", t);
        }
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

    private static Judge judge(Path gameDir, LocalDateTime since, CaseSpec spec) {
        Judge cards = judge(gameDir, since, spec.item());
        if (spec == null || !spec.hasExpect()) {
            return cards;
        }
        FocusRow row = latestFocus(gameDir, since);
        if (row == null || row.kind.isEmpty() || row.winner.isEmpty() || row.state.isEmpty()) {
            return new Judge(null, cards.cardsOut, cards.traceFile, cards.llmRoundsSeen);
        }
        String status = mismatch(spec, row);
        if (!"OK".equals(status)) {
            return new Judge(status, cards.cardsOut, row.file, cards.llmRoundsSeen);
        }
        if ("NONE".equals(row.kind)) {
            if ("OK".equals(cards.status)) {
                return new Judge("FOCUS_HAS_CARDS", cards.cardsOut, row.file, cards.llmRoundsSeen);
            }
            return new Judge("OK", 0, row.file, cards.llmRoundsSeen);
        }
        return cards;
    }

    private static Judge judge(Path gameDir, LocalDateTime since, String itemId) {
        Path traceDir = gameDir.resolve("packai").resolve("trace");
        if (!Files.isDirectory(traceDir)) {
            return new Judge(null, 0, "", 0);
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
            return new Judge(null, 0, "", 0);
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
        int displayLlmRounds = 0;
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
                    ok = new Judge("OK", hit.cardsOut, file.getFileName().toString(), hit.llmRoundsSeen);
                }
            } else if (hit.display) {
                displayOnly = true;
                displayFile = file.getFileName().toString();
                displayLlmRounds = hit.llmRoundsSeen;
            }
        }
        if (ok != null) {
            return ok;
        }
        if (displayOnly) {
            return new Judge("NO_CARDS", 0, displayFile, displayLlmRounds);
        }
        return new Judge(null, 0, "", 0);
    }

    private static final class ScanState {
        long offset;
        boolean display;
        boolean cards;
        int cardsOut;
        int llmRoundsSeen;
        boolean found;
        long lastLen;
    }

    private static final Map<String, ScanState> SCAN_CACHE = new HashMap<>();

    private static int scanChannelOpensForTest;
    private static boolean scanCacheHitForTest;
    private static boolean forceFullFileScanForTest;

    static void clearScanCacheForTest() {
        SCAN_CACHE.clear();
        scanChannelOpensForTest = 0;
        scanCacheHitForTest = false;
        forceFullFileScanForTest = false;
    }

    static void setForceFullFileScanForTest(boolean force) {
        forceFullFileScanForTest = force;
    }

    static int scanChannelOpensForTest() {
        return scanChannelOpensForTest;
    }

    static boolean lastScanCacheHitForTest() {
        return scanCacheHitForTest;
    }

    static final class TraceScanHit {
        final boolean display;
        final boolean cards;
        final int cardsOut;

        TraceScanHit(boolean display, boolean cards, int cardsOut) {
            this.display = display;
            this.cards = cards;
            this.cardsOut = cardsOut;
        }
    }

    static TraceScanHit scanTraceFileForTest(Path file, LocalDateTime since, String itemId) {
        scanCacheHitForTest = false;
        LineHit hit = scanFile(file, since, itemId);
        return new TraceScanHit(hit.display, hit.cards, hit.cardsOut);
    }

    private static String scanCacheKey(Path file, LocalDateTime since, String itemId) {
        String item = itemId == null ? "" : itemId;
        return file.toAbsolutePath().normalize() + "\0" + since + "\0" + item;
    }

    private static LineHit scanFile(Path file, LocalDateTime since, String itemId) {
        long size;
        try {
            if (!Files.isRegularFile(file)) {
                SCAN_CACHE.remove(scanCacheKey(file, since, itemId));
                return new LineHit(false, false, 0, 0);
            }
            size = Files.size(file);
        } catch (IOException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] stat trace 失敗", t);
            SCAN_CACHE.remove(scanCacheKey(file, since, itemId));
            return new LineHit(false, false, 0, 0);
        }
        String key = scanCacheKey(file, since, itemId);
        ScanState state = SCAN_CACHE.get(key);
        if (state != null) {
            if (size == state.lastLen && !forceFullFileScanForTest) {
                scanCacheHitForTest = true;
                return new LineHit(state.display, state.cards, state.cardsOut, state.llmRoundsSeen);
            }
            if (size < state.lastLen) {
                SCAN_CACHE.remove(key);
                state = null;
            }
        }
        if (state == null) {
            state = new ScanState();
            SCAN_CACHE.put(key, state);
        }
        if (forceFullFileScanForTest) {
            state.offset = 0;
            state.display = false;
            state.cards = false;
            state.cardsOut = 0;
            state.llmRoundsSeen = 0;
            state.found = false;
        }
        try {
            if (forceFullFileScanForTest) {
                readAndApplyTraceBytes(file, 0, size, state, since, itemId);
                state.offset = size;
            } else {
                readAndApplyTraceBytes(file, state.offset, size, state, since, itemId);
            }
        } catch (IOException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] read trace 失敗", t);
            SCAN_CACHE.remove(key);
            return new LineHit(false, false, 0, 0);
        }
        state.lastLen = size;
        state.found = state.display && state.cards;
        return new LineHit(state.display, state.cards, state.cardsOut, state.llmRoundsSeen);
    }

    private static void readAndApplyTraceBytes(
            Path file, long start, long fileSize, ScanState state, LocalDateTime since, String itemId)
            throws IOException {
        if (start >= fileSize) {
            return;
        }
        scanChannelOpensForTest++;
        byte[] chunk;
        try (SeekableByteChannel ch = Files.newByteChannel(file, StandardOpenOption.READ)) {
            ch.position(start);
            int toRead = (int) Math.min(fileSize - start, Integer.MAX_VALUE);
            if (toRead < 0) {
                toRead = 0;
            }
            chunk = new byte[toRead];
            int off = 0;
            while (off < toRead) {
                int n = ch.read(ByteBuffer.wrap(chunk, off, toRead - off));
                if (n < 0) {
                    break;
                }
                off += n;
            }
            if (off < toRead) {
                chunk = off == 0 ? new byte[0] : java.util.Arrays.copyOf(chunk, off);
            }
        }
        if (chunk.length == 0) {
            state.offset = fileSize;
            return;
        }
        String text = new String(chunk, StandardCharsets.UTF_8);
        for (String rawLine : text.split("\n", -1)) {
            applyTraceLine(rawLine, state, since, itemId);
        }
        state.offset = fileSize;
    }

    private static void applyTraceLine(String rawLine, ScanState state, LocalDateTime since, String itemId) {
        String line = rawLine.trim();
        if (line.isEmpty()) {
            return;
        }
        JsonObject o;
        try {
            JsonElement el = JsonParser.parseString(line);
            if (!el.isJsonObject()) {
                return;
            }
            o = el.getAsJsonObject();
        } catch (RuntimeException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] parse trace line 失敗", t);
            return;
        }
        if (!newer(o, since)) {
            return;
        }
        String event = text(o, "event");
        if ("display.body.final".equals(event)) {
            state.display = true;
        } else if ("usage".equals(event) || "model.reply.final".equals(event)) {
            state.llmRoundsSeen++;
        } else if ("render.cards.final".equals(event)) {
            boolean match = (itemId == null || itemId.isBlank())
                    || itemId.equals(text(o, "item"));
            if (match) {
                state.cards = true;
                state.cardsOut = intProp(o, "cardsOut");
            }
        }
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
        runJeiCoverageIfRequested();
        runSpamAuditIfRequested();
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
            if (r.benchRuns() != null) {
                o.addProperty("runs", r.benchRuns());
                o.addProperty("allEqual", r.benchAllEqual() != null && r.benchAllEqual());
                JsonArray edgeArr = new JsonArray();
                if (r.benchEdges() != null) {
                    for (int e : r.benchEdges()) {
                        edgeArr.add(e);
                    }
                }
                o.add("edges", edgeArr);
                JsonArray shaArr = new JsonArray();
                if (r.benchShas() != null) {
                    for (String sha : r.benchShas()) {
                        shaArr.add(sha);
                    }
                }
                o.add("shas", shaArr);
            }
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
        START, POLL, REST, ROUTE_LIBRARY_SHOT, OPEN_SCREEN_SHOT
    }

    private record CaseSpec(
            String id,
            String item,
            String question,
            String action,
            String kind,
            int runs,
            String expectKind,
            String expectWinner,
            String expectState,
            String pitch,
            String yaw,
            String text
    ) {
        boolean hasExpect() {
            return !expectKind.isEmpty() || !expectWinner.isEmpty() || !expectState.isEmpty();
        }
    }

    private record CaseResult(
            String id,
            String status,
            long elapsedMs,
            int cardsOut,
            String traceFile,
            Integer benchRuns,
            List<Integer> benchEdges,
            List<String> benchShas,
            Boolean benchAllEqual) {

        CaseResult(String id, String status, long elapsedMs, int cardsOut, String traceFile) {
            this(id, status, elapsedMs, cardsOut, traceFile, null, null, null, null);
        }
    }

    /** {@code status == null} means keep polling. */
    private record Judge(String status, int cardsOut, String traceFile, int llmRoundsSeen) {}

    private record LineHit(boolean display, boolean cards, int cardsOut, int llmRoundsSeen) {}

    private record FocusRow(String kind, String winner, String state, String file) {}
}
