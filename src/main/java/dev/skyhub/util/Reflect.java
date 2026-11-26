package dev.skyhub.util;

import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defensive, cached reflection helpers for crash-proof optional mod adapters.
 * Never throws checked exceptions; callers check null/Optional and degrade gracefully.
 */
public final class Reflect {
    private static final Map<String, Optional<Class<?>>> CLASS_CACHE = new ConcurrentHashMap<>();

    private Reflect() {}

    public static Class<?> findClass(String className) {
        return CLASS_CACHE.computeIfAbsent(className, name -> {
            try {
                return Optional.of(Class.forName(name, false, Reflect.class.getClassLoader()));
            } catch (Throwable ignored) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    public static Class<?> loadClass(String className) {
        try {
            return Class.forName(className, true, Reflect.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object getStaticField(Class<?> clazz, String fieldName) {
        if (clazz == null) return null;
        try {
            Field f = findField(clazz, fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object getField(Object target, String fieldName) {
        if (target == null) return null;
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean setField(Object target, String fieldName, Object value) {
        if (target == null) return false;
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null || Modifier.isFinal(f.getModifiers())) return false;
            f.setAccessible(true);
            f.set(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Field findField(Class<?> clazz, String fieldName) {
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            try {
                return cur.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    public static Object invoke(Object target, String methodName, Object... args) {
        if (target == null) return null;
        try {
            Method m = findMethod(target.getClass(), methodName, args);
            if (m == null) return null;
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object invokeStatic(Class<?> clazz, String methodName, Object... args) {
        if (clazz == null) return null;
        try {
            Method m = findMethod(clazz, methodName, args);
            if (m == null) return null;
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Method findMethod(Class<?> clazz, String methodName, Object... args) {
        int count = args == null ? 0 : args.length;
        // First pass: exact parameter count & assignable match in public methods (handles interfaces)
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == count && argsCompatible(m.getParameterTypes(), args)) {
                return m;
            }
        }
        // Second pass: declared methods up the hierarchy
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            for (Method m : cur.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == count && argsCompatible(m.getParameterTypes(), args)) {
                    return m;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static boolean argsCompatible(Class<?>[] paramTypes, Object[] args) {
        if (args == null) return paramTypes.length == 0;
        for (int i = 0; i < paramTypes.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                if (paramTypes[i].isPrimitive()) return false;
                continue;
            }
            Class<?> expected = wrapPrimitive(paramTypes[i]);
            if (!expected.isInstance(arg)) {
                return false;
            }
        }
        return true;
    }

    public static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    /**
     * Converts Minecraft Component, MoulConfig StructuredText, ResourcefulConfig TranslatableValue,
     * or arbitrary objects to a clean plain string with formatting codes stripped.
     */
    public static String extractText(Object obj) {
        if (obj == null) return "";
        if (obj instanceof String s) return stripFormatting(s);
        if (obj instanceof Component c) return stripFormatting(c.getString());
        // ResourcefulConfig TranslatableValue.toComponent()
        Object comp = invoke(obj, "toComponent");
        if (comp instanceof Component c) return stripFormatting(c.getString());
        // MoulConfig StructuredText.getText()
        Object text = invoke(obj, "getText");
        if (text instanceof String s) return stripFormatting(s);
        // TranslatableValue.toLocalizedString()
        Object loc = invoke(obj, "toLocalizedString");
        if (loc instanceof String s) return stripFormatting(s);
        return stripFormatting(obj.toString());
    }

    public static String stripFormatting(String s) {
        if (s == null) return "";
        return s.replaceAll("§.", "").trim();
    }

    public static void clearCache() {
        CLASS_CACHE.clear();
    }
}
