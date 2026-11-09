package fun.commons.template4j.core.components;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import fun.commons.template4j.core.RenderEngine;

/**
 * Covers the 3 missed branches in SwitchRenderer.render():
 *
 * 1. select instanceof Number = true  (Number-to-int fallback matches a case key)
 * 2. !intStr.equals(field) = true     (string "9.0" parsed via BigDecimal matches case "9")
 * 3. cases == null                    (no "cases" key present, falls through to default/null)
 */
public class SwitchRendererBranchTest {

    // Branch 1: select is a Number (Double 9.0), cases has key "9"
    // The direct String match fails ("9.0" != "9"), but the Number instanceof
    // block converts 9.0 -> intValue "9" and finds the case.
    @Test
    public void testNumberFallbackMatchesIntKey() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"{= @val}\",\"cases\":{\"9\":\"matched\"},\"default\":\"no\"}",
                Map.of("val", 9.0));
        Assertions.assertEquals(r, "matched");
    }

    // Branch 1 continued: select is a Number (Integer 5), direct String match "5" works
    // This also exercises the Number instanceof path but the direct match fires first.
    @Test
    public void testNumberDirectStringMatch() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"{= @val}\",\"cases\":{\"5\":\"five\"},\"default\":\"no\"}",
                Map.of("val", 5));
        Assertions.assertEquals(r, "five");
    }

    // Branch 1 continued: select is a Number but int key not in cases -> falls to default
    @Test
    public void testNumberFallbackNoIntKeyMatch() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"{= @val}\",\"cases\":{\"7\":\"seven\"},\"default\":\"fallback\"}",
                Map.of("val", 3.5));
        Assertions.assertEquals(r, "fallback");
    }

    // Branch 2: select is a string "9.0" (not a Number), BigDecimal parses it to int "9",
    // !intStr.equals(field) is true ("9" != "9.0"), and cases contains "9".
    @Test
    public void testStringDecimalMatchesIntKey() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"9.0\",\"cases\":{\"9\":\"matched\"},\"default\":\"no\"}",
                Map.of());
        Assertions.assertEquals(r, "matched");
    }

    // Branch 2 continued: string "9.0" but cases does NOT have key "9" -> default
    @Test
    public void testStringDecimalNoIntKeyMatch() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"9.0\",\"cases\":{\"8\":\"eight\"},\"default\":\"fallback\"}",
                Map.of());
        Assertions.assertEquals(r, "fallback");
    }

    // Branch 3: cases is null (no "cases" key in template) -> falls to default
    @Test
    public void testNullCasesFallsToDefault() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"hello\",\"default\":\"fallback\"}",
                Map.of());
        Assertions.assertEquals(r, "fallback");
    }

    // Branch 3 continued: cases is null and no default -> returns null
    @Test
    public void testNullCasesNoDefault() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"hello\"}",
                Map.of());
        Assertions.assertNull(r);
    }

    // Additional: select is null -> skips entire cases block, falls to default
    @Test
    public void testNullSelectFallsToDefault() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"{@missing}\",\"cases\":{\"a\":\"A\"},\"default\":\"fallback\"}",
                Map.of());
        Assertions.assertEquals(r, "fallback");
    }

    // Additional: select is null, no default -> returns null
    @Test
    public void testNullSelectNoDefault() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"{@missing}\",\"cases\":{\"a\":\"A\"}}",
                Map.of());
        Assertions.assertNull(r);
    }

    // Additional: select is blank string (isNotBlank=false) -> skips direct match,
    // not a Number, BigDecimal parse of "" throws NumberFormatException (caught),
    // falls to default
    @Test
    public void testBlankSelectFallsToDefault() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"\",\"cases\":{\"\":\"empty\"},\"default\":\"fallback\"}",
                Map.of());
        Assertions.assertEquals(r, "fallback");
    }

    // Additional: Number fallback with object value in case
    @Test
    public void testNumberFallbackWithObjectValue() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"{= @val}\",\"cases\":{\"3\":{\"label\":\"three\"}},\"default\":{\"label\":\"other\"}}",
                Map.of("val", 3.0));
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertEquals(((JSONObject) r).getString("label"), "three");
    }

    // Additional: string "10.00" matches case "10" via BigDecimal path
    @Test
    public void testStringDecimalWithTrailingZeros() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"switch\",\"select\":\"10.00\",\"cases\":{\"10\":\"matched\"},\"default\":\"no\"}",
                Map.of());
        Assertions.assertEquals(r, "matched");
    }
}
