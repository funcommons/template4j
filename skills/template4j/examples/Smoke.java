import fun.commons.template4j.core.RenderEngine;

import java.util.List;
import java.util.Map;

/**
 * 5-case smoke test for template4j. Run via scripts/smoke.sh.
 */
public class Smoke {
    public static void main(String[] args) {
        RenderEngine e = RenderEngine.build();
        int passed = 0, failed = 0;
        try { check("string-placeholder", e, "{\"greeting\": \"Hello, {@name}!\"}",
                Map.of("name", "World"), Map.of("greeting", "Hello, World!")); passed++; }
        catch (Throwable t) { failed++; t.printStackTrace(); }

        try { check("groovy-expr", e, "{\"doubled\": \"{= @n * 2 }\"}",
                Map.of("n", 21), Map.of("doubled", 42)); passed++; }
        catch (Throwable t) { failed++; t.printStackTrace(); }

        try { check("if-pass", e, "{\"@class\":\"if\",\"select\":\"true\",\"is\":\"true\",\"then\":{\"result\":\"pass\"},\"else\":{\"result\":\"fail\"}}",
                Map.of(), Map.of("result", "pass")); passed++; }
        catch (Throwable t) { failed++; t.printStackTrace(); }

        try { check("each", e, "{\"@class\":\"each\",\"select\":\"{@items}\",\"body\":{\"v\":\"{$item}\"}}",
                Map.of("items", List.of("a", "b", "c")),
                List.of(Map.of("v", "a"), Map.of("v", "b"), Map.of("v", "c"))); passed++; }
        catch (Throwable t) { failed++; t.printStackTrace(); }

        try { check("nulls-raw", e, "{\"name\": \"{@name}\", \"missing\": \"{@missing}\"}",
                Map.of("name", "alice"),
                Map.of("name", "alice", "missing", null)); passed++; }
        catch (Throwable t) { failed++; t.printStackTrace(); }

        System.out.println();
        System.out.println("Passed: " + passed + " / 5");
        if (failed > 0) System.exit(1);
    }

    @SuppressWarnings("unchecked")
    private static void check(String name, RenderEngine e, String template,
                             Object data, Object expected) {
        Object actual = e.render(template, _toMap(data));
        if (!equalsDeep(actual, expected)) {
            throw new AssertionError(name + ":\n  expected: " + expected + "\n  actual:   " + actual);
        }
        System.out.println("[" + name + "] OK");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> _toMap(Object o) {
        if (o instanceof Map) return (Map<String, Object>) o;
        return Map.of();
    }

    private static boolean equalsDeep(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) {
            // null 容忍:如果 expected 包含 null,actual 包含 null 也算相等
            return false;
        }
        if (a instanceof Map && b instanceof Map) {
            Map<?, ?> ma = (Map<?, ?>) a;
            Map<?, ?> mb = (Map<?, ?>) b;
            if (ma.size() != mb.size()) return false;
            for (Object k : ma.keySet()) {
                if (!equalsDeep(ma.get(k), mb.get(k))) return false;
            }
            return true;
        }
        if (a instanceof List && b instanceof List) {
            List<?> la = (List<?>) a;
            List<?> lb = (List<?>) b;
            if (la.size() != lb.size()) return false;
            for (int i = 0; i < la.size(); i++) {
                if (!equalsDeep(la.get(i), lb.get(i))) return false;
            }
            return true;
        }
        return a.equals(b);
    }
}
