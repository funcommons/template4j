package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers IfRenderer all remaining branches (lines 46-70).
 * Each 'is' method has two branches (true/false).
 * The partial misses indicate the else-branch of each if-else chain.
 */
public class IfRendererAllBranchesTest {

    // Line 46: !true → test=false → else branch
    @Test
    public void testIfTrueWithFalseValue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "{= false }",
                  "is": "true",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "no");
    }

    // Line 55: !true with true value
    @Test
    public void testIfNotTrueWithTrueValue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "{= true }",
                  "is": "!true",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "no");
    }

    // Line 58: false with false value
    @Test
    public void testIfFalseWithTrueValue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "{= true }",
                  "is": "false",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "no");
    }

    // Line 61: !null with null value
    @Test
    public void testIfNotNullWithNullValue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "{@nonexistent}",
                  "is": "!null",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "no");
    }

    // Line 64: !blank with blank value
    @Test
    public void testIfNotBlankWithBlankValue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "",
                  "is": "!blank",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "no");
    }

    // Line 67: !empty with empty value
    @Test
    public void testIfNotEmptyWithEmptyValue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": [],
                  "is": "!empty",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "no");
    }

    // Line 70: !empty with non-empty value
    @Test
    public void testIfNotEmptyWithNonEmptyValue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": [1,2],
                  "is": "!empty",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "yes");
    }

    // Make sure true→false path works for each "is" type
    @Test
    public void testIfMethodWithMethodField() throws Exception {
        // method field overrides is
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "{= true }",
                  "is": "method",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        // "method" falls through to the default (not matched), test=false → "no"
        Assertions.assertEquals(r, "no");
    }
}
