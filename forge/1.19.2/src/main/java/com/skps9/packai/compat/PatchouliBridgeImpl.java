package com.skps9.packai.compat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.datafixers.util.Pair;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import com.skps9.packai.logic.GuidebookEntry;
import com.skps9.packai.logic.GuidebookPins;
import com.skps9.packai.logic.PatchouliEntryScan;

import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.client.book.BookContents;
import vazkii.patchouli.client.book.BookEntry;
import vazkii.patchouli.client.book.BookPage;
import vazkii.patchouli.client.book.page.abstr.PageWithText;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.book.BookRegistry;

/**
 * Patchouli client book lookup. Loaded only via {@link PatchouliBridge} Class.forName.
 * Ctrl quick-search uses {@link BookContents#getEntryForStack} on a book in the hotbar;
 * we use the same map on every registered book (no inventory, no GUI).
 */
public final class PatchouliBridgeImpl {
    private PatchouliBridgeImpl() {}

    public static String lookupGuideText(ItemStack stack) {
        return lookupGuideText(stack, GuidebookPins.SCOPE_ANY_MOD, null);
    }

    public static String lookupGuideText(ItemStack stack, String guidebookScope, String itemNs) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String scope = GuidebookPins.normalizeScope(guidebookScope);
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String itemId = key == null ? "" : key.toString();
        List<GuidebookEntry> pins = new ArrayList<>();
        try {
            for (Book book : BookRegistry.INSTANCE.books.values()) {
                if (book == null) {
                    continue;
                }
                try {
                    var ext = book.getClass().getField("isExtension").getBoolean(book);
                    if (ext) {
                        continue;
                    }
                } catch (Throwable ignored) {
                    // continue
                }
                String bookNs = bookNamespace(book);
                if (GuidebookPins.SCOPE_SAME_MOD.equals(scope)
                        && itemNs != null
                        && !itemNs.isBlank()
                        && !bookNs.isEmpty()
                        && !bookNs.equalsIgnoreCase(itemNs)) {
                    continue;
                }
                BookContents contents = book.getContents();
                if (contents == null || contents.isErrored()) {
                    continue;
                }
                Pair<BookEntry, Integer> mapped = entryForStack(contents, stack);
                if (mapped == null || mapped.getFirst() == null) {
                    continue;
                }
                BookEntry entry = mapped.getFirst();
                String title = entryTitle(entry);
                String pages = pagesText(entry);
                if ((title == null || title.isBlank()) && (pages == null || pages.isBlank())) {
                    continue;
                }
                pins.add(GuidebookPins.apiFallbackEntry(
                        bookNs,
                        bookIdOf(book),
                        entryIdOf(entry),
                        title,
                        pages,
                        itemId));
            }
        } catch (Throwable t) {
            return "";
        }
        return GuidebookPins.formatPins(pins, itemId);
    }

    /**
     * Ctrl path first ({@code getEntryForStack} / StackWrapper NBT equals), then plain item,
     * then recipeMappings by Item identity (JEI stacks often carry extra NBT).
     */
    @SuppressWarnings("unchecked")
    private static Pair<BookEntry, Integer> entryForStack(BookContents contents, ItemStack stack) {
        Pair<BookEntry, Integer> mapped = contents.getEntryForStack(stack);
        if (mapped != null && mapped.getFirst() != null) {
            return mapped;
        }
        ItemStack plain = new ItemStack(stack.getItem());
        mapped = contents.getEntryForStack(plain);
        if (mapped != null && mapped.getFirst() != null) {
            return mapped;
        }
        try {
            Field f = BookContents.class.getDeclaredField("recipeMappings");
            f.setAccessible(true);
            Object raw = f.get(contents);
            if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
                return null;
            }
            var want = stack.getItem();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                ItemStack mappedStack = wrapperStack(e.getKey());
                if (mappedStack.isEmpty() || mappedStack.getItem() != want) {
                    continue;
                }
                Object v = e.getValue();
                if (v instanceof Pair<?, ?> p && p.getFirst() instanceof BookEntry) {
                    return (Pair<BookEntry, Integer>) p;
                }
            }
        } catch (Throwable ignored) {
            // ponytail: private recipeMappings — upgrade if Patchouli exposes iterator
        }
        return null;
    }

    private static ItemStack wrapperStack(Object key) {
        if (key instanceof ItemStack is) {
            return is;
        }
        if (key == null) {
            return ItemStack.EMPTY;
        }
        try {
            Field f = key.getClass().getField("stack");
            Object v = f.get(key);
            if (v instanceof ItemStack s) {
                return s;
            }
        } catch (Throwable ignored) {
            // try declared ItemStack fields
        }
        try {
            for (Field f : key.getClass().getDeclaredFields()) {
                if (!ItemStack.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                f.setAccessible(true);
                Object v = f.get(key);
                if (v instanceof ItemStack s) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
            // unmapped wrapper
        }
        return ItemStack.EMPTY;
    }

    private static String bookNamespace(Book book) {
        try {
            Object id = book.getClass().getField("id").get(book);
            if (id instanceof ResourceLocation rl) {
                return rl.getNamespace().toLowerCase(Locale.ROOT);
            }
            if (id != null) {
                String s = id.toString();
                int colon = s.indexOf(':');
                if (colon > 0) {
                    return s.substring(0, colon).toLowerCase(Locale.ROOT);
                }
            }
        } catch (Throwable ignored) {
            // unmapped
        }
        return "";
    }

    private static String bookIdOf(Book book) {
        try {
            Object id = book.getClass().getField("id").get(book);
            if (id instanceof ResourceLocation rl) {
                return rl.getPath();
            }
            if (id != null) {
                String s = id.toString();
                int colon = s.indexOf(':');
                return colon > 0 ? s.substring(colon + 1) : s;
            }
        } catch (Throwable ignored) {
            // unmapped
        }
        return "api";
    }

    private static String entryIdOf(BookEntry entry) {
        try {
            Object id = entry.getId();
            if (id instanceof ResourceLocation rl) {
                return rl.getPath();
            }
            if (id != null) {
                String s = id.toString();
                int colon = s.indexOf(':');
                return colon > 0 ? s.substring(colon + 1) : s;
            }
        } catch (Throwable ignored) {
            // unmapped
        }
        return "live";
    }

    private static String entryTitle(BookEntry entry) {
        try {
            String name = entry.getName().getString();
            if (name != null && !name.isBlank()) {
                return GuidebookPins.resolveDisplayString(name.trim());
            }
        } catch (Throwable ignored) {
            // headless / unloaded lang
        }
        return "";
    }

    private static String pagesText(BookEntry entry) {
        List<String> parts = new ArrayList<>();
        try {
            for (Object pageObj : entry.getPages()) {
                if (!(pageObj instanceof BookPage page)) {
                    continue;
                }
                if (page instanceof PageWithText pwt) {
                    String text = readPageText(pwt);
                    if (text != null && !text.isBlank()) {
                        parts.add(PatchouliEntryScan.stripMacros(text));
                    }
                    continue;
                }
                String title = pageTitle(page);
                if (!title.isBlank()) {
                    parts.add(title);
                }
            }
        } catch (Throwable t) {
            return "";
        }
        return String.join("\n", parts).trim();
    }

    private static String pageTitle(BookPage page) {
        try {
            var m = page.getClass().getMethod("getTitle");
            Object t = m.invoke(page);
            if (t instanceof Component c) {
                String s = c.getString();
                if (s != null && !s.isBlank()) {
                    return GuidebookPins.resolveDisplayString(s.trim());
                }
            }
        } catch (Throwable ignored) {
            // recipe pages often have no getTitle
        }
        return "";
    }

    private static String readPageText(PageWithText page) {
        try {
            Field f = PageWithText.class.getDeclaredField("text");
            f.setAccessible(true);
            Object v = f.get(page);
            if (!(v instanceof IVariable iv)) {
                return "";
            }
            try {
                Object comp = iv.as(Component.class);
                if (comp instanceof Component c) {
                    String s = c.getString();
                    if (s != null && !s.isBlank()) {
                        return GuidebookPins.resolveDisplayString(s.trim());
                    }
                }
            } catch (Throwable ignored) {
                // fall through to asString + I18n
            }
            String raw = iv.asString();
            return GuidebookPins.resolveDisplayString(raw == null ? "" : raw);
        } catch (Throwable ignored) {
            // ponytail: reflection on protected IVariable — upgrade if Patchouli exposes getter
        }
        return "";
    }
}
