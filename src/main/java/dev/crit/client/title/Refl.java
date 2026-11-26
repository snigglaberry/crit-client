package dev.crit.client.title;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Tiny reflection helpers for the few game calls whose names or signatures change between Minecraft versions
 * (joining a world or server). Looking them up by name keeps the client compiling, and every caller has a fallback.
 */
final class Refl {
    /** Calls the instance method `name` that accepts these arguments. */
    static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        Method m = find(target.getClass(), name, args, false);
        return m.invoke(target, args);
    }

    /** Calls the static method `name` that accepts these arguments. */
    static Object callStatic(Class<?> type, String name, Object... args) throws ReflectiveOperationException {
        Method m = find(type, name, args, true);
        return m.invoke(null, args);
    }

    /** A text value read from a field or no-argument method called `name` (also tries getName() style). */
    static String text(Object target, String name) {
        if (target == null) return "";
        try {
            Object v = target.getClass().getField(name).get(target);
            if (v != null) return String.valueOf(v);
        } catch (ReflectiveOperationException ignored) {
        }
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String n : new String[]{name, getter}) {
            try {
                Object v = target.getClass().getMethod(n).invoke(target);
                if (v != null) return String.valueOf(v);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return "";
    }

    private static Method find(Class<?> type, String name, Object[] args, boolean wantStatic) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                if (Modifier.isStatic(m.getModifiers()) != wantStatic || !accepts(m.getParameterTypes(), args)) continue;
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static boolean accepts(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            Object a = args[i];
            if (a == null) {
                if (t.isPrimitive()) return false;
            } else if (!box(t).isInstance(a)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == boolean.class) return Boolean.class;
        if (t == int.class) return Integer.class;
        if (t == long.class) return Long.class;
        if (t == float.class) return Float.class;
        if (t == double.class) return Double.class;
        return t;
    }

    private Refl() {}
}
