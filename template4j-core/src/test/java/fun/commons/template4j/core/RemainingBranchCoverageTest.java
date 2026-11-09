package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.utils.UString;
import fun.commons.template4j.core.beans.FuncFne;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers the 4 remaining single-branch gaps across the codebase:
 *
 * 1. SwitchRenderer.render() line 52: {@code !intStr.equals(field)} is false.
 *    When select is a plain integer-form string (e.g. "9"), the BigDecimal path
 *    produces intStr="9" which equals field="9", so the negation is false and
 *    the inner containsKey check is skipped.
 *
 * 2. LoaderUtil.loadResource() line 70: the {@code ||} short-circuit branch where
 *    the first isJsonObject is false but the second is true. This is unreachable
 *    because both operands call the same method on the same string (likely a bug:
 *    second call should be isJsonArray). Instead we cover the {@code result == null}
 *    false branch on line 80 by loading YAML-parseable content with no extension.
 *
 * 3. UString.relativeUrl() line 56: {@code rootUrl.matches("^[/\\\\]+.*$")} is false.
 *    When rootUrl is a plain relative path with no protocol and no leading slash
 *    (e.g. "a/b/"), none of the three prefix branches match, so head stays ""
 *    and leftUrl stays as rootUrl.
 *
 * 4. FuncFne.call() line 41: {@code val != null} is false in the else block.
 *    This branch is logically dead because {@code val == null} on line 20 causes
 *    a continue, so val is always non-null when the else block is reached.
 *    Not coverable, but we exercise the else block with additional types to
 *    confirm all reachable branches are hit.
 */
public class RemainingBranchCoverageTest {

    // ========== 1. SwitchRenderer: !intStr.equals(field) is false ==========

    /**
     * select is a string "9" (integer form, not a Number instance).
     * cases does NOT contain key "9", so the direct match on line 39 fails.
     * select is not instanceof Number, so line 43 is skipped.
     * BigDecimal("9").intValue() = 9, intStr = "9", intStr.equals(field) is true,
     * so !intStr.equals(field) is false -- the missed branch.
     * Falls through to default.
     */
    @Test
    public void testSwitchStringIntegerFormNoCaseMatch() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"9\",\"cases\":{\"8\":\"eight\"},\"default\":\"fallback\"}",
                Map.of());
        Assertions.assertEquals(r, "fallback");
    }

    /**
     * Same scenario but with no default -- should return null.
     */
    @Test
    public void testSwitchStringIntegerFormNoCaseMatchNoDefault() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"9\",\"cases\":{\"8\":\"eight\"}}",
                Map.of());
        Assertions.assertNull(r);
    }

    /**
     * select is "0" (integer form string), cases has no "0" key.
     * The BigDecimal path: intStr="0", !intStr.equals("0") is false.
     */
    @Test
    public void testSwitchStringZeroFormNoCaseMatch() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"0\",\"cases\":{\"1\":\"one\"},\"default\":\"none\"}",
                Map.of());
        Assertions.assertEquals(r, "none");
    }

    // ========== 2. LoaderUtil: result == null false (YAML-safe parse succeeds) ==========

    /**
     * Load content that is valid YAML (not a JSON object) with no file extension
     * and no resourceType. The code falls to the else block at line 66.
     * isJsonObject returns false for both calls (it's YAML, not JSON).
     * UJson.JSONFromYamlSafe succeeds and returns a non-null result,
     * so the {@code result == null} check on line 80 is false.
     */
    @Test
    public void testLoadResourceYamlContentNoExtensionResultNotNull() throws Exception {
        TemplateLoader loader = url -> "key: value\nnum: 42";
        Object result = LoaderUtil.loadResource(loader, "/noext", null);
        Assertions.assertTrue(result instanceof JSONObject, "Result should be a JSONObject from YAML parse");
        Assertions.assertEquals(((JSONObject) result).getString("key"), "value");
        Assertions.assertEquals(42, ((JSONObject) result).getIntValue("num"));
    }

    /**
     * Load content that is a valid JSON object string with no extension.
     * isJsonObject returns true (short-circuit on first call), so result
     * comes from JSON.parse -- result == null check is never reached.
     * This just confirms the JSON object path works.
     */
    @Test
    public void testLoadResourceJsonObjectNoExtension() throws Exception {
        TemplateLoader loader = url -> "{\"a\":1}";
        Object result = LoaderUtil.loadResource(loader, "/noext", null);
        Assertions.assertTrue(result instanceof JSONObject);
        Assertions.assertEquals(1, ((JSONObject) result).getIntValue("a"));
    }

    /**
     * Load content that is neither valid JSON object nor valid YAML,
     * with no extension and no resourceType. Falls to the else block,
     * isJsonObject returns false, JSONFromYamlSafe returns null,
     * so result == null is true and result becomes the raw string.
     */
    @Test
    public void testLoadResourcePlainTextNoExtensionResultNull() throws Exception {
        TemplateLoader loader = url -> "just some plain text";
        Object result = LoaderUtil.loadResource(loader, "/noext", null);
        Assertions.assertEquals(result, "just some plain text");
    }

    // ========== 3. UString.relativeUrl: rootUrl with no prefix (line 56 false) ==========

    /**
     * rootUrl is a plain relative path "a/b/" -- no protocol prefix, no leading slash.
     * - Line 44: does not match ^(https?|ftp|file)://.*$  -> false
     * - Line 53: does not match ^\w+:.*$                  -> false
     * - Line 56: does not match ^[/\\]+.*$                -> FALSE (the missed branch)
     * head stays "", leftUrl stays "a/b/".
     * Then url "c.json" is appended.
     */
    @Test
    public void testRelativeUrlPlainRelativeRootNoPrefix() {
        String result = UString.relativeUrl("a/b/", "c.json");
        Assertions.assertEquals(result, "a/b/c.json");
    }

    /**
     * rootUrl is just a single directory name "dir" -- no prefix at all.
     * All three prefix conditions are false, so line 56 false branch is taken.
     * leftPaths from "dir" gets removed by removeIf, result is just "file.json".
     */
    @Test
    public void testRelativeUrlSingleDirRootNoPrefix() {
        String result = UString.relativeUrl("dir", "file.json");
        Assertions.assertEquals(result, "file.json");
    }

    /**
     * rootUrl is "sub/dir/" with url "../sibling.json".
     * None of the three prefix patterns match.
     * rightPaths = ["..", "sibling.json"], rightPaths.get(0) = ".." which is not blank,
     * so the else branch processes ".." by removing the last leftPath.
     */
    @Test
    public void testRelativeUrlPlainRelativeRootWithDotDot() {
        String result = UString.relativeUrl("sub/dir/", "../sibling.json");
        Assertions.assertEquals(result, "sub/sibling.json");
    }

    // ========== 4. FuncFne: else block with various non-standard types ==========

    /**
     * The missed branch in FuncFne is {@code val != null} being false in the
     * else block (line 41), which is logically dead code. We exercise the else
     * block with additional types to confirm all reachable branches are covered.
     */

    /**
     * An arbitrary object type (e.g. Object[]) that is not CharSequence,
     * Collection, or Map. Hits the else block where val != null is true.
     */
    @Test
    public void testFuncFneCallWithObjectArray() {
        FuncFne fne = new FuncFne();
        Object[] arr = new Object[]{"x"};
        // Object[] is not CharSequence, not Collection, not Map -> else block
        Object result = fne.call(null, arr);
        Assertions.assertSame(arr, result);
    }

    /**
     * A java.util.Date object -- not CharSequence/Collection/Map -> else block.
     */
    @Test
    public void testFuncFneCallWithDate() {
        FuncFne fne = new FuncFne();
        java.util.Date date = new java.util.Date();
        Object result = fne.call(null, date);
        Assertions.assertSame(date, result);
    }

    /**
     * A Number (Double) -- not CharSequence/Collection/Map -> else block.
     * val != null is true (the only reachable branch).
     */
    @Test
    public void testFuncFneCallWithDouble() {
        FuncFne fne = new FuncFne();
        Object result = fne.call(null, 3.14);
        Assertions.assertEquals(3.14, result);
    }
}
