package me.wolfii.allthelogs.client.script;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Exposes Java API types to JavaScript with field-style access ({@code entry.message}) and method
 * calls ({@code ChatQuery.all().withVersion("26.2")}).
 */
final class HostObjects {
    private static final Set<String> SKIP = Set.of(
        "wait", "notify", "notifyAll", "getClass", "hashCode", "equals", "clone", "finalize");

    private HostObjects() {
    }

    static Object wrap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof HostObject || value instanceof HostList || value instanceof HostType) {
            return value;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Character || value instanceof TemporalAccessor || value instanceof java.nio.file.Path) {
            return value.toString();
        }
        if (value instanceof Optional<?> optional) {
            return wrap(optional.orElse(null));
        }
        if (value instanceof Collection<?> collection) {
            return new HostList(List.copyOf(collection));
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> items = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                items.add(Array.get(value, i));
            }
            return new HostList(items);
        }
        if (value instanceof Class<?> type) {
            return new HostType(type);
        }
        return new HostObject(value);
    }

    static Object unwrap(Object value) {
        if (value instanceof Value guest) {
            if (guest.isNull()) {
                return null;
            }
            if (guest.isHostObject()) {
                return unwrap(guest.asHostObject());
            }
            if (guest.isProxyObject()) {
                Object proxy = guest.asProxyObject();
                if (proxy instanceof HostObject host) {
                    return host.target();
                }
                if (proxy instanceof HostType type) {
                    return type.type();
                }
            }
            if (guest.isString()) {
                return guest.asString();
            }
            if (guest.isBoolean()) {
                return guest.asBoolean();
            }
            if (guest.isNumber()) {
                if (guest.fitsInInt()) {
                    return guest.asInt();
                }
                if (guest.fitsInLong()) {
                    return guest.asLong();
                }
                return guest.asDouble();
            }
            return guest;
        }
        if (value instanceof HostObject host) {
            return host.target();
        }
        if (value instanceof HostType type) {
            return type.type();
        }
        return value;
    }

    static Object invoke(Object target, String name, Object[] args) {
        Object[] unwrapped = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            unwrapped[i] = unwrap(args[i]);
        }
        Method method = findMethod(target instanceof Class<?> type ? type : target.getClass(),
            name, unwrapped.length, target instanceof Class<?>);
        if (method == null) {
            throw new IllegalArgumentException("no method " + name + "/" + args.length + " on "
                + (target instanceof Class<?> type ? type.getName() : target.getClass().getName()));
        }
        try {
            Object receiver = Modifier.isStatic(method.getModifiers()) ? null : target;
            return wrap(method.invoke(receiver, coerce(unwrapped, method.getParameterTypes())));
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static Object[] coerce(Object[] args, Class<?>[] types) {
        Object[] coerced = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            coerced[i] = coerce(args[i], types[i]);
        }
        return coerced;
    }

    private static Object coerce(Object value, Class<?> type) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return value;
        }
        if (value instanceof Number number) {
            if (type == int.class || type == Integer.class) {
                return number.intValue();
            }
            if (type == long.class || type == Long.class) {
                return number.longValue();
            }
            if (type == double.class || type == Double.class) {
                return number.doubleValue();
            }
            if (type == float.class || type == Float.class) {
                return number.floatValue();
            }
            if (type == short.class || type == Short.class) {
                return number.shortValue();
            }
            if (type == byte.class || type == Byte.class) {
                return number.byteValue();
            }
        }
        if ((type == boolean.class || type == Boolean.class) && value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if (type == LocalDateTime.class) {
                return LocalDateTime.parse(text);
            }
            if (type == LocalDate.class) {
                return LocalDate.parse(text);
            }
        }
        if (type.isEnum() && value instanceof String name) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object constant = Enum.valueOf((Class<Enum>) type.asSubclass(Enum.class), name);
            return constant;
        }
        return value;
    }

    private static Method findMethod(Class<?> type, String name, int arity, boolean staticOnly) {
        Method fallback = null;
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || SKIP.contains(name) && method.getDeclaringClass() == Object.class) {
                continue;
            }
            if (staticOnly && !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getParameterCount() == arity) {
                return method;
            }
            if (method.isVarArgs() && arity >= method.getParameterCount() - 1) {
                fallback = method;
            }
        }
        return fallback;
    }

    static Set<String> members(Class<?> type, boolean staticOnly) {
        Set<String> names = new LinkedHashSet<>();
        for (Method method : type.getMethods()) {
            if (SKIP.contains(method.getName()) && method.getDeclaringClass() == Object.class) {
                continue;
            }
            if (staticOnly && !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            names.add(method.getName());
        }
        for (Class<?> nested : type.getClasses()) {
            names.add(nested.getSimpleName());
        }
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                names.add(((Enum<?>) constant).name());
            }
        }
        return names;
    }

    static final class HostObject implements ProxyObject {
        private final Object target;

        HostObject(Object target) {
            this.target = target;
        }

        Object target() {
            return target;
        }

        @Override
        public Object getMember(String key) {
            if ("toString".equals(key)) {
                return (ProxyExecutable) args -> target.toString();
            }
            Class<?> type = target.getClass();
            boolean zero = findMethod(type, key, 0, false) != null;
            boolean other = false;
            for (Method method : type.getMethods()) {
                if (method.getName().equals(key) && method.getParameterCount() != 0) {
                    other = true;
                    break;
                }
            }
            if (zero && !other) {
                return invoke(target, key, new Object[0]);
            }
            return (ProxyExecutable) args -> invoke(target, key, args);
        }

        @Override
        public Object getMemberKeys() {
            return members(target.getClass(), false).toArray(String[]::new);
        }

        @Override
        public boolean hasMember(String key) {
            return members(target.getClass(), false).contains(key) || "toString".equals(key);
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException("read-only");
        }

        @Override
        public String toString() {
            return String.valueOf(target);
        }
    }

    static final class HostType implements ProxyObject {
        private final Class<?> type;

        HostType(Class<?> type) {
            this.type = type;
        }

        Class<?> type() {
            return type;
        }

        @Override
        public Object getMember(String key) {
            if (type.isEnum()) {
                for (Object constant : type.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equals(key)) {
                        return wrap(constant);
                    }
                }
            }
            for (Class<?> nested : type.getClasses()) {
                if (nested.getSimpleName().equals(key)) {
                    return new HostType(nested);
                }
            }
            return (ProxyExecutable) args -> invoke(type, key, args);
        }

        @Override
        public Object getMemberKeys() {
            return members(type, true).toArray(String[]::new);
        }

        @Override
        public boolean hasMember(String key) {
            return members(type, true).contains(key);
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException("read-only");
        }
    }

    static final class HostList implements ProxyArray {
        private final List<?> items;

        HostList(List<?> items) {
            this.items = items;
        }

        @Override
        public Object get(long index) {
            return wrap(items.get((int) index));
        }

        @Override
        public void set(long index, Value value) {
            throw new UnsupportedOperationException("read-only");
        }

        @Override
        public long getSize() {
            return items.size();
        }
    }
}
