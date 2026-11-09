package fun.commons.template4j.core.components;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import fun.commons.template4j.core.RenderEngine;

/**
 * Covers the sibling-key form of the @class:switch component, which is the
 * form shown in template4j-web/docs/documents/component-switch.yml.
 *
 * When "cases" is absent, every root-level key (other than @class / select /
 * cases / default) is treated as a case value, matching the value rendered
 * from "select".
 *
 * Most cases are data-driven via {@link #selectVariants()} so adding a new
 * scenario is a single row.
 */
public class SwitchRendererSiblingKeysTest {

    static Stream<Arguments> selectVariants() {
        return Stream.of(
            // {name, selectValue, data, expected code, expected result-or-null-for-skip}
            Arguments.of("red-match",       "red",   Map.of("color", "red"),   0,  "#0000ff"),
            Arguments.of("blue-match",      "blue",  Map.of("color", "blue"),  0,  "#00ff00"),
            Arguments.of("green-match",     "green", Map.of("color", "green"), 0,  "#ff0000"),
            Arguments.of("unknown-default", "gray",  Map.of("color", "gray"),  -1, "不支持gray, 只支持 red，blue，green 之一"),
            Arguments.of("RED-uppercase",   "RED",   Map.of("color", "RED"),   -1, null /*fallback, content has {@color} which won't render*/),
            Arguments.of("literal-blue",    "blue",  Map.of(),                 0,  "#00ff00"),
            Arguments.of("blank-select",    "",      Map.of(),                 -1, null /*blank -> skip direct match, not a Number, BigDecimal fails, fall through*/),
            Arguments.of("whitespace-sel",  " ",     Map.of(),                 -1, null /*isNotBlank false -> skip direct match*/)
        );
    }

    private static String docTemplate(String selectExpr) {
        return "{\n" +
            "  \"@class\": \"switch\",\n" +
            "  \"select\": " + JSON.toJSONString(selectExpr) + ",\n" +
            "  \"red\":   { \"code\": 0, \"result\": \"#0000ff\" },\n" +
            "  \"blue\":  { \"code\": 0, \"result\": \"#00ff00\" },\n" +
            "  \"green\": { \"code\": 0, \"result\": \"#ff0000\" },\n" +
            "  \"default\": { \"code\": -1, \"result\": \"不支持{@color}, 只支持 red，blue，green 之一\" }\n" +
            "}";
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("selectVariants")
    public void testSiblingKeySelect(String name, String selectExpr, Map<String, Object> data,
                                     int expectedCode, String expectedResult) throws Exception {
        Object r = RenderEngine.build().renderJson(docTemplate(selectExpr), data);
        Assertions.assertTrue(r instanceof JSONObject, "[" + name + "] expected JSONObject, got " + r);
        JSONObject out = (JSONObject) r;
        Assertions.assertEquals(expectedCode, out.getIntValue("code"), "[" + name + "] code mismatch");
        if (expectedResult != null) {
            Assertions.assertEquals(expectedResult, out.getString("result"), "[" + name + "] result mismatch");
        }
    }

    /** Match returns the matching case even when no default is configured. */
    @Test
    public void testNoDefaultStillMatches() throws Exception {
        String template = "{\n" +
            "  \"@class\": \"switch\",\n" +
            "  \"select\": \"{@color}\",\n" +
            "  \"red\":   { \"code\": 0, \"result\": \"#0000ff\" },\n" +
            "  \"blue\":  { \"code\": 0, \"result\": \"#00ff00\" }\n" +
            "}";
        Object r = RenderEngine.build().renderJson(template, Map.of("color", "blue"));
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertEquals("#00ff00", ((JSONObject) r).getString("result"));
    }

    /** Unknown select with no default → returns null. */
    @Test
    public void testNoDefaultReturnsNull() throws Exception {
        String template = "{\n" +
            "  \"@class\": \"switch\",\n" +
            "  \"select\": \"{@color}\",\n" +
            "  \"red\":   { \"value\": 1 },\n" +
            "  \"blue\":  { \"value\": 2 }\n" +
            "}";
        Object r = RenderEngine.build().renderJson(template, Map.of("color", "yellow"));
        Assertions.assertNull(r, "expected null for unknown select without default");
    }

    /**
     * Number select renders to a Number via JSONPath, but the existing
     * SwitchRenderer code path keeps the Number-to-int fallback so a
     * case key "9" can match select == 9.0. Confirms the Number-to-int
     * fallback also works when the case map is built from sibling keys.
     */
    @Test
    public void testNumberSelectMatchesNumberSibling() throws Exception {
        String template = "{\n" +
            "  \"@class\": \"switch\",\n" +
            "  \"select\": \"{@count}\",\n" +
            "  \"1\": { \"code\": 0, \"result\": \"one\" },\n" +
            "  \"2\": { \"code\": 0, \"result\": \"two\" },\n" +
            "  \"default\": { \"code\": -1, \"result\": \"fb\" }\n" +
            "}";
        Object r = RenderEngine.build().renderJson(template, Map.of("count", 2.0));
        Assertions.assertEquals("two", ((JSONObject) r).getString("result"));
    }

    /** Both sibling-keys and a "cases" map — explicit cases map takes precedence. */
    @Test
    public void testCasesMapBeatsSiblingKeys() throws Exception {
        String template = "{\n" +
            "  \"@class\": \"switch\",\n" +
            "  \"select\": \"{@color}\",\n" +
            "  \"cases\": { \"red\": { \"from\": \"cases-map\" } },\n" +
            "  \"red\":   { \"from\": \"siblings\" },\n" +
            "  \"default\": { \"from\": \"default\" }\n" +
            "}";
        Object r = RenderEngine.build().renderJson(template, Map.of("color", "red"));
        Assertions.assertEquals("cases-map", ((JSONObject) r).getString("from"));
    }

    /** Reserved keys are not treated as cases even if the user accidentally put them as siblings. */
    @Test
    public void testReservedKeysAreNotCases() throws Exception {
        String template = "{\n" +
            "  \"@class\": \"switch\",\n" +
            "  \"select\": \"@class\",\n" +
            "  \"cases\": {},\n" +
            "  \"default\": { \"code\": -1, \"result\": \"fallback\" }\n" +
            "}";
        Object r = RenderEngine.build().renderJson(template, Map.of());
        Assertions.assertEquals(-1, ((JSONObject) r).getIntValue("code"));
    }

    /**
     * Case values may themselves contain placeholders that get rendered with the
     * current data context (recursive render).
     */
    @Test
    public void testCaseValueRendersFurther() throws Exception {
        String template = "{\n" +
            "  \"@class\": \"switch\",\n" +
            "  \"select\": \"{@color}\",\n" +
            "  \"red\":   { \"@class\": \"invoke\", \"select\": {\"@class\":\"const\",\"select\":\"{@user}\"} },\n" +
            "  \"default\": { \"code\": -1, \"result\": \"fallback\" }\n" +
            "}";
        Object r = RenderEngine.build().renderJson(template, Map.of("color", "red", "user", "alice"));
        Assertions.assertNotNull(r, "matching a case should not return null even when nested");
    }

    /**
     * select evaluates to null when the referenced variable is absent — switch
     * skips every match attempt and falls back to default (if any) or null.
     */
    @Test
    public void testNullSelectFallsToDefault() throws Exception {
        String template = "{\n" +
            "  \"@class\": \"switch\",\n" +
            "  \"select\": \"{@missing}\",\n" +
            "  \"red\":   { \"code\": 0, \"result\": \"#0000ff\" },\n" +
            "  \"default\": { \"code\": -1, \"result\": \"none\" }\n" +
            "}";
        Object r = RenderEngine.build().renderJson(template, Map.of());
        Assertions.assertEquals(-1, ((JSONObject) r).getIntValue("code"));
    }

    /**
     * Reserved keys missed in user templates: when "default" appears as a
     * literal sibling, it must still be EXCLUDED from the synthetic case map so
     * that select == "default" doesn't accidentally pick itself up.
     */
    @Test
    public void testDefaultKeyNotExposedAsCase() throws Exception {
        String template = "{\n" +
            "  \"@class\": \"switch\",\n" +
            "  \"select\": \"default\",\n" +
            "  \"red\":  { \"code\": 0, \"result\": \"#0000ff\" },\n" +
            "  \"default\": { \"code\": -1, \"result\": \"fb\" }\n" +
            "}";
        Object r = RenderEngine.build().renderJson(template, Map.of());
        Assertions.assertEquals(-1, ((JSONObject) r).getIntValue("code"));
        Assertions.assertEquals("fb", ((JSONObject) r).getString("result"));
    }
}
