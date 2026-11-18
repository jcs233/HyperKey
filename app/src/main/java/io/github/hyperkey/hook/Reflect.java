package io.github.hyperkey.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class Reflect {
    private Reflect() {}
    static Object field(Object instance, String name) throws ReflectiveOperationException {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(instance); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    static void set(Object instance, String name, Object value) throws ReflectiveOperationException {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(instance, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    static Object call(Object instance, String name, Object... args) throws ReflectiveOperationException {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                Class<?>[] parameters = method.getParameterTypes();
                boolean match = true;
                for (int i = 0; i < args.length; i++) {
                    Class<?> p = parameters[i];
                    if (p == boolean.class) p = Boolean.class;
                    if (p == int.class) p = Integer.class;
                    if (p == long.class) p = Long.class;
                    if (args[i] != null && !p.isInstance(args[i])) { match = false; break; }
                }
                if (match) { method.setAccessible(true); return method.invoke(instance, args); }
            }
        }
        // SecurityCore's AndroidX group method names are shortened by R8 on this ROM.
        String alias = switch (name) {
            case "addPreference" -> "g";
            case "findPreference" -> "h";
            default -> null;
        };
        if (alias != null && instance.getClass().getName().contains("preference")) return call(instance, alias, args);
        throw new NoSuchMethodException(instance.getClass().getName() + "." + name);
    }
}
