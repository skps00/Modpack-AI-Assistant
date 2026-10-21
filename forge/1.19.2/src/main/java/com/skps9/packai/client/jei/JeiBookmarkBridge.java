package com.skps9.packai.client.jei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.routes.ItemManagerBridge;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * JEI bookmark list injection across P1 (11.64+), P2 (11.8), P3 (11.6). Internal JEI types via reflection only.
 */
public final class JeiBookmarkBridge implements ItemManagerBridge {
    private static final String BOOKMARK_LIST_CN = "mezz.jei.gui.bookmarks.BookmarkList";
    private static final String INGREDIENT_BOOKMARK_CN = "mezz.jei.gui.bookmarks.IngredientBookmark";
    private static final String I_BOOKMARK_CN = "mezz.jei.gui.bookmarks.IBookmark";
    private static final String I_ELEMENT_CN = "mezz.jei.gui.overlay.elements.IElement";

    private enum Profile {
        P1,
        P2,
        P3,
        NONE
    }

    private static String lastProfileCode = "NONE";

    private static String lastClearMode = "NONE";

    private static final String CLEAR_MODE_SILENT = "SILENT";

    private static final String F_BOOKMARKS_LIST = "bookmarksList";
    private static final String F_BOOKMARKS_SET = "bookmarksSet";
    private static final String F_INGREDIENT_LIST = "ingredientList";
    /** JEI 11.6 {@code BookmarkList} backing list field (javap). */
    private static final String F_LIST_LEGACY = "list";

    private static final List<String> LAST_TRACE = new ArrayList<>();

    private final Map<String, Object> ours = new LinkedHashMap<>();

    /** Copy of the last {@link #detectProfile} diagnostic trace (may be empty). */
    public static List<String> trace() {
        return new ArrayList<>(LAST_TRACE);
    }

    private static void traceAdd(String line) {
        try {
            LAST_TRACE.add(line);
        } catch (RuntimeException ignored) {
            // diagnostic only
        }
    }

    private static Field declaredFieldOrNull(Class<?> clz, String... names) {
        for (String name : names) {
            try {
                Field f = clz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // try next
            }
        }
        return null;
    }

    private Method mAddWithoutNotify;
    private Method mAddToList;
    private Method mContains;
    private Method mRemoveBookmark;
    private Method mRemoveTyped;
    private Method mNotify;
    private Method mGetElements;
    private Method mGetIngredientList;
    private Method mGetTypedIngredient;
    private Method mGetElement;
    private Method mIngredientBookmarkCreate;
    private Method mRuntimeCreateTyped;
    private Method mManagerCreateTyped;
    private Field fBookmarkList;
    private Class<?> iBookmarkClass;

    @Override
    public Capability capability() {
        return profileToCapability(detectProfile(null).profile);
    }

    /** Last {@link #detectProfile} result; never null (NONE when unavailable). */
    public static String profileCode() {
        return lastProfileCode;
    }

    /** {@link #CLEAR_MODE_SILENT} or NONE — last {@link #clear()} path. */
    public static String lastClearMode() {
        return lastClearMode;
    }

    @Override
    public int inject(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return 0;
        }
        Detected d = detectProfile(null);
        if (d.profile == Profile.NONE || d.list == null) {
            return 0;
        }
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return 0;
        }
        IJeiRuntime rt = opt.get();
        int ok = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            try {
                ITypedIngredient<?> typed = createTyped(rt, stack);
                if (typed == null) {
                    continue;
                }
                String uid = uid(stack);
                if (d.profile == Profile.P3) {
                    if (mAddToList == null) {
                        continue;
                    }
                    if (mGetIngredientList != null) {
                        List<?> ingList = (List<?>) mGetIngredientList.invoke(d.list);
                        if (ingList != null && p3ListContainsUid(ingList, uid)) {
                            continue;
                        }
                    }
                    mAddToList.invoke(d.list, typed, false);
                    ours.put(uid, typed);
                    ok++;
                } else {
                    Object bookmark = createBookmark(typed, rt.getIngredientManager());
                    if (bookmark == null || mContains == null || mAddWithoutNotify == null) {
                        continue;
                    }
                    if ((boolean) mContains.invoke(d.list, bookmark)) {
                        continue;
                    }
                    mAddWithoutNotify.invoke(d.list, bookmark, false);
                    ours.put(uid, bookmark);
                    ok++;
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                PackAiMod.LOGGER.debug("[packai] JeiBookmarkBridge inject skip", e);
            }
        }
        if (ok > 0) {
            try {
                if (mNotify != null) {
                    mNotify.invoke(d.list);
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                PackAiMod.LOGGER.debug("[packai] JeiBookmarkBridge notify after inject", e);
            }
        }
        return ok;
    }

    @Override
    public int clear() {
        Detected d = detectProfile(null);
        if (d.profile == Profile.NONE || d.list == null || ours.isEmpty()) {
            ours.clear();
            lastClearMode = "NONE";
            return 0;
        }
        List<Object> values = new ArrayList<>(ours.values());
        ours.clear();
        lastClearMode = CLEAR_MODE_SILENT;
        int removed = clearSilent(d, values);
        try {
            if (mNotify != null) {
                mNotify.invoke(d.list);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            PackAiMod.LOGGER.debug("[packai] JeiBookmarkBridge notify after clear", e);
        }
        return removed;
    }

    /** Remove tracked entries without calling BookmarkList.remove (avoids saveBookmarks). */
    private static int clearSilent(Detected d, List<Object> values) {
        if (values.isEmpty()) {
            return 0;
        }
        try {
            if (d.profile == Profile.P3) {
                return clearSilentP3(d.list, values);
            }
            if (d.profile == Profile.P1 || d.profile == Profile.P2) {
                return clearSilentP12(d.list, values);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            traceAdd("clear miss: " + values.size());
            return 0;
        }
        traceAdd("clear miss: " + values.size());
        return 0;
    }

    private static Integer clearSilentP12(Object list, List<Object> values)
            throws ReflectiveOperationException {
        Class<?> clz = list.getClass();
        Field fList;
        Field fSet;
        try {
            fList = clz.getDeclaredField(F_BOOKMARKS_LIST);
            fSet = clz.getDeclaredField(F_BOOKMARKS_SET);
        } catch (NoSuchFieldException e) {
            traceAdd("clear miss: " + values.size());
            return 0;
        }
        fList.setAccessible(true);
        fSet.setAccessible(true);
        Object listRaw = fList.get(list);
        Object setRaw = fSet.get(list);
        if (!(listRaw instanceof List<?> bookmarksList)) {
            traceAdd("clear miss: " + values.size());
            return 0;
        }
        if (!(setRaw instanceof Set<?> bookmarksSet)) {
            traceAdd("clear miss: " + values.size());
            return 0;
        }
        int removed = 0;
        for (Object tracked : values) {
            boolean hit = bookmarksList.remove(tracked) | bookmarksSet.remove(tracked);
            if (hit) {
                removed++;
            }
        }
        int miss = values.size() - removed;
        if (miss > 0) {
            traceAdd("clear miss: " + miss);
        }
        return removed;
    }

    private static int clearSilentP3(Object list, List<Object> values)
            throws ReflectiveOperationException {
        List<?> ingredientList = p3IngredientList(list);
        if (ingredientList == null) {
            traceAdd("clear miss: " + values.size());
            return 0;
        }
        int removed = removeP3TrackedByUid(ingredientList, values);
        int miss = values.size() - removed;
        if (miss > 0) {
            traceAdd("clear miss: " + miss);
        }
        return removed;
    }

    /** P3: JEI 11.6 stores normalized {@link ITypedIngredient} instances — match by item uid, not identity. */
    private static int removeP3TrackedByUid(List<?> ingredientList, List<Object> values) {
        Set<String> trackedUids = new HashSet<>();
        for (Object tracked : values) {
            String u = uidOfTyped(tracked);
            if (u != null) {
                trackedUids.add(u);
            }
        }
        if (trackedUids.isEmpty()) {
            return 0;
        }
        int removed = 0;
        Iterator<?> it = ingredientList.iterator();
        while (it.hasNext()) {
            Object el = it.next();
            String u = uidOfTyped(el);
            if (u != null && trackedUids.contains(u)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    private static List<?> p3IngredientList(Object list) throws ReflectiveOperationException {
        Class<?> clz = list.getClass();
        Field fIng = declaredFieldOrNull(clz, F_INGREDIENT_LIST, F_LIST_LEGACY);
        if (fIng == null) {
            traceAdd("clear P3: missing " + F_INGREDIENT_LIST + "/" + F_LIST_LEGACY);
            return null;
        }
        Object listRaw = fIng.get(list);
        if (!(listRaw instanceof List<?> ingredientList)) {
            traceAdd("clear P3: " + fIng.getName() + " not a List");
            return null;
        }
        return ingredientList;
    }

    private static boolean p3ListContainsUid(List<?> ingList, String uid) {
        if (uid == null || ingList == null) {
            return false;
        }
        for (Object el : ingList) {
            if (uid.equals(uidOfTyped(el))) {
                return true;
            }
        }
        return false;
    }

    /** Registry ids of bookmark list in display order (debug / order checks). */
    public List<String> currentOrder() {
        Detected d = detectProfile(null);
        if (d.profile == Profile.NONE || d.list == null) {
            return List.of();
        }
        try {
            List<?> elements;
            if (d.profile == Profile.P3) {
                if (mGetIngredientList == null) {
                    return List.of();
                }
                elements = (List<?>) mGetIngredientList.invoke(d.list);
            } else {
                if (mGetElements == null) {
                    return List.of();
                }
                elements = (List<?>) mGetElements.invoke(d.list);
            }
            if (elements == null) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (Object el : elements) {
                String id = ingredientIdFromElement(el);
                if (id != null) {
                    out.add(id);
                }
            }
            return out != null ? out : List.of();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            PackAiMod.LOGGER.debug("[packai] JeiBookmarkBridge currentOrder", e);
            return List.of();
        }
    }

    private static Capability profileToCapability(Profile p) {
        return switch (p) {
            case P1, P2 -> Capability.FULL;
            case P3 -> Capability.APPEND_ONLY;
            case NONE -> Capability.NONE;
        };
    }

    private record Detected(Profile profile, Object list) {}

    private static void rememberProfile(Profile profile) {
        lastProfileCode = profile == Profile.NONE ? "NONE" : profile.name();
    }

    private Detected detectProfile(IJeiRuntime ignored) {
        LAST_TRACE.clear();
        traceAdd("detectProfile: start");
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        traceAdd("runtime present: " + opt.isPresent());
        if (opt.isEmpty()) {
            rememberProfile(Profile.NONE);
            traceAdd("final profile: NONE");
            return new Detected(Profile.NONE, null);
        }
        IJeiRuntime runtime = opt.get();
        try {
            Method getBm = null;
            try {
                getBm = runtime.getClass().getMethod("getBookmarkManager");
                traceAdd("P1 getMethod getBookmarkManager: ok");
            } catch (NoSuchMethodException ignoredNs) {
                traceAdd("P1 getMethod getBookmarkManager: NoSuchMethodException");
            }
            if (getBm != null) {
                Object list;
                try {
                    list = getBm.invoke(runtime);
                    traceAdd("getBookmarkManager invoke: "
                            + (list == null ? "null" : list.getClass().getName()));
                } catch (ReflectiveOperationException e) {
                    traceAdd("getBookmarkManager invoke: " + e.getClass().getName());
                    throw e;
                }
                boolean bookmarkListCn = list != null && BOOKMARK_LIST_CN.equals(list.getClass().getName());
                traceAdd("BOOKMARK_LIST_CN match: " + bookmarkListCn);
                if (bookmarkListCn) {
                    boolean boundP1 = bindListApis(list, Profile.P1);
                    traceAdd("bindListApis(P1): " + boundP1);
                    if (boundP1) {
                        Detected d = new Detected(Profile.P1, list);
                        rememberProfile(d.profile);
                        traceAdd("final profile: P1");
                        return d;
                    }
                }
            }
            Object overlay = runtime.getBookmarkOverlay();
            traceAdd("overlay null: " + (overlay == null));
            if (overlay == null) {
                rememberProfile(Profile.NONE);
                traceAdd("final profile: NONE");
                return new Detected(Profile.NONE, null);
            }
            traceAdd("overlay class: " + overlay.getClass().getName());
            if (fBookmarkList == null) {
                try {
                    fBookmarkList = overlay.getClass().getDeclaredField("bookmarkList");
                    fBookmarkList.setAccessible(true);
                    traceAdd("declaredField bookmarkList: found");
                } catch (NoSuchFieldException e) {
                    traceAdd("declaredField bookmarkList: " + e.getClass().getName());
                    throw e;
                }
            } else {
                traceAdd("declaredField bookmarkList: cached");
            }
            Object list;
            try {
                list = fBookmarkList.get(overlay);
                traceAdd("field.get bookmarkList: "
                        + (list == null ? "null" : list.getClass().getName()));
            } catch (IllegalAccessException e) {
                traceAdd("field.get bookmarkList: " + e.getClass().getName());
                throw e;
            }
            if (list == null) {
                rememberProfile(Profile.NONE);
                traceAdd("final profile: NONE");
                return new Detected(Profile.NONE, null);
            }
            try {
                Class<?> iBookmark = Class.forName(I_BOOKMARK_CN);
                try {
                    list.getClass().getMethod("addToListWithoutNotifying", iBookmark, boolean.class);
                    traceAdd("P2 getMethod addToListWithoutNotifying: ok");
                } catch (NoSuchMethodException e) {
                    traceAdd("P2 getMethod addToListWithoutNotifying: " + e.getClass().getName());
                    throw e;
                }
                boolean boundP2 = bindListApis(list, Profile.P2);
                traceAdd("bindListApis(P2): " + boundP2);
                if (boundP2) {
                    Detected d = new Detected(Profile.P2, list);
                    rememberProfile(d.profile);
                    traceAdd("final profile: P2");
                    return d;
                }
            } catch (ClassNotFoundException e) {
                traceAdd("P2 Class.forName IBookmark: " + e.getClass().getName());
            } catch (NoSuchMethodException ignoredNs) {
                // try P3
            }
            try {
                list.getClass().getMethod("addToList", ITypedIngredient.class, boolean.class);
                traceAdd("P3 getMethod addToList: ok");
                boolean boundP3 = bindListApis(list, Profile.P3);
                traceAdd("bindListApis(P3): " + boundP3);
                if (boundP3) {
                    Detected d = new Detected(Profile.P3, list);
                    rememberProfile(d.profile);
                    traceAdd("final profile: P3");
                    return d;
                }
            } catch (NoSuchMethodException e) {
                traceAdd("P3 getMethod addToList: " + e.getClass().getName());
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            traceAdd("detectProfile exception: " + e.getClass().getName());
            PackAiMod.LOGGER.debug("[packai] JeiBookmarkBridge profile detect", e);
        }
        rememberProfile(Profile.NONE);
        traceAdd("final profile: NONE");
        return new Detected(Profile.NONE, null);
    }

    private boolean bindListApis(Object list, Profile profile) {
        try {
            Class<?> listClz = list.getClass();
            if (profile == Profile.P3) {
                mAddToList = listClz.getMethod("addToList", ITypedIngredient.class, boolean.class);
                mRemoveTyped = listClz.getMethod("remove", ITypedIngredient.class);
                mGetIngredientList = listClz.getMethod("getIngredientList");
                mNotify = listClz.getMethod("notifyListenersOfChange");
                if (mGetTypedIngredient == null) {
                    try {
                        Class<?> iElementClass = Class.forName(I_ELEMENT_CN);
                        mGetTypedIngredient = iElementClass.getMethod("getTypedIngredient");
                    } catch (ReflectiveOperationException | LinkageError ignored) {
                        // JEI 11.8+: optional; order read skips rows when absent
                    }
                }
                return true;
            }
            if (iBookmarkClass == null) {
                iBookmarkClass = Class.forName(I_BOOKMARK_CN);
            }
            mAddWithoutNotify = listClz.getMethod("addToListWithoutNotifying", iBookmarkClass, boolean.class);
            mContains = listClz.getMethod("contains", iBookmarkClass);
            mRemoveBookmark = listClz.getMethod("remove", iBookmarkClass);
            mGetElements = listClz.getMethod("getElements");
            mNotify = listClz.getMethod("notifyListenersOfChange");
            if (mGetTypedIngredient == null) {
                try {
                    Class<?> iElementClass = Class.forName(I_ELEMENT_CN);
                    mGetTypedIngredient = iElementClass.getMethod("getTypedIngredient");
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // JEI 11.8+: optional; order read skips rows when absent
                }
            }
            if (mGetElement == null) {
                try {
                    mGetElement = iBookmarkClass.getMethod("getElement");
                } catch (NoSuchMethodException ignored) {
                    // optional
                }
            }
            if (mIngredientBookmarkCreate == null) {
                try {
                    Class<?> ibClz = Class.forName(INGREDIENT_BOOKMARK_CN);
                    mIngredientBookmarkCreate = ibClz.getMethod("create", ITypedIngredient.class,
                            IIngredientManager.class);
                } catch (ClassNotFoundException e) {
                    traceAdd("bindListApis: IngredientBookmark ClassNotFoundException (optional)");
                } catch (NoSuchMethodException e) {
                    traceAdd("bindListApis: IngredientBookmark.create NoSuchMethodException (optional)");
                }
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            PackAiMod.LOGGER.debug("[packai] JeiBookmarkBridge bindListApis", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private ITypedIngredient<?> createTyped(IJeiRuntime rt, ItemStack stack) {
        try {
            if (mRuntimeCreateTyped == null) {
                try {
                    mRuntimeCreateTyped = IJeiRuntime.class.getMethod("createTypedIngredient",
                            mezz.jei.api.ingredients.IIngredientType.class, Object.class);
                } catch (NoSuchMethodException ignored) {
                    mRuntimeCreateTyped = null;
                }
            }
            if (mRuntimeCreateTyped != null) {
                ITypedIngredient<?> typed = unwrapTyped(mRuntimeCreateTyped.invoke(rt, VanillaTypes.ITEM_STACK, stack));
                if (typed != null) {
                    return typed;
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            PackAiMod.LOGGER.debug("[packai] JeiBookmarkBridge runtime createTypedIngredient", e);
        }
        try {
            IIngredientManager mgr = rt.getIngredientManager();
            if (mManagerCreateTyped == null) {
                mManagerCreateTyped = IIngredientManager.class.getMethod("createTypedIngredient",
                        mezz.jei.api.ingredients.IIngredientType.class, Object.class);
            }
            ITypedIngredient<?> typed = unwrapTyped(mManagerCreateTyped.invoke(mgr, VanillaTypes.ITEM_STACK, stack));
            if (typed != null) {
                return typed;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            PackAiMod.LOGGER.debug("[packai] JeiBookmarkBridge manager createTypedIngredient", e);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static ITypedIngredient<?> unwrapTyped(Object raw) {
        if (raw instanceof ITypedIngredient<?> typed) {
            return typed;
        }
        if (raw instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof ITypedIngredient<?> typed) {
            return typed;
        }
        return null;
    }

    private Object createBookmark(ITypedIngredient<?> typed, IIngredientManager manager) {
        try {
            if (mIngredientBookmarkCreate == null) {
                Class<?> ibClz = Class.forName(INGREDIENT_BOOKMARK_CN);
                mIngredientBookmarkCreate = ibClz.getMethod("create", ITypedIngredient.class, IIngredientManager.class);
            }
            return mIngredientBookmarkCreate.invoke(null, typed, manager);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            PackAiMod.LOGGER.debug("[packai] JeiBookmarkBridge IngredientBookmark.create", e);
            return null;
        }
    }

    private String ingredientIdFromElement(Object el) {
        if (el == null) {
            return null;
        }
        try {
            String id = ingredientIdFromIngredientHost(el);
            if (id != null) {
                return id;
            }
            if (mGetElement != null) {
                Object element = mGetElement.invoke(el);
                if (element != null) {
                    return ingredientIdFromIngredientHost(element);
                }
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            PackAiMod.LOGGER.debug("[packai] JeiBookmarkBridge ingredientIdFromElement", e);
            return null;
        }
    }

    private String ingredientIdFromIngredientHost(Object host) {
        if (host == null) {
            return null;
        }
        try {
            Object typed;
            if (host instanceof ITypedIngredient<?> ti) {
                typed = ti;
            } else if (mGetTypedIngredient != null) {
                typed = mGetTypedIngredient.invoke(host);
            } else {
                return null;
            }
            if (!(typed instanceof ITypedIngredient<?> ing)) {
                return null;
            }
            Optional<ItemStack> opt = ing.getIngredient(VanillaTypes.ITEM_STACK);
            ItemStack stack = opt.orElse(ItemStack.EMPTY);
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            return uid(stack);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            PackAiMod.LOGGER.debug("[packai] JeiBookmarkBridge ingredientIdFromIngredientHost", e);
            return null;
        }
    }

    public static String uid(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        }
        return id != null ? id.toString() : "unknown";
    }

    /** Item registry id for a bookmark row ingredient; null when not an item or empty. */
    public static String uidOfTyped(Object typed) {
        if (!(typed instanceof ITypedIngredient<?> ing)) {
            return null;
        }
        try {
            Optional<ItemStack> opt = ing.getIngredient(VanillaTypes.ITEM_STACK);
            ItemStack stack = opt.orElse(ItemStack.EMPTY);
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            return uid(stack);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
