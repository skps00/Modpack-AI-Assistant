package com.skps9.packai.client.jei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.world.item.crafting.Ingredient;

/**
 * Read craft gates from the <em>original</em> recipe {@link Ingredient} when possible
 * (custom ingredient request objects), instead of guessing from JEI sample NBT alone.
 *
 * <p>Works via reflection — no hard dependency on SlashBlade / other mods. Recognizes
 * common accessors such as {@code request}/{@code getRequest}, and numeric fields
 * {@code killCount}/{@code refineCount}/{@code proudSoulCount} (and recipe JSON aliases
 * {@code kill}/{@code refine}/{@code proud_soul}).
 */
public final class RecipeIngredientGates {
    private RecipeIngredientGates() {}

    /**
     * Gate labels from the ingredient’s custom request definition, or empty if none.
     * Example: {@code refine≥100}, {@code kill≥50}, {@code broken}.
     */
    public static List<String> fromIngredient(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return List.of();
        }
        try {
            Object custom = unwrapCustomIngredient(ingredient);
            if (custom == null) {
                return List.of();
            }
            Object request = findRequest(custom);
            if (request != null) {
                List<String> fromReq = formatRequest(request);
                if (!fromReq.isEmpty()) {
                    return fromReq;
                }
            }
            // Custom ingredient itself may be the request-like record.
            return formatRequest(custom);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    static Object unwrapCustomIngredient(Ingredient ingredient) {
        // NeoForge: Optional<ICustomIngredient> getCustomIngredient()
        Object opt = invokeNoArg(ingredient, "getCustomIngredient");
        if (opt instanceof Optional<?> o) {
            return o.orElse(null);
        }
        if (opt != null) {
            return opt;
        }
        // Older / alternate: getCustomIngredient() returning the value directly, or field.
        Object field = readField(ingredient, "customIngredient");
        if (field instanceof Optional<?> o2) {
            return o2.orElse(null);
        }
        return field;
    }

    static Object findRequest(Object custom) {
        if (custom == null) {
            return null;
        }
        Object viaMethod = invokeNoArg(custom, "request", "getRequest", "requestDefinition", "getRequestDefinition");
        if (viaMethod != null) {
            return viaMethod;
        }
        Object viaField = readField(custom, "request", "requestDefinition");
        if (viaField != null) {
            return viaField;
        }
        // Looks like a request record itself (has refineCount / killCount).
        if (invokeNoArg(custom, "refineCount") != null || invokeNoArg(custom, "killCount") != null
                || invokeNoArg(custom, "proudSoulCount") != null) {
            return custom;
        }
        return null;
    }

    /**
     * Format a request-like object into short gate labels (package-visible for tests via python mirror).
     */
    @SuppressWarnings("unchecked")
    static List<String> formatRequest(Object request) {
        if (request == null) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();

        int kill = intProp(request, "killCount", "kill", "getKillCount");
        int proud = intProp(request, "proudSoulCount", "proudSoul", "proud_soul", "getProudSoulCount");
        int refine = intProp(request, "refineCount", "refine", "getRefineCount", "RepairCounter");

        if (kill > 0) {
            out.add("kill≥" + kill);
        }
        if (proud > 0) {
            out.add("proud_soul≥" + proud);
        }
        if (refine > 0) {
            out.add("refine≥" + refine);
        }

        Object name = invokeNoArg(request, "name", "getName");
        if (name != null) {
            String ns = name.toString();
            String lower = ns.toLowerCase(Locale.ROOT);
            if (!lower.isEmpty() && !lower.endsWith(":none") && !"none".equals(lower)) {
                // Prefer path after last colon for readability.
                int c = ns.indexOf(':');
                out.add("blade:" + (c >= 0 ? ns.substring(c + 1) : ns));
            }
        }

        Object types = invokeNoArg(request, "defaultType", "swordType", "sword_type", "getDefaultType", "getSwordType");
        if (types instanceof Collection<?> col) {
            for (Object t : col) {
                if (t == null) {
                    continue;
                }
                String s = t.toString();
                // Enum name() often BROKEN; toString may be same.
                if (s.contains(".")) {
                    s = s.substring(s.lastIndexOf('.') + 1);
                }
                s = s.toLowerCase(Locale.ROOT);
                if (!s.isEmpty() && !"none".equals(s)) {
                    out.add(s);
                }
            }
        } else if (types instanceof Object[] arr) {
            for (Object t : arr) {
                if (t == null) {
                    continue;
                }
                String s = t.toString().toLowerCase(Locale.ROOT);
                int dot = s.lastIndexOf('.');
                if (dot >= 0) {
                    s = s.substring(dot + 1);
                }
                if (!s.isEmpty() && !"none".equals(s)) {
                    out.add(s);
                }
            }
        }

        List<String> list = new ArrayList<>(out);
        if (list.size() > 8) {
            return List.copyOf(list.subList(0, 8));
        }
        return List.copyOf(list);
    }

    private static int intProp(Object target, String... names) {
        for (String name : names) {
            Object v = invokeNoArg(target, name);
            if (v instanceof Number n) {
                return n.intValue();
            }
            Object f = readField(target, name);
            if (f instanceof Number n2) {
                return n2.intValue();
            }
        }
        return 0;
    }

    private static Object invokeNoArg(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        Class<?> c = target.getClass();
        for (String name : methodNames) {
            try {
                Method m = c.getMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // try next
            }
            // Also search declared (package-private) on class hierarchy.
            Class<?> walk = c;
            while (walk != null && walk != Object.class) {
                try {
                    Method m = walk.getDeclaredMethod(name);
                    m.setAccessible(true);
                    return m.invoke(target);
                } catch (ReflectiveOperationException ignored) {
                    walk = walk.getSuperclass();
                }
            }
        }
        return null;
    }

    private static Object readField(Object target, String... fieldNames) {
        if (target == null) {
            return null;
        }
        Class<?> walk = target.getClass();
        while (walk != null && walk != Object.class) {
            for (String name : fieldNames) {
                try {
                    Field f = walk.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (ReflectiveOperationException ignored) {
                    // next
                }
            }
            walk = walk.getSuperclass();
        }
        return null;
    }
}
