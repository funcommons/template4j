package fun.commons.template4j.utils;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPObject;
import com.alibaba.fastjson2.util.TypeUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class UType {

    private final static ConcurrentMap<String, Class<?>> classMappings = new ConcurrentHashMap<>();

    static {
        Class<?>[] classes = new Class[]{
                Object.class,
                Cloneable.class,
                Exception.class,
                RuntimeException.class,
                IllegalAccessError.class,
                IllegalAccessException.class,
                IllegalArgumentException.class,
                IllegalMonitorStateException.class,
                IllegalStateException.class,
                IllegalThreadStateException.class,
                IndexOutOfBoundsException.class,
                InstantiationError.class,
                InstantiationException.class,
                InternalError.class,
                InterruptedException.class,
                LinkageError.class,
                NegativeArraySizeException.class,
                NoClassDefFoundError.class,
                NoSuchFieldError.class,
                NoSuchFieldException.class,
                NoSuchMethodError.class,
                NoSuchMethodException.class,
                NullPointerException.class,
                NumberFormatException.class,
                OutOfMemoryError.class,
                SecurityException.class,
                StackOverflowError.class,
                StringIndexOutOfBoundsException.class,
                TypeNotPresentException.class,
                VerifyError.class,
                StackTraceElement.class,
                java.util.HashMap.class,
                java.util.Hashtable.class,
                java.util.TreeMap.class,
                java.util.IdentityHashMap.class,
                java.util.WeakHashMap.class,
                java.util.LinkedHashMap.class,
                java.util.HashSet.class,
                java.util.LinkedHashSet.class,
                java.util.TreeSet.class,
                java.util.ArrayList.class,
                TimeUnit.class,
                ConcurrentHashMap.class,
                java.util.concurrent.atomic.AtomicInteger.class,
                java.util.concurrent.atomic.AtomicLong.class,
                java.util.Collections.EMPTY_MAP.getClass(),
                Boolean.class,
                Character.class,
                Byte.class,
                Short.class,
                Integer.class,
                Long.class,
                Float.class,
                Double.class,
                Number.class,
                String.class,
                BigDecimal.class,
                java.math.BigInteger.class,
                java.util.BitSet.class,
                java.util.Calendar.class,
                java.util.Date.class,
                java.util.Locale.class,
                java.util.UUID.class,
                java.sql.Time.class,
                java.sql.Date.class,
                java.sql.Timestamp.class,
                java.text.SimpleDateFormat.class,
                JSONObject.class,
                JSONPObject.class,
                JSONArray.class,
        };

        for (Class clazz : classes) {
            classMappings.put(clazz.getSimpleName(), clazz);
            classMappings.put(clazz.getSimpleName().toLowerCase(), clazz);
        }

    }

    public static Class<?> loadClass(String className) {
        Class<?> clazz = classMappings.get(className);
        if (clazz != null) {
            return clazz;
        }
        return TypeUtils.loadClass(className);
    }

    public static Class<?> getClass(Object obj) {
        return obj == null ? null : obj.getClass();
    }

    public static <T> T castValue(Object value, Class<T> cls) {

        if (value == null) {
            return null;
        }

        if (value instanceof String && JSON.class == cls) {
            if (StringUtils.isBlank((String) value)) {
                return null;
            }
            return (T) JSON.parse(String.valueOf(value));
        }

        if (value instanceof String && JSONObject.class == cls) {
            if (StringUtils.isBlank((String) value)) {
                return null;
            }
            return (T) JSON.parseObject(String.valueOf(value));
        }

        if (value instanceof String && JSONArray.class == cls) {
            if (StringUtils.isBlank((String) value)) {
                return null;
            }
            return (T) JSON.parseArray(String.valueOf(value));
        }

        return TypeUtils.cast(value, cls);

    }

    public static Object castValue(Object value, String className) {


        if (value == null) {
            return null;
        }

        if ("JSONString".equalsIgnoreCase(className)) {
            return JSON.toJSONString(value);
        }

        return castValue(value, loadClass(className));

    }


        public static boolean testBoolean(Object testValue) {

        boolean test = true;

        if (testValue == null) {
            test = false;
        } else if (testValue instanceof Boolean) {
            test = (Boolean) testValue;
        } else if (testValue instanceof Number) {
            test = ((Number) testValue).longValue() > 0;
        } else if (testValue instanceof String strTest) {
            test = BooleanUtils.toBoolean(strTest) || strTest.matches("真|是");
        } else if (testValue instanceof Collection) {
            test = !((Collection) testValue).isEmpty();
        } else if (testValue instanceof Map) {
            test = !((Map) testValue).isEmpty();
        } else if (testValue.getClass().isArray()) {
            try {
                test = getArrayLength(testValue) > 0;
            } catch (Exception ignored) {
                test = false;
            }
        }
        return test;
    }

    public static int getArrayLength(Object array) {
        try {
            Method lengthMethod = Array.class.getMethod("getLength", Object.class);
            return (int) lengthMethod.invoke(null, array);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isTrue(Object testValue) {
        if (testValue == null) {
            return false;
        }
        if (testValue instanceof Boolean b) {
            return b;
        }
        if (testValue instanceof Integer i) {
            return BooleanUtils.toBooleanObject(i) == Boolean.TRUE;
        }
        return BooleanUtils.toBooleanObject(String.valueOf(testValue)) == Boolean.TRUE;
    }

    public static boolean isFalse(Object testValue) {
        if (testValue == null) {
            return false;
        }
        if (testValue instanceof Boolean b) {
            return !b;
        }
        if (testValue instanceof Integer i) {
            return BooleanUtils.toBooleanObject(i) == Boolean.FALSE;
        }
        return BooleanUtils.toBooleanObject(String.valueOf(testValue)) == Boolean.FALSE;
    }

}

