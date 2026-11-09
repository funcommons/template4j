package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared test fixtures for template4j-core tests. Pull this in instead of
 * re-typing JSON template strings or building data maps inline — both are
 * noisy, error-prone, and change for unrelated reasons when refactoring.
 *
 * <h3>Templates</h3>
 * Constant JSON-as-string forms that exercise one component at a time. Each
 * accepts the data produced by the matching {@code data...()} helper so
 * adding a test case is "tmpl + data + assert".
 *
 * <h3>Data</h3>
 * Constructors return {@code Map<String,Object>}; the engine accepts Map
 * directly so no JSON serialization is needed on the test side.
 */
public final class TestTemplates {

    private TestTemplates() {}

    // ===== JSON template strings =====

    /** JSONPath lookup: {@code {"v":"{@user.name}"}}. */
    public static final String JSONPATH_LOOKUP = "{\"v\":\"{@user.name}\"}";
    /** Variable reference: {@code {"$x":"hello","v":"{$x}"}}. */
    public static final String VAR_REF = "{\"$x\":\"hello\",\"v\":\"{$x}\"}";
    /** Inline Groovy: {@code {"v":"{= 2 + 3 }"}}. */
    public static final String GROOVY_INLINE = "{\"v\":\"{= 2 + 3 }\"}";
    /** Block Groovy: {@code {"v":"{% return 11; %}"}}. */
    public static final String GROOVY_BLOCK = "{\"v\":\"{% return 11; %}\"}";
    /** Literal pass-through: {@code {"v":"<&not-an-expression&>"}}. */
    public static final String LITERAL_ANGLE = "{\"v\":\"<&not-an-expression&>\"}";
    /** Comment pass-through: {@code {"v":"{#@not-evaluated#}"}}. */
    public static final String LITERAL_HASH = "{\"v\":\"{#@not-evaluated#}\"}";

    /** Switch sibling-keys form, matched from docs example. */
    public static final String SWITCH_SIBLING_KEYS_DOC = "{\n" +
        "  \"@class\": \"switch\",\n" +
        "  \"select\": \"{@color}\",\n" +
        "  \"red\":   { \"code\": 0, \"result\": \"#0000ff\" },\n" +
        "  \"blue\":  { \"code\": 0, \"result\": \"#00ff00\" },\n" +
        "  \"green\": { \"code\": 0, \"result\": \"#ff0000\" },\n" +
        "  \"default\": { \"code\": -1, \"result\": \"不支持{@color}\" }\n" +
        "}";

    /** If-then-else with predicate. */
    public static String ifTemplate(String select, String is) {
        return "{\"@class\":\"if\",\"select\":\"" + select + "\",\"is\":\"" + is + "\"," +
            "\"then\":\"YES\",\"else\":\"NO\"}";
    }

    /** Each-over-array, returns the joined item names. */
    public static final String EACH_NAMES = "{\n" +
        "  \"@class\": \"each\",\n" +
        "  \"select\": \"{@users}\",\n" +
        "  \"body\": \"{item$name};\"\n" +
        "}";

    // ===== Data maps =====

    /** {@code {"user":{"name":"Alice","age":30}}} — typical user fixture. */
    public static Map<String, Object> dataWithUser(String name, int age) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("name", name);
        user.put("age", age);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", user);
        return data;
    }

    /** {@code {"users":[{"name":"A"},{"name":"B"}]}} — for each-over-list tests. */
    public static Map<String, Object> dataWithUsers(String... names) {
        List<Map<String, Object>> users = java.util.stream.IntStream.range(0, names.length)
            .mapToObj(i -> {
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("name", names[i]);
                return u;
            })
            .toList();
        Map<String, Object> data = new HashMap<>();
        data.put("users", users);
        return data;
    }

    /** {@code {"color":"red"}} — for switch sibling-keys tests. */
    public static Map<String, Object> dataWithColor(String color) {
        Map<String, Object> data = new HashMap<>();
        data.put("color", color);
        return data;
    }

    /** Empty data, useful as a default. */
    public static Map<String, Object> emptyData() {
        return new HashMap<>();
    }

    // ===== Assertion helpers =====

    /** Convenience: render a JSON template and assert a single string field. */
    public static void assertJsonField(Object rendered, String field, Object expected) {
        if (!(rendered instanceof JSONObject)) {
            throw new AssertionError("expected JSONObject, got " + rendered);
        }
        Object actual = ((JSONObject) rendered).get(field);
        if (!java.util.Objects.equals(actual, expected)) {
            throw new AssertionError("field '" + field + "': expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
